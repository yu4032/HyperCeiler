/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */

#include <android/log.h>
#include <dlfcn.h>
#include <jni.h>
#include <link.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

namespace {

constexpr const char* kTag = "HyperCeiler-IconTitle8";
constexpr const char* kLauncherLibrary = "libapp_launcher.so";
constexpr const char* kGetTitleSymbol = "ShortcutInfo_get_title";
constexpr const char* kGetPackageSymbol = "ShortcutInfo_get_package_name";

using HookFunType = int (*)(void* function, void* replacement, void** backup);
using UnhookFunType = int (*)(void* function);
using NativeOnModuleLoaded = void (*)(const char* name, void* handle);

struct NativeAPIEntries {
    uint32_t version;
    HookFunType hook_func;
    UnhookFunType unhook_func;
};

struct BridgeStringResult {
    uint8_t tag;
    uint8_t padding[7];
    const char* data;
    uintptr_t length;
};

static_assert(sizeof(BridgeStringResult) == 24, "Unexpected FRB string result ABI");

using ShortcutStringGetter = BridgeStringResult (*)(void* shortcut_info);
using TitleMap = std::unordered_map<std::string, std::string>;

HookFunType g_hook_func = nullptr;
ShortcutStringGetter g_original_get_title = nullptr;
ShortcutStringGetter g_get_package_name = nullptr;
std::atomic<bool> g_enabled{false};
std::mutex g_install_mutex;
std::shared_ptr<const TitleMap> g_titles = std::make_shared<const TitleMap>();

// The analyzed 8.01 Dart caller copies the returned UTF-8 bytes immediately
// after ShortcutInfo_get_title returns. Keep the selected immutable snapshot
// alive across that return boundary so replacement.data cannot dangle during
// the caller's malloc/memcpy sequence, even if preferences refresh concurrently.
thread_local std::shared_ptr<const TitleMap> g_thread_titles;

bool ends_with(const char* value, const char* suffix) {
    if (value == nullptr || suffix == nullptr) return false;
    const size_t value_length = std::strlen(value);
    const size_t suffix_length = std::strlen(suffix);
    return value_length >= suffix_length
        && std::memcmp(value + value_length - suffix_length, suffix, suffix_length) == 0;
}

void log_symbol_origin(const char* symbol_name, void* symbol) {
    Dl_info info{};
    if (symbol != nullptr && dladdr(symbol, &info) != 0 && info.dli_fname != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Resolved %s=%p provider=%s base=%p",
            symbol_name, symbol, info.dli_fname, info.dli_fbase);
        return;
    }

    __android_log_print(ANDROID_LOG_WARN, kTag,
        "Resolved %s=%p but dladdr could not identify its provider",
        symbol_name, symbol);
}

void append_utf8(std::string& output, uint32_t code_point) {
    if (code_point <= 0x7f) {
        output.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7ff) {
        output.push_back(static_cast<char>(0xc0 | (code_point >> 6)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    } else if (code_point <= 0xffff) {
        output.push_back(static_cast<char>(0xe0 | (code_point >> 12)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    } else {
        output.push_back(static_cast<char>(0xf0 | (code_point >> 18)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 12) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | ((code_point >> 6) & 0x3f)));
        output.push_back(static_cast<char>(0x80 | (code_point & 0x3f)));
    }
}

std::string to_utf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};

    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t code_point = chars[i];
        if (code_point >= 0xd800 && code_point <= 0xdbff && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xdc00 && low <= 0xdfff) {
                code_point = 0x10000 + ((code_point - 0xd800) << 10) + (low - 0xdc00);
                ++i;
            }
        }
        append_utf8(output, code_point);
    }
    env->ReleaseStringChars(value, chars);
    return output;
}

BridgeStringResult hooked_get_title(void* shortcut_info) {
    BridgeStringResult original{};
    if (g_original_get_title == nullptr) return original;
    original = g_original_get_title(shortcut_info);

    if (!g_enabled.load(std::memory_order_acquire) || g_get_package_name == nullptr) {
        return original;
    }

    const BridgeStringResult package_result = g_get_package_name(shortcut_info);
    if (package_result.tag != 0 || package_result.data == nullptr || package_result.length == 0) {
        return original;
    }

    g_thread_titles = std::atomic_load_explicit(&g_titles, std::memory_order_acquire);
    if (!g_thread_titles || g_thread_titles->empty()) return original;

    const std::string package_name(package_result.data, package_result.length);
    const auto custom_title = g_thread_titles->find(package_name);
    if (custom_title == g_thread_titles->end()) return original;

    BridgeStringResult replacement{};
    replacement.tag = 0;
    replacement.data = custom_title->second.data();
    replacement.length = custom_title->second.size();
    return replacement;
}

bool try_install_from_handle(void* handle) {
    if (!g_enabled.load(std::memory_order_acquire) || g_hook_func == nullptr || handle == nullptr) {
        return false;
    }
    if (g_original_get_title != nullptr) return true;

    std::lock_guard<std::mutex> lock(g_install_mutex);
    if (g_original_get_title != nullptr) return true;

    void* title_symbol = dlsym(handle, kGetTitleSymbol);
    void* package_symbol = dlsym(handle, kGetPackageSymbol);

    // This 8.01 libapp_launcher.so imports the ShortcutInfo bridge functions
    // rather than defining them. If Android's handle scope does not expose the
    // provider directly, resolve from the already-loaded global namespace only
    // after the launcher library itself has been positively identified.
    if (title_symbol == nullptr) title_symbol = dlsym(RTLD_DEFAULT, kGetTitleSymbol);
    if (package_symbol == nullptr) package_symbol = dlsym(RTLD_DEFAULT, kGetPackageSymbol);

    if (title_symbol == nullptr || package_symbol == nullptr) {
        __android_log_print(ANDROID_LOG_WARN, kTag,
            "HyperOS 8 launcher symbols are not available yet: title=%p package=%p",
            title_symbol, package_symbol);
        return false;
    }

    log_symbol_origin(kGetTitleSymbol, title_symbol);
    log_symbol_origin(kGetPackageSymbol, package_symbol);

    g_get_package_name = reinterpret_cast<ShortcutStringGetter>(package_symbol);
    const int result = g_hook_func(
        title_symbol,
        reinterpret_cast<void*>(hooked_get_title),
        reinterpret_cast<void**>(&g_original_get_title)
    );
    if (result != 0 || g_original_get_title == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
            "Failed to hook %s: result=%d backup=%p",
            kGetTitleSymbol, result, reinterpret_cast<void*>(g_original_get_title));
        g_original_get_title = nullptr;
        return false;
    }

    __android_log_print(ANDROID_LOG_INFO, kTag,
        "Installed HyperOS 8 icon-title hook: title=%p package=%p",
        title_symbol, package_symbol);
    return true;
}

struct FindLauncherContext {
    bool installed = false;
};

int find_loaded_launcher(dl_phdr_info* info, size_t, void* data) {
    auto* context = static_cast<FindLauncherContext*>(data);
    if (context == nullptr || info == nullptr || !ends_with(info->dlpi_name, kLauncherLibrary)) {
        return 0;
    }

    void* handle = dlopen(info->dlpi_name, RTLD_NOW | RTLD_NOLOAD);
    if (handle != nullptr) {
        context->installed = try_install_from_handle(handle);
        dlclose(handle);
    }
    return context->installed ? 1 : 0;
}

bool install_for_loaded_launcher() {
    FindLauncherContext context;
    dl_iterate_phdr(find_loaded_launcher, &context);
    return context.installed;
}

void on_library_loaded(const char* name, void* handle) {
    if (!g_enabled.load(std::memory_order_acquire) || !ends_with(name, kLauncherLibrary)) return;
    try_install_from_handle(handle);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sevtinge_hyperceiler_libhook_rules_home_title_IconTitleNativeBridge_nativeInstall(
    JNIEnv*, jclass) {
    g_enabled.store(true, std::memory_order_release);
    const bool installed_now = install_for_loaded_launcher();
    if (!installed_now && g_hook_func != nullptr) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
            "Native hook armed; waiting for %s to load", kLauncherLibrary);
    }
    return g_hook_func != nullptr ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sevtinge_hyperceiler_libhook_rules_home_title_IconTitleNativeBridge_nativeUpdateTitles(
    JNIEnv* env, jclass, jobjectArray package_names, jobjectArray titles) {
    auto next = std::make_shared<TitleMap>();
    if (package_names != nullptr && titles != nullptr) {
        const jsize count = std::min(
            env->GetArrayLength(package_names),
            env->GetArrayLength(titles)
        );
        next->reserve(static_cast<size_t>(count));
        for (jsize i = 0; i < count; ++i) {
            auto package_name = static_cast<jstring>(env->GetObjectArrayElement(package_names, i));
            auto title = static_cast<jstring>(env->GetObjectArrayElement(titles, i));
            std::string package_utf8 = to_utf8(env, package_name);
            std::string title_utf8 = to_utf8(env, title);
            if (package_name != nullptr) env->DeleteLocalRef(package_name);
            if (title != nullptr) env->DeleteLocalRef(title);
            if (!package_utf8.empty() && !title_utf8.empty()) {
                (*next)[std::move(package_utf8)] = std::move(title_utf8);
            }
        }
    }

    std::shared_ptr<const TitleMap> snapshot = next;
    std::atomic_store_explicit(&g_titles, std::move(snapshot), std::memory_order_release);
    __android_log_print(ANDROID_LOG_INFO, kTag, "Updated custom title snapshot: %zu entries", next->size());
}

extern "C" __attribute__((visibility("default"))) __attribute__((used))
NativeOnModuleLoaded native_init(const NativeAPIEntries* entries) {
    if (entries == nullptr || entries->hook_func == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Native Xposed API is unavailable");
        return on_library_loaded;
    }
    g_hook_func = entries->hook_func;
    __android_log_print(ANDROID_LOG_INFO, kTag, "Native Xposed API attached: version=%u", entries->version);
    if (g_enabled.load(std::memory_order_acquire)) install_for_loaded_launcher();
    return on_library_loaded;
}

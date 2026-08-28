package com.sevtinge.hyperceiler.libhook.rules.systemframework.home;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class MiuiHomeIconTitlePolicy {
    static final String MIUI_HOME_PACKAGE = "com.miui.home";
    private static final char SEPARATOR = '฿';
    private static final long NATIVE_LAUNCHER_MIN_VERSION = 800000000L;
    private static final long NATIVE_LAUNCHER_MAX_VERSION_EXCLUSIVE = 900000000L;

    private MiuiHomeIconTitlePolicy() {
    }

    static boolean isSupportedLauncherVersion(long versionCode) {
        return versionCode >= NATIVE_LAUNCHER_MIN_VERSION
            && versionCode < NATIVE_LAUNCHER_MAX_VERSION_EXCLUSIVE;
    }

    static Map<String, String> parseOverrides(Set<String> rawEntries) {
        if (rawEntries == null || rawEntries.isEmpty()) {
            return Collections.emptyMap();
        }

        LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
        for (String entry : rawEntries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            int firstSeparator = entry.indexOf(SEPARATOR);
            int lastSeparator = entry.lastIndexOf(SEPARATOR);
            if (firstSeparator <= 0 || lastSeparator <= firstSeparator + 1) {
                continue;
            }

            String packageName = entry.substring(0, firstSeparator);
            String title = entry.substring(firstSeparator + 1, lastSeparator);
            if (packageName.isEmpty() || title.isEmpty()) {
                continue;
            }
            overrides.put(packageName, title);
        }
        if (overrides.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(overrides);
    }

    static String resolveOverride(
        String callingPackage,
        String packageName,
        boolean enabled,
        Map<String, String> overrides
    ) {
        if (!enabled || !MIUI_HOME_PACKAGE.equals(callingPackage)
            || packageName == null || overrides == null || overrides.isEmpty()) {
            return null;
        }
        return overrides.get(packageName);
    }

    static Set<String> affectedPackages(
        Map<String, String> before,
        Map<String, String> after
    ) {
        Map<String, String> oldValues = before != null ? before : Collections.emptyMap();
        Map<String, String> newValues = after != null ? after : Collections.emptyMap();

        LinkedHashSet<String> allPackages = new LinkedHashSet<>(oldValues.keySet());
        allPackages.addAll(newValues.keySet());

        LinkedHashSet<String> affected = new LinkedHashSet<>();
        for (String packageName : allPackages) {
            if (!Objects.equals(oldValues.get(packageName), newValues.get(packageName))) {
                affected.add(packageName);
            }
        }
        if (affected.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(affected);
    }
}

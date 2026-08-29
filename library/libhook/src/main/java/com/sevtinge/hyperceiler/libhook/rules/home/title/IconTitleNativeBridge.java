/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */
package com.sevtinge.hyperceiler.libhook.rules.home.title;

import java.util.Set;

/** JNI bridge for the no-Dex HyperOS 8 launcher title path. */
final class IconTitleNativeBridge {
    private static final int SUPPORTED_LAUNCHER_VERSION = 801025465;

    private static boolean loadAttempted;
    private static boolean loaded;

    private IconTitleNativeBridge() {
    }

    static boolean supportsLauncherVersion(int versionCode) {
        return versionCode == SUPPORTED_LAUNCHER_VERSION;
    }

    static synchronized boolean ensureLoaded() {
        if (loadAttempted) return loaded;
        loadAttempted = true;
        try {
            System.loadLibrary("hyperceiler_launcher");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        return loaded;
    }

    static boolean install() {
        return ensureLoaded() && nativeInstall();
    }

    static void updateTitles(Set<String> storedEntries) {
        if (!ensureLoaded()) return;
        IconTitleNativeConfig config = IconTitleNativeConfig.from(storedEntries);
        nativeUpdateTitles(config.packageNames(), config.titles());
    }

    private static native boolean nativeInstall();

    private static native void nativeUpdateTitles(String[] packageNames, String[] titles);
}

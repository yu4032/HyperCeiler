/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */
package com.sevtinge.hyperceiler.libhook.rules.home.title;

/** JNI bridge for the no-Dex HyperOS 8 launcher title-size path. */
final class TitleFontSizeNativeBridge {
    private static final int SUPPORTED_LAUNCHER_VERSION = 801025465;

    private TitleFontSizeNativeBridge() {
    }

    static boolean supportsLauncherVersion(int versionCode) {
        return versionCode == SUPPORTED_LAUNCHER_VERSION;
    }
}

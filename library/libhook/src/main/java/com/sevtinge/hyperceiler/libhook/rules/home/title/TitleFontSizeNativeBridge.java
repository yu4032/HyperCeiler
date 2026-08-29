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
    private static final int LEGACY_DEFAULT_SP = 12;
    private static final long TARGET_OFFSET = 0x101E71CL;
    private static final byte[] TARGET_FINGERPRINT = new byte[] {
        (byte) 0xfd, 0x79, (byte) 0xbf, (byte) 0xa9, (byte) 0xfd, 0x03, 0x0f, (byte) 0xaa,
        (byte) 0xe0, 0x03, 0x01, (byte) 0xaa, 0x38, 0x63, (byte) 0xe8, (byte) 0x97,
        0x01, (byte) 0xf0, 0x46, (byte) 0xb8, 0x21, (byte) 0x80, 0x1c, (byte) 0x8b,
        0x20, (byte) 0xf0, 0x41, (byte) 0xfc, (byte) 0xef, 0x03, 0x1d, (byte) 0xaa,
        (byte) 0xfd, 0x79, (byte) 0xc1, (byte) 0xa8, (byte) 0xc0, 0x03, 0x5f, (byte) 0xd6
    };

    private TitleFontSizeNativeBridge() {
    }

    static boolean supportsLauncherVersion(int versionCode) {
        return versionCode == SUPPORTED_LAUNCHER_VERSION;
    }

    static boolean canUseSharedSize(int desktopSp, int drawerSp) {
        return desktopSp != LEGACY_DEFAULT_SP && desktopSp == drawerSp;
    }

    static double scaleMultiplierFor(int requestedSp) {
        return requestedSp / (double) LEGACY_DEFAULT_SP;
    }

    static long targetOffset() {
        return TARGET_OFFSET;
    }

    static byte[] targetFingerprint() {
        return TARGET_FINGERPRINT.clone();
    }

    static synchronized boolean install(int versionCode, int desktopSp, int drawerSp) {
        if (!supportsLauncherVersion(versionCode) || !canUseSharedSize(desktopSp, drawerSp)) {
            return false;
        }
        if (!IconTitleNativeBridge.ensureLoaded()) {
            return false;
        }
        try {
            return nativeInstall(
                scaleMultiplierFor(desktopSp),
                TARGET_OFFSET,
                targetFingerprint()
            );
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static native boolean nativeInstall(double scale, long targetOffset, byte[] fingerprint);
}

package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TitleFontSizeNativeBridgeTest {

    @Test
    public void nativeLayoutHookIsRestrictedToAnalyzedLauncherBuild() {
        assertTrue(TitleFontSizeNativeBridge.supportsLauncherVersion(801025465));
        assertFalse(TitleFontSizeNativeBridge.supportsLauncherVersion(801025464));
        assertFalse(TitleFontSizeNativeBridge.supportsLauncherVersion(801025466));
        assertFalse(TitleFontSizeNativeBridge.supportsLauncherVersion(800000000));
        assertFalse(TitleFontSizeNativeBridge.supportsLauncherVersion(899999999));
    }
}

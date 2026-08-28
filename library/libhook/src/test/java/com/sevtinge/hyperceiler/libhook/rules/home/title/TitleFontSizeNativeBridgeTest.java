package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void sharedNativeGetterRequiresSameNonDefaultDesktopAndDrawerSize() {
        assertFalse(TitleFontSizeNativeBridge.canUseSharedSize(12, 12));
        assertFalse(TitleFontSizeNativeBridge.canUseSharedSize(14, 12));
        assertFalse(TitleFontSizeNativeBridge.canUseSharedSize(12, 14));
        assertFalse(TitleFontSizeNativeBridge.canUseSharedSize(14, 16));
        assertTrue(TitleFontSizeNativeBridge.canUseSharedSize(14, 14));
    }

    @Test
    public void nativeScalePreservesLauncherPixelsRelativeToLegacyTwelveSpDefault() {
        assertEquals(1.0, TitleFontSizeNativeBridge.scaleMultiplierFor(12), 0.000001);
        assertEquals(1.25, TitleFontSizeNativeBridge.scaleMultiplierFor(15), 0.000001);
        assertEquals(0.75, TitleFontSizeNativeBridge.scaleMultiplierFor(9), 0.000001);
    }
}

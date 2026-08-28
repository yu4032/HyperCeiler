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

    @Test
    public void analyzedAotGetterTargetIsPinnedByOffsetAndFullMachineCode() {
        assertEquals(0x101E71CL, TitleFontSizeNativeBridge.targetOffset());
        assertEquals(
            "fd79bfa9fd030faae00301aa3863e89701f046b821801c8b20f041fcef031daafd79c1a8c0035fd6",
            toHex(TitleFontSizeNativeBridge.targetFingerprint())
        );
    }

    @Test
    public void targetFingerprintCannotBeMutatedByCaller() {
        byte[] first = TitleFontSizeNativeBridge.targetFingerprint();
        first[0] = 0;
        assertEquals("fd", toHex(TitleFontSizeNativeBridge.targetFingerprint()).substring(0, 2));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}

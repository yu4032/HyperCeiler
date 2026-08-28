package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IconTitleApplicationDetectorTest {

    private final IconTitleApplicationDetector detector = new IconTitleApplicationDetector();

    public static class LegacyShortcut {
        public boolean isApplicatoin() {
            return true;
        }
    }

    public static class ModernShortcut {
        public boolean isApplication() {
            return true;
        }
    }

    public static class LegacyNonApplication {
        public boolean isApplicatoin() {
            return false;
        }
    }

    public static class ModernNonApplication {
        public boolean isApplication() {
            return false;
        }
    }

    public static class BothSpellingsShortcut {
        public boolean isApplicatoin() {
            return false;
        }

        public boolean isApplication() {
            return true;
        }
    }

    public static class UnsupportedShortcut {
    }

    @Test
    public void recognizesHyperOs450LegacyTypoMethod() {
        assertTrue(detector.isLauncherApplication(new LegacyShortcut()));
    }

    @Test
    public void fallsBackToModernSpelling() {
        assertTrue(detector.isLauncherApplication(new ModernShortcut()));
    }

    @Test
    public void preservesFalseResultForLegacyNonApplicationItems() {
        assertFalse(detector.isLauncherApplication(new LegacyNonApplication()));
    }

    @Test
    public void preservesFalseResultForModernNonApplicationItems() {
        assertFalse(detector.isLauncherApplication(new ModernNonApplication()));
    }

    @Test
    public void legacySpellingTakesPrecedenceWhenBothExist() {
        assertFalse(detector.isLauncherApplication(new BothSpellingsShortcut()));
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsShortcutWithoutApplicationTypeMethod() {
        detector.isLauncherApplication(new UnsupportedShortcut());
    }
}

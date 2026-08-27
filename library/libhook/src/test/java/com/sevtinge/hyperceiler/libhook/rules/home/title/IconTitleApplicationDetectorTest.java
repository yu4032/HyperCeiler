package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;

public class IconTitleApplicationDetectorTest {

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

    @Test
    public void recognizesHyperOs450LegacyTypoMethod() throws Exception {
        assertTrue(detect(new LegacyShortcut()));
    }

    @Test
    public void fallsBackToModernSpelling() throws Exception {
        assertTrue(detect(new ModernShortcut()));
    }

    @Test
    public void preservesFalseResultForNonApplicationItems() throws Exception {
        assertFalse(detect(new LegacyNonApplication()));
    }

    private boolean detect(Object shortcut) throws Exception {
        final Class<?> detectorClass;
        try {
            detectorClass = Class.forName(
                "com.sevtinge.hyperceiler.libhook.rules.home.title.IconTitleApplicationDetector"
            );
        } catch (ClassNotFoundException e) {
            fail("IconTitleApplicationDetector is missing");
            return false;
        }

        Method detector = detectorClass.getDeclaredMethod("isApplication", Object.class);
        detector.setAccessible(true);
        return (boolean) detector.invoke(null, shortcut);
    }
}

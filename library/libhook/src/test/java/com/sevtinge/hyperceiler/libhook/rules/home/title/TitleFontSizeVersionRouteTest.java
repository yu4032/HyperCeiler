package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.sevtinge.hyperceiler.libhook.appbase.mihome.Version;

import java.lang.reflect.Method;

import org.junit.Test;

public class TitleFontSizeVersionRouteTest {

    @Test
    public void legacyJavaRouteStopsBeforeRustLauncherBand() throws Exception {
        Version route = routeFor("initForNewHome");
        assertEquals(600000000, route.min());
        assertEquals(799999999, route.max());
    }

    @Test
    public void rustLauncherHasDedicatedHyperOs8Route() throws Exception {
        Version route = routeFor("initForRustHome");
        assertEquals(800000000, route.min());
        assertEquals(899999999, route.max());
    }

    private static Version routeFor(String methodName) throws Exception {
        Method method = TitleFontSize.class.getDeclaredMethod(methodName);
        Version route = method.getAnnotation(Version.class);
        assertNotNull(route);
        return route;
    }
}

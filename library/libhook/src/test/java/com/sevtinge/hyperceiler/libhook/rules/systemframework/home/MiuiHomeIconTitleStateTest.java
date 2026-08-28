package com.sevtinge.hyperceiler.libhook.rules.systemframework.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

public class MiuiHomeIconTitleStateTest {

    @Test
    public void enablingOverrideRefreshesPackageAndMakesTitleVisible() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();

        Set<String> affected = state.reload(true, records("com.example.a฿Alpha฿abcde"));

        assertEquals(Set.of("com.example.a"), affected);
        assertEquals("Alpha", state.resolve("com.miui.home", "com.example.a"));
    }

    @Test
    public void addingOverrideDoesNotRefreshUnchangedPackage() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();
        state.reload(true, records("com.example.a฿Alpha฿abcde"));

        Set<String> affected = state.reload(true, records(
            "com.example.a฿Alpha฿abcde",
            "com.example.b฿Beta฿fghij"));

        assertEquals(Set.of("com.example.b"), affected);
    }

    @Test
    public void renamingOverrideRefreshesOnlyRenamedPackage() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();
        state.reload(true, records("com.example.a฿Alpha฿abcde"));

        Set<String> affected = state.reload(true, records("com.example.a฿Renamed฿fghij"));

        assertEquals(Set.of("com.example.a"), affected);
        assertEquals("Renamed", state.resolve("com.miui.home", "com.example.a"));
    }

    @Test
    public void deletingOverrideRefreshesPackageAndRestoresNoOverride() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();
        state.reload(true, records("com.example.a฿Alpha฿abcde"));

        Set<String> affected = state.reload(true, Set.of());

        assertEquals(Set.of("com.example.a"), affected);
        assertNull(state.resolve("com.miui.home", "com.example.a"));
    }

    @Test
    public void disablingFeatureRefreshesEveryPreviouslyActiveOverride() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();
        state.reload(true, records(
            "com.example.a฿Alpha฿abcde",
            "com.example.b฿Beta฿fghij"));

        Set<String> affected = state.reload(false, records(
            "com.example.a฿Alpha฿abcde",
            "com.example.b฿Beta฿fghij"));

        assertEquals(Set.of("com.example.a", "com.example.b"), affected);
        assertNull(state.resolve("com.miui.home", "com.example.a"));
    }

    @Test
    public void identicalReloadDoesNothing() {
        MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();
        state.reload(true, records("com.example.a฿Alpha฿abcde"));

        assertTrue(state.reload(true, records("com.example.a฿Alpha฿abcde")).isEmpty());
    }

    private static Set<String> records(String... values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(value);
        }
        return result;
    }
}

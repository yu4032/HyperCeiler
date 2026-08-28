package com.sevtinge.hyperceiler.libhook.rules.systemframework.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class MiuiHomeIconTitlePolicyTest {

    @Test
    public void parseOverridesUsesExactPackageAndPreservesSeparatorInsideTitle() {
        Set<String> raw = new LinkedHashSet<>();
        raw.add("com.example.alpha฿Alpha฿abcde");
        raw.add("com.example.beta฿Beta฿With฿Separator฿fghij");

        Map<String, String> overrides = MiuiHomeIconTitlePolicy.parseOverrides(raw);

        assertEquals("Alpha", overrides.get("com.example.alpha"));
        assertEquals("Beta฿With฿Separator", overrides.get("com.example.beta"));
        assertNull(overrides.get("com.example"));
    }

    @Test
    public void parseOverridesIgnoresMalformedOrBlankEntries() {
        Set<String> raw = new LinkedHashSet<>();
        raw.add(null);
        raw.add("");
        raw.add("missing-separators");
        raw.add("com.example.empty฿฿abcde");
        raw.add("฿NoPackage฿abcde");
        raw.add("com.example.valid฿Valid฿abcde");

        Map<String, String> overrides = MiuiHomeIconTitlePolicy.parseOverrides(raw);

        assertEquals(Map.of("com.example.valid", "Valid"), overrides);
    }

    @Test
    public void resolveOverrideOnlyTargetsMiuiHomeAndEnabledFeature() {
        Map<String, String> overrides = Map.of("com.example.app", "Custom");

        assertEquals(
            "Custom",
            MiuiHomeIconTitlePolicy.resolveOverride(
                "com.miui.home", "com.example.app", true, overrides));
        assertNull(MiuiHomeIconTitlePolicy.resolveOverride(
            "com.android.settings", "com.example.app", true, overrides));
        assertNull(MiuiHomeIconTitlePolicy.resolveOverride(
            "com.miui.home", "com.example.app", false, overrides));
        assertNull(MiuiHomeIconTitlePolicy.resolveOverride(
            "com.miui.home", "com.example.other", true, overrides));
    }

    @Test
    public void affectedPackagesIncludesRemovedAddedAndRenamedOverrides() {
        Map<String, String> before = Map.of(
            "com.example.removed", "Old",
            "com.example.renamed", "Before");
        Map<String, String> after = Map.of(
            "com.example.renamed", "After",
            "com.example.added", "New");

        assertEquals(
            Set.of("com.example.removed", "com.example.renamed", "com.example.added"),
            MiuiHomeIconTitlePolicy.affectedPackages(before, after));
    }
}

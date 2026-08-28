package com.sevtinge.hyperceiler.libhook.rules.home.title;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

public class IconTitleNativeConfigTest {

    @Test
    public void convertsStoredEntriesToParallelNativeArrays() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add("com.example.first฿First Name฿com.example.first.MainActivity");
        entries.add("com.example.second฿Second Name฿com.example.second.MainActivity");

        IconTitleNativeConfig config = IconTitleNativeConfig.from(entries);

        assertArrayEquals(
            new String[]{"com.example.first", "com.example.second"},
            config.packageNames()
        );
        assertArrayEquals(
            new String[]{"First Name", "Second Name"},
            config.titles()
        );
    }

    @Test
    public void ignoresMalformedAndEmptyTitles() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add("broken");
        entries.add("com.example.empty฿฿com.example.empty.MainActivity");
        entries.add("com.example.ok฿Custom฿com.example.ok.MainActivity");

        IconTitleNativeConfig config = IconTitleNativeConfig.from(entries);

        assertEquals(1, config.packageNames().length);
        assertEquals("com.example.ok", config.packageNames()[0]);
        assertEquals("Custom", config.titles()[0]);
    }
}

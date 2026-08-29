/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 */
package com.sevtinge.hyperceiler.libhook.rules.home.title;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Converts the existing icon-title preference format into a native-friendly snapshot. */
final class IconTitleNativeConfig {
    private static final char DELIMITER = '฿';

    private final String[] packageNames;
    private final String[] titles;

    private IconTitleNativeConfig(String[] packageNames, String[] titles) {
        this.packageNames = packageNames;
        this.titles = titles;
    }

    static IconTitleNativeConfig from(Set<String> storedEntries) {
        if (storedEntries == null || storedEntries.isEmpty()) {
            return new IconTitleNativeConfig(new String[0], new String[0]);
        }

        List<Entry> parsed = new ArrayList<>();
        for (String storedEntry : storedEntries) {
            if (storedEntry == null) continue;

            int packageEnd = storedEntry.indexOf(DELIMITER);
            if (packageEnd <= 0) continue;

            int titleEnd = storedEntry.indexOf(DELIMITER, packageEnd + 1);
            if (titleEnd < 0) continue;

            String packageName = storedEntry.substring(0, packageEnd);
            String title = storedEntry.substring(packageEnd + 1, titleEnd);
            if (title.isEmpty()) continue;

            parsed.add(new Entry(packageName, title));
        }

        // Set implementations used by preferences do not promise stable iteration order.
        // Sorting makes JNI snapshots deterministic and keeps tests/reproducibility stable.
        Collections.sort(parsed);

        String[] packageNames = new String[parsed.size()];
        String[] titles = new String[parsed.size()];
        for (int i = 0; i < parsed.size(); i++) {
            Entry entry = parsed.get(i);
            packageNames[i] = entry.packageName();
            titles[i] = entry.title();
        }
        return new IconTitleNativeConfig(packageNames, titles);
    }

    String[] packageNames() {
        return packageNames.clone();
    }

    String[] titles() {
        return titles.clone();
    }

    private record Entry(String packageName, String title) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return packageName.compareTo(other.packageName);
        }
    }
}

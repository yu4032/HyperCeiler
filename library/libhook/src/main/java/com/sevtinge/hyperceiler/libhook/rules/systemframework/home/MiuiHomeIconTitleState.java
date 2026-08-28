package com.sevtinge.hyperceiler.libhook.rules.systemframework.home;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

final class MiuiHomeIconTitleState {
    private boolean enabled;
    private Map<String, String> overrides = Collections.emptyMap();

    synchronized Set<String> reload(boolean newEnabled, Set<String> rawEntries) {
        Map<String, String> parsed = MiuiHomeIconTitlePolicy.parseOverrides(rawEntries);
        Map<String, String> before = enabled ? overrides : Collections.emptyMap();
        Map<String, String> after = newEnabled ? parsed : Collections.emptyMap();

        Set<String> affected = MiuiHomeIconTitlePolicy.affectedPackages(before, after);
        enabled = newEnabled;
        overrides = parsed;
        return affected;
    }

    synchronized String resolve(String callingPackage, String packageName) {
        return MiuiHomeIconTitlePolicy.resolveOverride(
            callingPackage,
            packageName,
            enabled,
            overrides
        );
    }
}

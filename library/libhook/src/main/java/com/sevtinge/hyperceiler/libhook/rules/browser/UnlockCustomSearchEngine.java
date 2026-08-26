/*
 * This file is part of HyperCeiler.
 *
 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.browser;

import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * Restores Xiaomi Browser's built-in custom/third-party search engine path.
 *
 * <p>Browser v20.6 still contains SearchEngineDataProvider, SearchEngineBean,
 * CustomizeSearchEngineActivity and the custom-search Room database. The
 * feature is hidden by preference/configuration gates, so keep the browser's
 * own data path and only unlock those gates.</p>
 */
public class UnlockCustomSearchEngine extends BaseHook {
    private static final int MIN_CUSTOM_ENGINE_DISPLAY_COUNT = 2;

    private static final String[] CUSTOM_ENGINE_PREF_CLASSES = {
        "com.android.browser.data.pref.KVPrefs",
        "com.android.browser.search.SearchKVPrefs",
        "com.android.browser.search.interaction.settings.SearchModuleKVPrefs"
    };

    private static final String SEARCH_MODULE_SETTINGS =
        "com.android.browser.search.interaction.settings.SearchModuleSettings";

    @Override
    public void init() {
        IMethodHook forceCustomEngineVisible = new IMethodHook() {
            @Override
            public void before(HookParam param) {
                param.setResult(true);
            }
        };

        for (String className : CUSTOM_ENGINE_PREF_CLASSES) {
            hookAllMethods(className, "isCustomSearchEngineDisplay", forceCustomEngineVisible);
        }

        // Compatibility with browser builds that keep a second per-item display gate.
        hookAllMethods(
            "com.android.browser.data.pref.KVPrefs",
            "isDisplayCustomEngine",
            forceCustomEngineVisible
        );

        // Pad v20.6 ships two config-defined custom engines (Bing and Quark).
        // Preserve a larger remote value, and do not touch config_search_engine_display_count:
        // the Pad scene list contains duplicate entries and expanding the normal count would
        // surface duplicates.
        hookAllMethods(SEARCH_MODULE_SETTINGS, "getCustomSearchEngineDisplayCount", new IMethodHook() {
            @Override
            public void after(HookParam param) {
                Object result = param.getResult();
                if (result instanceof Integer count && count < MIN_CUSTOM_ENGINE_DISPLAY_COUNT) {
                    param.setResult(MIN_CUSTOM_ENGINE_DISPLAY_COUNT);
                }
            }
        });
    }
}

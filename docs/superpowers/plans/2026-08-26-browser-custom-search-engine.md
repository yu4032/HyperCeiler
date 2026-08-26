# Browser Custom Search Engine Unlock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore Xiaomi Browser's built-in third-party/custom search engine feature that is hidden by runtime configuration on Pad v20.6.920730.

**Architecture:** Keep Xiaomi Browser's own `SearchEngineDataProvider`, `SearchEngineBean`, Room database, and customization UI as the source of truth. Hook only the visibility gates and the custom-engine display-count getter; do not rewrite the bundled JSON or inject a parallel engine model.

**Tech Stack:** Java 25, HyperCeiler `BaseHook`, EzHookTool/libxposed, Android preference XML.

**Spec:** User-provided Xiaomi Browser Pad v20.6.920730 DEX files and `local_search_engine_tablet.json` / `local_search_engine_phone.json` archived under `浏览器反编译`.

## Global Constraints

- Target package remains `com.android.browser`.
- Do not modify Xiaomi Browser APK resources or remote JSON.
- Preserve the user's current/default search engine selection.
- Preserve normal `config_search_engine_display_count`; only unlock custom engines.
- Ensure both bundled custom definitions (Bing and Quark) can be shown by enforcing a minimum custom display count of 2.
- Missing host classes/methods must degrade to a no-op rather than crash the browser.

---

### Task 1: Add runtime unlock hook

**Files:**
- Create: `library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/browser/UnlockCustomSearchEngine.java`

**Interfaces:**
- Consumes: `BaseHook.hookAllMethods(...)` and Xiaomi Browser runtime classes.
- Produces: a hook that forces `isCustomSearchEngineDisplay()` true and returns at least 2 from `getCustomSearchEngineDisplayCount()`.

- [ ] Implement hooks for `KVPrefs`, `SearchKVPrefs`, and `SearchModuleKVPrefs` visibility gates.
- [ ] Add compatibility hook for legacy `KVPrefs.isDisplayCustomEngine(...)` when present.
- [ ] Hook `SearchModuleSettings.getCustomSearchEngineDisplayCount()` to return 2 when the configured value is lower.
- [ ] Compile with Canary/Debug CI.

### Task 2: Wire setting into Browser module

**Files:**
- Modify: `library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/Browser.java`
- Modify: `library/core/src/main/res/xml/browser.xml`
- Modify: `library/core/src/main/res/values/strings.xml`
- Modify: `library/core/src/main/res/values-zh-rCN/strings.xml`

**Interfaces:**
- Consumes: preference key `prefs_key_browser_unlock_custom_search_engine`.
- Produces: user-facing switch and conditional hook initialization.

- [ ] Add `UnlockCustomSearchEngine` import and `initHook` call using `PrefsBridge.getBoolean("browser_unlock_custom_search_engine")`.
- [ ] Add a default-off switch to Browser settings.
- [ ] Add English and Simplified Chinese title/summary resources.
- [ ] Compile resources and Java with CI.

### Task 3: Verify branch

**Files:**
- No production files beyond Tasks 1-2.

**Interfaces:**
- Consumes: GitHub Actions CI Build.
- Produces: a reviewable branch/PR with a build artifact when CI succeeds.

- [ ] Review diff for accidental changes to normal search-engine counts/defaults.
- [ ] Run PR CI (`assembleDebug`) and inspect failures if any.
- [ ] Keep the branch separate from `main` until integration is explicitly chosen.

package com.sevtinge.hyperceiler.libhook.rules.systemframework.home;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;

import com.sevtinge.hyperceiler.common.log.XposedLog;
import com.sevtinge.hyperceiler.common.utils.PrefsBridge;
import com.sevtinge.hyperceiler.common.utils.prefs.PrefType;
import com.sevtinge.hyperceiler.common.utils.prefs.PrefsChangeObserver;
import com.sevtinge.hyperceiler.libhook.base.BaseHook;

import java.util.List;
import java.util.Set;

import io.github.lingqiqi5211.ezhooktool.xposed.common.HookParam;
import io.github.lingqiqi5211.ezhooktool.xposed.java.IMethodHook;

/**
 * HyperOS 4 / MiuiHome 8.x no longer runs the launcher model in the normal ART app
 * process. Keep title customization in system_server instead: customize only the
 * LauncherApps results returned to com.miui.home, then reuse MiuiHome's own package
 * changed callback to make its native model reload a changed label.
 */
public class MiuiHomeIconTitleBridge extends BaseHook {
    private static final String LAUNCHER_APPS_IMPL =
        "com.android.server.pm.LauncherAppsService$LauncherAppsImpl";

    private static final String ENABLE_KEY =
        "home_title_title_icontitlecustomization_onoff";
    private static final String TITLE_SET_KEY =
        "home_title_title_icontitlecustomization";
    private static final String ENABLE_OBSERVER_KEY =
        "prefs_key_home_title_title_icontitlecustomization_onoff";
    private static final String TITLE_SET_OBSERVER_KEY =
        "prefs_key_home_title_title_icontitlecustomization";

    private final Object initializationLock = new Object();
    private final MiuiHomeIconTitleState state = new MiuiHomeIconTitleState();

    private volatile Context systemContext;
    private volatile boolean observersRegistered;
    private volatile Object miuiHomeListener;
    private volatile UserHandle miuiHomeUser;

    // Keep strong references for the lifetime of system_server.
    private PrefsChangeObserver titleObserver;
    private PrefsChangeObserver enabledObserver;

    @Override
    public void init() {
        if (findClassIfExists(LAUNCHER_APPS_IMPL) == null) {
            XposedLog.w(TAG, getPackageName(),
                "LauncherAppsImpl not found; skip native MiuiHome title bridge");
            return;
        }

        hookActivityList();
        hookResolvedActivity();
        hookLauncherListener();
    }

    private void hookActivityList() {
        hookAllMethods(LAUNCHER_APPS_IMPL, "getLauncherActivities", new IMethodHook() {
            @Override
            public void after(HookParam param) {
                if (!isMiuiHomeCaller(param) || !ensureReady(param.getThisObject())) return;

                Object result = param.getResult();
                if (result == null) return;

                try {
                    Object listObject = callMethod(result, "getList");
                    if (!(listObject instanceof List<?> list)) return;
                    for (Object item : list) {
                        applyOverride(item);
                    }
                } catch (Throwable t) {
                    XposedLog.e(TAG, getPackageName(),
                        "Failed to rewrite getLauncherActivities result", t);
                }
            }
        });
    }

    private void hookResolvedActivity() {
        hookAllMethods(LAUNCHER_APPS_IMPL, "resolveLauncherActivityInternal", new IMethodHook() {
            @Override
            public void after(HookParam param) {
                if (!isMiuiHomeCaller(param) || !ensureReady(param.getThisObject())) return;
                applyOverride(param.getResult());
            }
        });
    }

    private void hookLauncherListener() {
        hookAllMethods(LAUNCHER_APPS_IMPL, "addOnAppsChangedListener", new IMethodHook() {
            @Override
            public void after(HookParam param) {
                if (!isMiuiHomeCaller(param) || !ensureReady(param.getThisObject())) return;

                Object[] args = param.getArgs();
                if (args == null || args.length < 2 || args[1] == null) return;

                UserHandle callingUser = resolveCallingUser(param.getThisObject());
                if (callingUser == null) return;

                miuiHomeListener = args[1];
                miuiHomeUser = callingUser;
            }
        });

        hookAllMethods(LAUNCHER_APPS_IMPL, "removeOnAppsChangedListener", new IMethodHook() {
            @Override
            public void after(HookParam param) {
                Object[] args = param.getArgs();
                if (args == null || args.length == 0) return;
                Object listener = miuiHomeListener;
                if (listener != null && listener == args[0]) {
                    miuiHomeListener = null;
                    miuiHomeUser = null;
                }
            }
        });
    }

    private UserHandle resolveCallingUser(Object launcherAppsImpl) {
        try {
            Object value = callMethod(launcherAppsImpl, "injectCallingUserId");
            if (value instanceof Integer userId) {
                return UserHandle.of(userId);
            }
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(),
                "Failed to resolve MiuiHome calling user", t);
        }
        return null;
    }

    private boolean isMiuiHomeCaller(HookParam param) {
        Object[] args = param.getArgs();
        return args != null
            && args.length > 0
            && MiuiHomeIconTitlePolicy.MIUI_HOME_PACKAGE.equals(args[0]);
    }

    private boolean ensureReady(Object launcherAppsImpl) {
        Context context = systemContext;
        if (context == null) {
            try {
                Object value = getObjectField(launcherAppsImpl, "mContext");
                if (value instanceof Context found) {
                    context = found;
                    systemContext = found;
                }
            } catch (Throwable t) {
                XposedLog.e(TAG, getPackageName(),
                    "Failed to obtain LauncherAppsService context", t);
                return false;
            }
        }

        if (context == null || !isSupportedLauncher(context)) return false;
        ensureObservers(context);
        return true;
    }

    private boolean isSupportedLauncher(Context context) {
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                MiuiHomeIconTitlePolicy.MIUI_HOME_PACKAGE,
                0
            );
            return MiuiHomeIconTitlePolicy.isSupportedLauncherVersion(
                packageInfo.getLongVersionCode()
            );
        } catch (Throwable t) {
            XposedLog.w(TAG, getPackageName(),
                "Unable to determine MiuiHome version; keep original LauncherApps result", t);
            return false;
        }
    }

    private void ensureObservers(Context context) {
        if (observersRegistered) return;

        synchronized (initializationLock) {
            if (observersRegistered) return;

            // Initial state must be ready before the first LauncherApps result is rewritten.
            state.reload(
                PrefsBridge.getBoolean(ENABLE_KEY),
                PrefsBridge.getStringSet(TITLE_SET_KEY)
            );

            Handler handler = new Handler(context.getMainLooper());
            titleObserver = new PrefsChangeObserver(
                context,
                handler,
                true,
                TITLE_SET_OBSERVER_KEY
            ) {
                @Override
                public void onChange(PrefType type, Uri uri, String name, Object def) {
                    reloadAndNotify();
                }
            };

            enabledObserver = new PrefsChangeObserver(
                context,
                handler,
                true,
                PrefType.Boolean,
                ENABLE_OBSERVER_KEY,
                false
            ) {
                @Override
                public void onChange(PrefType type, Uri uri, String name, Object def) {
                    reloadAndNotify();
                }
            };

            observersRegistered = true;
        }
    }

    private void applyOverride(Object launcherActivityInfoInternal) {
        if (launcherActivityInfoInternal == null) return;

        try {
            Object value = callMethod(launcherActivityInfoInternal, "getActivityInfo");
            if (!(value instanceof ActivityInfo original)) return;

            String title = state.resolve(
                MiuiHomeIconTitlePolicy.MIUI_HOME_PACKAGE,
                original.packageName
            );
            if (title == null || title.isEmpty()) return;

            // Never mutate PackageManager's shared ActivityInfo. LauncherApps created this
            // wrapper for the caller, but cloning makes the ownership boundary explicit.
            ActivityInfo copy = new ActivityInfo(original);
            copy.nonLocalizedLabel = title;
            setObjectField(launcherActivityInfoInternal, "mActivityInfo", copy);
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(),
                "Failed to apply MiuiHome title override", t);
        }
    }

    private void reloadAndNotify() {
        Set<String> affected;
        try {
            affected = state.reload(
                PrefsBridge.getBoolean(ENABLE_KEY),
                PrefsBridge.getStringSet(TITLE_SET_KEY)
            );
        } catch (Throwable t) {
            XposedLog.e(TAG, getPackageName(), "Failed to reload icon title prefs", t);
            return;
        }

        if (affected.isEmpty()) return;

        Context context = systemContext;
        if (context == null || !isSupportedLauncher(context)) return;

        Object listener = miuiHomeListener;
        UserHandle user = miuiHomeUser;
        if (listener == null || user == null) return;

        for (String packageName : affected) {
            try {
                callMethod(listener, "onPackageChanged", user, packageName);
            } catch (Throwable t) {
                XposedLog.e(TAG, getPackageName(),
                    "Failed to notify MiuiHome about title change for " + packageName, t);
            }
        }
    }
}

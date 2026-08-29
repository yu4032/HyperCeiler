/*
 * This file is part of HyperCeiler.

 * HyperCeiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.

 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.

 * Copyright (C) 2023-2026 HyperCeiler Contributions
 */
package com.sevtinge.hyperceiler.libhook.rules.home.title

import android.util.TypedValue
import android.widget.TextView
import com.sevtinge.hyperceiler.common.log.XposedLog
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.appbase.mihome.HomeBaseHookNew
import com.sevtinge.hyperceiler.libhook.appbase.mihome.Version
import io.github.lingqiqi5211.ezhooktool.core.callStaticMethod
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.core.java.Constructors
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHooks
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createHook

class TitleFontSize : HomeBaseHookNew() {

    @Version(isPad = false, min = 600000000, max = 799999999)
    private fun initForNewHome() {
        val desktopSp = PrefsBridge.getInt("home_title_font_size", 12).toFloat()
        val drawerSp = PrefsBridge.getInt("home_drawer_title_font_size", 12).toFloat()
        if (desktopSp == 12f && drawerSp == 12f) {
            XposedLog.d(TAG, "No need to be hooked")
            return
        }

        val defaultSizePx by lazy {  // 必须在 hooker 内被 call，DeviceConfig 依赖 Context
            findClass("com.miui.home.common.device.DeviceConfigs").callStaticMethod("getIconTitleTextSize") as Float
        }

        val appIconClass =
            findClass("com.miui.home.launcher.AppIcon", lpparam.classLoader)  // 抽屉

        findClass("com.miui.home.launcher.ShortcutIcon").findMethod {
            name("onMeasure")
        }.createHook {
            before {
                (it.thisObject as TextView).setTextSize(0, defaultSizePx)
            }
            after {
                with((it.thisObject as TextView)) {
                    textSize = if (appIconClass.isInstance(this)) drawerSp else desktopSp
                }
            }
        }

        if (desktopSp == 12f) return
        // 文件夹标题
        runCatching {
            findClass("com.miui.home.icon.TitleTextView").findMethod {
                name("updateSizeOnIconSizeChanged")
            }.createHook {
                replace {
                    (it.thisObject as TextView).textSize = desktopSp
                    null
                }
            }

            Constructors.find(findClass("com.miui.home.icon.TitleTextView"))
                .toList().createAfterHooks {
                    (it.thisObject as TextView).textSize = desktopSp
                }
        }.onFailure {
            XposedLog.e(TAG, lpparam.packageName, "TitleFontSize failed", it)
        }
    }

    @Version(min = 800000000, max = 899999999)
    private fun initForRustHome() {
        val desktopSp = PrefsBridge.getInt("home_title_font_size", 12)
        val drawerSp = PrefsBridge.getInt("home_drawer_title_font_size", 12)
        val version = appVersion()

        if (!TitleFontSizeNativeBridge.supportsLauncherVersion(version)) {
            XposedLog.w(
                TAG,
                lpparam.packageName,
                "Rust launcher title-size hook is not analyzed for version $version, skip"
            )
            return
        }

        if (!TitleFontSizeNativeBridge.canUseSharedSize(desktopSp, drawerSp)) {
            if (desktopSp == 12 && drawerSp == 12) {
                XposedLog.d(TAG, lpparam.packageName, "No need to hook Rust launcher title size")
            } else {
                XposedLog.w(
                    TAG,
                    lpparam.packageName,
                    "Rust launcher 8.01 exposes one shared title-size getter; " +
                        "desktop and drawer must use the same non-default size " +
                        "(desktop=$desktopSp, drawer=$drawerSp)"
                )
            }
            return
        }

        if (TitleFontSizeNativeBridge.install(version, desktopSp, drawerSp)) {
            XposedLog.d(
                TAG,
                lpparam.packageName,
                "Rust launcher title-size native hook armed: ${desktopSp}sp"
            )
        } else {
            XposedLog.w(TAG, lpparam.packageName, "Failed to arm Rust launcher title-size native hook")
        }
    }

    override fun initBase() {
        runCatching {
            initForNewHome()
        }.onFailure {
            initForHomeLower9777()
        }
    }

    private fun initForHomeLower9777() {
        if (PrefsBridge.getInt("home_title_font_size", 12) == 12) return

        findClass("com.miui.home.launcher.common.Utilities")
            .findMethod {
                name("adaptTitleStyleToWallpaper")
            }.createAfterHook { param ->
                val mTitle = param.args[1] as? TextView
                if (mTitle != null && mTitle.id == mTitle.resources.getIdentifier("icon_title", "id", "com.miui.home")) {
                    mTitle.setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        PrefsBridge.getInt("home_title_font_size", 12).toFloat()
                    )
                }
            }
    }
}

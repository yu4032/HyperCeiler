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
package com.sevtinge.hyperceiler.libhook.rules.home.layout

import android.content.Context
import com.sevtinge.hyperceiler.common.log.XposedLog
import com.sevtinge.hyperceiler.common.utils.PrefsBridge
import com.sevtinge.hyperceiler.libhook.appbase.mihome.HomeBaseHookNew
import com.sevtinge.hyperceiler.libhook.appbase.mihome.Version
import com.sevtinge.hyperceiler.libhook.utils.api.DeviceHelper.Miui.isPad
import com.sevtinge.hyperceiler.libhook.utils.api.DisplayUtils
import io.github.lingqiqi5211.ezhooktool.core.callMethod
import io.github.lingqiqi5211.ezhooktool.core.findMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.afterHookMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.beforeHookMethod
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getIntField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.getObjectFieldAs
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.setIntField
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createAfterHook
import io.github.lingqiqi5211.ezhooktool.xposed.dsl.createBeforeHook
import kotlin.math.max

object LayoutRules : HomeBaseHookNew() {
    private const val PHONE_RULES = "com.miui.home.launcher.compat.PhoneDeviceRules"
    private const val PHONE_RULES_NEW = "com.miui.home.common.gridconfig.PhoneDeviceRules"
    private const val HOME_SETTINGS = "com.miui.home.settings.MiuiHomeSettings"

    private val isUnlockGridsHook by lazy {
        PrefsBridge.getBoolean("home_layout_unlock_grids_new")
    }
    private val isSetWSPaddingTopHook by lazy {
        PrefsBridge.getBoolean("home_layout_workspace_padding_top_enable")
    }
    private val isSetWSPaddingBottomHook by lazy {
        PrefsBridge.getBoolean("home_layout_workspace_padding_bottom_enable")
    }
    private val isSetWSPaddingSideHook by lazy {
        PrefsBridge.getBoolean("home_layout_workspace_padding_horizontal_enable")
    }
    private val isSetDockSideHook by lazy {
        PrefsBridge.getBoolean("home_layout_workspace_padding_horizontal_enable") &&
        PrefsBridge.getBoolean("home_dock_workspace_padding_horizontal_enable")
    }

    private var sCellCountX = 0
    private var sCellCountY = 0

    private var currentCellCountX = 0
    private var currentCellCountY = 0
    private var currentCellWidth = 0
    private var currentCellHeight = 0

    @Version(isPad = false, min = 600000000)
    private fun isOS3Hook() {
        if (isUnlockGridsHook) {
            sCellCountX = PrefsBridge.getInt("home_layout_unlock_grids_cell_x", 0)
            sCellCountY = PrefsBridge.getInt("home_layout_unlock_grids_cell_y", 0)
            XposedLog.d(TAG, lpparam.packageName, "Setup layout rules: ${sCellCountX}x${sCellCountY}")

            findClass(HOME_SETTINGS).findMethod {
                name("setUpScreenCellsConfig")
                parameterTypes(Boolean::class.java, Int::class.java)
            }.createBeforeHook { param ->
                val settings = param.thisObject

                val mMiuiHomeConfig = settings.getObjectField("mMiuiHomeConfig")
                val mScreenCellsConfig = settings.getObjectField("mScreenCellsConfig")

                mMiuiHomeConfig?.callMethod("removePreference", mScreenCellsConfig)
                XposedLog.d(
                    TAG, lpparam.packageName,
                    "Remove preference($mScreenCellsConfig) form MIUIHomeSettings"
                )
                param.result = null
            }
        }

        PHONE_RULES_NEW.afterHookMethod(
            "calGridSize",
            Context::class.java, Int::class.java, Int::class.java, Int::class.java, Boolean::class.java, Int::class.java
        ) { param ->
            val rules = param.thisObject

            val maxGridWidth = rules.getIntField("mScreenWidth")
            val mCellWidth = rules.getIntField("mCellWidth")
            val mCellHeight = rules.getIntField("mCellHeight")
            val mWorkspaceCellSideDefault = rules.getIntField("mWorkspaceCellSideDefault")
            val mCellCountY = rules.getIntField("mCellCountY")
            val mWorkspaceTopPadding = rules.callMethod("getWorkspacePaddingTop") as Int
            val mWorkspaceCellPaddingBottom =
                rules.getObjectFieldAs<Any>("mWorkspaceCellPaddingBottom")
                    .callMethod("getValue") as Int

            val sWorkspacePaddingTop = if (isSetWSPaddingTopHook) {
                DisplayUtils.dp2px(
                    PrefsBridge.getInt(
                        "home_layout_workspace_padding_top",
                        0
                    ).toFloat()
                )
            } else {
                -1
            }

            val sWorkspacePaddingBottom = if (isSetWSPaddingBottomHook) {
                DisplayUtils.dp2px(
                    PrefsBridge.getInt(
                        "home_layout_workspace_padding_bottom",
                        0
                    ).toFloat()
                )
            } else {
                -1
            }

            val sWorkspaceCellSide = if (isSetWSPaddingSideHook) {
                DisplayUtils.dp2px(
                    PrefsBridge.getInt(
                        "home_layout_workspace_padding_horizontal",
                        0
                    ).toFloat()
                )
            } else {
                -1
            }

            currentCellCountX = if (sCellCountX == 0) {
                param.args[1] as Int
            } else {
                sCellCountX
            }
            currentCellCountY = if (sCellCountY == 0) {
                mCellCountY
            } else {
                sCellCountY
            }

            currentCellWidth = mCellWidth
            currentCellHeight = mCellHeight

            val cellWorkspaceHeight = mCellWidth * mCellCountY

            if (isUnlockGridsHook || isSetWSPaddingSideHook) {
                currentCellWidth = (maxGridWidth - if (isSetWSPaddingSideHook) {
                    sWorkspaceCellSide
                } else {
                    mWorkspaceCellSideDefault
                }) / currentCellCountX
            }

            if (isUnlockGridsHook || isSetWSPaddingTopHook || isSetWSPaddingBottomHook) {
                currentCellHeight = (cellWorkspaceHeight + if (isSetWSPaddingTopHook) {
                    mWorkspaceTopPadding - sWorkspacePaddingTop
                } else {
                    0
                } + if (isSetWSPaddingBottomHook) {
                    mWorkspaceCellPaddingBottom - sWorkspacePaddingBottom
                } else {
                    0
                }) / currentCellCountY
            }

            val cellSize = max(currentCellWidth, currentCellHeight)
            rules.setIntField("mCellWidth", cellSize)
            rules.setIntField("mCellHeight", cellSize)

            if (isSetWSPaddingTopHook) {
                rules.getObjectFieldAs<Any>("mWorkspaceTopPadding")
                    .callMethod("setValue", sWorkspacePaddingTop)
            }

            if (isSetWSPaddingBottomHook) {
                rules.getObjectFieldAs<Any>("mWorkspaceCellPaddingBottom")
                    .callMethod("setValue", sWorkspacePaddingBottom)
            }

            if (isSetWSPaddingSideHook) {
                rules.setIntField(
                    "mWorkspaceCellSide",
                    (maxGridWidth - currentCellWidth * currentCellCountX) / 2
                )
            }

            XposedLog.i(
                TAG, lpparam.packageName,
                """ |
                    |Applied layout rules:
                    |  cellCountX    => $currentCellCountX
                    |  cellCountY    => $currentCellCountY
                    |  paddingTop    => $sWorkspacePaddingTop
                    |  paddingBottom => $sWorkspacePaddingBottom
                    |  cellSide      => $sWorkspaceCellSide
                    |  cellSizeO     => $mCellWidth
                    |  cellWidth     => $currentCellWidth
                    |  cellHeight    => $currentCellHeight
                """.trimMargin()
            )
        }

        GRID_CONFIG_NEW.beforeHookMethod("getCellWidth") { param ->
            if (currentCellWidth != 0) {
                param.result = currentCellWidth
            }
        }

        GRID_CONFIG_NEW.beforeHookMethod("getCellHeight") { param ->
            if (currentCellHeight != 0) {
                param.result = currentCellHeight
            }
        }

        GRID_CONFIG_NEW.beforeHookMethod("getCountCellX") { param ->
            if (isUnlockGridsHook && currentCellCountX != 0) {
                param.result = currentCellCountX
            }
        }

        GRID_CONFIG_NEW.beforeHookMethod("getCountCellY") { param ->
            if (isUnlockGridsHook && currentCellCountY != 0) {
                param.result = currentCellCountY
            }
        }

        findClass(DEVICE_CONFIG_NEW).findMethod { name("getMiuiWidgetSizeSpec"); paramCount(4) }.createAfterHook { param ->
                val gridConfig = param.args[0] ?: return@createAfterHook
                val spanX = param.args[1] as Int
                val cellWidth = gridConfig.callMethod("getCellWidth") as Int
                val iconSize = gridConfig.callMethod("getIconSize") as Int

                val widthSpec = cellWidth * spanX - (cellWidth - iconSize)
                val spec = param.result as Long
                param.result = (widthSpec.toLong() shl 32) or (spec and 0xFFFFFFFFL)
            }

        // Dock horizontal spacing: adjust icon margins, skip background view
        if (isSetDockSideHook) {
            findClass("com.miui.home.launcher.hotseats.HotSeats").findMethod {
                name("initContent")
            }.createAfterHook {
                val hotSeats = it.thisObject as android.view.ViewGroup
                val dockDp = PrefsBridge.getInt("home_dock_workspace_padding_horizontal", 0)
                val wsDp = PrefsBridge.getInt("home_layout_workspace_padding_horizontal", 0)
                val marginPx = DisplayUtils.dp2px(((dockDp - wsDp) / 2).toFloat())
                if (marginPx == 0) return@createAfterHook
                hotSeats.post {
                    adjustChildMargins(hotSeats, marginPx)
                }
            }
        }
    }

    private fun adjustChildMargins(parent: android.view.ViewGroup, marginPx: Int) {
        val bgOrigins = mutableMapOf<android.view.View, Pair<Int, Int>>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val lp = child.layoutParams
            if (lp is android.view.ViewGroup.MarginLayoutParams) {
                // Save original margins for potential background views
                if (child !is android.view.ViewGroup || child.childCount == 0) {
                    bgOrigins[child] = Pair(lp.marginStart, lp.marginEnd)
                }
                lp.marginStart += marginPx
                lp.marginEnd += marginPx
                child.layoutParams = lp
            }
            if (child is android.view.ViewGroup) {
                adjustChildMargins(child, marginPx)
            }
        }
        // Restore original margins on background views
        for ((bg, orig) in bgOrigins) {
            val lp = bg.layoutParams as? android.view.ViewGroup.MarginLayoutParams ?: continue
            lp.marginStart = orig.first
            lp.marginEnd = orig.second
            bg.layoutParams = lp
            break
        }
    }

    override fun initBase() {
        if (isUnlockGridsHook) {
            sCellCountX = PrefsBridge.getInt("home_layout_unlock_grids_cell_x", 0)
            sCellCountY = PrefsBridge.getInt("home_layout_unlock_grids_cell_y", 0)
            XposedLog.d(TAG, lpparam.packageName, "Setup layout rules: ${sCellCountX}x${sCellCountY}")

            findClass(HOME_SETTINGS).findMethod {
                name("setUpScreenCellsConfig")
                parameterTypes(Boolean::class.java, Int::class.java)
            }.createBeforeHook { param ->
                val settings = param.thisObject

                val mMiuiHomeConfig = settings.getObjectField("mMiuiHomeConfig")
                val mScreenCellsConfig = settings.getObjectField("mScreenCellsConfig")

                mMiuiHomeConfig?.callMethod("removePreference", mScreenCellsConfig)
                XposedLog.d(
                    TAG, lpparam.packageName,
                    "Remove preference($mScreenCellsConfig) form MIUIHomeSettings"
                )
                param.result = null
            }
        }

        if (!isPad()) {
            PHONE_RULES.afterHookMethod(
                "calGridSize",
                Context::class.java, Int::class.java, Int::class.java, Boolean::class.java
            ) { param ->
                val rules = param.thisObject

                val mMaxGridWidth = rules.getIntField("mMaxGridWidth")
                val mWorkspaceCellSideDefault = rules.getIntField("mWorkspaceCellSideDefault")
                val mCellSize = rules.getIntField("mCellSize")
                val mCellCountY = rules.getIntField("mCellCountY")
                val mWorkspaceTopPadding = rules.callMethod("getWorkspacePaddingTop") as Int
                val mWorkspaceCellPaddingBottom =
                    rules.getObjectFieldAs<Any>("mWorkspaceCellPaddingBottom")
                        .callMethod("getValue") as Int

                val sWorkspacePaddingTop = if (isSetWSPaddingTopHook) {
                    DisplayUtils.dp2px(
                        PrefsBridge.getInt(
                            "home_layout_workspace_padding_top",
                            0
                        ).toFloat()
                    )
                } else {
                    -1
                }

                val sWorkspacePaddingBottom = if (isSetWSPaddingBottomHook) {
                    DisplayUtils.dp2px(
                        PrefsBridge.getInt(
                            "home_layout_workspace_padding_bottom",
                            0
                        ).toFloat()
                    )
                } else {
                    -1
                }

                val sWorkspaceCellSide = if (isSetWSPaddingSideHook) {
                    DisplayUtils.dp2px(
                        PrefsBridge.getInt(
                            "home_layout_workspace_padding_horizontal",
                            0
                        ).toFloat()
                    )
                } else {
                    -1
                }

                currentCellCountX = if (sCellCountX == 0) {
                    param.args[1] as Int
                } else {
                    sCellCountX
                }
                currentCellCountY = if (sCellCountY == 0) {
                    mCellCountY
                } else {
                    sCellCountY
                }

                currentCellWidth = mCellSize
                currentCellHeight = mCellSize

                val cellWorkspaceHeight = mCellSize * mCellCountY

                if (isUnlockGridsHook || isSetWSPaddingSideHook) {
                    currentCellWidth = (mMaxGridWidth - if (isSetWSPaddingSideHook) {
                        sWorkspaceCellSide
                    } else {
                        mWorkspaceCellSideDefault
                    }) / currentCellCountX
                }

                if (isUnlockGridsHook || isSetWSPaddingTopHook || isSetWSPaddingBottomHook) {
                    currentCellHeight = (cellWorkspaceHeight + if (isSetWSPaddingTopHook) {
                        mWorkspaceTopPadding - sWorkspacePaddingTop
                    } else {
                        0
                    } + if (isSetWSPaddingBottomHook) {
                        mWorkspaceCellPaddingBottom - sWorkspacePaddingBottom
                    } else {
                        0
                    }) / currentCellCountY
                }

                rules.setIntField("mCellSize", max(currentCellWidth, currentCellHeight))

                if (isSetWSPaddingTopHook) {
                    rules.getObjectFieldAs<Any>("mWorkspaceTopPadding")
                        .callMethod("setValue", sWorkspacePaddingTop)
                }

                if (isSetWSPaddingBottomHook) {
                    rules.getObjectFieldAs<Any>("mWorkspaceCellPaddingBottom")
                        .callMethod("setValue", sWorkspacePaddingBottom)
                }

                if (isSetWSPaddingSideHook) {
                    rules.setIntField(
                        "mWorkspaceCellSide",
                        (mMaxGridWidth - currentCellWidth * currentCellCountX) / 2
                    )
                }

                XposedLog.i(
                    TAG, lpparam.packageName,
                    """ |
                        |Applied layout rules:
                        |  cellCountX    => $currentCellCountX
                        |  cellCountY    => $currentCellCountY
                        |  paddingTop    => $sWorkspacePaddingTop
                        |  paddingBottom => $sWorkspacePaddingBottom
                        |  cellSide      => $sWorkspaceCellSide
                        |  cellSizeO     => $mCellSize
                        |  cellWidth     => $currentCellWidth
                        |  cellHeight    => $currentCellHeight
                    """.trimMargin()
                )
            }
        }

        GRID_CONFIG_OLD.beforeHookMethod("getCellWidth") { param ->
            if (currentCellWidth != 0) {
                param.result = currentCellWidth
            }
        }

        GRID_CONFIG_OLD.beforeHookMethod("getCellHeight") { param ->
            if (currentCellHeight != 0) {
                param.result = currentCellHeight
            }
        }

        GRID_CONFIG_OLD.beforeHookMethod("getCountCellX") { param ->
            if (isUnlockGridsHook && currentCellCountX != 0) {
                param.result = currentCellCountX
            }
        }

        GRID_CONFIG_OLD.beforeHookMethod("getCountCellY") { param ->
            if (isUnlockGridsHook && currentCellCountY != 0) {
                param.result = currentCellCountY
            }
        }
    }

}

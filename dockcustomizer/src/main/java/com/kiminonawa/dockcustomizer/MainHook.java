package com.kiminonawa.dockcustomizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static View overlay;
    private static int bgW, bgH;
    private static float bgR = 30f;
    private static final float RADIUS_OFFSET = -1f;
    private static float gyroX, gyroY;
    private static long gyroTime;
    private static String lightMode = "fixed";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;
        XposedBridge.log("[DC] load");

        // Read config
        try {
            java.io.File cfg = new java.io.File("/sdcard/dock_light.txt");
            if (cfg.exists()) {
                java.io.FileInputStream fis = new java.io.FileInputStream(cfg);
                byte[] buf = new byte[16];
                int len = fis.read(buf);
                fis.close();
                lightMode = new String(buf, 0, len).trim();
            }
        } catch (Throwable ignored) {}
        XposedBridge.log("[DC] mode=" + lightMode);

        try {
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.Launcher", lpparam.classLoader,
                "setupViews", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            XposedBridge.log("[DC] setupViews");
                            if (overlay != null) return;
                            Object hotSeats = XposedHelpers.getObjectField(param.thisObject, "mHotSeats");
                            if (hotSeats == null) return;
                            View oldBg = (View) XposedHelpers.getObjectField(hotSeats, "mBlurBackground2");
                            if (oldBg == null) return;
                            ViewGroup parent = (ViewGroup) oldBg.getParent();
                            if (parent == null) return;

                            XposedBridge.log("[DC] creating overlay");
                            overlay = new View(oldBg.getContext()) {
                                @Override
                                protected void onDraw(Canvas canvas) {
                                    if (bgW < 1 || bgH < 1) return;
                                    float w = bgW, h = bgH, r = Math.max(0, bgR + RADIUS_OFFSET);
                                    float maxDim = Math.max(w, h);

                                    if ("none".equals(lightMode)) {
                                        // Simple white line only
                                        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        s.setStyle(Paint.Style.STROKE);
                                        s.setStrokeWidth(6f);
                                        s.setColor(Color.argb(200, 255, 255, 255));
                                        canvas.drawRoundRect(1, 1, w-1, h-1, r, r, s);
                                        return;
                                    }

                                    boolean dyn = "dynamic".equals(lightMode);
                                    float s1x = dyn ? w * (0.5f + gyroY * 0.3f) : w * 0.5f;
                                    float s1y = dyn ? h * (0.5f + gyroX * 0.3f) : h * 0.5f;

                                    // Base gray edge
                                    Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    base.setStyle(Paint.Style.STROKE);
                                    base.setStrokeWidth(6f);
                                    base.setColor(Color.argb(120, 255, 255, 255));
                                    canvas.drawRoundRect(1, 1, w-1, h-1, r, r, base);

                                    // RadialGradient highlight
                                    Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    s1p.setStyle(Paint.Style.STROKE);
                                    s1p.setStrokeWidth(6f);
                                    s1p.setShader(new RadialGradient(s1x, s1y, maxDim * 0.4f,
                                        new int[]{Color.argb(255, 255, 255, 255),
                                                  Color.argb(120, 255, 255, 255),
                                                  Color.argb(0, 255, 255, 255)},
                                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                                    canvas.drawRoundRect(1, 1, w-1, h-1, r, r, s1p);
                                }
                            };
                            overlay.setId(View.generateViewId());
                            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                                ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity);
                            parent.addView(overlay, lp);
                            XposedBridge.log("[DC] added overlay");
                            syncAll(oldBg);

                            // Gyro only for dynamic mode
                            if ("dynamic".equals(lightMode)) {
                                try {
                                    SensorManager sm = (SensorManager) oldBg.getContext()
                                        .getSystemService(android.content.Context.SENSOR_SERVICE);
                                    Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                                    if (gyro != null) {
                                        sm.registerListener(new SensorEventListener() {
                                            @Override public void onSensorChanged(SensorEvent e) {
                                                if (e.values.length < 3) return;
                                                long now = e.timestamp;
                                                if (gyroTime > 0) {
                                                    float dt = (now - gyroTime) / 1e9f;
                                                    if (dt > 0 && dt < 0.1f) {
                                                        gyroX = clamp(gyroX + e.values[0]*dt*2.4f, -1.35f,1.35f);
                                                        gyroY = clamp(gyroY + e.values[1]*dt*2.4f, -1.35f,1.35f);
                                                    }
                                                }
                                                gyroTime = now;
                                                if (overlay != null) overlay.postInvalidate();
                                            }
                                            @Override public void onAccuracyChanged(Sensor s, int a) {}
                                        }, gyro, SensorManager.SENSOR_DELAY_GAME);
                                        XposedBridge.log("[DC] gyro registered");
                                    }
                                } catch (Throwable e) {
                                    XposedBridge.log("[DC] sensor: " + e.getMessage());
                                }
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] err: " + e.getClass().getSimpleName() + " " + e.getMessage());
                        }
                    }
                });

            String cls = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundWidth", int.class, mkHook());
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundHeight", int.class, mkHook());
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundRadius", float.class, mkHook());
        } catch (Throwable e) {
            XposedBridge.log("[DC] init err: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static XC_MethodHook mkHook() {
        return new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }
        };
    }

    private static void syncAll(View oldBg) {
        if (overlay == null || oldBg == null) return;
        try {
            bgW = XposedHelpers.getIntField(oldBg, "mWidth");
            bgH = XposedHelpers.getIntField(oldBg, "mHeight");
            Object r = XposedHelpers.getObjectField(oldBg, "mCornerRadius");
            if (r instanceof Float) bgR = (Float) r;
            if (bgW <= 0) return;
            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
            if (lp != null) { lp.width = bgW; lp.height = bgH; overlay.setLayoutParams(lp); }
            overlay.invalidate();
        } catch (Throwable ignored) {}
    }
}

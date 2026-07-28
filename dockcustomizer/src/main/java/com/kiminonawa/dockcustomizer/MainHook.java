package com.kiminonawa.dockcustomizer;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
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
import java.io.File;
import java.io.FileInputStream;

public class MainHook implements IXposedHookLoadPackage {

    private static View overlay;
    private static int bgW, bgH;
    private static float bgR = 30f;
    private static float gyroX, gyroY;
    private static long gyroTime;
    private static String lightMode = "fixed";
    private static int blurRadius = 100;
    private static int heightOffset, widthOffset, cornerOffset = -1;
    private static final int SQUIRCLE_STROKE_OFF = 5;
    private static boolean useSquircle;

    private static int readInt(String path, int def) {
        try {
            File f = new File(path);
            if (f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[16]; int len = fis.read(buf); fis.close();
                return Integer.parseInt(new String(buf, 0, len).trim());
            }
        } catch (Throwable ignored) {}
        return def;
    }

    private static String readStr(String path, String def) {
        try {
            File f = new File(path);
            if (f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[16]; int len = fis.read(buf); fis.close();
                return new String(buf, 0, len).trim();
            }
        } catch (Throwable ignored) {}
        return def;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;
        XposedBridge.log("[DC] load");

        lightMode = readStr("/sdcard/dock_light.txt", "fixed");
        blurRadius = readInt("/sdcard/dock_blur_radius.txt", 100);
        heightOffset = readInt("/sdcard/dock_height_offset.txt", 0);
        widthOffset = readInt("/sdcard/dock_width_offset.txt", 0);
        cornerOffset = readInt("/sdcard/dock_corner_offset.txt", -1);
        useSquircle = "1".equals(readStr("/sdcard/dock_squircle.txt", "0"));
        XposedBridge.log("[DC] mode=" + lightMode + " blur=" + blurRadius + " ho=" + heightOffset + " wo=" + widthOffset);

        try {
            // Hook setBackgroundWidth to apply width offset
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                lpparam.classLoader, "setBackgroundWidth", int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (widthOffset != 0) {
                            p.args[0] = ((Integer) p.args[0]) + widthOffset;
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }
                });

            // Hook setBackgroundHeight to apply height offset
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                lpparam.classLoader, "setBackgroundHeight", int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (heightOffset != 0) {
                            p.args[0] = ((Integer) p.args[0]) + heightOffset;
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }
                });

            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                lpparam.classLoader, "setBackgroundRadius", float.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (cornerOffset != 0) {
                            p.args[0] = ((Float) p.args[0]) + cornerOffset;
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View) p.thisObject); }
                });

            // Hook updateRoundRect for squircle outline (disable shadow)
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                lpparam.classLoader, "updateRoundRect", int.class, int.class, float.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!useSquircle) return;
                        View v = (View) p.thisObject;
                        final int w = (Integer) p.args[0];
                        final int h = (Integer) p.args[1];
                        final float r = (Float) p.args[2];
                        v.setClipToOutline(true);
                        Path sp = squirclePath(new RectF(0, 0, w, h), r);
                        v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                            @Override public void getOutline(View vv, Outline o) { o.setPath(sp); }
                        });
                    }
                });

            // Hook MiShadowUtils to skip shadow when squircle is on
            try {
                Class<?> ms = XposedHelpers.findClass(
                    "com.miui.home.launcher.common.MiShadowUtils", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(ms, "applyViewShadow",
                    View.class, int.class, float.class, float.class, float.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (useSquircle) p.setResult(null);
                        }
                    });
            } catch (Throwable ignored) {}

            // Hook BlurUtilities.setBackgroundBlur to override blur radius
            try {
                Class<?> bu = XposedHelpers.findClass(
                    "com.miui.home.launcher.common.BlurUtilities", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(bu, "setBackgroundBlur",
                    View.class, int.class, float[].class, int[][].class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (blurRadius != 100) {
                                // Replace the blur radius parameter (was from DeviceConfig.scaleMingou...)
                                p.args[1] = blurRadius;
                            }
                        }
                    });
            } catch (Throwable e) {
                XposedBridge.log("[DC] blur hook: " + e.getMessage());
            }

            // setupViews: create overlay with edge bloom
            XposedHelpers.findAndHookMethod(
                "com.miui.home.launcher.Launcher", lpparam.classLoader,
                "setupViews", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (overlay != null) return;
                            Object hotSeats = XposedHelpers.getObjectField(param.thisObject, "mHotSeats");
                            if (hotSeats == null) return;
                            View oldBg = (View) XposedHelpers.getObjectField(hotSeats, "mBlurBackground2");
                            if (oldBg == null) return;
                            ViewGroup parent = (ViewGroup) oldBg.getParent();
                            if (parent == null) return;

                            overlay = new View(oldBg.getContext()) {
                                @Override
                                protected void onDraw(Canvas canvas) {
                                    if (bgW < 1 || bgH < 1) return;
                                    float w = bgW, h = bgH;
                                    float r = Math.max(0, useSquircle ? bgR + SQUIRCLE_STROKE_OFF : bgR - 1f);
                                    float maxDim = Math.max(w, h);

                                    // Squircle stroke: fill outer - fill inner = perfect edge
                                    // Squircle stroke: fill outer - fill inner = perfect edge
                                    if (useSquircle) {
                                        float strokeW = 6f;
                                        float off = SQUIRCLE_STROKE_OFF;
                                        Path outer = squirclePath(new RectF(-off, -off, w+off, h+off), r);
                                        Path inner = squirclePath(new RectF(-off+strokeW, -off+strokeW, w+off-strokeW, h+off-strokeW), r - strokeW * 0.5f);

                                        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        fill.setStyle(Paint.Style.FILL);

                                        if ("none".equals(lightMode)) {
                                            fill.setColor(Color.argb(200, 255, 255, 255));
                                            canvas.drawPath(outer, fill);
                                            fill.setColor(Color.argb(0, 255, 255, 255));
                                            canvas.drawPath(inner, fill);
                                            return;
                                        }

                                        // Base gray
                                        fill.setColor(Color.argb(120, 255, 255, 255));
                                        canvas.drawPath(outer, fill);
                                        fill.setColor(Color.argb(0, 255, 255, 255));
                                        canvas.drawPath(inner, fill);

                                        // Source highlight
                                        boolean dyn = "dynamic".equals(lightMode);
                                        float s1x = dyn ? w*(0.5f+gyroY*0.3f) : w*0.5f;
                                        float s1y = dyn ? h*(0.5f+gyroX*0.3f) : h*0.5f;
                                        Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        s1p.setStyle(Paint.Style.FILL);
                                        s1p.setShader(new RadialGradient(s1x, s1y, maxDim*0.4f,
                                            new int[]{Color.argb(255,255,255,255),
                                                      Color.argb(120,255,255,255),
                                                      Color.argb(0,255,255,255)},
                                            new float[]{0f,0.5f,1f}, Shader.TileMode.CLAMP));
                                        canvas.drawPath(outer, s1p);
                                        fill.setColor(Color.argb(0, 255, 255, 255));
                                        canvas.drawPath(inner, fill);
                                        return;
                                    }

                                    if ("none".equals(lightMode)) {
                                        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        s.setStyle(Paint.Style.STROKE); s.setStrokeWidth(6f);
                                        s.setColor(Color.argb(200, 255, 255, 255));
                                        canvas.drawRoundRect(1, 1, w-1, h-1, r, r, s);
                                        return;
                                    }

                                    boolean dyn = "dynamic".equals(lightMode);
                                    float s1x = dyn ? w*(0.5f+gyroY*0.3f) : w*0.5f;
                                    float s1y = dyn ? h*(0.5f+gyroX*0.3f) : h*0.5f;

                                    Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    base.setStyle(Paint.Style.STROKE); base.setStrokeWidth(6f);
                                    base.setColor(Color.argb(120, 255, 255, 255));
                                    Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    s1p.setStyle(Paint.Style.STROKE); s1p.setStrokeWidth(6f);
                                    s1p.setShader(new RadialGradient(s1x, s1y, maxDim*0.4f,
                                        new int[]{Color.argb(255,255,255,255),Color.argb(120,255,255,255),Color.argb(0,255,255,255)},
                                        new float[]{0f,0.5f,1f}, Shader.TileMode.CLAMP));

                                    if (useSquircle) {
                                        Path sp = squirclePath(new RectF(1,1,w-1,h-1), r);
                                        canvas.drawPath(sp, base);
                                        canvas.drawPath(sp, s1p);
                                    } else {
                                        canvas.drawRoundRect(1,1,w-1,h-1,r,r,base);
                                        canvas.drawRoundRect(1,1,w-1,h-1,r,r,s1p);
                                    }
                                }
                            };
                            overlay.setId(View.generateViewId());
                            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                                ((FrameLayout.LayoutParams) oldBg.getLayoutParams()).gravity);
                            parent.addView(overlay, lp);
                            syncAll(oldBg);

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
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] err: " + e.getClass().getSimpleName() + " " + e.getMessage());
                        }
                    }
                });
        } catch (Throwable e) {
            XposedBridge.log("[DC] init err: " + e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Path squirclePath(RectF rect, float radius) {
        Path p = new Path();
        if (radius <= 1) { p.addRect(rect, Path.Direction.CW); return p; }
        float r = radius, c = r * 0.65f; // iPad dock continuous curve
        float l = rect.left, t = rect.top, ri = rect.right, b = rect.bottom;
        p.moveTo(l, t+r);
        p.cubicTo(l, t+r-c, l+r-c, t, l+r, t);
        p.lineTo(ri-r, t);
        p.cubicTo(ri-r+c, t, ri, t+r-c, ri, t+r);
        p.lineTo(ri, b-r);
        p.cubicTo(ri, b-r+c, ri-r+c, b, ri-r, b);
        p.lineTo(l+r, b);
        p.cubicTo(l+r-c, b, l, b-r+c, l, b-r);
        p.close();
        return p;
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

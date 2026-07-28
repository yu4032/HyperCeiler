package com.kiminonawa.betterdock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
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

    private static View overlay, glassOverlay, oldBg;
    private static int bgW, bgH;
    private static float bgR = 30f;
    private static float gyroX, gyroY;
    private static long gyroTime;
    private static String lightMode = "fixed";
    private static int blurRadius = 100;
    private static int heightOffset, widthOffset, cornerOffset = -1;
    private static int sqStrokeW = 4, sqStrokeOff = 8;
    private static float sqOuterCp = 0.58f;
    private static boolean useSquircle;
    private static boolean liquidGlass;
    private static int lgAlpha = 80;
    private static int lgTint = 0x38FFFFFF;

    private static int readInt(String path, int def) {
        try { File f = new File(path); if (f.exists()) {
            FileInputStream fis = new FileInputStream(f); byte[] b = new byte[16];
            int n = fis.read(b); fis.close(); return Integer.parseInt(new String(b,0,n).trim());
        }} catch (Throwable ignored) {} return def;
    }
    private static String readStr(String path, String def) {
        try { File f = new File(path); if (f.exists()) {
            FileInputStream fis = new FileInputStream(f); byte[] b = new byte[16];
            int n = fis.read(b); fis.close(); return new String(b,0,n).trim();
        }} catch (Throwable ignored) {} return def;
    }
    private static int parseHex(String s, int def) {
        try { return (int) Long.parseLong(s.replace("#",""), 16); } catch (Throwable e) { return def; }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.miui.home")) return;

        lightMode = readStr("/sdcard/dock_light.txt", "fixed");
        blurRadius = readInt("/sdcard/dock_blur_radius.txt", 100);
        heightOffset = readInt("/sdcard/dock_height_offset.txt", 0);
        widthOffset = readInt("/sdcard/dock_width_offset.txt", 0);
        cornerOffset = readInt("/sdcard/dock_corner_offset.txt", -1);
        sqStrokeW = readInt("/sdcard/dock_sq_stroke_w.txt", 4);
        sqStrokeOff = readInt("/sdcard/dock_sq_stroke_off.txt", 8);
        sqOuterCp = readInt("/sdcard/dock_sq_outer_cp.txt", 58) / 100f;
        useSquircle = "1".equals(readStr("/sdcard/dock_squircle.txt", "0"));
        liquidGlass = "1".equals(readStr("/sdcard/dock_lg.txt", "0"));
        lgAlpha = readInt("/sdcard/dock_lg_alpha.txt", 80);
        lgTint = parseHex(readStr("/sdcard/dock_lg_tint.txt", "38FFFFFF"), 0x38FFFFFF);

        try {
            // Size hooks
            String cls = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundWidth", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (widthOffset != 0) p.args[0] = ((Integer)p.args[0]) + widthOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundHeight", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (heightOffset != 0) p.args[0] = ((Integer)p.args[0]) + heightOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundRadius", float.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (cornerOffset != 0) p.args[0] = ((Float)p.args[0]) + cornerOffset;
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        syncAll((View)p.thisObject);
                        if (useSquircle) {
                            View v = (View)p.thisObject;
                            float r = (Float)XposedHelpers.getObjectField(v, "mCornerRadius");
                            if (r > 0) {
                                Path sp = squirclePath(new RectF(0,0,v.getWidth(),v.getHeight()), r);
                                v.setOutlineProvider(new android.view.ViewOutlineProvider() {
                                    @Override public void getOutline(View vv, android.graphics.Outline o) { o.setPath(sp); }
                                });
                            }
                        }
                    }
                });

            // Blur radius hook
            try {
                Class<?> bu = XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(bu, "setBackgroundBlur", View.class, int.class, float[].class, int[][].class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (blurRadius != 100) p.args[1] = blurRadius;
                        }
                    });
            } catch (Throwable ignored) {}

            // Disable shadow when squircle
            try {
                Class<?> ms = XposedHelpers.findClass("com.miui.home.launcher.common.MiShadowUtils", lpparam.classLoader);
                XposedHelpers.findAndHookMethod(ms, "applyViewShadow", View.class, int.class, float.class, float.class, float.class, float.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (useSquircle) p.setResult(null);
                        }
                    });
            } catch (Throwable ignored) {}

            // setupViews
            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader,
                "setupViews", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object hotSeats = XposedHelpers.getObjectField(param.thisObject, "mHotSeats");
                            if (hotSeats == null) return;
                            oldBg = (View)XposedHelpers.getObjectField(hotSeats, "mBlurBackground2");
                            if (oldBg == null) return;
                            ViewGroup parent = (ViewGroup)oldBg.getParent();
                            if (parent == null) return;

                            int gravity = ((FrameLayout.LayoutParams)oldBg.getLayoutParams()).gravity;

                            // Liquid Glass overlay (behind bloom, above blur bg)
                            if (liquidGlass && glassOverlay == null) {
                                glassOverlay = new View(oldBg.getContext()) {
                                    @Override protected void onDraw(Canvas canvas) {
                                        if (bgW < 1 || bgH < 1) return;
                                        float w = bgW, h = bgH, r = Math.max(0, bgR);
                                        int a = Color.alpha(lgTint) * lgAlpha / 255;

                                        // Refraction gradient (gyro-driven specular highlight and shadow)
                                        float sx = gyroY * 0.5f; // horizontal shift from Y-rotation
                                        float sy = gyroX * 0.5f; // vertical shift from X-rotation

                                        // Specular highlight gradient (top-left to bottom-right, shifted by gyro)
                                        float hlx1 = w*(0.2f + sx), hly1 = h*(0.1f + sy);
                                        float hlx2 = w*(0.6f + sx), hly2 = h*(0.4f + sy);
                                        Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        highlight.setStyle(Paint.Style.FILL);
                                        highlight.setShader(new LinearGradient(hlx1, hly1, hlx2, hly2,
                                            new int[]{Color.argb(lgAlpha, 255,255,255),
                                                      Color.argb(lgAlpha/3, 255,255,255),
                                                      Color.argb(0,255,255,255)},
                                            new float[]{0f, 0.3f, 1f}, Shader.TileMode.CLAMP));

                                        // Shadow gradient (opposite side)
                                        float shx1 = w*(0.8f - sx), shy1 = h*(0.8f - sy);
                                        float shx2 = w*(0.5f - sx), shy2 = h*(0.7f - sy);
                                        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        shadow.setStyle(Paint.Style.FILL);
                                        shadow.setShader(new LinearGradient(shx1, shy1, shx2, shy2,
                                            new int[]{Color.argb(lgAlpha/2, 0,0,0),
                                                      Color.argb(0,0,0,0)},
                                            new float[]{0f, 1f}, Shader.TileMode.CLAMP));

                                        // Tint fill
                                        Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        tint.setStyle(Paint.Style.FILL);
                                        tint.setColor(Color.argb(a, Color.red(lgTint), Color.green(lgTint), Color.blue(lgTint)));

                                        // Lens refraction: concentric shifted layers for glass edge bending
                                        Paint lens = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        lens.setStyle(Paint.Style.STROKE);

                                        // Red channel: outermost, thickest
                                        lens.setStrokeWidth(12f);
                                        lens.setColor(Color.argb(a/2, 255, 0, 0));
                                        if (useSquircle) canvas.drawPath(squirclePath(new RectF(-6,-6,w+6,h+6), r+6), lens);
                                        else canvas.drawRoundRect(-6,-6,w+6,h+6, r+6,r+6, lens);

                                        // Cyan channel: middle
                                        lens.setStrokeWidth(8f);
                                        lens.setColor(Color.argb(a/3, 0, 255, 255));
                                        if (useSquircle) canvas.drawPath(squirclePath(new RectF(-3,-3,w+3,h+3), r+3), lens);
                                        else canvas.drawRoundRect(-3,-3,w+3,h+3, r+3,r+3, lens);

                                        // Blue channel: innermost
                                        lens.setStrokeWidth(5f);
                                        lens.setColor(Color.argb(a/4, 0, 0, 255));
                                        if (useSquircle) canvas.drawPath(squirclePath(new RectF(0,0,w,h), r), lens);
                                        else canvas.drawRoundRect(0,0,w,h, r,r, lens);
                                    }
                                };
                                glassOverlay.setId(View.generateViewId());
                                parent.addView(glassOverlay, parent.indexOfChild(oldBg)+1,
                                    new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, gravity));
                            }

                            // Bloom overlay
                            if (overlay != null) return;
                            overlay = new View(oldBg.getContext()) {
                                @Override protected void onDraw(Canvas canvas) {
                                    if (bgW < 1 || bgH < 1) return;
                                    float w = bgW, h = bgH;
                                    float r = Math.max(0, useSquircle ? bgR + sqStrokeOff : bgR - 1f);
                                    float maxDim = Math.max(w,h);

                                    if (useSquircle) {
                                        Path outer = squirclePath(new RectF(-sqStrokeOff,-sqStrokeOff,w+sqStrokeOff,h+sqStrokeOff), r, sqOuterCp);
                                        Path inner = squirclePath(new RectF(-sqStrokeOff+sqStrokeW,-sqStrokeOff+sqStrokeW,w+sqStrokeOff-sqStrokeW,h+sqStrokeOff-sqStrokeW), r-sqStrokeW*0.5f, 0.65f);
                                        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG); fill.setStyle(Paint.Style.FILL);

                                        if ("none".equals(lightMode)) {
                                            fill.setColor(Color.argb(200,255,255,255)); canvas.drawPath(outer,fill);
                                            fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill); return;
                                        }
                                        fill.setColor(Color.argb(120,255,255,255)); canvas.drawPath(outer,fill);
                                        fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill);

                                        boolean dyn = "dynamic".equals(lightMode);
                                        float s1x = dyn ? w*(0.5f+gyroY*0.3f) : w*0.5f;
                                        float s1y = dyn ? h*(0.5f+gyroX*0.3f) : h*0.5f;
                                        Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG); s1p.setStyle(Paint.Style.FILL);
                                        s1p.setShader(new RadialGradient(s1x,s1y,maxDim*0.4f,
                                            new int[]{Color.argb(255,255,255,255),Color.argb(120,255,255,255),Color.argb(0,255,255,255)},
                                            new float[]{0f,0.5f,1f}, Shader.TileMode.CLAMP));
                                        canvas.drawPath(outer,s1p);
                                        fill.setColor(Color.argb(0,255,255,255)); canvas.drawPath(inner,fill); return;
                                    }

                                    if ("none".equals(lightMode)) {
                                        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setStyle(Paint.Style.STROKE);
                                        s.setStrokeWidth(4f); s.setColor(Color.argb(200,255,255,255));
                                        canvas.drawRoundRect(1,1,w-1,h-1,r,r,s); return;
                                    }

                                    boolean dyn = "dynamic".equals(lightMode);
                                    float s1x = dyn ? w*(0.5f+gyroY*0.3f) : w*0.5f;
                                    float s1y = dyn ? h*(0.5f+gyroX*0.3f) : h*0.5f;
                                    Paint base = new Paint(Paint.ANTI_ALIAS_FLAG); base.setStyle(Paint.Style.STROKE);
                                    base.setStrokeWidth(4f); base.setColor(Color.argb(120,255,255,255));
                                    Paint s1p = new Paint(Paint.ANTI_ALIAS_FLAG); s1p.setStyle(Paint.Style.STROKE);
                                    s1p.setStrokeWidth(4f);
                                    s1p.setShader(new RadialGradient(s1x,s1y,maxDim*0.4f,
                                        new int[]{Color.argb(255,255,255,255),Color.argb(120,255,255,255),Color.argb(0,255,255,255)},
                                        new float[]{0f,0.5f,1f}, Shader.TileMode.CLAMP));
                                    canvas.drawRoundRect(1,1,w-1,h-1,r,r,base);
                                    canvas.drawRoundRect(1,1,w-1,h-1,r,r,s1p);
                                }
                            };
                            overlay.setId(View.generateViewId());
                            parent.addView(overlay, new FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, gravity));
                            syncAll(oldBg);

                            if ("dynamic".equals(lightMode)) {
                                try {
                                    SensorManager sm = (SensorManager)oldBg.getContext().getSystemService(android.content.Context.SENSOR_SERVICE);
                                    Sensor gyro = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                                    if (gyro != null) {
                                        sm.registerListener(new SensorEventListener() {
                                            @Override public void onSensorChanged(SensorEvent e) {
                                                if (e.values.length < 3) return;
                                                long now = e.timestamp;
                                                if (gyroTime > 0) { float dt = (now-gyroTime)/1e9f;
                                                    if (dt>0 && dt<0.1f) {
                                                        gyroX=clamp(gyroX+e.values[0]*dt*2.4f,-1.35f,1.35f);
                                                        gyroY=clamp(gyroY+e.values[1]*dt*2.4f,-1.35f,1.35f);
                                                    }}
                                                gyroTime=now;
                                                if (overlay!=null) overlay.postInvalidate();
                                                if (glassOverlay!=null) glassOverlay.postInvalidate();
                                            }
                                            @Override public void onAccuracyChanged(Sensor s,int a){}
                                        }, gyro, SensorManager.SENSOR_DELAY_GAME);
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable e) {
                            XposedBridge.log("[DC] err: "+e.getClass().getSimpleName()+" "+e.getMessage());
                        }
                    }
                });
        } catch (Throwable e) {
            XposedBridge.log("[DC] init err: "+e.getClass().getSimpleName()+" "+e.getMessage());
        }
    }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    private static Path squirclePath(RectF rect, float radius) { return squirclePath(rect, radius, 0.65f); }
    private static Path squirclePath(RectF rect, float radius, float cp) {
        Path p = new Path();
        if (radius <= 1) { p.addRect(rect, Path.Direction.CW); return p; }
        float r = radius, c = r*cp;
        float l=rect.left, t=rect.top, ri=rect.right, b=rect.bottom;
        p.moveTo(l, t+r); p.cubicTo(l, t+r-c, l+r-c, t, l+r, t); p.lineTo(ri-r, t);
        p.cubicTo(ri-r+c, t, ri, t+r-c, ri, t+r); p.lineTo(ri, b-r);
        p.cubicTo(ri, b-r+c, ri-r+c, b, ri-r, b); p.lineTo(l+r, b);
        p.cubicTo(l+r-c, b, l, b-r+c, l, b-r); p.close();
        return p;
    }

    private static void syncAll(View bg) {
        if (overlay == null || bg == null) return;
        try {
            bgW = XposedHelpers.getIntField(bg, "mWidth"); bgH = XposedHelpers.getIntField(bg, "mHeight");
            Object r = XposedHelpers.getObjectField(bg, "mCornerRadius");
            if (r instanceof Float) bgR = (Float)r;
            if (bgW <= 0) return;
            ViewGroup.LayoutParams lp = overlay.getLayoutParams();
            if (lp != null) { lp.width = bgW; lp.height = bgH; overlay.setLayoutParams(lp); }
            overlay.invalidate();
            if (glassOverlay != null) {
                ViewGroup.LayoutParams glp = glassOverlay.getLayoutParams();
                if (glp != null) { glp.width = bgW; glp.height = bgH; glassOverlay.setLayoutParams(glp); }
                glassOverlay.invalidate();
            }
        } catch (Throwable ignored) {}
    }
}

package com.kiminonawa.betterdock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
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
    private static boolean useSquircle, liquidGlass;
    private static int lgAlpha = 80, lgBlurScale = 4;
    private static int lgTint = 0x38FFFFFF;
    private static Bitmap cachedBg, blurredBg;
    private static int cachedW, cachedH;
    private static long lastCapture;

    private static int readInt(String p, int d) { try { File f=new File(p); if(f.exists()){
        FileInputStream s=new FileInputStream(f); byte[] b=new byte[16]; int n=s.read(b);s.close();
        return Integer.parseInt(new String(b,0,n).trim());}}catch(Throwable e){} return d; }
    private static String readStr(String p, String d) { try { File f=new File(p); if(f.exists()){
        FileInputStream s=new FileInputStream(f); byte[] b=new byte[32]; int n=s.read(b);s.close();
        return new String(b,0,n).trim();}}catch(Throwable e){} return d; }
    private static int parseHex(String s, int d) { try {
        return (int)Long.parseLong(s.replace("#",""),16); }catch(Throwable e){return d;} }

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
        lgBlurScale = readInt("/sdcard/dock_lg_blur_scale.txt", 4);

        try {
            String cls = "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundWidth", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (widthOffset!=0) p.args[0]=((Integer)p.args[0])+widthOffset;
                    } @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundHeight", int.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (heightOffset!=0) p.args[0]=((Integer)p.args[0])+heightOffset;
                    } @Override protected void afterHookedMethod(MethodHookParam p) { syncAll((View)p.thisObject); }
                });
            XposedHelpers.findAndHookMethod(cls, lpparam.classLoader, "setBackgroundRadius", float.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (cornerOffset!=0) p.args[0]=((Float)p.args[0])+cornerOffset;
                    } @Override protected void afterHookedMethod(MethodHookParam p) {
                        syncAll((View)p.thisObject);
                        if (useSquircle) {
                            View v=(View)p.thisObject;
                            float r=(Float)XposedHelpers.getObjectField(v,"mCornerRadius");
                            if (r>0){ Path sp=squirclePath(new RectF(0,0,v.getWidth(),v.getHeight()),r);
                                v.setOutlineProvider(new android.view.ViewOutlineProvider(){
                                    @Override public void getOutline(View vv,android.graphics.Outline o){o.setPath(sp);}});}
                        }
                    }
                });

            try {
                Class<?> bu=XposedHelpers.findClass("com.miui.home.launcher.common.BlurUtilities",lpparam.classLoader);
                XposedHelpers.findAndHookMethod(bu,"setBackgroundBlur",View.class,int.class,float[].class,int[][].class,
                    new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
                        if(blurRadius!=100) p.args[1]=blurRadius; }});
            }catch(Throwable e){}

            try {
                Class<?> ms=XposedHelpers.findClass("com.miui.home.launcher.common.MiShadowUtils",lpparam.classLoader);
                XposedHelpers.findAndHookMethod(ms,"applyViewShadow",View.class,int.class,float.class,float.class,float.class,float.class,
                    new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
                        if(useSquircle) p.setResult(null); }});
            }catch(Throwable e){}

            XposedHelpers.findAndHookMethod("com.miui.home.launcher.Launcher",lpparam.classLoader,
                "setupViews",new XC_MethodHook(){
                    @Override protected void afterHookedMethod(MethodHookParam param){
                        try{
                            Object hotSeats=XposedHelpers.getObjectField(param.thisObject,"mHotSeats");
                            if(hotSeats==null)return;
                            oldBg=(View)XposedHelpers.getObjectField(hotSeats,"mBlurBackground2");
                            if(oldBg==null)return;
                            ViewGroup parent=(ViewGroup)oldBg.getParent();
                            if(parent==null)return;
                            int gravity=((FrameLayout.LayoutParams)oldBg.getLayoutParams()).gravity;
                            View root=oldBg.getRootView();

                            if(liquidGlass && glassOverlay==null){
                                glassOverlay=new View(oldBg.getContext()){
                                    @Override protected void onDraw(Canvas canvas){
                                        if(bgW<1||bgH<1)return;
                                        float w=bgW,h=bgH,r=Math.max(0,bgR);
                                        int a=Color.alpha(lgTint)*lgAlpha/255;

                                        // Capture background behind dock (throttled)
                                        long now=System.currentTimeMillis();
                                        if(now-lastCapture>500){
                                            try{
                                                int sw=Math.max(1,(int)(w/lgBlurScale));
                                                int sh=Math.max(1,(int)(h/lgBlurScale));
                                                if(cachedBg==null||cachedW!=sw||cachedH!=sh){
                                                    cachedBg=Bitmap.createBitmap(sw,sh,Bitmap.Config.ARGB_8888);
                                                    if(blurredBg!=null){blurredBg.recycle();blurredBg=null;}
                                                    cachedW=sw;cachedH=sh;
                                                }
                                                // Capture root view at scaled size
                                                Canvas cap=new Canvas(cachedBg);
                                                cap.scale(1f/lgBlurScale,1f/lgBlurScale);
                                                cap.translate(-(root.getWidth()-w)/2f,-root.getBottom()+oldBg.getBottom()+h-oldBg.getHeight()*2);
                                                root.draw(cap);
                                                // Box blur
                                                blurredBg=boxBlur(cachedBg,lgBlurScale);
                                            }catch(Throwable ignored){}
                                            lastCapture=now;
                                        }

                                        // Draw blurred background
                                        if(blurredBg!=null&&!blurredBg.isRecycled()){
                                            Paint bp=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                                            bp.setAlpha(lgAlpha);
                                            if(useSquircle){
                                                canvas.save();
                                                canvas.clipPath(squirclePath(new RectF(0,0,w,h),r));
                                                canvas.drawBitmap(blurredBg,null,new RectF(0,0,w,h),bp);
                                                canvas.restore();
                                            }else{
                                                canvas.save();
                                                canvas.clipRect(0,0,w,h);
                                                canvas.drawBitmap(blurredBg,null,new RectF(0,0,w,h),bp);
                                                canvas.restore();
                                            }
                                        }

                                        // Refraction gradients
                                        float sx=gyroY*0.5f,sy=gyroX*0.5f;
                                        Paint highlight=new Paint(Paint.ANTI_ALIAS_FLAG);highlight.setStyle(Paint.Style.FILL);
                                        highlight.setShader(new LinearGradient(w*(0.2f+sx),h*(0.1f+sy),w*(0.6f+sx),h*(0.4f+sy),
                                            new int[]{Color.argb(lgAlpha,255,255,255),Color.argb(lgAlpha/3,255,255,255),Color.argb(0,255,255,255)},
                                            new float[]{0f,0.3f,1f},Shader.TileMode.CLAMP));
                                        Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);shadow.setStyle(Paint.Style.FILL);
                                        shadow.setShader(new LinearGradient(w*(0.8f-sx),h*(0.8f-sy),w*(0.5f-sx),h*(0.7f-sy),
                                            new int[]{Color.argb(lgAlpha/2,0,0,0),Color.argb(0,0,0,0)},new float[]{0f,1f},Shader.TileMode.CLAMP));
                                        Paint tint=new Paint(Paint.ANTI_ALIAS_FLAG);tint.setStyle(Paint.Style.FILL);
                                        tint.setColor(Color.argb(a,Color.red(lgTint),Color.green(lgTint),Color.blue(lgTint)));

                                        if(useSquircle){
                                            Path p=squirclePath(new RectF(0,0,w,h),r);
                                            canvas.drawPath(p,shadow);canvas.drawPath(p,tint);canvas.drawPath(p,highlight);
                                            Paint rs=new Paint(Paint.ANTI_ALIAS_FLAG);rs.setStyle(Paint.Style.STROKE);
                                            rs.setStrokeWidth(3f);rs.setColor(Color.argb(a/4,255,0,0));
                                            canvas.drawPath(squirclePath(new RectF(-2,-2,w+2,h+2),r+2),rs);
                                            Paint bs=new Paint(Paint.ANTI_ALIAS_FLAG);bs.setStyle(Paint.Style.STROKE);
                                            bs.setStrokeWidth(3f);bs.setColor(Color.argb(a/4,0,0,255));
                                            canvas.drawPath(squirclePath(new RectF(2,2,w-2,h-2),r-2),bs);
                                        }else{
                                            canvas.drawRoundRect(0,0,w,h,r,r,shadow);
                                            canvas.drawRoundRect(0,0,w,h,r,r,tint);
                                            canvas.drawRoundRect(0,0,w,h,r,r,highlight);
                                            Paint rs=new Paint(Paint.ANTI_ALIAS_FLAG);rs.setStyle(Paint.Style.STROKE);
                                            rs.setStrokeWidth(3f);rs.setColor(Color.argb(a/4,255,0,0));
                                            canvas.drawRoundRect(-2,-2,w+2,h+2,r+2,r+2,rs);
                                            Paint bs=new Paint(Paint.ANTI_ALIAS_FLAG);bs.setStyle(Paint.Style.STROKE);
                                            bs.setStrokeWidth(3f);bs.setColor(Color.argb(a/4,0,0,255));
                                            canvas.drawRoundRect(2,2,w-2,h-2,r-2,r-2,bs);
                                        }
                                    }
                                };
                                glassOverlay.setId(View.generateViewId());
                                parent.addView(glassOverlay,parent.indexOfChild(oldBg)+1,
                                    new FrameLayout.LayoutParams(-1,-1,gravity));
                            }

                            if(overlay!=null)return;
                            overlay=new View(oldBg.getContext()){
                                @Override protected void onDraw(Canvas canvas){
                                    if(bgW<1||bgH<1)return;
                                    float w=bgW,h=bgH,r=Math.max(0,useSquircle?bgR+sqStrokeOff:bgR-1f),maxDim=Math.max(w,h);
                                    if(useSquircle){
                                        Path outer=squirclePath(new RectF(-sqStrokeOff,-sqStrokeOff,w+sqStrokeOff,h+sqStrokeOff),r,sqOuterCp);
                                        Path inner=squirclePath(new RectF(-sqStrokeOff+sqStrokeW,-sqStrokeOff+sqStrokeW,w+sqStrokeOff-sqStrokeW,h+sqStrokeOff-sqStrokeW),r-sqStrokeW*0.5f,0.65f);
                                        Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);fill.setStyle(Paint.Style.FILL);
                                        if("none".equals(lightMode)){fill.setColor(Color.argb(200,255,255,255));canvas.drawPath(outer,fill);
                                            fill.setColor(Color.argb(0,255,255,255));canvas.drawPath(inner,fill);return;}
                                        fill.setColor(Color.argb(120,255,255,255));canvas.drawPath(outer,fill);
                                        fill.setColor(Color.argb(0,255,255,255));canvas.drawPath(inner,fill);
                                        boolean dyn="dynamic".equals(lightMode);
                                        float s1x=dyn?w*(0.5f+gyroY*0.3f):w*0.5f,s1y=dyn?h*(0.5f+gyroX*0.3f):h*0.5f;
                                        Paint s1p=new Paint(Paint.ANTI_ALIAS_FLAG);s1p.setStyle(Paint.Style.FILL);
                                        s1p.setShader(new RadialGradient(s1x,s1y,maxDim*0.4f,new int[]{Color.argb(255,255,255,255),
                                            Color.argb(120,255,255,255),Color.argb(0,255,255,255)},new float[]{0f,0.5f,1f},Shader.TileMode.CLAMP));
                                        canvas.drawPath(outer,s1p);fill.setColor(Color.argb(0,255,255,255));canvas.drawPath(inner,fill);return;}
                                    if("none".equals(lightMode)){Paint s=new Paint(Paint.ANTI_ALIAS_FLAG);
                                        s.setStyle(Paint.Style.STROKE);s.setStrokeWidth(4f);s.setColor(Color.argb(200,255,255,255));
                                        canvas.drawRoundRect(1,1,w-1,h-1,r,r,s);return;}
                                    boolean dyn="dynamic".equals(lightMode);
                                    float s1x=dyn?w*(0.5f+gyroY*0.3f):w*0.5f,s1y=dyn?h*(0.5f+gyroX*0.3f):h*0.5f;
                                    Paint base=new Paint(Paint.ANTI_ALIAS_FLAG);base.setStyle(Paint.Style.STROKE);
                                    base.setStrokeWidth(4f);base.setColor(Color.argb(120,255,255,255));
                                    Paint s1p=new Paint(Paint.ANTI_ALIAS_FLAG);s1p.setStyle(Paint.Style.STROKE);s1p.setStrokeWidth(4f);
                                    s1p.setShader(new RadialGradient(s1x,s1y,maxDim*0.4f,new int[]{Color.argb(255,255,255,255),
                                        Color.argb(120,255,255,255),Color.argb(0,255,255,255)},new float[]{0f,0.5f,1f},Shader.TileMode.CLAMP));
                                    canvas.drawRoundRect(1,1,w-1,h-1,r,r,base);
                                    canvas.drawRoundRect(1,1,w-1,h-1,r,r,s1p);
                                }
                            };
                            overlay.setId(View.generateViewId());
                            parent.addView(overlay,new FrameLayout.LayoutParams(-1,-1,gravity));
                            syncAll(oldBg);

                            if("dynamic".equals(lightMode)){try{SensorManager sm=(SensorManager)oldBg.getContext()
                                .getSystemService(android.content.Context.SENSOR_SERVICE);
                                Sensor gyro=sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                                if(gyro!=null){sm.registerListener(new SensorEventListener(){
                                    @Override public void onSensorChanged(SensorEvent e){if(e.values.length<3)return;
                                        long now=e.timestamp;if(gyroTime>0){float dt=(now-gyroTime)/1e9f;
                                        if(dt>0&&dt<0.1f){gyroX=clamp(gyroX+e.values[0]*dt*2.4f,-1.35f,1.35f);
                                        gyroY=clamp(gyroY+e.values[1]*dt*2.4f,-1.35f,1.35f);}}gyroTime=now;
                                        if(overlay!=null)overlay.postInvalidate();
                                        if(glassOverlay!=null)glassOverlay.postInvalidate();}
                                    @Override public void onAccuracyChanged(Sensor s,int a){}},gyro,SensorManager.SENSOR_DELAY_GAME);}
                            }catch(Throwable e){}}
                        }catch(Throwable e){XposedBridge.log("[DC] err: "+e.getClass().getSimpleName()+" "+e.getMessage());}
                    }
                });
        }catch(Throwable e){XposedBridge.log("[DC] init: "+e.getClass().getSimpleName()+" "+e.getMessage());}
    }

    private static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}
    private static Path squirclePath(RectF r,float rad){return squirclePath(r,rad,0.65f);}
    private static Path squirclePath(RectF r,float rad,float cp){Path p=new Path();
        if(rad<=1){p.addRect(r,Path.Direction.CW);return p;}float rr=rad,c=rr*cp;
        float l=r.left,t=r.top,ri=r.right,b=r.bottom;
        p.moveTo(l,t+rr);p.cubicTo(l,t+rr-c,l+rr-c,t,l+rr,t);p.lineTo(ri-rr,t);
        p.cubicTo(ri-rr+c,t,ri,t+rr-c,ri,t+rr);p.lineTo(ri,b-rr);
        p.cubicTo(ri,b-rr+c,ri-rr+c,b,ri-rr,b);p.lineTo(l+rr,b);
        p.cubicTo(l+rr-c,b,l,b-rr+c,l,b-rr);p.close();return p;}

    private static Bitmap boxBlur(Bitmap src,int radius){
        if(src==null||src.isRecycled())return null;
        int w=src.getWidth(),h=src.getHeight(),r=Math.min(radius,Math.min(w,h)/2);
        if(r<=0)return src;
        int[] px=new int[w*h];src.getPixels(px,0,w,0,0,w,h);
        int[] out=new int[w*h];
        // Horizontal pass
        for(int y=0;y<h;y++){int sumA=0,sumR=0,sumG=0,sumB=0,count=0;
            int row=y*w;for(int i=-r;i<=r;i++){int x=clampI(i,0,w-1);
                int c=px[row+x];sumA+=(c>>24)&0xFF;sumR+=(c>>16)&0xFF;sumG+=(c>>8)&0xFF;sumB+=c&0xFF;count++;}
            out[row]=pack(sumA/count,sumR/count,sumG/count,sumB/count);
            for(int x=1;x<w;x++){int lx=clampI(x-r-1,0,w-1),rx=clampI(x+r,0,w-1);
                int cl=px[row+lx],cr=px[row+rx];sumA+=((cr>>24)&0xFF)-((cl>>24)&0xFF);
                sumR+=((cr>>16)&0xFF)-((cl>>16)&0xFF);sumG+=((cr>>8)&0xFF)-((cl>>8)&0xFF);
                sumB+=(cr&0xFF)-(cl&0xFF);out[row+x]=pack(sumA/count,sumR/count,sumG/count,sumB/count);}}
        // Vertical pass
        int[] out2=new int[w*h];
        for(int x=0;x<w;x++){int sumA=0,sumR=0,sumG=0,sumB=0,count=0;
            for(int i=-r;i<=r;i++){int y=clampI(i,0,h-1);int c=out[y*w+x];
                sumA+=(c>>24)&0xFF;sumR+=(c>>16)&0xFF;sumG+=(c>>8)&0xFF;sumB+=c&0xFF;count++;}
            out2[x]=pack(sumA/count,sumR/count,sumG/count,sumB/count);
            for(int y=1;y<h;y++){int ty=clampI(y-r-1,0,h-1),by=clampI(y+r,0,h-1);
                int ct=out[ty*w+x],cb=out[by*w+x];sumA+=((cb>>24)&0xFF)-((ct>>24)&0xFF);
                sumR+=((cb>>16)&0xFF)-((ct>>16)&0xFF);sumG+=((cb>>8)&0xFF)-((ct>>8)&0xFF);
                sumB+=(cb&0xFF)-(ct&0xFF);out2[y*w+x]=pack(sumA/count,sumR/count,sumG/count,sumB/count);}}
        return Bitmap.createBitmap(out2,w,h,Bitmap.Config.ARGB_8888);
    }
    private static int clampI(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static int pack(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}

    private static void syncAll(View bg){
        if(overlay==null||bg==null)return;try{bgW=XposedHelpers.getIntField(bg,"mWidth");
            bgH=XposedHelpers.getIntField(bg,"mHeight");Object r=XposedHelpers.getObjectField(bg,"mCornerRadius");
            if(r instanceof Float)bgR=(Float)r;if(bgW<=0)return;
            ViewGroup.LayoutParams lp=overlay.getLayoutParams();
            if(lp!=null){lp.width=bgW;lp.height=bgH;overlay.setLayoutParams(lp);}overlay.invalidate();
            if(glassOverlay!=null){ViewGroup.LayoutParams glp=glassOverlay.getLayoutParams();
                if(glp!=null){glp.width=bgW;glp.height=bgH;glassOverlay.setLayoutParams(glp);}glassOverlay.invalidate();}
        }catch(Throwable e){}}
}

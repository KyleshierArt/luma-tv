package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-bleed scenic photographs with a quiet editorial clock. Sleeps between minute changes. */
final class ScenicClockView extends View {
    private static final long PHOTO_INTERVAL=180000,PHOTO_FADE=1800,DIGIT_FADE=700,DRIFT=1800;
    private final WeatherStore weather;
    private final WeatherArtwork weatherArt=new WeatherArtwork();
    private final WeatherStore.Listener weatherChanged=()->invalidate();
    private final Paint photoPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG),small=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.US);
    private final SimpleDateFormat dateFormat;
    private final RectF cover=new RectF();
    private final float[] widths=new float[5];
    private Bitmap photo,previous;
    private Shader sideShade,bottomShade;
    private String time="",oldTime="",date="";
    private long minute=-1,photoStart,digitStart,driftStart,nextPhotoAt=Long.MAX_VALUE;
    private int index=-1,oldIndex=-1,generation,digitTransitions,photoTransitions;
    private float fromX,fromY,toX,toY;
    private boolean active,loading,motion;
    private final Runnable tick=()->{if(active){update();invalidate();}};
    ScenicClockView(Context c){
        super(c);weather=WeatherStore.get(c);dateFormat=new SimpleDateFormat(Language.pick(c,"EEEE, MMMM d","M月d日  EEEE"),Language.locale(c));setBackgroundColor(Color.BLACK);
        type.setTypeface(DisplayTypography.display(c));type.setFontFeatureSettings("'tnum' 1, 'lnum' 1");
        small.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void resume(){active=true;weather.listen(weatherChanged);motion=UiMotion.enabled(getContext());update();invalidate();}
    void pause(){active=false;weather.unlisten(weatherChanged);generation++;loading=false;previous=null;removeCallbacks(tick);}
    private static float fraction(long now,long start,long length){return Math.max(0,Math.min(1,(now-start)/(float)length));}
    private static float ease(float p){return 1-(float)Math.pow(1-p,3);}
    private void update(){
        removeCallbacks(tick);if(!active)return;
        long now=System.currentTimeMillis(),up=SystemClock.uptimeMillis();
        if(minute!=now/60000){
            boolean initial=minute<0;minute=now/60000;Date current=new Date(now);
            oldTime=time;time=timeFormat.format(current);date=dateFormat.format(current).toUpperCase(Locale.US);
            if(initial)oldTime=time;else digitTransitions++;
            digitStart=up;
            float progress=ease(fraction(up,driftStart,DRIFT));
            fromX=DisplayTypography.mix(fromX,toX,progress);fromY=DisplayTypography.mix(fromY,toY,progress);
            toX=(float)Math.sin(minute*2.399)*.004f;toY=(float)Math.cos(minute*1.618)*.009f;
            if(initial){fromX=toX;fromY=toY;}
            driftStart=up;setContentDescription(time+", "+date);
        }
        if(!loading&&(photo==null||up>=nextPhotoAt)){
            loading=true;final int request=++generation;
            final int next=index<0?0:(index+1)%ScenicPhotos.FILES.length;
            ScenicPhotos.request(getContext(),next,image->{
                if(!active||request!=generation)return;loading=false;
                long ready=SystemClock.uptimeMillis();
                if(image!=null){
                    previous=motion?photo:null;oldIndex=index;photo=image;index=next;
                    photoStart=ready;nextPhotoAt=ready+PHOTO_INTERVAL;if(previous!=null)photoTransitions++;
                }else nextPhotoAt=ready+5000;
                schedule();invalidate();
            });
        }
        schedule();
    }
    private void schedule(){
        removeCallbacks(tick);if(!active)return;
        long delay=60000-System.currentTimeMillis()%60000;
        if(!loading)delay=Math.min(delay,Math.max(16,nextPhotoAt-SystemClock.uptimeMillis()));
        postDelayed(tick,delay);
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        sideShade=new LinearGradient(0,0,w*.63f,0,new int[]{0x8F0A0D11,0x000A0D11},null,Shader.TileMode.CLAMP);
        bottomShade=new LinearGradient(0,h*.37f,0,h,new int[]{0x000A0D11,0x8F0A0D11},null,Shader.TileMode.CLAMP);
    }
    private void drawPhoto(Canvas canvas,Bitmap image,int alpha){
        if(image==null)return;
        float w=getWidth(),h=getHeight(),scale=Math.max(w/image.getWidth(),h/image.getHeight());
        float pw=image.getWidth()*scale,ph=image.getHeight()*scale;
        cover.set((w-pw)/2,(h-ph)/2,(w+pw)/2,(h+ph)/2);
        photoPaint.setShader(null);photoPaint.setColor(Color.WHITE);photoPaint.setAlpha(alpha);
        canvas.drawBitmap(image,null,cover,photoPaint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(photo==null)return;
        float w=getWidth(),h=getHeight();long now=SystemClock.uptimeMillis();
        float blend=previous==null?1:ease(fraction(now,photoStart,PHOTO_FADE));
        drawPhoto(canvas,previous,255);drawPhoto(canvas,photo,Math.round(255*blend));
        photoPaint.setShader(null);photoPaint.setColor(0x1F000000);photoPaint.setAlpha(31);canvas.drawRect(0,0,w,h,photoPaint);
        photoPaint.setAlpha(255);photoPaint.setShader(sideShade);canvas.drawRect(0,0,w,h,photoPaint);
        photoPaint.setShader(bottomShade);canvas.drawRect(0,0,w,h,photoPaint);photoPaint.setShader(null);
        float rawPhoto=previous==null?1:fraction(now,photoStart,PHOTO_FADE);
        int clockPhoto=rawPhoto<.5f?oldIndex:index;
        float clockAlpha=previous==null?1:rawPhoto<.5f?Math.max(0,1-rawPhoto*4):Math.min(1,(rawPhoto-.5f)*2);
        float drift=motion?ease(fraction(now,driftStart,DRIFT)):1;
        float dx=DisplayTypography.mix(fromX,toX,drift)*w,dy=DisplayTypography.mix(fromY,toY,drift)*h;
        float tx=w*.064f+dx;
        small.setTextSize(w*.0132f);small.setColor(0xFFF5F1E9);small.setAlpha(Math.round(194*clockAlpha));
        type.setTextSize(w*.148f);type.setColor(0xFFF5F1E9);
        // Keep the clock in the shaded lower-left area for every landscape.
        float baseline=h*.80f+dy;
        float dateBaseline=baseline+type.ascent()-w*.012f;
        DisplayTypography.tracking(canvas,date,tx+w*.0055f,dateBaseline,w*.0025f,small);
        float digit=motion?ease(fraction(now,digitStart,DIGIT_FADE)):1;
        type.getTextWidths(time,widths);float x=tx;
        for(int i=0;i<time.length();i++){
            boolean changing=oldTime.length()==time.length()&&oldTime.charAt(i)!=time.charAt(i)&&digit<1;
            if(changing){
                type.setAlpha(Math.round(245*clockAlpha*(1-digit)));
                canvas.drawText(oldTime,i,i+1,x,baseline-digit*type.getTextSize()*.12f,type);
                type.setAlpha(Math.round(245*clockAlpha*digit));
                canvas.drawText(time,i,i+1,x,baseline+(1-digit)*type.getTextSize()*.15f,type);
            }else{type.setAlpha(Math.round(245*clockAlpha));canvas.drawText(time,i,i+1,x,baseline,type);}
            x+=widths[i]-type.getTextSize()*.035f;
        }
        if(weather.enabled(true))weatherArt.inline(canvas,weather.snapshot(),tx+w*.0055f,baseline+w*.033f,w*.017f,w*.68f,0xFFF5F1E9,Math.round(245*clockAlpha),false,getContext());
        boolean photoMoving=previous!=null&&rawPhoto<1;
        if(!photoMoving)previous=null;
        if(active&&(photoMoving||(motion&&(digit<1||drift<1))))postInvalidateOnAnimation();
    }
    void dump(java.io.PrintWriter out){out.println("ScenicClock: index="+index+" active="+active+" loading="+loading+" time="+time+" local=true fullscreen=true design=editorial-serif photoInterval="+PHOTO_INTERVAL+" digitTransitions="+digitTransitions+" photoTransitions="+photoTransitions);}
}

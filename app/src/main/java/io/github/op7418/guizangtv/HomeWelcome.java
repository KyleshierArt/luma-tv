package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One photograph, one clock: the welcome clock travels into its actual home position. */
final class HomeWelcome {
    static final String TEXT="Welcome home.";
    private final FrameHomeActivity activity;
    private final FrameLayout root;
    private Greeting greeting;
    private Runnable reveal;
    private static long lastShown=-1;
    private int shown,skipped,generation;
    private boolean pending,clockCovered;
    HomeWelcome(FrameHomeActivity activity,FrameLayout root){this.activity=activity;this.root=root;}
    boolean busy(){return pending||greeting!=null;}
    void show(boolean preview,boolean wake,Runnable ready){
        cancel(false);
        long now=SystemClock.elapsedRealtime();
        if(!UiMotion.enabled(activity)||(!preview&&activity.appearance.get("welcome")==1)
                ||(!preview&&lastShown>=0&&now>=lastShown&&now-lastShown<(wake?1800:60000))){ready.run();return;}
        reveal=ready;final int request=generation;pending=true;
        if(activity.wallpaperDrawable() instanceof ScenicPhotos.Wallpaper)
            start(request,((ScenicPhotos.Wallpaper)activity.wallpaperDrawable()).image());
        else ScenicPhotos.request(activity,ScenicPhotos.WELCOME,image->start(request,image));
    }
    private void start(int request,Bitmap image){
        if(request!=generation||activity.isFinishing()||activity.isDestroyed())return;
        if(image==null){cancel(true);return;}
        pending=false;lastShown=SystemClock.elapsedRealtime();shown++;
        Greeting v=new Greeting(image);greeting=v;
        root.addView(v,new FrameLayout.LayoutParams(-1,-1));v.timeline.start();
    }
    private void reveal(){Runnable value=reveal;reveal=null;if(value!=null)value.run();}
    private final class Greeting extends View {
        private final Bitmap image;
        private final Paint photo=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG),small=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ValueAnimator timeline=ValueAnimator.ofFloat(0,3200);
        private final PathInterpolator ease=new PathInterpolator(.65f,0,.22f,1);
        private final RectF cover=new RectF();
        private final int[] clockLocation=new int[2],ownLocation=new int[2];
        private final SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm",Locale.US);
        private final SimpleDateFormat dateFormat=new SimpleDateFormat(Language.pick(activity,"EEEE, MMMM d","M月d日  EEEE"),Language.locale(activity));
        private long minute=-1;
        private String time="",date="";
        private float elapsed;
        private boolean handedOff;
        Greeting(Bitmap image){
            super(activity);this.image=image;setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            type.setTypeface(DisplayTypography.display(activity));type.setFontFeatureSettings("'tnum' 1, 'lnum' 1");
            small.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            timeline.setDuration(3200);timeline.setInterpolator(new LinearInterpolator());
            timeline.addUpdateListener(a->{
                elapsed=(float)a.getAnimatedValue();
                if(elapsed>=1450&&!handedOff&&greeting==this){
                    handedOff=true;reveal();clockCovered=true;activity.surface.clockArrival(true);
                }
                invalidate();
            });
            timeline.addListener(new AnimatorListenerAdapter(){
                @Override public void onAnimationEnd(Animator animation){if(greeting==Greeting.this)cancel(false);}
            });
        }
        private float range(float start,float length){return Math.max(0,Math.min(1,(elapsed-start)/length));}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight();
            float settle=1-(float)Math.pow(1-range(0,1800),3);
            float scale=Math.max(w/image.getWidth(),h/image.getHeight())*(1.035f-.035f*settle);
            float pw=image.getWidth()*scale,ph=image.getHeight()*scale;
            photo.setAlpha(Math.round(255*(1-ease.getInterpolation(range(1450,1200)))));
            cover.set((w-pw)/2,(h-ph)/2,(w+pw)/2,(h+ph)/2);canvas.drawBitmap(image,null,cover,photo);
            long now=System.currentTimeMillis();
            if(minute!=now/60000){minute=now/60000;Date current=new Date(now);time=timeFormat.format(current);date=dateFormat.format(current).toUpperCase(Locale.US);}
            float appear=range(280,650),arrival=ease.getInterpolation(range(1450,1300));
            TextView homeClock=activity.surface.clock;
            homeClock.getLocationInWindow(clockLocation);getLocationInWindow(ownLocation);
            type.setTextSize(w*.148f);
            float initialBaseline=h*.09f-type.ascent();
            float finalSize=homeClock.getTextSize();
            float size=DisplayTypography.mix(w*.148f,finalSize,arrival);
            type.setTextSize(finalSize);
            float finalX=clockLocation[0]-ownLocation[0]+homeClock.getWidth()-homeClock.getCompoundPaddingRight()-type.measureText(time);
            // Dock mode keeps its clock aligned on the left.
            if(activity.appearance.get("layout")==1)finalX=clockLocation[0]-ownLocation[0]+homeClock.getCompoundPaddingLeft();
            float finalY=clockLocation[1]-ownLocation[1]+homeClock.getBaseline();
            type.setTextSize(size);type.setColor(blend(0xFF33302B,activity.appearance.ink(),arrival));type.setAlpha(Math.round(255*appear));
            canvas.drawText(time,DisplayTypography.mix(w*.07f,finalX,arrival),DisplayTypography.mix(initialBaseline,finalY,arrival),type);
            float greetingAlpha=appear*(1-range(1300,500));
            small.setTextSize(w*.012f);small.setColor(0xFF514A42);small.setAlpha(Math.round(230*greetingAlpha));
            DisplayTypography.tracking(canvas,date,w*.074f,h*.37f,w*.0023f,small);
            type.setTextSize(w*.0425f);type.setColor(0xFF33302B);type.setAlpha(Math.round(255*greetingAlpha));
            canvas.drawText(Language.pick(activity,TEXT,"欢迎回家。"),w*.07f,h*.43f-type.ascent()-(1-greetingAlpha)*h*.009f,type);
        }
    }
    private static int blend(int a,int b,float p){
        int r=Math.round(DisplayTypography.mix(Color.red(a),Color.red(b),p));
        int g=Math.round(DisplayTypography.mix(Color.green(a),Color.green(b),p));
        int blue=Math.round(DisplayTypography.mix(Color.blue(a),Color.blue(b),p));
        return Color.rgb(r,g,blue);
    }
    void cancel(boolean revealHome){
        generation++;pending=false;
        Greeting old=greeting;greeting=null;
        if(old!=null){old.timeline.cancel();root.removeView(old);}
        if(clockCovered){clockCovered=false;activity.surface.clockArrival(false);}
        if(revealHome)reveal();else reveal=null;
    }
    void onInput(){if(busy())skipped++;cancel(true);}
    void dump(java.io.PrintWriter out){out.println("HomeWelcome: visible="+(greeting!=null)+" pending="+pending+" fullscreen=true design=time-arrival duration=3200 clockCovered="+clockCovered+" elapsed="+(greeting==null?-1:Math.round(greeting.elapsed))+" shown="+shown+" skippedByInput="+skipped);}
}

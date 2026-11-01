package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** A stationary rounded material shelf containing a remotely scrollable icon track. */
final class DockShelf extends FrameLayout {
    static final int TILE_WIDTH = 128, GAP = 8;
    static int width(int count) { return Math.min(884, 24 + count * TILE_WIDTH + Math.max(0, count - 1) * GAP); }
    final LinearLayout track;
    final View focusPlate;
    private final FrameLayout viewport;
    private View selected;
    private int offset;
    private boolean focused = true;

    DockShelf(Context context,int count){
        super(context);
        setPadding(dp(12),dp(10),dp(12),dp(10));
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,Outline outline){
                outline.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(24));
            }
        });
        viewport=new FrameLayout(context);
        viewport.setClipChildren(true);viewport.setClipToPadding(true);
        addView(viewport,new LayoutParams(-1,-1));
        focusPlate=new View(context);
        focusPlate.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        focusPlate.setAlpha(0);
        viewport.addView(focusPlate,new LayoutParams(dp(TILE_WIDTH),-1));
        track=new LinearLayout(context);track.setOrientation(LinearLayout.HORIZONTAL);
        int width=count*dp(TILE_WIDTH)+Math.max(0,count-1)*dp(GAP);
        viewport.addView(track,new LayoutParams(width,-1));
    }
    void reveal(View tile,boolean animate){selected=tile;position(animate);}
    void focused(boolean value,boolean animate){if(focused==value)return;focused=value;UiMotion.fade(focusPlate,value?1:0,animate,150);}
    private void position(boolean animate){
        if(selected==null||viewport.getWidth()==0||selected.getWidth()==0)return;
        int available=viewport.getWidth(),content=track.getWidth();
        offset=DockScroll.reveal(offset,selected.getLeft(),selected.getRight(),available,content);
        float x=content<=available?(available-content)/2f:-offset;
        track.animate().cancel();
        if(animate&&UiMotion.enabled(getContext()))track.animate().translationX(x).setDuration(180).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        else track.setTranslationX(x);
        float plateX=x+selected.getLeft();
        focusPlate.animate().cancel();focusPlate.setAlpha(focused?1:0);
        if(animate&&UiMotion.enabled(getContext()))focusPlate.animate().translationX(plateX)
                .setDuration(180).setInterpolator(new DecelerateInterpolator(1.6f))
                .setUpdateListener(a->focusPlate.invalidate()).withEndAction(()->focusPlate.invalidate()).start();
        else {focusPlate.setTranslationX(plateX);focusPlate.invalidate();}
    }
    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom){
        super.onLayout(changed,left,top,right,bottom);position(false);
    }
    @Override protected void onDetachedFromWindow(){track.animate().cancel();focusPlate.animate().cancel();super.onDetachedFromWindow();}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
}

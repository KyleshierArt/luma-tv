package io.github.op7418.guizangtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.animation.PathInterpolator;

/** One moving native material behind the list. Retarget from the visible rect, not the last item. */
final class MenuFocusRail extends View {
    private final RectF rect=new RectF(),target=new RectF();
    private Drawable material;
    private ValueAnimator motion;
    private boolean placed;
    MenuFocusRail(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void material(Drawable value){material=value;invalidate();}
    void move(float left,float top,float width,float height,boolean animate){
        if(motion!=null)motion.cancel();
        target.set(left,top,left+width,top+height);
        if(!placed||!animate||!UiMotion.enabled(getContext())){rect.set(target);placed=true;invalidate();return;}
        final float l=rect.left,t=rect.top,r=rect.right,b=rect.bottom;
        final float dl=target.left-l,dt=target.top-t,dr=target.right-r,db=target.bottom-b;
        motion=ValueAnimator.ofFloat(0,1);motion.setDuration(280);
        motion.setInterpolator(new PathInterpolator(.22f,1,.36f,1));
        motion.addUpdateListener(a->{float v=(float)a.getAnimatedValue();rect.set(l+dl*v,t+dt*v,r+dr*v,b+db*v);invalidate();});motion.start();
    }
    @Override protected void onDraw(Canvas canvas){
        if(!placed||material==null)return;
        material.setBounds(Math.round(rect.left),Math.round(rect.top),Math.round(rect.right),Math.round(rect.bottom));material.draw(canvas);
    }
    void stop(){if(motion!=null)motion.cancel();}
    void dump(java.io.PrintWriter out){out.println("MenuFocusRail: y="+rect.top+" targetY="+target.top+" moving="+(motion!=null&&motion.isRunning()));}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
}

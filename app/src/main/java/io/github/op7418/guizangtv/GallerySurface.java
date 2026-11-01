package io.github.op7418.guizangtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A vertical, circular picker. Focus stays at the centre while the menu moves through it. */
final class GallerySurface extends FrameLayout {
    private static final float STEP=74f,HEIGHT=420f,ROW=76f;
    final java.util.List<View> apps=new ArrayList<>();
    final java.util.List<View> chrome=new ArrayList<>();
    final TextView clock;
    final View shade;
    private final HomeAppearance style;
    private final boolean preview;
    private final FrameLayout wheel;
    private final View plate;
    private final java.util.List<TextView> labels=new ArrayList<>();
    private final java.util.List<ImageView> icons=new ArrayList<>();
    private final java.util.List<String> ids;
    private Bitmap material;
    private int materialRequest,selected,target;
    private float position;
    private ValueAnimator rolling;

    GallerySurface(Context c,HomeAppearance style,HomeItems items,int selected,boolean preview,HomeSurface.Actions actions) {
        super(c);this.style=style;this.preview=preview;ids=new ArrayList<>(items.order);
        this.selected=Math.max(0,Math.min(ids.size()-1,selected));target=this.selected;position=target;
        setClipChildren(false);setClipToPadding(false);
        shade=new Veil(c);addView(shade,new LayoutParams(-1,-1));
        clock=text("",38);clock.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);clock.setTypeface(DisplayTypography.display(c));
        clock.setFontFeatureSettings("'tnum' 1, 'lnum' 1");
        LayoutParams cp=new LayoutParams(dp(190),dp(57),Gravity.TOP|Gravity.RIGHT);cp.setMargins(0,dp(30),dp(58),0);addView(clock,cp);
        wheel=new FrameLayout(c);wheel.setClipChildren(true);wheel.setClipToPadding(true);
        LayoutParams wp=new LayoutParams(dp(432),dp(HEIGHT),Gravity.LEFT|Gravity.CENTER_VERTICAL);wp.leftMargin=dp(46);addView(wheel,wp);chrome.add(wheel);
        plate=new View(c);plate.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(350),dp(ROW));pp.leftMargin=dp(10);pp.topMargin=dp((HEIGHT-ROW)/2);wheel.addView(plate,pp);
        for(int i=0;i<ids.size();i++) {
            final int index=i;String id=ids.get(i);
            LinearLayout tile=new LinearLayout(c);tile.setOrientation(LinearLayout.HORIZONTAL);tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(17),0,dp(17),0);tile.setPivotX(0);tile.setPivotY(dp(ROW/2));
            tile.setId(View.generateViewId());tile.setFocusable(!preview);tile.setFocusableInTouchMode(!preview);tile.setClickable(!preview);
            tile.setContentDescription(items.homeLabel(id));
            ImageView icon=new ImageView(c);icon.setImageDrawable(HomeIcons.get(c,id,style.ink()));
            tile.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
            TextView label=text(items.homeLabel(id),new int[]{28,30,32}[style.get("size")]);
            label.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));label.setSingleLine(true);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.leftMargin=dp(17);label.setGravity(Gravity.CENTER_VERTICAL);tile.addView(label,lp);
            FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(dp(400),dp(ROW));tp.leftMargin=dp(10);tp.topMargin=dp((HEIGHT-ROW)/2);wheel.addView(tile,tp);
            apps.add(tile);labels.add(label);icons.add(icon);
            tile.setOnClickListener(v->actions.open(id));
            tile.setOnFocusChangeListener((v,focus)->{if(focus){centre(index,true);actions.selected(index);}});
            tile.setOnKeyListener((v,key,e)->{
                if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN) {
                    if(e.getAction()==KeyEvent.ACTION_DOWN)apps.get(Math.floorMod(index+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1),apps.size())).requestFocus();
                    return true;
                }
                if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)return true;
                return (key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0;
            });
        }
        for(int i=0;i<apps.size();i++) {
            View v=apps.get(i);v.setNextFocusUpId(apps.get(Math.floorMod(i-1,apps.size())).getId());v.setNextFocusDownId(apps.get((i+1)%apps.size()).getId());
            v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(v.getId());
        }
        colours();render();
    }
    private void centre(int index,boolean animate) {
        selected=index;
        // Choose the next occurrence near the previous target, so 6 -> 0 moves one row, not six.
        target=index+Math.round((target-index)/(float)apps.size())*apps.size();
        if(rolling!=null){rolling.cancel();rolling=null;}
        colours();
        if(preview||!animate||!isAttachedToWindow()||!UiMotion.enabled(getContext())||Math.abs(target-position)<.001f){position=target;render();return;}
        rolling=ValueAnimator.ofFloat(position,target);rolling.setDuration(320);
        rolling.setInterpolator(new android.view.animation.PathInterpolator(.22f,1,.36f,1));
        rolling.addUpdateListener(a->{position=(float)a.getAnimatedValue();render();});rolling.start();
    }
    private void colours() {
        for(int i=0;i<apps.size();i++) {
            labels.get(i).setTextColor(style.ink());
            icons.get(i).getDrawable().setTint(style.ink());
        }
        if(!labels.isEmpty()) {
            float width=labels.get(selected).getPaint().measureText(labels.get(selected).getText().toString())+dp(81);
            android.view.ViewGroup.LayoutParams lp=plate.getLayoutParams();lp.width=Math.round(Math.max(dp(200),Math.min(dp(400),width)));plate.setLayoutParams(lp);
        }
        plate.setBackground(new WallpaperMaterial.Glass(this,plate,material,dp(18),style.light()?0x24FFFFFF:0x1A18252C,style.light()?0x30000000:0x4DFFFFFF,64));
    }
    private void render() {
        int count=apps.size();
        for(int i=0;i<count;i++) {
            float distance=i-position;distance-=Math.round(distance/count)*count;
            float depth=Math.abs(distance),scale=Math.max(.55f,1-.19f*depth);
            float alpha=depth<=1?1-.34f*depth:depth<=2?.66f-.37f*(depth-1):Math.max(0,.29f*(1-(depth-2)/.65f));
            View tile=apps.get(i);tile.setScaleX(scale);tile.setScaleY(scale);tile.setAlpha(alpha);
            tile.setTranslationY(dp(STEP)*distance);tile.setTranslationX(dp(10)*Math.min(3,depth));
            tile.setTranslationZ(dp(5)*Math.max(0,1-depth));
        }
    }
    void revealEntrance(boolean animate) {
        if(rolling!=null){rolling.cancel();rolling=null;}position=target;render();
        wheel.animate().cancel();wheel.setTranslationX(0);
        if(animate&&UiMotion.enabled(getContext())){wheel.setAlpha(0);wheel.animate().alpha(1).setStartDelay(120).setDuration(620).withEndAction(()->wheel.animate().setStartDelay(0)).start();}
        else wheel.setAlpha(1);
    }
    void wallpaper(Drawable d) {
        clock.setTextColor(style.ink());int request=++materialRequest;
        WallpaperMaterial.request(d,8,b->{if(request!=materialRequest)return;material=b;colours();});
    }
    void dump(java.io.PrintWriter out) {out.println("HomeWheel: selected="+selected+" target="+target+" position="+position+" scale="+apps.get(selected).getScaleX()+" y="+apps.get(selected).getTranslationY());}
    @Override protected void onDetachedFromWindow(){if(rolling!=null){rolling.cancel();rolling=null;}wheel.animate().cancel();super.onDetachedFromWindow();}
    private TextView text(String s,int size){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(style.ink());t.setIncludeFontPadding(false);return t;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private final class Veil extends View {
        final Paint p=new Paint();Shader side,right;
        Veil(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){
            side=new LinearGradient(0,0,w,0,new int[]{0xCE0B1114,0xA00B1114,0x350B1114,0x000B1114},new float[]{0,.23f,.39f,.55f},Shader.TileMode.CLAMP);
            right=new RadialGradient(w*.96f,0,w*.30f,new int[]{0x880B1114,0x000B1114},null,Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas c){if(style.light())return;p.setShader(side);c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(right);c.drawRect(0,0,getWidth(),getHeight(),p);}
    }
}

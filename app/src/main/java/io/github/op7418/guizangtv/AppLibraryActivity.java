package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A separate, remote-first app library. Home remains underneath in the same task. */
public final class AppLibraryActivity extends Activity {
    private static final int COLUMNS=6;
    private static final int FOCUS_INK=0xFFF2F4F5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final List<AppLibraryApps.Entry> entries=new ArrayList<>();
    private final List<AppTile> tiles=new ArrayList<>();
    private GridLayout grid;
    private ScrollView scroll;
    private TextView status;
    private String selectedPackage;
    private boolean destroyed,ready;
    private int selected=-1;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                |View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        selectedPackage=state!=null?state.getString("selected-package")
                :getPreferences(0).getString("selected-package","");
        FrameLayout root=new FrameLayout(this);
        root.addView(new Backdrop(),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(56),dp(32),dp(56),dp(32));page.setClipChildren(false);
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        TextView title=label(Language.pick(this,"Apps","应用"),26,0xFFF5F5F7);
        title.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        title.setLetterSpacing(-.025f);
        title.setPadding(dp(14),0,0,dp(12));
        page.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));

        FrameLayout body=new FrameLayout(this);body.setClipChildren(false);
        page.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);
        scroll.setPadding(dp(8),0,dp(8),0);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.setFocusable(false);
        scroll.setSmoothScrollingEnabled(UiMotion.enabled(this));
        grid=new GridLayout(this);grid.setColumnCount(COLUMNS);grid.setClipChildren(false);
        grid.setClipToPadding(false);grid.setPadding(0,dp(8),0,dp(8));grid.setFocusable(false);
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        status=label(Language.pick(this,"Loading…","加载中…"),16,0xFFAEAEB2);status.setGravity(Gravity.CENTER);
        body.addView(status,new FrameLayout.LayoutParams(-1,-1));

        setContentView(root);
        UiMotion.enter(page,0);
        worker.execute(()->{
            List<AppLibraryApps.Entry> apps;
            try { apps=AppLibraryApps.load(this); }
            catch(RuntimeException error) {
                android.util.Log.e("TvHome.AppLibrary","Unable to load apps",error);
                handler.post(()->{if(!destroyed){status.setText(Language.text(this,"应用暂时无法加载，请返回后重试"));}});
                return;
            }
            handler.post(()->{if(!destroyed)showApps(apps);});
        });
    }

    private void showApps(List<AppLibraryApps.Entry> apps) {
        entries.addAll(apps);ready=true;
        if(apps.isEmpty()){status.setText(Language.text(this,"没有可打开的应用，按返回键回到首页"));return;}
        status.setVisibility(View.GONE);
        int restore=0;
        for(int i=0;i<apps.size();i++) {
            final int index=i;
            AppTile tile=new AppTile(apps.get(i));tile.setId(View.generateViewId());
            tile.setOnFocusChangeListener((v,focused)->{
                tile.focused(focused);
                if(focused) {
                    selected=index;selectedPackage=entries.get(index).id;
                }
            });
            tile.setOnClickListener(v->open(entries.get(index)));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(i/COLUMNS),GridLayout.spec(i%COLUMNS,1f));
            p.width=0;p.height=dp(132);p.leftMargin=dp(6);p.rightMargin=dp(6);
            p.bottomMargin=i/COLUMNS==(apps.size()-1)/COLUMNS?0:dp(8);grid.addView(tile,p);tiles.add(tile);
            if(apps.get(i).id.equals(selectedPackage))restore=i;
        }
        for(int i=0;i<tiles.size();i++) {
            AppTile tile=tiles.get(i);int column=i%COLUMNS;
            tile.setNextFocusLeftId(tiles.get(column==0?i:i-1).getId());
            tile.setNextFocusRightId(tiles.get(column==COLUMNS-1||i==tiles.size()-1?i:i+1).getId());
            tile.setNextFocusUpId(tiles.get(i<COLUMNS?i:i-COLUMNS).getId());
            tile.setNextFocusDownId(tiles.get(i/COLUMNS==(tiles.size()-1)/COLUMNS?i:Math.min(i+COLUMNS,tiles.size()-1)).getId());
        }
        final int target=restore;
        grid.post(()->{if(!destroyed)tiles.get(target).requestFocus();});
        UiMotion.enter(grid,0);
    }

    private void open(AppLibraryApps.Entry entry) {
        getPreferences(0).edit().putString("selected-package",entry.id).apply();
        try { startActivity(new Intent(entry.launch)); }
        catch(RuntimeException unavailable) {
            android.util.Log.w("TvHome.AppLibrary","Launch failed: "+entry.id,unavailable);
            Toast.makeText(this,Language.pick(this,"Unable to open ","暂时无法打开 ")+entry.name,Toast.LENGTH_SHORT).show();
        }
    }

    private final class AppTile extends FrameLayout {
        final AppIcon icon;
        final TextView name;
        AppTile(AppLibraryApps.Entry entry) {
            super(AppLibraryActivity.this);
            setFocusable(true);setFocusableInTouchMode(true);setClickable(true);
            setContentDescription(entry.name);setClipChildren(false);setClipToPadding(false);
            icon=new AppIcon(entry);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            place(this,icon,108,108,0,0,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            name=label(entry.name,15,FOCUS_INK);name.setGravity(Gravity.CENTER);name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);name.setPadding(dp(5),0,dp(5),0);
            name.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            name.setAlpha(0);
            place(this,name,-1,21,0,111,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            focused(false);
        }
        void focused(boolean value) {
            UiMotion.fade(name,value?1:0,true,160);
            icon.focused(value);
            UiMotion.focus(icon,value?1.10f:1,value?-dp(2):0,1,true);
        }
    }

    /** Original artwork floats on focus; its name appears only beneath the selected icon. */
    private final class AppIcon extends View {
        final AppLibraryApps.Entry entry;
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        final RectF artwork=new RectF(),ring=new RectF();
        BitmapShader image;
        Drawable fallback;
        ValueAnimator animator;
        float focus;
        AppIcon(AppLibraryApps.Entry entry) {
            super(AppLibraryActivity.this);this.entry=entry;
            if(entry.icon!=null)image=new BitmapShader(entry.icon,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
            else fallback=HomeIcons.get(AppLibraryActivity.this,entry.id,FOCUS_INK);
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            float inset=dp(10);artwork.set(inset,inset,w-inset,h-inset);
            ring.set(artwork);ring.inset(-dp(2),-dp(2));
            if(image!=null) {
                float scale=Math.max(artwork.width()/entry.artwork.width(),artwork.height()/entry.artwork.height());
                Matrix matrix=new Matrix();matrix.setScale(scale,scale);
                matrix.postTranslate(artwork.centerX()-entry.artwork.exactCenterX()*scale,
                        artwork.centerY()-entry.artwork.exactCenterY()*scale);
                image.setLocalMatrix(matrix);
            }
        }
        void focused(boolean value) {
            if(animator!=null)animator.cancel();
            float target=value?1:0;
            if(!UiMotion.enabled(AppLibraryActivity.this)){focus=target;invalidate();return;}
            animator=ValueAnimator.ofFloat(focus,target);animator.setDuration(180);
            animator.addUpdateListener(a->{focus=(float)a.getAnimatedValue();invalidate();});animator.start();
        }
        @Override protected void onDraw(Canvas canvas) {
            paint.setShader(null);
            if(focus>0) {
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(6));paint.setColor(FOCUS_INK);paint.setAlpha(Math.round(10*focus));
                canvas.drawRoundRect(ring,dp(22),dp(22),paint);
                paint.setStrokeWidth(dp(1));paint.setAlpha(Math.round(155*focus));
                canvas.drawRoundRect(ring,dp(22),dp(22),paint);
            }
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF29292C);paint.setAlpha(255);
            canvas.drawRoundRect(artwork,dp(20),dp(20),paint);
            if(image!=null) {
                paint.setShader(image);canvas.drawRoundRect(artwork,dp(20),dp(20),paint);paint.setShader(null);
            } else if(fallback!=null) {
                float inset=dp(12);
                fallback.setBounds(Math.round(artwork.left+inset),Math.round(artwork.top+inset),
                        Math.round(artwork.right-inset),Math.round(artwork.bottom-inset));fallback.draw(canvas);
            }
        }
        @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }

    private final class Backdrop extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader base,glow;
        Backdrop(){super(AppLibraryActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
            base=new LinearGradient(0,0,w,h,new int[]{0xFF242428,0xFF1B1B1F,0xFF111114},null,Shader.TileMode.CLAMP);
            glow=new RadialGradient(w*.12f,0,Math.max(1,w*.66f),new int[]{0x08FFFFFF,0x00FFFFFF},null,Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas){paint.setShader(base);canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(glow);canvas.drawRect(0,0,getWidth(),getHeight(),paint);}
    }

    private TextView label(String value,int size,int color) {
        TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setGravity(Gravity.CENTER_VERTICAL);text.setIncludeFontPadding(false);text.setFocusable(false);return text;
    }
    private void place(FrameLayout parent,View child,int width,int height,int left,int top,int gravity) {
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height),gravity);
        p.leftMargin=dp(left);p.topMargin=dp(top);parent.addView(child,p);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){if(selectedPackage!=null)getPreferences(0).edit().putString("selected-package",selectedPackage).apply();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("selected-package",selectedPackage);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){finish();overridePendingTransition(0,UiMotion.enabled(this)?R.anim.tv_panel_exit:0);}
    @Override protected void onDestroy(){destroyed=true;worker.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args) {
        super.dump(prefix,fd,out,args);
        int originals=0,nativeIcons=0;for(AppLibraryApps.Entry entry:entries){if(entry.icon!=null)originals++;if(entry.xiaomiArtwork)nativeIcons++;}
        out.println("AppLibrary: ready="+ready+" count="+entries.size()+" originalIcons="+originals
                +" xiaomiArtwork="+nativeIcons+" columns="+COLUMNS+" selected="+selected+" package="+selectedPackage+" scrollY="+scroll.getScrollY());
        for(AppLibraryApps.Entry entry:entries)if(entry.xiaomiArtwork)out.println("XiaomiArtwork: "+entry.id+" "+XiaomiAppIcons.resource(entry.id));
    }
}

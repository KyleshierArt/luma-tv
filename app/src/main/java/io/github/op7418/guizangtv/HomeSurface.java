package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The same native home layout is used for the live home and Settings preview. */
final class HomeSurface extends FrameLayout {
    interface Actions { void open(String id); void selected(int index); }
    final List<View> apps = new ArrayList<>();
    private final List<String> ids;
    final ImageView previewPhoto;
    TextView clock;
    private HomeWeatherView weather;
    private GallerySurface gallery;
    private final HomeAppearance style;
    private final boolean preview;
    private final Actions actions;
    private int selected;
    private android.graphics.Bitmap material;
    private int materialRequest;
    private DockShelf dockShelf;
    private MenuFocusRail rail;
    private android.widget.ScrollView listViewport;
    private boolean entrancePrepared;
    private int entranceReveals;
    private final List<View> chrome = new ArrayList<>();
    private View topShade, bottomShade, sideShade, listScroll;
    private View scenicShade;
    private final List<TextView> appLabels=new ArrayList<>(),appTags=new ArrayList<>();
    private boolean immersive;
    HomeSurface(Context c, HomeAppearance style, HomeItems items, Drawable wallpaper, int selected,
                boolean preview, Actions actions) {
        super(c); this.style=style;this.preview=preview;this.actions=actions;this.ids=new ArrayList<>(items.order);
        this.selected=Math.max(0,Math.min(items.order.size()-1,selected));
        previewPhoto = new ImageView(c);
        previewPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if(preview) {
            setBackgroundColor(style.light()?0xFFF0EFE9:0xFF11151B);
            if(style.get("background")==0){previewPhoto.setImageDrawable(wallpaper);addView(previewPhoto,new LayoutParams(-1,-1));}
        }
        if(style.get("layout")==2) {
            gallery=new GallerySurface(c,style,items,this.selected,preview,new Actions(){
                public void open(String id){actions.open(id);}
                public void selected(int index){HomeSurface.this.selected=index;actions.selected(index);}
            });
            addView(gallery,new LayoutParams(-1,-1));apps.addAll(gallery.apps);clock=gallery.clock;
            scenicShade=gallery.shade;chrome.addAll(gallery.chrome);addWeather();wallpaper(wallpaper);
            if(preview)setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
            return;
        }
        boolean dock=style.get("layout")==1;
        if(style.get("background")==0 && dock) {
            topShade=new View(c);
            topShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0xAA071018,0x40071018,0x00071018}));
            addView(topShade,new LayoutParams(-1,dp(150),Gravity.TOP));
            bottomShade=new View(c);
            bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x00070D12,0x28070D12,0x92070D12}));
            addView(bottomShade,new LayoutParams(-1,dp(220),Gravity.BOTTOM));
        } else if(style.get("background")==0) {
            View shade=new View(c);sideShade=shade;
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x990C0C0C,0x360C0C0C,0x700C0C0C}));
            addView(shade,new LayoutParams(-1,-1));
        }
        scenicShade=new ScenicShade(c);scenicShade.setVisibility(INVISIBLE);
        addView(scenicShade,new LayoutParams(-1,-1));
        LinearLayout list;
        if(dock){
            dockShelf=new DockShelf(c,items.order.size());list=dockShelf.track;
            LayoutParams p=new LayoutParams(dp(DockShelf.width(items.order.size())),dp(128),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            p.bottomMargin=dp(44);addView(dockShelf,p);chrome.add(dockShelf);paintShelf();
        }else{
            list=new LinearLayout(c);list.setOrientation(LinearLayout.VERTICAL);
            list.setClipChildren(false);list.setClipToPadding(false);
            FrameLayout stage=new FrameLayout(c);stage.setClipChildren(false);listScroll=stage;
            rail=new MenuFocusRail(c);stage.addView(rail,new LayoutParams(-1,-1));
            ScrollView scroll=new ScrollView(c);listViewport=scroll;scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
            scroll.addView(list,new ScrollView.LayoutParams(-1,-2));
            LayoutParams p=new LayoutParams(dp(620),dp(Math.min(420,items.order.size()*(items.order.size()>6?56:65))),Gravity.CENTER_VERTICAL|Gravity.START);p.leftMargin=dp(56);addView(stage,p);chrome.add(stage);
            stage.addView(scroll,new LayoutParams(-1,-1));
            scroll.getViewTreeObserver().addOnScrollChangedListener(()->positionRail(false));
            stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->positionRail(false));
        }
        for(int i=0;i<items.order.size();i++){
            String id=items.order.get(i);final int index=i;
            String suffix=id.startsWith("hdmi:")?"HDMI "+id.substring(5):"";
            LinearLayout tile=new LinearLayout(c);tile.setOrientation(dock?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
            tile.setGravity(dock?Gravity.CENTER:Gravity.CENTER_VERTICAL);tile.setPadding(dp(dock?8:14),dp(dock?6:4),dp(dock?8:18),dp(dock?6:4));
            TextView label=text(items.homeLabel(id),dock?14:style.size());label.setTypeface(style.typeface());
            appLabels.add(label);
            label.setMaxWidth(dp(dock?116:style.get("apps")==0?510:450));
            label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);
            ImageView icon=new ImageView(c);icon.setTag(id);icon.setImageDrawable(HomeIcons.get(c,id,style.ink()));
            int mode=style.get("apps");
            if(mode!=0){LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(dock?48:32),dp(dock?48:32));if(!dock)ip.rightMargin=dp(16);else ip.bottomMargin=dp(6);tile.addView(icon,ip);}
            // Icon-only mode still reveals the focused app name and keeps its accessible label.
            tile.addView(label,new LinearLayout.LayoutParams(dock?-1:-2,dock?dp(26):-2));
            label.setGravity(dock?Gravity.CENTER:Gravity.CENTER_VERTICAL);
            TextView tag=text(suffix,11);appTags.add(tag);tag.setAlpha(.7f);
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,dock?dp(14):-2);if(!dock)tp.leftMargin=dp(16);tile.addView(tag,tp);
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(dock?dp(DockShelf.TILE_WIDTH):-2,dock?-1:dp(items.order.size()>6?56:65));
            if(dock&&i<items.order.size()-1)row.rightMargin=dp(DockShelf.GAP);list.addView(tile,row);apps.add(tile);
            wire(tile,()->actions.open(id));tile.setContentDescription(items.homeLabel(id)+(suffix.isEmpty()?"":", "+suffix));
            boolean chosen=index==this.selected;
            paint(tile,label,icon,chosen,mode==1);
            tile.setOnFocusChangeListener((v,focus)->{
                paint(tile,label,icon,focus,mode==1);
                if(focus){if(dockShelf!=null){dockShelf.focused(true,false);dockShelf.reveal(tile,true);}this.selected=index;actions.selected(index);positionRail(true);}
                else if(dockShelf!=null)post(()->dockShelf.focused(dockShelf.track.hasFocus(),true));
            });
        }
        clock=text("",26);clock.setTypeface(style.typeface());clock.setGravity(Gravity.END);
        LayoutParams cp=new LayoutParams(dp(160),dp(45),Gravity.TOP|(dock?Gravity.START:Gravity.END));cp.topMargin=dp(45);cp.rightMargin=dp(64);if(dock){cp.leftMargin=dp(60);clock.setGravity(Gravity.START);}addView(clock,cp);
        addWeather();
        for(int i=0;i<apps.size();i++){
            View v=apps.get(i),prev=apps.get(Math.max(0,i-1)),next=apps.get(Math.min(apps.size()-1,i+1));
            v.setNextFocusUpId(dock?v.getId():prev.getId());v.setNextFocusDownId(dock?v.getId():next.getId());
            v.setNextFocusLeftId(dock?prev.getId():v.getId());v.setNextFocusRightId(dock?next.getId():v.getId());
        }
        if(preview)setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        if(dockShelf!=null)dockShelf.reveal(apps.get(this.selected),false);
        wallpaper(wallpaper);
    }
    private void addWeather(){
        weather=new HomeWeatherView(getContext(),style.ink(),preview);
        LayoutParams p=new LayoutParams(dp(194),dp(151),Gravity.TOP|Gravity.RIGHT);
        p.topMargin=dp(style.get("layout")==2?94:96);p.rightMargin=dp(style.get("layout")==2?58:64);addView(weather,p);
    }
    void refreshWeather(){if(weather!=null){weather.invalidate();if(!preview)WeatherStore.get(getContext()).request(false);}}
    void focusItem(String id){int index=ids.indexOf(id);if(index>=0)apps.get(index).requestFocus();else focusApp();}
    void focusApp(){apps.get(selected).requestFocus();}
    private void paint(View v,TextView label,boolean focused,boolean iconsOnly){
        paint(v,label,null,focused,iconsOnly);
    }
    private void paint(View v,TextView label,ImageView icon,boolean focused,boolean iconsOnly){
        boolean animate=!preview&&isAttachedToWindow();
        if(dockShelf==null){
            v.animate().cancel();v.animate().setStartDelay(0);
            float offset=focused?dp(4):0;
            if(animate&&UiMotion.enabled(getContext()))v.animate().alpha(focused?1:style.faded())
                    .scaleX(focused?1.018f:1).scaleY(focused?1.018f:1).translationX(offset)
                    .setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(.22f,1,.36f,1)).start();
            else{v.setAlpha(focused?1:style.faded());v.setScaleX(focused?1.018f:1);v.setScaleY(focused?1.018f:1);v.setTranslationX(offset);}
        }else UiMotion.fade(v,focused?1f:style.faded(),animate,180);
        label.setTextColor(style.ink());
        if(icon!=null){icon.getDrawable().setTint(style.ink());UiMotion.focus(icon,focused?1.08f:1f,focused?-dp(2):0,1,animate);}
        label.setVisibility(iconsOnly&&!focused&&style.get("layout")==0?GONE:VISIBLE);
        if(iconsOnly&&style.get("layout")==1)label.setAlpha(focused?1:0);
        if(dockShelf!=null&&apps.contains(v)){
            v.setBackgroundColor(Color.TRANSPARENT);
        }else if(rail!=null){
            v.setBackgroundColor(Color.TRANSPARENT);
        }else if(style.get("focus")==1&&focused){
            int veil=style.light()?0x2AFFFFFF:new int[]{0x60101418,0x48101418,0x30101418}[style.get("material")];
            v.setBackground(new WallpaperMaterial.Glass(this,v,material,dp(12),veil));
        }else v.setBackgroundColor(Color.TRANSPARENT);
    }
    void wallpaper(Drawable drawable){
        weather.wallpaper(this,drawable,style.get("background")==0);
        if(gallery!=null){gallery.wallpaper(drawable);return;}
        boolean scenic=style.get("background")==0&&drawable instanceof ScenicPhotos.Wallpaper;
        clock.setTypeface(scenic?DisplayTypography.display(getContext()):style.typeface());
        clock.setTextSize(scenic?34:26);
        clock.setFontFeatureSettings(scenic?"'tnum' 1, 'lnum' 1":null);
        clock.setShadowLayer(scenic?0:dp(2),0,dp(1),0x70000000);
        scenicShade.setVisibility(scenic?VISIBLE:INVISIBLE);
        for(View shade:new View[]{sideShade,topShade,bottomShade})if(shade!=null)shade.setVisibility(scenic?INVISIBLE:VISIBLE);
        if(listScroll!=null){
            android.view.ViewGroup.LayoutParams p=listScroll.getLayoutParams();p.width=dp(scenic?340:620);listScroll.setLayoutParams(p);
            for(TextView label:appLabels){label.setTextSize(scenic?25:style.size());label.setMaxWidth(dp(scenic?210:style.get("apps")==0?510:450));}
            for(TextView tag:appTags)tag.setVisibility(scenic?GONE:VISIBLE);
        }

        paintRail();
        int request=++materialRequest;
        if((style.get("focus")!=1&&dockShelf==null)||style.get("background")!=0)return;
        WallpaperMaterial.request(drawable,new int[]{14,42,2}[style.get("material")],field->{
            if(request!=materialRequest)return;material=field;paintShelf();paintRail();
            for(View v:apps)if(rail==null&&dockShelf==null&&(v.isFocused()||(preview&&apps.indexOf(v)==selected)))
                v.setBackground(new WallpaperMaterial.Glass(this,v,material,dp(12),new int[]{0x60101418,0x48101418,0x30101418}[style.get("material")]));
        });
    }
    private void paintRail(){
        if(rail==null)return;
        rail.material(style.get("focus")==1?new WallpaperMaterial.Glass(this,rail,material,dp(14),
                style.light()?0x2AFFFFFF:new int[]{0x60101418,0x48101418,0x30101418}[style.get("material")],
                style.light()?0x18000000:0x18FFFFFF):null);
        positionRail(false);
    }
    private void positionRail(boolean animate){
        if(rail==null||apps.isEmpty()||listViewport==null)return;
        View tile=apps.get(Math.max(0,Math.min(selected,apps.size()-1)));
        if(tile.getWidth()==0)return;
        rail.move(tile.getLeft(),tile.getTop()-listViewport.getScrollY(),
                Math.min(listViewport.getWidth(),tile.getWidth()+dp(8)),tile.getHeight(),animate);
    }
    boolean entrancePrepared(){return entrancePrepared;}
    void clockArrival(boolean covered){
        clock.animate().cancel();clock.animate().setStartDelay(0);
        clock.setAlpha(covered||entrancePrepared?0:1);
        weather.animate().cancel();weather.setAlpha(covered||entrancePrepared?0:1);
    }
    void prepareEntrance(){
        if(preview)return;entrancePrepared=true;
        for(View v:chrome){v.animate().cancel();v.setAlpha(0);}
        clock.animate().cancel();clock.setAlpha(0);
        weather.animate().cancel();weather.setAlpha(0);
        for(View shade:new View[]{scenicShade,sideShade,topShade,bottomShade})if(shade!=null){shade.animate().cancel();shade.setAlpha(0);}
    }
    void revealEntrance(boolean animate){
        if(preview)return;entrancePrepared=false;immersive=false;entranceReveals++;
        boolean motion=animate&&UiMotion.enabled(getContext());
        for(View v:chrome){v.animate().cancel();v.setAlpha(1);}
        if(gallery!=null)gallery.revealEntrance(motion);
        else for(int i=0;i<apps.size();i++){
            View tile=apps.get(i);boolean focused=i==selected;
            tile.animate().cancel();float alpha=gallery!=null||focused?1:style.faded();
            float endX=gallery==null&&dockShelf==null&&focused?dp(4):0;
            if(motion){tile.setAlpha(0);tile.setTranslationX(endX-dp(14));
                tile.animate().alpha(alpha).translationX(endX).setStartDelay(Math.min(240,i*38L))
                        .setDuration(620).setInterpolator(new android.view.animation.PathInterpolator(.22f,1,.36f,1))
                        .withEndAction(()->tile.animate().setStartDelay(0)).start();}
            else{tile.setAlpha(alpha);tile.setTranslationX(endX);tile.animate().setStartDelay(0);}
        }
        clock.animate().cancel();clock.animate().setStartDelay(0);
        if(motion){clock.setAlpha(0);clock.animate().alpha(1).setStartDelay(160).setDuration(700)
                    .withEndAction(()->clock.animate().setStartDelay(0)).start();}else clock.setAlpha(1);
        UiMotion.fade(weather,1,motion,700);
        for(View shade:new View[]{scenicShade,sideShade,topShade,bottomShade})if(shade!=null)UiMotion.fade(shade,1,motion,850);
        if(rail!=null){rail.animate().cancel();rail.setAlpha(motion?0:1);if(motion)rail.animate().alpha(1).setDuration(650).setStartDelay(160).withEndAction(()->rail.animate().setStartDelay(0)).start();}
    }
    private void paintShelf(){
        if(dockShelf==null)return;
        if(style.get("background")==0)
            dockShelf.setBackground(new WallpaperMaterial.Glass(this,dockShelf,material,dp(24),new int[]{0x78101418,0x60101418,0x48101418}[style.get("material")],0x25FFFFFF));
        else dockShelf.setBackground(WallpaperMaterial.rounded(style.light()?0xE6E0DED8:0xE624282B,dp(22)));
        dockShelf.focusPlate.setBackground(new WallpaperMaterial.Glass(this,dockShelf.focusPlate,material,dp(18),
                style.light()?0x20000000:style.get("focus")==1?0x38FFFFFF:0x20FFFFFF,
                style.light()?0x22000000:0x50FFFFFF));
    }
    boolean immersive(){return immersive;}
    void immersive(boolean value,boolean animate){
        if(preview)return;
        if(entrancePrepared){immersive=false;return;}
        if(immersive==value)return;
        immersive=value;
        for(View view:chrome)UiMotion.fade(view,value?0:1,animate,value?700:180);
        UiMotion.fade(clock,value?.45f:1,animate,value?700:180);
        UiMotion.fade(weather,value?.6f:1,animate,value?700:180);
        UiMotion.fade(scenicShade,value?.18f:1,animate,value?700:180);
        if(topShade!=null)UiMotion.fade(topShade,value?.25f:1,animate,value?700:180);
        if(bottomShade!=null)UiMotion.fade(bottomShade,value?.12f:1,animate,value?700:180);
    }
    void dump(java.io.PrintWriter out){out.println("HomeSurface: selected="+selected+" immersive="+immersive+" entrancePrepared="+entrancePrepared+" reveals="+entranceReveals+" plateX="+(dockShelf==null?-1:dockShelf.focusPlate.getTranslationX()));if(rail!=null)rail.dump(out);if(gallery!=null)gallery.dump(out);}
    @Override protected void onDetachedFromWindow(){
        if(rail!=null){rail.stop();rail.animate().cancel();}
        for(View view:chrome)view.animate().cancel();
        for(View view:apps){view.animate().cancel();if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).animate().cancel();}}
        clock.animate().cancel();weather.animate().cancel();
        scenicShade.animate().cancel();
        if(topShade!=null)topShade.animate().cancel();if(bottomShade!=null)bottomShade.animate().cancel();super.onDetachedFromWindow();
    }
    /** Local contrast fades into the photo; no panel edge or image frame. */
    private static final class ScenicShade extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint();
        private android.graphics.Shader side,edges,right;
        ScenicShade(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
            side=new android.graphics.LinearGradient(0,0,w,0,
                    new int[]{0xE6070B10,0xBF070B10,0x55070B10,0x00070B10,0x00070B10},
                    new float[]{0,.2f,.42f,.68f,1},android.graphics.Shader.TileMode.CLAMP);
            edges=new android.graphics.LinearGradient(0,0,0,h,
                    new int[]{0x55070B10,0x00070B10,0x00070B10,0x70070B10},
                    new float[]{0,.25f,.65f,1},android.graphics.Shader.TileMode.CLAMP);
            right=new android.graphics.RadialGradient(w*.95f,h*.06f,Math.max(1,w*.26f),
                    new int[]{0x88070B10,0x00070B10},null,android.graphics.Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            paint.setShader(side);canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(edges);canvas.drawRect(0,0,getWidth(),getHeight(),paint);
            paint.setShader(right);canvas.drawRect(0,0,getWidth(),getHeight(),paint);
        }
    }
    private void wire(View v,Runnable run){
        v.setId(View.generateViewId());v.setFocusable(!preview);v.setFocusableInTouchMode(!preview);v.setClickable(!preview);
        if(!preview){v.setOnClickListener(w->run.run());v.setOnKeyListener((w,key,e)->(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0);}
    }
    private TextView text(String s,float size){TextView v=new TextView(getContext());v.setText(Language.text(getContext(),s));v.setTextSize(size);v.setTextColor(style.ink());v.setIncludeFontPadding(false);if(style.get("background")==0)v.setShadowLayer(dp(2),0,dp(1),0x70000000);return v;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}

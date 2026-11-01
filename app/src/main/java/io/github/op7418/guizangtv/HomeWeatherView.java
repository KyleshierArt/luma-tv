package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.graphics.drawable.Drawable;

/** Home weather is a non-focusable part of the clock, not a launcher entry. */
final class HomeWeatherView extends View {
    private final WeatherStore store;
    private final WeatherArtwork art=new WeatherArtwork();
    private final Paint type=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int ink;private final boolean preview;private boolean listening;
    private int materialRequest;
    private Drawable panel;
    private final WeatherStore.Listener changed=()->{WeatherStore.Snapshot s=data();setContentDescription(s==null?"":s.summary(getContext()));invalidate();};
    HomeWeatherView(Context c,int ink,boolean preview){super(c);store=WeatherStore.get(c);this.ink=ink;this.preview=preview;type.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));setFocusable(false);}
    private WeatherStore.Snapshot data(){return store.enabled(false)?store.snapshot():null;}
    void wallpaper(View stage,Drawable source,boolean photo){
        int request=++materialRequest;
        if(!photo){panel=null;invalidate();return;}
        WallpaperMaterial.request(source,8,field->{
            if(request!=materialRequest)return;
            // Keep the photo registered to the screen. Neutral tint never adds a blue cast.
            panel=new WallpaperMaterial.Glass(stage,this,field,
                    24*getResources().getDisplayMetrics().density,
                    Color.luminance(ink)>.5?0x16000000:0x16FFFFFF,
                    Color.luminance(ink)>.5?0x24FFFFFF:0x16000000,72);invalidate();
        });
    }
    private void watching(){boolean next=!preview&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE;if(next==listening)return;listening=next;if(next)store.listen(changed);else store.unlisten(changed);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();watching();}
    @Override protected void onWindowVisibilityChanged(int visible){super.onWindowVisibilityChanged(visible);if(store!=null)watching();}
    @Override protected void onDetachedFromWindow(){if(listening){store.unlisten(changed);listening=false;}super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);if(!store.enabled(false))return;
        WeatherStore.Snapshot s=data();float d=getResources().getDisplayMetrics().density,w=getWidth();
        if(store.place()==null)return;
        if(panel!=null){panel.setBounds(0,0,getWidth(),getHeight());panel.draw(c);}
        type.setTextAlign(Paint.Align.LEFT);type.setColor(ink);type.setTextSize(15*d);
        float x=18*d;c.drawText(Language.place(getContext(),store.place().name),x,29*d,type);
        type.setTextSize(45*d);c.drawText(s==null?"—":s.degrees(),x,79*d,type);
        if(s!=null)art.icon(c,w-66*d,36*d,45*d,s.code,s.day,false);
        type.setTextSize(13*d);type.setAlpha(235);
        c.drawText(s==null?store.status():s.condition(getContext()),x,105*d,type);
        if(s!=null){type.setTextSize(11*d);type.setAlpha(200);c.drawText(s.range(getContext()),x,125*d,type);}
        if(s!=null&&s.stale()){type.setTextSize(8*d);type.setAlpha(150);c.drawText(WeatherArtwork.updated(getContext(),s),x,141*d,type);}
    }
}

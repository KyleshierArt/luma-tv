package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Launcher entries and original package artwork, loaded off the UI thread. */
final class AppLibraryApps {
    static final class Entry {
        final String id, name;
        final Intent launch;
        final Bitmap icon;
        final Rect artwork;
        final boolean xiaomiArtwork;
        Entry(String id, String name, Intent launch, Bitmap icon,boolean xiaomiArtwork) {
            this.id=id; this.name=name; this.launch=launch; this.icon=icon;
            this.xiaomiArtwork=xiaomiArtwork;
            this.artwork=artworkBounds(icon);
        }
    }

    static List<Entry> load(Context context) {
        PackageManager pm=context.getPackageManager();
        LinkedHashMap<String,ResolveInfo> found=new LinkedHashMap<>();
        LinkedHashMap<String,Intent> intents=new LinkedHashMap<>();
        // Xiaomi My Apps uses the ordinary LAUNCHER entry for its icon, even when
        // the package also exposes a leanback entry with different artwork.
        LinkedHashMap<String,ResolveInfo> nativeArtwork=new LinkedHashMap<>();
        for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))
            if(r.activityInfo!=null)nativeArtwork.putIfAbsent(r.activityInfo.packageName,r);
        // Prefer a TV's leanback entry when a package also has a phone launcher entry.
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(ResolveInfo resolve:pm.queryIntentActivities(query,0)) {
                ActivityInfo info=resolve.activityInfo;
                if(info==null || !info.enabled || !info.applicationInfo.enabled
                        || !info.exported || context.getPackageName().equals(info.packageName)
                        || found.containsKey(info.packageName)) continue;
                ActivityInfo target=info;
                // Xiaomi's enumerated target requires HOME_STATE; its public alias does not.
                if("com.mitv.tvhome".equals(info.packageName) && XiaomiControls.nativeEntriesEnabled()) {
                    try { target=pm.getActivityInfo(new ComponentName(info.packageName,"com.mitv.tvhome.MainActivity"),0); }
                    catch(PackageManager.NameNotFoundException missing) { continue; }
                }
                if(!target.enabled || !target.exported || (target.permission!=null
                        && context.checkSelfPermission(target.permission)!=PackageManager.PERMISSION_GRANTED)) continue;
                found.put(info.packageName,resolve);
                intents.put(info.packageName,new Intent(Intent.ACTION_MAIN).addCategory(category)
                        .setComponent(new ComponentName(target.packageName,target.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            }
        }
        List<Entry> result=new ArrayList<>();
        int size=Math.round(100*context.getResources().getDisplayMetrics().density);
        for(String id:found.keySet()) {
            if(Thread.currentThread().isInterrupted()) break;
            ResolveInfo resolve=found.get(id);
            String name=id;
            try { name=XiaomiControls.label(id,resolve.loadLabel(pm).toString()); }
            catch(RuntimeException unavailable) { android.util.Log.w("TvHome.AppLibrary","Label unavailable: "+id); }
            Bitmap icon=null;boolean xiaomiArtwork=false;
            try {
                if("com.mitv.tvhome".equals(id))
                    // The launcher advertises several unrelated app aliases. Its first
                    // LAUNCHER icon is not a reliable identity for Xiaomi Home.
                    icon=raster(context.getDrawable(R.drawable.xiaomi_home_identity),size);
                else {
                    Drawable nativeIcon=XiaomiAppIcons.get(context,id);
                    if(nativeIcon!=null){
                        icon=raster(nativeIcon,size);xiaomiArtwork=icon!=null;
                        if(icon!=null&&"com.xiaomi.mitv.calendar".equals(id))XiaomiAppIcons.calendarDate(context,icon);
                    }
                    if(icon==null) {
                        ResolveInfo art=nativeArtwork.getOrDefault(id,resolve);
                        icon=raster(pm.getActivityIcon(new ComponentName(id,art.activityInfo.name)),size);
                    }
                }
            }
            catch(PackageManager.NameNotFoundException|RuntimeException unavailable) { android.util.Log.w("TvHome.AppLibrary","Activity icon unavailable: "+id); }
            if(icon==null) {
                try { icon=raster(pm.getApplicationIcon(id),size); }
                catch(PackageManager.NameNotFoundException|RuntimeException unavailable) { android.util.Log.w("TvHome.AppLibrary","Package icon unavailable: "+id); }
            }
            name=Language.app(context,id,name);
            result.add(new Entry(id,name,intents.get(id),icon,xiaomiArtwork));
        }
        Collator collator=Collator.getInstance(Language.locale(context));
        result.sort((a,b)->{int order=collator.compare(a.name,b.name);return order!=0?order:a.id.compareTo(b.id);});
        return result;
    }

    private static Bitmap raster(Drawable drawable,int size) {
        if(drawable==null) return null;
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        int width=drawable.getIntrinsicWidth(),height=drawable.getIntrinsicHeight();
        float ratio=width>0&&height>0?Math.min((float)size/width,(float)size/height):1;
        width=width>0?Math.max(1,Math.round(width*ratio)):size;
        height=height>0?Math.max(1,Math.round(height*ratio)):size;
        drawable.setBounds((size-width)/2,(size-height)/2,(size+width)/2,(size+height)/2);
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }

    /** Ignore transparent letterboxing when presenting legacy wide icons in a square. */
    private static Rect artworkBounds(Bitmap icon) {
        if(icon==null)return new Rect();
        int w=icon.getWidth(),h=icon.getHeight(),left=w,top=h,right=0,bottom=0;
        int[] pixels=new int[w*h];icon.getPixels(pixels,0,w,0,0,w,h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if((pixels[y*w+x]>>>24)>16) {
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        return left<right&&top<bottom?new Rect(left,top,right,bottom):new Rect(0,0,w,h);
    }
}

package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Read the same built-in artwork that Xiaomi's My Apps uses for system packages. */
final class XiaomiAppIcons {
    static String resource(String id) {
        switch(id) {
            case "com.xiaomi.mitv.tvmanager":return "mitv_icn_tvmanager_v2";
            case "com.xiaomi.mitv.mediaexplorer":return "mitv_icn_hdplayer_v3";
            case "com.xiaomi.tweather":return "mitv_icn_weather_v2";
            case "com.xiaomi.mibox.gamecenter":return "mitv_icn_game_v2";
            case "com.xiaomi.mitv.systemui":return "mitv_icn_notification_v2";
            case "com.duokan.cloudalbum":
            case "com.mitv.gallery":return "mitv_icn_photo_v2";
            case "com.xiaomi.mitv.calendar":return "mitv_icn_calendar_v2";
            case "com.xiaomi.mimusic":return "mitv_icn_radio";
            case "com.xiaomi.mitv.settings":
            case "com.android.tv.settings":return "mitv_icn_setting_v2";
            case "com.xiaomi.mitv.shop":
            case "com.xiaomi.mitv.shoppub":return "mitv_icn_shop_v2";
            case "com.xiaomi.mitv.handbook":return "mitv_icn_tvbook_v2";
            case "com.xiaomi.mitv.smartshare":return "mitv_icn_wirelessshow_v2";
            case "com.xiaomi.tv.gallery":return "mitv_icn_huabao_v2";
            case "com.xiaomi.smarthome.tv":return "mitv_icn_mijia_v2";
            case "com.xiaomi.mitv.assistant.manual":return "mitv_icn_touping";
            case "com.xiaomi.mitv.appstore":
            case "com.xiaomi.mitv.appstore.common":return "mitv_icn_store_v2";
            default:return null;
        }
    }
    static Drawable get(Context context,String id) {
        String name=resource(id);if(name==null)return null;
        try {
            Resources r=context.getPackageManager().getResourcesForApplication("com.mitv.tvhome");
            int key=r.getIdentifier(name,"drawable","com.mitv.tvhome");
            return key==0?null:r.getDrawable(key,null);
        } catch(PackageManager.NameNotFoundException|RuntimeException unavailable) {
            return null; // Other firmware can still use the package's original icon.
        }
    }
    static void calendarDate(Context context,Bitmap bitmap) {
        // Xiaomi's calendar resource is a blank face; My Apps adds today's date.
        Canvas c=new Canvas(bitmap);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float w=bitmap.getWidth(),h=bitmap.getHeight();p.setTextAlign(Paint.Align.CENTER);
        Date now=new Date();p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        p.setColor(0xFF2F7EFF);p.setTextSize(h*.46f);
        c.drawText(new SimpleDateFormat("d",Locale.CHINA).format(now),w/2,h*.57f,p);
        p.setColor(0xFF111111);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(h*.155f);
        c.drawText(new SimpleDateFormat(Language.chinese(context)?"EEEE":"EEE",Language.locale(context)).format(now),w/2,h*.78f,p);
    }
}

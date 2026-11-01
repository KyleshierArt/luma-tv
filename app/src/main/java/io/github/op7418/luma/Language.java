package io.github.op7418.luma;

import android.content.*;
import java.util.*;

/** Local language preferences for the standalone TV UI. */
public final class Language {
    public static boolean chinese(Context c){return "zh".equals(code(c));}
    public static String code(Context c){return c.getSharedPreferences("luma-language",0).getString("language","en");}
    public static Locale locale(Context c){return chinese(c)?Locale.SIMPLIFIED_CHINESE:Locale.US;}
    public static void store(Context c,String code){if(!"en".equals(code)&&!"zh".equals(code))return;c.getSharedPreferences("luma-language",0).edit().putString("language",code).commit();Locale.setDefault(locale(c));}
    public static void change(Context c,String code){store(c,code);}
    public static String text(Context c,String value){return chinese(c)||"简体中文".equals(value)?value:LanguageCatalog.english(value);}
    public static String pick(Context c,String english,String chinese){return chinese(c)?chinese:english;}
    public static String place(Context c,String name){return name;}
    public static String app(Context c,String id,String fallback){
        String label=fallback;
        switch(id){
            case "com.xiaodianshi.tv.yst":return pick(c,"bilibili","哔哩哔哩");
            case "com.xiaomi.mitv.settings":case "com.android.tv.settings":label="电视设置";break;
            case "com.xiaomi.mitv.tvmanager":label="电视管家";break;
            case "com.xiaomi.mitv.calendar":label="日历";break;
            case "com.xiaomi.mitv.systemui":label="通知";break;
            case "com.xiaomi.voicecontrol":label="小爱同学";break;
            case "com.xiaomi.mitv.smartshare":label="无线投屏";break;
            case "com.xiaomi.mitv.assistant.manual":label="投屏神器";break;
            case "com.xiaomi.smarthome.tv":label="米家";break;
            case "com.xiaomi.mitv.handbook":label="玩机手册";break;
            case "com.xiaomi.mitv.appstore":label="应用中心";break;
            case "com.ktcp.video":label="云视听极光";break;
            case "com.hunantv.license":label="芒果TV";break;
            case "com.cibn.tv":label="CIBN酷喵";break;
        }
        return text(c,label);
    }
    private Language(){}
}

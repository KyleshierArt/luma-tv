package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import org.json.JSONArray;
import java.util.*;

/** Stable app/source identities; removing an entry never uninstalls an app. */
final class HomeItems {
    static final int MAX_ITEMS=11;
    static final String APPS = "apps:all", SETTINGS = "settings:home";
    static final String SOURCES = "sources:all";
    static final String TV_SETTINGS = "com.xiaomi.mitv.settings";
    static final String[] DEFAULTS = {"hdmi:1", "hdmi:2", "com.xiaodianshi.tv.yst",
            APPS, SOURCES, "com.mitv.tvhome", SETTINGS, TV_SETTINGS};
    final Context context;
    final LinkedHashMap<String,String> names = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    HomeItems(Context c) {
        context = c;
        String[] labels = {"PlayStation 5", "Nintendo Switch", "哔哩哔哩", "全部应用", "信号源", "小米原桌面", "设置", "电视设置"};
        for (int i=0;i<DEFAULTS.length;i++) names.put(DEFAULTS[i], labels[i]);
        discover();
        android.content.SharedPreferences prefs = c.getSharedPreferences("home-items",0);
        boolean migrate = !prefs.getBoolean("sources-migrated", false);
        try {
            JSONArray data = new JSONArray(prefs.getString("order","[]"));
            for (int i=0;i<data.length() && order.size()<MAX_ITEMS;i++) {
                String id = data.getString(i);
                if (migrate && "com.mitv.gallery".equals(id)) id = SOURCES;
                if (validId(id) && !order.contains(id)) order.add(id);
            }
        } catch (Exception ignored) { order.clear(); }
        if (order.isEmpty()) order.addAll(Arrays.asList(DEFAULTS));
        if(!prefs.getBoolean("main-menu-migrated",false)) {
            int cast=order.indexOf("com.xiaomi.mitv.smartshare");
            if(cast>=0){order.remove(cast);if(!order.contains(APPS))order.add(cast,APPS);}
            else if(!order.contains(APPS))order.add(Math.min(3,order.size()),APPS);
            if(!order.contains(SETTINGS))order.add(SETTINGS);
            if(save(new ArrayList<>(order)))prefs.edit().putBoolean("main-menu-migrated",true).commit();
        }
        // Keep a route back to settings even after an older saved order is restored.
        if(!order.contains(SETTINGS))order.add(SETTINGS);
        if(!prefs.getBoolean("native-settings-added",false) && order.size()<MAX_ITEMS) {
            if(!order.contains(TV_SETTINGS))order.add(TV_SETTINGS);
            if(save(new ArrayList<>(order)))prefs.edit().putBoolean("native-settings-added",true).commit();
        }
        if (migrate && save(new ArrayList<>(order)))
            prefs.edit().putBoolean("sources-migrated", true).commit();
    }
    void discover() {
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
                String id = r.activityInfo.packageName;
                if (!id.equals(context.getPackageName()) && r.activityInfo.enabled && r.activityInfo.exported)
                    names.putIfAbsent(id, XiaomiControls.label(id, r.loadLabel(context.getPackageManager()).toString()));
            }
        }
    }
    String name(String id) { return Language.app(context,id,names.getOrDefault(id, context.getSharedPreferences("home-items",0).getString("name:"+id,id))); }
    /** Localized captions share the same persisted language as every Luma page. */
    String homeLabel(String id) {
        if(Language.chinese(context))return name(id);
        switch(id) {
            case "hdmi:1": return "PlayStation 5";
            case "hdmi:2": return "Nintendo Switch";
            case "com.xiaodianshi.tv.yst": return "bilibili";
            case "com.xiaomi.mitv.smartshare": return "Xiaomi Cast";
            case APPS: return "Apps";
            case SETTINGS: return "Settings";
            case SOURCES: return "Inputs";
            case "com.mitv.tvhome": return "Xiaomi Home";
            default: return name(id);
        }
    }
    static boolean validId(String id) { return APPS.equals(id) || SETTINGS.equals(id) || SOURCES.equals(id) || id.equals("hdmi:1") || id.equals("hdmi:2") || id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"); }
    boolean save(List<String> next) {
        if (!next.contains(SETTINGS) || next.isEmpty() || next.size()>MAX_ITEMS || new HashSet<>(next).size()!=next.size()) return false;
        for(String id : next) if(!validId(id)) return false;
        android.content.SharedPreferences.Editor e = context.getSharedPreferences("home-items",0).edit()
                .putString("order",new JSONArray(next).toString());
        for(String id:next) e.putString("name:"+id,name(id));
        if(!e.commit()) return false;
        order.clear(); order.addAll(next); return true;
    }
}

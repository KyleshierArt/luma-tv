package io.github.op7418.guizangtv;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Original MTSU0 profile plus native entry checks for MTEQ0 firmware 22.2.9.1687. */
final class XiaomiControls {
    private XiaomiControls() { }

    static boolean enabled() {
        return "MiTV-MTSU0".equals(Build.MODEL);
    }

    static boolean nativeEntriesEnabled() {
        return enabled() || "MiTV-MTEQ0".equals(Build.MODEL);
    }

    /** MTEQ0: HDMI 1/2/3 are source 23/24/25, verified via the TV player. */
    static boolean openHdmi(Context context, int port) {
        int maxPort = "MiTV-MTEQ0".equals(Build.MODEL) ? 3 : 2;
        if (!nativeEntriesEnabled() || port < 1 || port > maxPort) return false;
        return startPlayer(context, "ExternalSourceActivity", "EXTSRC_PLAY", 22 + port);
    }

    static boolean openSignal(Context context, String source) {
        if (!nativeEntriesEnabled()) return false;
        switch (source) {
            case "hdmi:1": return openHdmi(context, 1);
            case "hdmi:2": return openHdmi(context, 2);
            case "hdmi:3": return openHdmi(context, 3);
            // Source 2 is AV in this firmware's TV player source map.
            case "av": return startPlayer(context, "ExternalSourceActivity", "EXTSRC_PLAY", 2);
            case "atv": return startPlayer(context, "AtvActivity", "ATV_PLAY", null);
            case "dtmb": return startPlayer(context, "dtmb.DTMBActivity", "DTMB_PLAY", null);
            default: return false;
        }
    }

    private static boolean startPlayer(Context context, String activity, String action, Integer input) {
        ComponentName target = new ComponentName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer." + activity);
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(target, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            Intent intent = new Intent("com.xiaomi.mitv.tvplayer." + action).setComponent(target)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (input != null) intent.putExtra("input", input.intValue());
            context.startActivity(intent);
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Xiaomi", "Signal entry unavailable: " + activity, unavailable);
            return false;
        }
    }

    static Intent settings(Context context, String standardAction) {
        if (!nativeEntriesEnabled()) return new Intent(standardAction);
        String action, activity;
        if (Settings.ACTION_SETTINGS.equals(standardAction)) {
            action = "com.xiaomi.mitv.settings.SETTINGS";
            activity = ".entry.MainActivity";
        } else if (Settings.ACTION_WIFI_SETTINGS.equals(standardAction)) {
            action = "com.xiaomi.mitv.settings.WIFI_SETTINGS";
            activity = ".wifi.MitvWifiActivity";
        } else return null;
        Intent intent = new Intent(action).setComponent(ComponentName.unflattenFromString(
                "com.xiaomi.mitv.settings/" + activity));
        return intent.resolveActivity(context.getPackageManager()) != null ? intent : null;
    }

    static String label(String pkg, String original) {
        if (!nativeEntriesEnabled()) return original;
        switch (pkg) {
            case "com.xiaodianshi.tv.yst": return "哔哩哔哩";
            case "com.xiaomi.mitv.smartshare": return "小米投屏";
            case "com.mitv.gallery": return "相册";
            case "com.mitv.tvhome": return "小米桌面";
            default: return original;
        }
    }

    static boolean startStockHome(Context context, ComponentName component) {
        if (!nativeEntriesEnabled() || !"com.mitv.tvhome".equals(component.getPackageName())) return false;
        // The public MAIN alias is exported without HOME_STATE. The directly enumerated
        // MainActivityUserMode target requires Xiaomi's signature permission on this firmware.
        ComponentName alias = new ComponentName("com.mitv.tvhome", "com.mitv.tvhome.MainActivity");
        try {
            ActivityInfo info = context.getPackageManager().getActivityInfo(alias, 0);
            if (!info.enabled || !info.exported) return false;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                return false;
            context.startActivity(new Intent(Intent.ACTION_MAIN).setComponent(alias)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            android.util.Log.w("TvHome.Xiaomi", "Public Xiaomi Home entry unavailable", unavailable);
            return false;
        }
    }
}

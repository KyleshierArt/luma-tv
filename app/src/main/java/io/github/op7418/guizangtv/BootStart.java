package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

/** Opt-in entry called by the stock launcher's configured startup hook. */
public final class BootStart {
    private static final String PREFS = "boot-start";
    static final String EXTRA = "guizang.boot-count";
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    private static boolean tv6() { return "MiTV-MTEQ0".equals(android.os.Build.MODEL); }
    static boolean enabled(Context c) {
        // Public builds require explicit opt-in, even on a configured device.
        return prefs(c).getBoolean("enabled", false);
    }
    private static int bootCount(Context c) {
        return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }
    static void enter(Activity a) {
        int boot = bootCount(a);
        String target=Settings.System.getString(a.getContentResolver(),"start_3rd_app");
        prefs(a).edit().putInt("entry-boot",boot).putLong("entry-elapsed",SystemClock.elapsedRealtime()).commit();
        String reason = BootStartPolicy.skipReason(XiaomiControls.enabled(), enabled(a), boot, prefs(a).getInt("attempted", -1),
                SystemClock.elapsedRealtime());
        if (tv6()) reason = BootStartPolicy.tv6SkipReason(enabled(a), BootStartPolicy.tv6TargetAllowed(target,a.getPackageName()),
                SystemClock.elapsedRealtime(), prefs(a).getLong("attempted-elapsed", -1));
        if (reason != null) { Log.i("GuiZangBoot",reason+" optIn="+enabled(a)+" configured="+configured(a));return; }
        Log.i("GuiZangBoot","Entry accepted boot="+boot+" cachedTarget="+!configured(a));
        if (!prefs(a).edit().putInt("attempted", boot).putLong("attempted-elapsed", SystemClock.elapsedRealtime())
                .putString("status", "系统已调用启动入口，等待界面确认").commit()) return;
        try {
            a.startActivity(new Intent(a, FrameHomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(EXTRA, boot));
        } catch (RuntimeException error) {
            status(a, "启动失败：" + error.getClass().getSimpleName());
            Log.w("GuiZangBoot", "Launch rejected", error);
        }
    }
    private static boolean configured(Context c) {
        return c.getPackageName().equals(Settings.System.getString(c.getContentResolver(), "start_3rd_app"));
    }
    private static void status(Context c, String value) {
        prefs(c).edit().putString("status", value).apply();
        Log.i("GuiZangBoot", value);
    }
    static void confirm(Activity activity) {
        Intent intent = activity.getIntent();
        int boot = intent.getIntExtra(EXTRA, -1);
        if (boot < 0) return;
        intent.removeExtra(EXTRA);
        if (boot == bootCount(activity) && prefs(activity).getInt("attempted", -1) == boot && enabled(activity)) {
            status(activity, tv6() ? "已通过系统启动入口进入桌面" : "已在本次开机后进入桌面");
        }
    }
    static void settings(Activity a, Runnable systemSettings) {
        new AlertDialog.Builder(a).setTitle(Language.text(a,"设置"))
            .setItems(new String[]{Language.text(a,"电视系统设置"),Language.text(a,"开机自动进入")},(d,i)->{if(i==0)systemSettings.run();else show(a);})
            .setNegativeButton(Language.text(a,"返回"),null).show();
    }
    static void show(Activity a) {
        show(a,()->{});
    }
    static void show(Activity a,Runnable changed) {
        boolean supported=XiaomiControls.enabled()||tv6();
        String message=!supported?Language.pick(a,"This TV is not supported.","当前电视尚未适配。"):
            configured(a)||prefs(a).getInt("entry-boot",-1)==bootCount(a)?Language.pick(a,"Startup entry configured.","启动入口已配置。"):
                Language.pick(a,"System setup required.","需要配置系统启动入口。");
        AlertDialog.Builder dialog=new AlertDialog.Builder(a).setTitle(Language.text(a,"开机自动进入")).setMessage(message)
            .setNegativeButton(Language.text(a,"返回"),null);
        if(supported && (!tv6() || configured(a) || prefs(a).contains("enabled")))dialog.setPositiveButton(Language.pick(a,enabled(a)?"Turn Off":"Turn On",enabled(a)?"关闭":"开启"),(d,w)->{
            boolean next=!enabled(a);
            boolean saved=prefs(a).edit().putBoolean("enabled",next).commit();
            if(saved)changed.run();
            Toast.makeText(a,saved?Language.text(a,next?"已开启":"已关闭"):
                    Language.pick(a,"Startup setup unavailable.","启动配置暂不可用。"),Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }
    static void dump(Context c, java.io.PrintWriter out) {
        out.println("BootStart: enabled=" + enabled(c) + " configured=" + configured(c)
                + " boot=" + bootCount(c) + " attempted=" + prefs(c).getInt("attempted", -1)
                + " entryBoot="+prefs(c).getInt("entry-boot",-1)
                + " status=" + prefs(c).getString("status", "none"));
    }
}

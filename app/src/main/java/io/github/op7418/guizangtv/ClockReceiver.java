package io.github.op7418.guizangtv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

/** Receives the firmware's existing idle/exit events from privileged senders. */
public final class ClockReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ((context.getPackageName() + ".DISABLE_CLOCK").equals(action)) {
            HomeClockIdle.enabled(context, false);
            ClockActivity.closeVisible();
        } else if ((context.getPackageName() + ".ENABLE_CLOCK").equals(action)) {
            HomeClockIdle.enabled(context, true);
        } else if ("com.duokan.intent.action.show.screensaver".equals(action)) {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (!HomeClockIdle.enabled(context) || power == null || !power.isInteractive()) return;
            android.util.Log.i("TvHome.Clock", "Firmware idle event received");
            try { ClockActivity.open(context, "system-idle"); }
            catch (RuntimeException error) { android.util.Log.w("TvHome.Clock", "Clock launch rejected", error); }
        } else {
            ClockActivity.closeVisible();
        }
    }
}

package io.github.op7418.guizangtv;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/** Home-only fallback; stops while settings, sources, video or HDMI are foreground. */
final class HomeClockIdle {
    private final FrameHomeActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private final Runnable idle = this::showClock;
    private void showClock() {
        if (active && enabled(activity) && activity.clockEligible()) ClockActivity.open(activity, "home-idle");
    }
    HomeClockIdle(FrameHomeActivity activity) { this.activity = activity; }
    static boolean enabled(Context context) {
        return context.getSharedPreferences("clock-settings", 0).getBoolean("enabled", true);
    }
    static void enabled(Context context, boolean value) {
        context.getSharedPreferences("clock-settings", 0).edit().putBoolean("enabled", value).apply();
    }
    void refresh(boolean eligible) {
        if (!eligible || !enabled(activity)) { pause(); return; }
        if (!active) { active = true; interacted(); }
    }
    void interacted() {
        handler.removeCallbacks(idle);
        if (!active) return;
        long delay = Settings.System.getLong(activity.getContentResolver(), "screen_saver_timeout", 240000);
        if (delay > 0) handler.postDelayed(idle, Math.max(15000, delay));
    }
    void pause() { active = false; handler.removeCallbacks(idle); }
}

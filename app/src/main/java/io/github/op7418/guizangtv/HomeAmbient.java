package io.github.op7418.guizangtv;

import android.os.Handler;
import android.os.Looper;

/** Home-only visual quiet mode. Never launches activities or changes screen power. */
final class HomeAmbient {
    static final long WAIT_MS = 30000;
    private final FrameHomeActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private final Runnable idle = this::quiet;
    private void quiet() {
        if (active && activity.clockEligible() && activity.appearance.get("ambient") == 0)
            activity.surface.immersive(true, true);
    }
    HomeAmbient(FrameHomeActivity activity) { this.activity = activity; }
    void refresh(boolean eligible) {
        if (!eligible || activity.appearance.get("ambient") != 0) { pause(); return; }
        if (!active) { active = true; interacted(); }
    }
    void interacted() {
        handler.removeCallbacks(idle);
        if (activity.surface != null) activity.surface.immersive(false, true);
        if (active) handler.postDelayed(idle, WAIT_MS);
    }
    void pause() {
        active = false; handler.removeCallbacks(idle);
        if (activity.surface != null) activity.surface.immersive(false, false);
    }
}

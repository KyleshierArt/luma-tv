package io.github.op7418.guizangtv;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;

/** Short, interruptible native transforms; obey both app and system motion preferences. */
final class UiMotion {
    private UiMotion() { }
    static boolean enabled(Context c) {
        return ValueAnimator.areAnimatorsEnabled() && new HomeAppearance(c).get("motion") == 0;
    }
    static void focus(View v, float scale, float y, float alpha, boolean animate) {
        v.animate().cancel();v.animate().setStartDelay(0);
        if (animate && enabled(v.getContext()))
            v.animate().scaleX(scale).scaleY(scale).translationY(y).alpha(alpha)
                    .setDuration(180).setInterpolator(new DecelerateInterpolator(1.6f)).start();
        else { v.setScaleX(scale); v.setScaleY(scale); v.setTranslationY(y); v.setAlpha(alpha); }
    }
    static void fade(View v, float alpha, boolean animate, long duration) {
        v.animate().cancel();v.animate().setStartDelay(0);
        if (animate && enabled(v.getContext())) v.animate().alpha(alpha).setDuration(duration).start();
        else v.setAlpha(alpha);
    }
    static void enter(View v, long delay) {
        if (!enabled(v.getContext())) return;
        v.setAlpha(0); v.setTranslationY(8 * v.getResources().getDisplayMetrics().density);
        v.animate().alpha(1).translationY(0).setStartDelay(delay).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.5f)).start();
    }
    static void window(Window window) {
        window.setWindowAnimations(enabled(window.getContext()) ? R.style.TVDialogMotion : 0);
    }
}

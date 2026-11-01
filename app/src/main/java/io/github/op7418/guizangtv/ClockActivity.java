package io.github.op7418.guizangtv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.lang.ref.WeakReference;

/** Standalone screensaver; closing it returns to the previous TV activity. */
public final class ClockActivity extends Activity {
    private static WeakReference<ClockActivity> current = new WeakReference<>(null);
    private ClockView clock;
    private ScenicClockView scenic;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish(); }
    };

    static void open(Context context, String reason) {
        if(context instanceof FrameHomeActivity)((FrameHomeActivity)context).prepareForClock();
        context.startActivity(new Intent(context, ClockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra("reason", reason));
    }
    static void closeVisible() {
        ClockActivity activity = current.get();
        if (activity != null) activity.finish();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = new WeakReference<>(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = .15f;
        getWindow().setAttributes(attributes);
        if(ScenicPhotos.clockEnabled(this)){scenic=new ScenicClockView(this);setContentView(scenic);}
        else {clock = new ClockView(this);setContentView(clock);}
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        android.util.Log.i("TvHome.Clock", "Clock shown: " + getIntent().getStringExtra("reason"));
    }
    @Override protected void onResume() { super.onResume(); if(scenic!=null)scenic.resume();else clock.resume(); }
    @Override protected void onPause() { if(scenic!=null)scenic.pause();else clock.pause(); super.onPause(); }
    @Override protected void onDestroy() {
        unregisterReceiver(screenOff);
        if (current.get() == this) current.clear();
        super.onDestroy();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_POWER || key == KeyEvent.KEYCODE_SLEEP || key == KeyEvent.KEYCODE_WAKEUP)
            return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) finish();
        return true;
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) finish();
        return true;
    }
    @Override public void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter out, String[] args) {
        super.dump(prefix, fd, out, args);
        if (clock != null) clock.dump(out);
        if (scenic != null) scenic.dump(out);
        WeatherStore.get(this).dump(out);
    }
}

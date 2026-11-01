package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** A remote-operated page for the six physical inputs on this TV 6 profile. */
final class SignalSourcePage {
    private static final String[] IDS = {"hdmi:1", "hdmi:2", "hdmi:3", "av", "atv", "dtmb"};
    private static final String[] TITLES = {"HDMI 1", "HDMI 2", "HDMI 3", "AV", "模拟电视", "数字电视"};
    private static final String[] DETAILS = {"PlayStation 5", "Nintendo Switch", "ARC · 外接设备", "AV视频输入", "同轴天线输入", "DTMB 地面数字电视"};
    private final FrameHomeActivity activity;
    private Dialog dialog;
    private int selected;
    private View origin;

    SignalSourcePage(FrameHomeActivity activity) { this.activity = activity; }
    boolean showing() { return dialog != null && dialog.isShowing(); }
    void close() { if (dialog != null) dialog.dismiss(); }

    void show() {
        if (showing()) return;
        origin = activity.getCurrentFocus();
        final Dialog page = new Dialog(activity);
        dialog = page;
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(0xFF10171A);
        ImageView backdrop = new ImageView(activity);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(activity);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xC010171A, 0xF00B1115}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(activity);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(60), dp(36), dp(60), dp(28));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        content.addView(text("信号源", 30, 0xF5FFFFFF), new LinearLayout.LayoutParams(-1, dp(46)));

        LinearLayout grid = new LinearLayout(activity);
        grid.setClipChildren(false);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        List<View> cards = new ArrayList<>();
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(activity);
            line.setClipChildren(false);
            grid.addView(line, new LinearLayout.LayoutParams(-1, dp(144)));
            for (int column = 0; column < 3; column++) {
                final int index = row * 3 + column;
                LinearLayout card = new LinearLayout(activity);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(22), dp(14), dp(22), dp(14));
                card.setId(View.generateViewId());
                card.setFocusable(true);
                card.setFocusableInTouchMode(true);
                card.setClickable(true);
                card.setContentDescription(Language.text(activity,TITLES[index])+Language.pick(activity,", ","，")+Language.text(activity,DETAILS[index]));
                ImageView icon = new ImageView(activity);
                card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                TextView title = text(TITLES[index], 23, 0xF2FFFFFF);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(34));
                tp.topMargin = dp(8);
                card.addView(title, tp);
                TextView detail = text(DETAILS[index], 14, 0xAAFFFFFF);
                card.addView(detail, new LinearLayout.LayoutParams(-1, dp(24)));
                View.OnFocusChangeListener paint = (view, focus) -> {
                    card.setBackground(WallpaperMaterial.rounded(focus ? 0xF5F5F4EF : 0x403E484D, dp(14)));
                    title.setTextColor(focus ? 0xFF172124 : 0xF2FFFFFF);
                    detail.setTextColor(focus ? 0xAA172124 : 0xAAFFFFFF);
                    String mark = index < 2 ? IDS[index] : "control:inputs";
                    icon.setImageDrawable(HomeIcons.get(activity, mark, focus ? 0xFF172124 : 0xDDFFFFFF));
                    if (focus) selected = index;
                    UiMotion.focus(card,focus?1.018f:1f,focus?-dp(2):0,1,card.isAttachedToWindow());
                };
                card.setOnFocusChangeListener(paint);
                paint.onFocusChange(card, false);
                card.setOnClickListener(view -> {
                    if (XiaomiControls.openSignal(activity, IDS[index])) close();
                    else activity.message("暂时无法打开" + TITLES[index]);
                });
                card.setOnKeyListener((view, key, event) ->
                        (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER)
                                && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
                if (column < 2) cp.rightMargin = dp(16);
                FrameLayout slot=new FrameLayout(activity);slot.setClipChildren(false);
                slot.addView(card,new FrameLayout.LayoutParams(-1,-1));line.addView(slot,cp);
                UiMotion.enter(slot,index*18L);
                cards.add(card);
            }
            if (row == 0) grid.addView(new View(activity), new LinearLayout.LayoutParams(-1, dp(14)));
        }
        for (int i = 0; i < cards.size(); i++) {
            View card = cards.get(i);
            card.setNextFocusLeftId(cards.get(i % 3 == 0 ? i : i - 1).getId());
            card.setNextFocusRightId(cards.get(i % 3 == 2 ? i : i + 1).getId());
            card.setNextFocusUpId(cards.get(i < 3 ? i : i - 3).getId());
            card.setNextFocusDownId(cards.get(i >= 3 ? i : i + 3).getId());
        }
        page.setContentView(root);
        page.setOnDismissListener(d -> {
            if (dialog == page) dialog = null;
            if (origin != null && origin.isAttachedToWindow()) origin.requestFocus();
            activity.updateRotation();
        });
        UiMotion.window(page.getWindow());
        page.show();
        Window window = page.getWindow();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setLayout(-1, -1);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        cards.get(selected).requestFocus();
        WallpaperMaterial.request(activity.wallpaperDrawable(), 42, image -> {
            if (dialog == page && page.isShowing() && image != null) backdrop.setImageBitmap(image);
        });
        activity.updateRotation();
    }

    private int dp(float value) { return activity.dp(value); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(Language.text(activity,value));
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }
}

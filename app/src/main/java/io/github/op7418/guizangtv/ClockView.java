package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Offline, black-background clock with low-contrast type and slow movement. */
final class ClockView extends View {
    private final WeatherStore weather;
    private final WeatherArtwork weatherArt=new WeatherArtwork();
    private final WeatherStore.Listener weatherChanged=()->invalidate();
    private final Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat dateFormat;
    private String time = "", date = "";
    private long minute = -1;
    private float x, y;
    private boolean active;

    ClockView(Context context) {
        super(context);weather=WeatherStore.get(context);dateFormat=new SimpleDateFormat(Language.pick(context,"EEEE, MMMM d","M月d日  EEEE"),Language.locale(context));
        setBackgroundColor(Color.BLACK);
        timePaint.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        timePaint.setColor(0xFFA0A8AC);
        timePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        datePaint.setColor(0xFF656D72);
        datePaint.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void resume() { active = true;weather.listen(weatherChanged);invalidate(); }
    void pause() { active = false;weather.unlisten(weatherChanged); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float size = Math.min(112 * density, getWidth() / 6f);
        timePaint.setTextSize(size);
        datePaint.setTextSize(size * .18f);
        long now = System.currentTimeMillis();
        if (minute != now / 60000) {
            minute = now / 60000;
            Date current = new Date(now);
            time = timeFormat.format(current);
            date = dateFormat.format(current);
            setContentDescription(time + "，" + date);
        }
        Paint.FontMetrics tf = timePaint.getFontMetrics(), df = datePaint.getFontMetrics();
        float timeHeight = tf.descent - tf.ascent;
        float gap = size * .1f;
        WeatherStore.Snapshot conditions=weather.enabled(true)?weather.snapshot():null;
        float weatherHeight=conditions==null?0:size*.43f;
        float height = timeHeight + gap + df.descent - df.ascent+weatherHeight;
        float width = Math.max(timePaint.measureText(time), datePaint.measureText(date));
        if(conditions!=null)width=Math.max(width,Math.min(getWidth()*.66f,size*3.8f));
        float[] position = ClockLayout.position(getWidth(), getHeight(), width, height,
                Math.min(60 * density, getWidth() / 12f), SystemClock.elapsedRealtime());
        x = position[0]; y = position[1];
        canvas.drawText(time, x + width / 2, y - tf.ascent, timePaint);
        canvas.drawText(date, x + width / 2, y + timeHeight + gap - df.ascent, datePaint);
        if(conditions!=null)weatherArt.inline(canvas,conditions,x+width*.07f,y+height-size*.18f,size*.15f,width*.86f,0xFF8B959B,255,true,getContext());
        if (active) postInvalidateDelayed(500);
    }

    void dump(java.io.PrintWriter out) {
        out.println("ClockView: time=" + time + " x=" + x + " y=" + y + " active=" + active);
    }
}

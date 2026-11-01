package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/** Offline display face shared by the welcome, home clock and scenic screensaver. */
final class DisplayTypography {
    private static Typeface display;
    static synchronized Typeface display(Context context) {
        if(display==null) {
            try {
                display=new Typeface.Builder(context.getAssets(),"fonts/BodoniModa.ttf")
                        .setFontVariationSettings("'wght' 400, 'opsz' 72").build();
            } catch(RuntimeException unavailable) {
                android.util.Log.w("TvHome.Type","Display font unavailable",unavailable);
                display=Typeface.create("serif",Typeface.NORMAL);
            }
        }
        return display;
    }
    static void tracking(Canvas canvas,String value,float x,float y,float spacing,Paint paint) {
        for(int i=0;i<value.length();i++) {
            canvas.drawText(value,i,i+1,x,y,paint);
            x+=paint.measureText(value,i,i+1)+spacing;
        }
    }
    static float mix(float from,float to,float amount){return from+(to-from)*amount;}
    private DisplayTypography(){}
}

package io.github.op7418.guizangtv;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.util.LruCache;
import android.view.View;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Registered wallpaper materials: blur once off-thread, then only crop during focus motion. */
final class WallpaperMaterial {
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<>(12);
    static void request(Drawable source,int blur,Consumer<Bitmap> ready) {
        Bitmap original=source instanceof ScenicPhotos.Wallpaper?((ScenicPhotos.Wallpaper)source).image():
                source instanceof BitmapDrawable?((BitmapDrawable)source).getBitmap():null;
        if(original==null){ready.accept(null);return;}
        String key=original.getGenerationId()+":"+blur;
        Bitmap cached=CACHE.get(key);if(cached!=null){ready.accept(cached);return;}
        WORKER.execute(()->{
            Bitmap result=CACHE.get(key);
            if(result==null){
                int w=384,h=216;
                Bitmap small=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas canvas=new Canvas(small);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
                float scale=Math.max(w/(float)original.getWidth(),h/(float)original.getHeight());
                float bw=original.getWidth()*scale,bh=original.getHeight()*scale;
                canvas.drawBitmap(original,null,new RectF((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2),paint);
                int[] pixels=new int[w*h];small.getPixels(pixels,0,w,0,0,w,h);
                int[] blurred=GaussianBlur.apply(pixels,w,h,blur/5f);
                small.setPixels(blurred,0,w,0,0,w,h);result=small;CACHE.put(key,result);
            }
            Bitmap material=result;MAIN.post(()->ready.accept(material));
        });
    }
    static GradientDrawable rounded(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}

    /** Draw in stage coordinates so the glass reveals the photo directly behind its bounds. */
    static final class Glass extends Drawable {
        private final View stage,host;
        private final Bitmap field;
        private final float radius;
        private final int veil;
        private final int stroke;
        private final int fieldAlpha;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private final Path clip=new Path();
        Glass(View stage,View host,Bitmap field,float radius,int veil){this(stage,host,field,radius,veil,0);}
        Glass(View stage,View host,Bitmap field,float radius,int veil,int stroke){this(stage,host,field,radius,veil,stroke,255);}
        Glass(View stage,View host,Bitmap field,float radius,int veil,int stroke,int fieldAlpha){this.stage=stage;this.host=host;this.field=field;this.radius=radius;this.veil=veil;this.stroke=stroke;this.fieldAlpha=Math.max(0,Math.min(255,fieldAlpha));}
        @Override public void draw(Canvas canvas){
            Rect bounds=getBounds();clip.reset();clip.addRoundRect(new RectF(bounds),radius,radius,Path.Direction.CW);
            canvas.save();canvas.clipPath(clip);
            if(field!=null){
                float x=0,y=0;View child=host;
                while(child!=stage && child.getParent() instanceof View){View parent=(View)child.getParent();x+=child.getX()-parent.getScrollX();y+=child.getY()-parent.getScrollY();child=parent;}
                paint.setColor(Color.WHITE);paint.setAlpha(fieldAlpha);canvas.drawBitmap(field,null,new RectF(-x,-y,stage.getWidth()-x,stage.getHeight()-y),paint);paint.setAlpha(255);
            }
            canvas.drawColor(veil);canvas.restore();
            if(stroke!=0){paint.setColor(stroke);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);
                canvas.drawRoundRect(new RectF(bounds.left+1,bounds.top+1,bounds.right-1,bounds.bottom-1),radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

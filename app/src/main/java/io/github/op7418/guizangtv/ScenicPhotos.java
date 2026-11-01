package io.github.op7418.guizangtv;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Curated public landscapes, shared by home, welcome and screensaver. */
final class ScenicPhotos {
    static final String[] FILES={"rSYsP5TlEAk.jpg","hqzyUL3M21k.jpg","4axNezh03UA.jpg","vwFvhJf6u_I.jpg","JtF_FvXVQQI.jpg"};
    static final String[] NAMES={"雾中雪岭","雨林山路","热带绿叶","山间彩虹","流光花影"};
    static final int WELCOME=0;
    static final int MATTE=0xFF151618;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(16*1024*1024){
        @Override protected int sizeOf(String key,Bitmap image){return image.getAllocationByteCount();}
    };
    static boolean available(Context c){try(InputStream in=c.getAssets().open(path(0))){return true;}catch(Exception absent){return false;}}
    private static String path(int index){return "wallpapers/bundled/"+FILES[Math.floorMod(index,FILES.length)];}
    static Bitmap load(Context c,int index,boolean thumbnail) throws Exception {
        String key=path(index)+(thumbnail?":thumb":":photo");
        Bitmap cached=CACHE.get(key);if(cached!=null)return cached;
        java.io.File directory=new java.io.File(c.getCacheDir(),"scenic-display-v2");
        java.io.File display=new java.io.File(directory,FILES[Math.floorMod(index,FILES.length)]+(thumbnail?"-thumb":"-screen")+".jpg");
        if(display.isFile()){
            Bitmap disk=BitmapFactory.decodeFile(display.getAbsolutePath());
            if(disk!=null){CACHE.put(key,disk);return disk;}
            display.delete();
        }
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        try(InputStream in=c.getAssets().open(path(index))){BitmapFactory.decodeStream(in,null,o);}
        if(o.outWidth<=0||o.outHeight<=0)throw new java.io.IOException("Invalid scenic image");
        int target=thumbnail?320:1920;o.inSampleSize=1;
        while(Math.max(o.outWidth,o.outHeight)/(o.inSampleSize*2)>=target*.85f)o.inSampleSize*=2;
        o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap image;
        try(InputStream in=c.getAssets().open(path(index))){image=BitmapFactory.decodeStream(in,null,o);}
        if(image==null)throw new java.io.IOException("Scenic image unavailable");
        // Honor camera EXIF orientation without changing the original file.
        int orientation;
        try(InputStream in=c.getAssets().open(path(index))){orientation=new android.media.ExifInterface(in).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}
        Matrix m=new Matrix();
        switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;
            case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;
            case 7:m.setRotate(270);m.postScale(-1,1);break;case 8:m.setRotate(270);break;}
        if(!m.isIdentity()){Bitmap rotated=Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),m,true);if(rotated!=image)image.recycle();image=rotated;}
        int longest=Math.max(image.getWidth(),image.getHeight());
        if(longest>target){
            float ratio=target/(float)longest;
            Bitmap scaled=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*ratio),Math.round(image.getHeight()*ratio),true);
            if(scaled!=image)image.recycle();image=scaled;
        }
        // Local display cache avoids unpacking and decoding the 6K original on every cold launch.
        java.io.File temporary=null;
        try{
            if(directory.isDirectory()||directory.mkdirs()){
                temporary=java.io.File.createTempFile("photo-",".jpg",directory);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(temporary)){
                    if(image.compress(Bitmap.CompressFormat.JPEG,93,out))temporary.renameTo(display);
                }
            }
        }catch(java.io.IOException unavailable){android.util.Log.w("TvHome.Scenic","Display cache unavailable",unavailable);}
        finally{if(temporary!=null&&temporary.exists())temporary.delete();}
        CACHE.put(key,image);return image;
    }
    static void request(Context c,int index,Consumer<Bitmap> ready){
        Context app=c.getApplicationContext();
        WORKER.execute(()->{Bitmap value=null;try{value=load(app,index,false);}catch(Exception e){android.util.Log.w("TvHome.Scenic","Photo unavailable",e);}
            final Bitmap image=value;MAIN.post(()->ready.accept(image));});
    }
    static RectF fit(Bitmap image,RectF area){
        float scale=Math.min(area.width()/image.getWidth(),area.height()/image.getHeight());
        float w=image.getWidth()*scale,h=image.getHeight()*scale;
        return new RectF(area.centerX()-w/2,area.centerY()-h/2,area.centerX()+w/2,area.centerY()+h/2);
    }
    static void draw(Canvas canvas,Bitmap image,RectF area,Paint paint){if(image!=null)canvas.drawBitmap(image,null,fit(image,area),paint);}
    static boolean clockEnabled(Context c){return c.getSharedPreferences("clock-settings",0).getBoolean("scenic",true)&&available(c);}
    static void clockEnabled(Context c,boolean value){c.getSharedPreferences("clock-settings",0).edit().putBoolean("scenic",value).apply();}

    /** Full-bleed wallpaper shared by the real home and its Settings preview. */
    static final class Wallpaper extends Drawable {
        private final Bitmap photo;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Wallpaper(Context c,Bitmap photo){this.photo=photo;}
        Bitmap image(){return photo;}
        @Override public int getIntrinsicWidth(){return 1920;}
        @Override public int getIntrinsicHeight(){return 1080;}
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);
            float w=b.width(),h=b.height();
            float scale=Math.max(w/photo.getWidth(),h/photo.getHeight());
            float pw=photo.getWidth()*scale,ph=photo.getHeight()*scale;
            canvas.clipRect(0,0,w,h);
            canvas.drawBitmap(photo,null,new RectF((w-pw)/2,(h-ph)/2,(w+pw)/2,(h+ph)/2),paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}

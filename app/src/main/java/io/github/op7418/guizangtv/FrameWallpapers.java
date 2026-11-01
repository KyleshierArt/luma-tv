package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bundled wallpapers: bounded decoding, saved choice, foreground-only rotation. */
final class FrameWallpapers {
    private static final class Entry {
        final String id, name, file, thumb;
        Entry(JSONObject json) throws org.json.JSONException {
            id = json.getString("id"); name = json.getString("name");
            file = json.getString("file"); thumb = json.getString("thumb");
        }
    }
    private final Activity activity;
    private final ImageView photo, transition;
    private final SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Entry> entries = new ArrayList<>();
    private boolean rotating, active, loading, destroyed, hasImage;
    private int current, minutes, generation;
    Runnable changed=()->{};
    private final Runnable rotate = this::advance;
    private void advance() {
        int next=WallpaperCycle.next(current,entries.size());
        load(next);
    }

    FrameWallpapers(Activity activity, ImageView photo, ImageView transition) {
        this.activity = activity; this.photo = photo; this.transition = transition;
        prefs = activity.getSharedPreferences("frame-wallpapers", Activity.MODE_PRIVATE);
        rotating = prefs.getBoolean("rotating", true);
        minutes = WallpaperCycle.minutes(prefs.getInt("minutes", WallpaperCycle.DEFAULT_MINUTES));
        try (InputStream stream = openCatalog()) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = stream.read(buffer)) != -1) bytes.write(buffer, 0, n);
            JSONArray json = new JSONArray(bytes.toString("UTF-8"));
            if (json.length() > 32) throw new IllegalArgumentException("At most 32 wallpapers");
            for (int i = 0; i < json.length(); i++) {
                JSONObject entry=json.getJSONObject(i);
                // Use the same cached landscape in the home and photo clock.
                if (i < ScenicPhotos.FILES.length && entry.getString("file").equals("bundled/"+ScenicPhotos.FILES[i]))
                    entry.put("file","scenic:"+i).put("thumb","scenic:"+i);
                entries.add(new Entry(entry));
            }
        } catch (Exception error) {
            android.util.Log.e("TvHome.Wallpaper", "Catalog unavailable", error);
        }
        String id = prefs.getString("selected", "");
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) current = i;
        if (entries.isEmpty()) {
            String[] names = {"暮色", "松林", "暖砂", "深海", "紫夜"};
            for (int i = 0; i < names.length; i++) {
                try {
                    entries.add(new Entry(new JSONObject().put("id", "builtin-" + i)
                            .put("name", names[i]).put("file", "builtin:" + i)
                            .put("thumb", "builtin:" + i)));
                } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
            }
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) current = i;
        }
        load(current);
    }

    private InputStream openCatalog() throws java.io.IOException {
        try { return activity.getAssets().open("wallpapers/local/catalog.json"); }
        catch (java.io.FileNotFoundException absent) {
            return activity.getAssets().open("wallpapers/catalog.json");
        }
    }

    private Bitmap decode(String file, boolean thumbnail) throws Exception {
        if(file.startsWith("scenic:"))return ScenicPhotos.load(activity,Integer.parseInt(file.substring(7)),thumbnail);
        if (file.startsWith("builtin:")) {
            int[][] palettes = {{0xFF172B3B, 0xFFB78060}, {0xFF152C28, 0xFF6D8B68},
                    {0xFF3C302B, 0xFFC6A376}, {0xFF0B2238, 0xFF397E92}, {0xFF24203A, 0xFF866B8D}};
            int index = Integer.parseInt(file.substring(8));
            Bitmap image = Bitmap.createBitmap(thumbnail ? 320 : 1920, thumbnail ? 180 : 1080,
                    Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas = new android.graphics.Canvas(image);
            android.graphics.Paint paint = new android.graphics.Paint();
            paint.setShader(new android.graphics.LinearGradient(0, image.getHeight(), image.getWidth(), 0,
                    palettes[index], null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, image.getWidth(), image.getHeight(), paint);
            return image;
        }
        try (InputStream stream = activity.getAssets().open("wallpapers/" + file)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap image = BitmapFactory.decodeStream(stream, null, options);
            if (image == null) throw new java.io.IOException("Image unavailable");
            return image;
        }
    }

    private void load(int index) { load(index, () -> {}); }
    private void load(int index, Runnable success) {
        if (destroyed || entries.isEmpty()) return;
        handler.removeCallbacks(rotate);
        final int request = ++generation;
        loading = true;
        worker.execute(() -> {
            Bitmap bitmap = null;
            try { bitmap = decode(entries.get(index).file, false); }
            catch (Exception error) { android.util.Log.e("TvHome.Wallpaper", "Decode failed", error); }
            final Bitmap image = bitmap;
            handler.post(() -> {
                if (destroyed || request != generation) {
                    if (image != null && !scenic(index)) image.recycle();
                    return;
                }
                loading = false;
                if (image == null) {
                    Toast.makeText(activity, Language.text(activity,"壁纸加载失败，保留当前图片"), Toast.LENGTH_SHORT).show();
                    schedule();
                    return;
                }
                if (!prefs.edit().putString("selected", entries.get(index).id).commit()) {
                    if(!scenic(index))image.recycle();
                    Toast.makeText(activity,Language.text(activity,"未能保存壁纸，保留原设置"),Toast.LENGTH_SHORT).show();
                    schedule(); return;
                }
                transition.animate().cancel();
                if (transition.getDrawable() != null) photo.setImageDrawable(transition.getDrawable());
                transition.setImageDrawable(null);
                if (!hasImage || !active) {
                    photo.setImageDrawable(drawable(index,image));
                    changed.run();
                } else {
                    transition.setImageDrawable(drawable(index,image));
                    transition.setAlpha(0f);
                    transition.animate().alpha(1f).setDuration(700).withEndAction(() -> {
                        photo.setImageDrawable(drawable(index,image));
                        changed.run();
                        transition.setImageDrawable(null);
                        transition.setAlpha(0f);
                    }).start();
                }
                hasImage = true;
                current = index;
                success.run();
                android.util.Log.i("TvHome.Wallpaper", "Applied " + entries.get(current).name
                        + " rotating=" + rotating + " minutes=" + minutes);
                schedule();
            });
        });
    }

    private void schedule() {
        handler.removeCallbacks(rotate);
        if (active && !destroyed && !loading && rotating && entries.size() > 1)
            handler.postDelayed(rotate, minutes * 60_000L);
    }

    void resume() { active = true; schedule(); }
    void pause() {
        active = false;
        handler.removeCallbacks(rotate);
    }
    void destroy() {
        destroyed = true;
        generation++;
        previewGeneration++;
        pause();
        transition.animate().cancel();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    android.graphics.drawable.Drawable thumbnail(int index) {
        try { return new android.graphics.drawable.BitmapDrawable(activity.getResources(), decode(entries.get(index).thumb,true)); }
        catch (Exception unavailable) { return null; }
    }
    int count() { return entries.size(); }
    int selected() { return current; }
    String name(int index) { return Language.text(activity,entries.get(index).name); }
    boolean rotating() { return rotating; }
    int minutes() { return minutes; }
    void select(int index, Runnable success) { load(index, ()->{
        success.run();
    }); }
    private boolean scenic(int index){return entries.get(index).file.startsWith("scenic:");}
    private android.graphics.drawable.Drawable drawable(int index,Bitmap image){return scenic(index)?new ScenicPhotos.Wallpaper(activity,image):new android.graphics.drawable.BitmapDrawable(activity.getResources(),image);}

    boolean rotation(boolean enabled, int value) {
        int next = WallpaperCycle.minutes(value);
        if (!prefs.edit().putBoolean("rotating", enabled).putInt("minutes", next).commit()) return false;
        rotating = enabled; minutes = next; schedule(); return true;
    }
    private int previewGeneration;
    void cancelPreview() { previewGeneration++; }
    void cancelSelection() { generation++; loading=false; schedule(); }
    void preview(int index, ImageView target, Runnable ready) {
        final int request=++previewGeneration;
        worker.execute(() -> {
            Bitmap value=null;
            try { value=decode(entries.get(index).file,false); }
            catch(Exception error) { android.util.Log.w("TvHome.Wallpaper","Preview unavailable",error); }
            final Bitmap image=value;
            handler.post(() -> {
                if(destroyed || request!=previewGeneration){if(image!=null&&!scenic(index))image.recycle();return;}
                if(image!=null){target.setImageDrawable(drawable(index,image));ready.run();}
                else Toast.makeText(activity,Language.text(activity,"预览加载失败，可重试或返回"),Toast.LENGTH_SHORT).show();
            });
        });
    }

    void dump(PrintWriter out) {
        out.println("FrameWallpaper: count=" + entries.size() + " index=" + current
                + " rotating=" + rotating + " minutes=" + minutes + " active=" + active
                + " loading=" + loading
                + " name=" + (entries.isEmpty() ? "" : entries.get(current).name));
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}

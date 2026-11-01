package io.github.op7418.guizangtv;
import android.content.Context;
import android.content.SharedPreferences;
/** Public defaults only; no imported device profile or personal preferences. */
final class LocalSetup {
    static void apply(Context context) {
        SharedPreferences marker=context.getSharedPreferences("public-setup",0);
        if(marker.getBoolean("seeded",false))return;
        boolean saved=context.getSharedPreferences("home-appearance",0).edit()
                .putInt("layout",2).putInt("background",0).putInt("controls",1)
                .putInt("apps",2).putInt("focus",1).commit();
        saved &= context.getSharedPreferences("frame-wallpapers",0).edit()
                .putString("selected","unsplash-rSYsP5TlEAk").commit();
        if(saved)marker.edit().putBoolean("seeded",true).commit();
    }
    private LocalSetup(){}
}

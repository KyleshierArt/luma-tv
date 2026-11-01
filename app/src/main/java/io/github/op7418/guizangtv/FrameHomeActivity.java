package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.*;
import android.content.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.*;
import java.util.*;

/** Native TV home and one grouped Settings entry. */
public final class FrameHomeActivity extends Activity implements HomeSurface.Actions {
    final Handler handler = new Handler(Looper.getMainLooper());
    HomeAppearance appearance;
    HomeItems items;
    FrameWallpapers wallpapers;
    HomeSettings settings;
    SignalSourcePage sources;
    HomeClockIdle clockIdle;
    HomeAmbient ambient;
    private HomeWelcome welcome;
    private boolean welcomePending=true,forceWelcome,entering,screenWasOff,returnFromClock;
    private int wakeTransitions,clockReturns;
    private final BroadcastReceiver screenEvents=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){
                screenWasOff=true;welcomePending=true;forceWelcome=true;entering=false;
                welcome.cancel(false);if(surface!=null)surface.prepareEntrance();
            }else if(Intent.ACTION_SCREEN_ON.equals(intent.getAction())){
                WeatherStore.get(FrameHomeActivity.this).foreground();
                if(screenWasOff){screenWasOff=false;welcomePending=true;forceWelcome=true;wakeTransitions++;}
                welcomeIfReady();
            }
        }
    };
    private int wakingKey=-1;
    private boolean wakingTouch;
    HomeSurface surface;
    private FrameLayout root;
    private ImageView photo, transition;
    private boolean resumed;
    private int selected;
    private final Runnable tick = new Runnable(){public void run(){
        String time=new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date());
        if(surface!=null){surface.clock.setText(time);surface.refreshWeather();}
        if(settings!=null)settings.time(time);
        handler.postDelayed(this,60000-System.currentTimeMillis()%60000);
    }};
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);java.util.Locale.setDefault(Language.locale(this));Language.change(this,Language.code(this));
        LocalSetup.apply(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        appearance=new HomeAppearance(this);items=new HomeItems(this);
        selected=getSharedPreferences("frame-home",0).getInt("selected",0);
        root=new FrameLayout(this);photo=new ImageView(this);transition=new ImageView(this);
        welcome=new HomeWelcome(this,root);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);transition.setScaleType(ImageView.ScaleType.CENTER_CROP);transition.setAlpha(0);
        root.addView(photo,new FrameLayout.LayoutParams(-1,-1));root.addView(transition,new FrameLayout.LayoutParams(-1,-1));
        wallpapers=new FrameWallpapers(this,photo,transition);
        settings=new HomeSettings(this);
        sources=new SignalSourcePage(this);
        clockIdle=new HomeClockIdle(this);
        ambient=new HomeAmbient(this);
        wallpapers.changed=()->{if(surface!=null)surface.wallpaper(wallpaperDrawable());welcomeIfReady();};
        setContentView(root);refreshHome();surface.focusApp();
        IntentFilter screens=new IntentFilter(Intent.ACTION_SCREEN_OFF);screens.addAction(Intent.ACTION_SCREEN_ON);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screenEvents,screens,Context.RECEIVER_EXPORTED);
        else registerReceiver(screenEvents,screens);
    }
    void refreshHome(){
        int restore=Math.max(0,Math.min(items.order.size()-1,selected));
        selected=restore;
        if(surface!=null)root.removeView(surface);
        root.setBackgroundColor(appearance.light()?0xFFF0EFE9:0xFF11151B);
        photo.setVisibility(appearance.get("background")==0?View.VISIBLE:View.INVISIBLE);
        transition.setVisibility(photo.getVisibility());
        surface=new HomeSurface(this,appearance,items,wallpaperDrawable(),restore,false,this);
        if(welcomePending||entering)surface.prepareEntrance();
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        surface.apps.get(restore).requestFocus();
        selected=restore;
        handler.removeCallbacks(tick);tick.run();updateRotation();
    }
    HomeSurface preview(){return new HomeSurface(this,appearance,items,wallpaperDrawable(),selected,true,this);}
    private void welcomeIfReady(){
        if(!resumed||!welcomePending||photo.getDrawable()==null||settings.showing()||sources.showing())return;
        boolean wake=forceWelcome;welcomePending=false;forceWelcome=false;
        if(entering||welcome.busy())return;
        returnFromClock=false;entering=true;surface.prepareEntrance();updateRotation();
        welcome.show(false,wake,()->{entering=false;surface.revealEntrance(true);updateRotation();});
    }
    void previewWelcome(){
        welcomePending=false;entering=true;surface.prepareEntrance();updateRotation();
        welcome.show(true,false,()->{entering=false;surface.revealEntrance(true);updateRotation();});
    }
    void prepareForClock(){returnFromClock=true;}
    Drawable wallpaperDrawable(){return photo.getDrawable();}
    boolean clockEligible(){return resumed&&!entering&&!welcomePending&&!settings.showing()&&!sources.showing();}
    void updateRotation(){if(clockEligible()&&appearance.get("background")==0)wallpapers.resume();else wallpapers.pause();if(clockIdle!=null)clockIdle.refresh(clockEligible());if(ambient!=null)ambient.refresh(clockEligible());if(welcomePending)welcomeIfReady();}
    @Override public void selected(int index){selected=index;getSharedPreferences("frame-home",0).edit().putInt("selected",index).apply();}
    @Override public void open(String id){
        if(HomeItems.APPS.equals(id)){showLibrary();return;}
        if(HomeItems.SETTINGS.equals(id)){settings.show();return;}
        if(HomeItems.TV_SETTINGS.equals(id)){systemSettings();return;}
        if(HomeItems.SOURCES.equals(id)){sources.show();return;}
        if(id.startsWith("hdmi:")){if(!XiaomiControls.openHdmi(this,Integer.parseInt(id.substring(5))))message("暂时无法切换信号源");return;}
        if("com.mitv.tvhome".equals(id)){stockHome();return;}
        Intent intent=getPackageManager().getLeanbackLaunchIntentForPackage(id);
        if(intent==null)intent=getPackageManager().getLaunchIntentForPackage(id);
        launch(intent,"应用暂时不可用");
    }
    void stockHome(){if(!XiaomiControls.startStockHome(this,new ComponentName("com.mitv.tvhome","com.mitv.tvhome.MainActivity")))message("暂时无法打开小米桌面");}
    void systemSettings(){Intent i=XiaomiControls.settings(this,Settings.ACTION_SETTINGS);if(i==null)i=new Intent(Settings.ACTION_SETTINGS);launch(i,"暂时无法打开设置");}
    private void launch(Intent i,String error){if(i==null){message(error);return;}try{startActivity(i);}catch(RuntimeException unavailable){android.util.Log.w("TvHome.Launch",error+" "+i,unavailable);message(error);}}
    private void showLibrary(){
        surface.focusItem(HomeItems.APPS);
        startActivity(new Intent(this,AppLibraryActivity.class));
        overridePendingTransition(UiMotion.enabled(this)?R.anim.tv_panel_enter:0,0);
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;BootStart.confirm(this);handler.removeCallbacks(tick);tick.run();
        WeatherStore.get(this).foreground();
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        if(screenWasOff&&power.isInteractive()){screenWasOff=false;welcomePending=true;forceWelcome=true;wakeTransitions++;}
        if(returnFromClock&&!welcomePending){returnFromClock=false;clockReturns++;surface.prepareEntrance();surface.revealEntrance(true);}
        else if(!welcomePending&&!entering)surface.revealEntrance(false);
        updateRotation();welcomeIfReady();
    }
    @Override protected void onNewIntent(Intent i){
        super.onNewIntent(i);setIntent(i);
        if(i.hasExtra(BootStart.EXTRA)){welcomePending=true;forceWelcome=true;}
        if(ambient!=null)ambient.interacted();
    }
    @Override public void onUserInteraction(){super.onUserInteraction();welcomePending=false;forceWelcome=false;if(welcome!=null)welcome.onInput();if(entering||surface.entrancePrepared()){entering=false;surface.revealEntrance(true);updateRotation();}if(clockIdle!=null)clockIdle.interacted();if(ambient!=null)ambient.interacted();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(wakingKey==e.getKeyCode()) {if(e.getAction()==KeyEvent.ACTION_UP)wakingKey=-1;return true;}
        int key=e.getKeyCode();
        boolean wake=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP
                ||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_BACK;
        if(e.getAction()==KeyEvent.ACTION_DOWN&&wake&&surface!=null&&(surface.immersive()||entering||welcomePending||welcome.busy())){
            wakingKey=key;onUserInteraction();return true;
        }
        return super.dispatchKeyEvent(e);
    }
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&surface!=null&&(surface.immersive()||entering||welcomePending||welcome.busy())){wakingTouch=true;onUserInteraction();}
        if(wakingTouch){if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)wakingTouch=false;return true;}
        return super.dispatchTouchEvent(e);
    }
    @Override protected void onPause(){resumed=false;if(!screenWasOff)welcomePending=false;entering=false;welcome.cancel(false);wakingKey=-1;wakingTouch=false;ambient.pause();clockIdle.pause();handler.removeCallbacks(tick);wallpapers.pause();sources.close();super.onPause();}
    @Override protected void onDestroy(){unregisterReceiver(screenEvents);welcome.cancel(false);ambient.pause();clockIdle.pause();settings.close();sources.close();wallpapers.destroy();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed(){if(XiaomiControls.enabled())stockHome();else super.onBackPressed();}
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter out,String[] args){
        super.dump(prefix,fd,out,args);wallpapers.dump(out);BootStart.dump(this,out);
        surface.dump(out);WeatherStore.get(this).dump(out);
        welcome.dump(out);
        out.println("HomeEntrance: entering="+entering+" pending="+welcomePending+" wakeTransitions="+wakeTransitions+" clockReturns="+clockReturns);
        out.println("LumaLanguage: "+Language.code(this));
        out.println("HomeAppearance: "+appearance.prefs.getAll()+" items="+items.order+" settings="+settings.page());
    }
    void message(String value){Toast.makeText(this,Language.text(this,value),Toast.LENGTH_LONG).show();}
    int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Remote-first grouped settings. Focus browses; OK commits; Back returns one level. */
final class HomeSettings {
    private final FrameHomeActivity a;
    private Dialog dialog;
    private String page="overview";
    private final Map<String,Integer> remembered=new HashMap<>();
    private final List<View> choices=new ArrayList<>();
    private final Deque<List<String>> undo=new ArrayDeque<>();
    private LinearLayout options;
    private GridLayout overviewGrid;
    private HomeSurface preview;
    private FrameLayout previewHost;
    private TextView status;
    private boolean rendering;
    private FrameLayout stage;
    private ImageView backdrop;
    private View content;
    private int backdropRequest;
    private final WeatherStore.Listener weatherChanged=this::weatherUpdated;
    private void weatherUpdated(){
        if(preview!=null)preview.refreshWeather();
        if(showing()&&page.equals("weather")&&status!=null)status.setText(WeatherStore.get(a).status());
    }
    HomeSettings(FrameHomeActivity activity){a=activity;}
    boolean showing(){return dialog!=null&&dialog.isShowing();}
    String page(){return showing()?page:"closed";}
    void time(String time){if(preview!=null)preview.clock.setText(time);}
    void show(){
        if(showing())return;
        page="overview";undo.clear();
        dialog=new Dialog(a){@Override public void onBackPressed(){back();}};
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setOnDismissListener(d->{WeatherStore.get(a).unlisten(weatherChanged);a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();undo.clear();a.updateRotation();a.surface.focusItem(HomeItems.SETTINGS);});
        UiMotion.window(dialog.getWindow());
        dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setLayout(-1,-1);
        dialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        stage=new FrameLayout(a);stage.setBackgroundColor(0xFF121619);
        backdrop=new ImageView(a);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);stage.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(a);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xC2080C0F,0x7D080C0F}));stage.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        dialog.setContentView(stage);render();WeatherStore.get(a).listen(weatherChanged);a.updateRotation();
    }
    void close(){if(dialog!=null)dialog.dismiss();}
    private void back(){
        a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();
        if(page.equals("overview")){close();return;}
        go(page.equals("wallpaper")||page.equals("rotation")?"background":page.startsWith("item:")||page.equals("add")?"home":"overview");
    }
    private void go(String next){a.wallpapers.cancelPreview();a.wallpapers.cancelSelection();page=next;render();}
    private String title(){
        switch(page){case "overview":return "设置";case "language":return "语言";case "about":return "关于";case "background":return "背景";case "wallpaper":return "壁纸";case "rotation":return "轮播";
            case "layout":return "布局";case "type":return "字体";case "focus":return "选中样式";case "home":return "首页内容";case "add":return "首页内容 / 添加";case "tv":return "电视与启动";case "clock":return "时钟屏保";case "weather":return "天气";case "motion":return "动效与沉浸";
            default:return "首页内容 / "+a.items.name(page.substring(5));}
    }
    private void render(){
        if(dialog!=null)UiMotion.window(dialog.getWindow());
        rendering=true;choices.clear();
        boolean overview=page.equals("overview");
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(a.dp(70),a.dp(overview?42:24),a.dp(70),a.dp(30));
        if(!overview){
            TextView back=label("‹  "+(page.equals("wallpaper")||page.equals("rotation")?"背景":"设置"),14);
            back.setTextColor(0xBBFFFFFF);back.setFocusable(true);back.setOnClickListener(v->back());
            back.setPadding(a.dp(8),0,a.dp(10),0);
            back.setOnFocusChangeListener((v,f)->decorate(v,f));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,a.dp(24));bp.leftMargin=-a.dp(8);panel.addView(back,bp);
        }
        TextView title=label(title(),28);title.setPadding(0,a.dp(6),0,0);
        panel.addView(title,new LinearLayout.LayoutParams(-1,a.dp(overview?64:68)));
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        options=new LinearLayout(a);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(0,0,0,a.dp(12));
        scroll.addView(options,new ScrollView.LayoutParams(-1,-2));
        body.addView(scroll,new LinearLayout.LayoutParams(overview?-1:a.dp(320),-1));
        overviewGrid=null;if(overview){overviewGrid=new GridLayout(a);overviewGrid.setColumnCount(2);overviewGrid.setUseDefaultMargins(false);options.addView(overviewGrid,new LinearLayout.LayoutParams(-1,-2));}
        LinearLayout right=new LinearLayout(a);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1);rp.leftMargin=a.dp(38);body.addView(right,rp);
        previewHost=new FrameLayout(a){@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);scalePreview(w,h);}};
        previewHost.setBackground(WallpaperMaterial.rounded(0x40000000,a.dp(10)));previewHost.setClipToOutline(true);
        right.addView(previewHost,new LinearLayout.LayoutParams(-1,a.dp(260)));
        status=label("首页预览",14);status.setTextColor(0xBFFFFFFF);status.setPadding(0,a.dp(16),0,0);
        right.addView(status,new LinearLayout.LayoutParams(-1,a.dp(65)));
        if(overview)right.setVisibility(View.GONE);

        switch(page){
            case "overview":
                entry("背景", "壁纸、轮播与纯色",()->go("background"));entry("布局","竖向滚轮、列表与 Dock",()->go("layout"));
                entry("字体","字形与字号",()->go("type"));entry("选中样式","文字高亮与毛玻璃",()->go("focus"));
                entry("首页内容","添加、排序、隐藏",()->go("home"));entry("电视与启动","自动进入与小米系统",()->go("tv"));
                entry("时钟屏保","风景照片与安静的时钟",()->go("clock"));entry("天气","首页与屏保",()->go("weather"));entry("动效与沉浸","",()->go("motion"));entry("语言","",()->go("language"));entry("关于","",()->go("about"));break;
            case "language":
                option("English",!Language.chinese(a),()->changeLanguage("en"));
                option("简体中文",Language.chinese(a),()->changeLanguage("zh"));break;
            case "about":
                heading("Luma TV · 0.6.0-public");
                note(Language.pick(a,"Weather: MET Norway · CC BY 4.0\nhttps://api.met.no/\nhttps://creativecommons.org/licenses/by/4.0/\nForecast values rounded and grouped for display.","天气：MET Norway · CC BY 4.0\nhttps://api.met.no/\nhttps://creativecommons.org/licenses/by/4.0/\n预测值经过取整和归组显示。"));
                note(Language.pick(a,"City search: GeoNames via Open-Meteo · CC BY 4.0\nhttps://open-meteo.com/\nBodoni Moda · SIL Open Font License","城市搜索：GeoNames / Open-Meteo · CC BY 4.0\nhttps://open-meteo.com/\n字体：Bodoni Moda · SIL Open Font License"));break;
            case "weather":
                WeatherStore weather=WeatherStore.get(a);
                entry("城市",weather.place()==null?Language.text(a,"选择城市"):Language.place(a,weather.place().name),this::chooseWeatherCity);
                option("主页天气",weather.enabled(false),()->{weather.enabled(false,!weather.enabled(false));a.surface.refreshWeather();render();});
                option("屏保天气",weather.enabled(true),()->{weather.enabled(true,!weather.enabled(true));render();});
                entry("刷新天气","",()->{weather.request(true);status.setText(weather.status());});break;
            case "background":
                row("背景","background",new String[]{"照片","浅色","深色"});
                if(a.appearance.get("background")==0){entry("壁纸",a.wallpapers.name(a.wallpapers.selected()),()->go("wallpaper"));entry("轮播",a.wallpapers.rotating()?Language.pick(a,"Every "+a.wallpapers.minutes()+" min","每 "+a.wallpapers.minutes()+" 分钟"):"固定壁纸",()->go("rotation"));}break;
            case "layout":
                row("首页布局","layout",new String[]{"纵向列表","横向 Dock","竖向滚轮"});row("应用显示","apps",new String[]{"文字","图标","图标＋文字"});break;
            case "type":
                row("字体风格","font",new String[]{"轻盈","标准","衬线"});row("首页字号","size",new String[]{"小","标准","大"});break;
            case "focus":
                row("焦点表现","focus",new String[]{"文字高亮","毛玻璃"});
                if(a.appearance.get("focus")==1)row("底板质感","material",new String[]{"柔和","浓雾","清透"});
                row("未选中项淡化","dim",new String[]{"轻","标准","明显"});break;
            case "rotation":
                option("固定壁纸",!a.wallpapers.rotating(),()->{if(a.wallpapers.rotation(false,a.wallpapers.minutes()))render();else failed();});
                for(int m:WallpaperCycle.MINUTES){final int value=m;option(Language.pick(a,"Every "+m+" min","每 "+m+" 分钟"),a.wallpapers.rotating()&&a.wallpapers.minutes()==m,()->{
                    if(a.wallpapers.rotation(true,value))render();else failed();});}break;
            case "wallpaper":gallery();break;
            case "home":
                for(int i=0;i<a.items.order.size();i++){String id=a.items.order.get(i);entry((i+1)+"  "+a.items.name(id),HomeItems.SOURCES.equals(id)?"全部信号源":id.startsWith("hdmi:")?"HDMI "+id.substring(5):"应用",()->go("item:"+id));}
                entry("添加入口","应用或 HDMI 信号源",()->go("add"));if(!undo.isEmpty())entry("撤销上一步","恢复本次编辑前的顺序",this::undo);break;
            case "add":
                a.items.discover();for(String id:a.items.names.keySet())if(!a.items.order.contains(id))entry(a.items.name(id),"添加到首页末尾",()->{
                    if(a.items.order.size()>=HomeItems.MAX_ITEMS){a.message("首页最多保留 11 个入口");return;}List<String> next=new ArrayList<>(a.items.order);next.add(id);if(save(next))go("home");});break;
            case "tv":
                entry("开机自动进入",BootStart.enabled(a)?"已开启":"已关闭",()->BootStart.show(a,this::render));entry("电视系统设置","网络、声音、画面等",a::systemSettings);entry("小米桌面","打开原来的电视首页",a::stockHome);break;
            case "clock":
                option("自动时钟屏保",HomeClockIdle.enabled(a),()->{HomeClockIdle.enabled(a,!HomeClockIdle.enabled(a));a.updateRotation();render();});
                if(ScenicPhotos.available(a))option("风景照片",ScenicPhotos.clockEnabled(a),()->{ScenicPhotos.clockEnabled(a,!ScenicPhotos.clockEnabled(a));render();});
                entry("立即预览","按遥控器方向键返回",()->{close();ClockActivity.open(a,"preview");});break;
            case "motion":
                row("过渡动效","motion",new String[]{"流畅动效","减弱动效"});
                row("首页沉浸","ambient",new String[]{Language.pick(a,"On","开启"),Language.pick(a,"Off","关闭")});
                row("欢迎画面","welcome",new String[]{Language.pick(a,"On","开启"),Language.pick(a,"Off","关闭")});
                entry("欢迎画面预览",HomeWelcome.TEXT,()->{close();a.previewWelcome();});break;
            default:
                String id=page.substring(5);int pos=a.items.order.indexOf(id);
                if(pos>0)entry("上移","与上一项交换位置",()->move(id,-1));if(pos>=0&&pos<a.items.order.size()-1)entry("下移","与下一项交换位置",()->move(id,1));
                if(!HomeItems.SETTINGS.equals(id)&&a.items.order.size()>1)entry("从首页隐藏","应用仍保留在电视上",()->{List<String> next=new ArrayList<>(a.items.order);next.remove(id);if(save(next))go("home");});
                break;
        }
        if(content!=null){content.animate().cancel();stage.removeView(content);}content=panel;stage.addView(panel,new FrameLayout.LayoutParams(-1,-1));UiMotion.enter(panel,0);
        if(overview)preview=null;else refreshPreview();background(a.wallpaperDrawable());rendering=false;
        if(page.equals("weather"))status.setText(WeatherStore.get(a).status());
        if(!choices.isEmpty())choices.get(Math.min(remembered.getOrDefault(page,0),choices.size()-1)).requestFocus();
    }
    private void chooseWeatherCity(){
        EditText input=new EditText(a);input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setTextSize(20);
        input.setHint(Language.text(a,"城市 / City"));input.setHintTextColor(0x88FFFFFF);input.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12));
        android.app.AlertDialog search=new android.app.AlertDialog.Builder(a).setTitle(Language.text(a,"天气城市")).setView(input)
                .setPositiveButton(Language.text(a,"搜索"),null).setNegativeButton(Language.text(a,"取消"),null).create();
        search.setOnShowListener(d->search.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String query=input.getText().toString().trim();if(query.isEmpty())return;
            search.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            WeatherStore.get(a).search(query,places->{
                if(!showing()||!search.isShowing())return;
                search.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                if(places.isEmpty()){input.setError(Language.text(a,"未找到城市，可试试拼音或英文名"));return;}
                search.dismiss();String[] names=new String[places.size()];for(int i=0;i<names.length;i++)names[i]=places.get(i).name+" · "+places.get(i).region;
                new android.app.AlertDialog.Builder(a).setTitle(Language.text(a,"选择城市")).setItems(names,(dialog,index)->{WeatherStore.get(a).select(places.get(index));render();}).setNegativeButton(Language.text(a,"取消"),null).show();
            },error->{if(search.isShowing()){search.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);input.setError(error);}});
        }));search.show();
    }
    private void gallery(){
        for(int i=0;i<a.wallpapers.count();i++){
            final int index=i;
            TextView v=button(a.wallpapers.name(i),()->{
                if(index==a.wallpapers.selected()){status.setText(Language.text(a,"已应用这张壁纸"));return;}
                status.setText(Language.text(a,"正在应用壁纸…"));String origin=page;
                a.wallpapers.select(index,()->{if(showing()&&page.equals(origin)){render();status.setText(Language.text(a,"已应用 · ")+a.wallpapers.name(index));}});
            });
            android.graphics.drawable.Drawable thumb=a.wallpapers.thumbnail(i);
            if(thumb!=null){thumb=roundedThumb(thumb);thumb.setBounds(0,0,a.dp(48),a.dp(27));v.setCompoundDrawables(thumb,null,checkmark(i==a.wallpapers.selected()),null);v.setCompoundDrawablePadding(a.dp(14));}
            v.setOnFocusChangeListener((view,focus)->{
                decorate(view,focus);if(!focus)return;remembered.put(page,choices.indexOf(view));
                a.wallpapers.cancelSelection();
                status.setText(a.wallpapers.name(index));
                if(preview!=null){HomeSurface target=preview;a.wallpapers.preview(index,target.previewPhoto,()->{
                    if(preview!=target||!showing())return;
                    target.wallpaper(target.previewPhoto.getDrawable());background(target.previewPhoto.getDrawable());
                });}
            });
            options.addView(v,new LinearLayout.LayoutParams(-1,a.dp(46)));
        }
    }
    private void row(String title,String key,String[] labels){
        heading(title);
        for(int i=0;i<labels.length;i++){
            final int value=i;
            TextView v=button(labels[i],()->{
                if(a.appearance.get(key)==value)return;
                if(a.appearance.set(key,value)){a.refreshHome();render();}else failed();
            });
            v.setCompoundDrawables(null,null,checkmark(a.appearance.get(key)==i),null);
            options.addView(v,new LinearLayout.LayoutParams(-1,a.dp(42)));
        }
    }
    private void option(String name,boolean selected,Runnable run){
        TextView v=button(name,run);v.setCompoundDrawables(null,null,checkmark(selected),null);
        options.addView(v,new LinearLayout.LayoutParams(-1,a.dp(44)));
    }
    private Drawable checkmark(boolean selected){
        if(!selected)return null;
        Drawable mark=a.getDrawable(R.drawable.mark_check).mutate();mark.setTint(0xDFFFFFFF);mark.setBounds(0,0,a.dp(16),a.dp(16));return mark;
    }
    private void heading(String s){TextView v=label(s,14);v.setTextColor(0xADFFFFFF);v.setPadding(a.dp(12),a.dp(12),0,a.dp(7));options.addView(v);}
    private void note(String s){TextView v=label(s,13);v.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);v.setLinkTextColor(0xFF8DBEFF);v.setTextColor(0xA6FFFFFF);v.setLineSpacing(a.dp(3),1);v.setPadding(a.dp(12),a.dp(14),a.dp(8),a.dp(12));options.addView(v);}
    private void changeLanguage(String code){
        if(Language.code(a).equals(code))return;
        Language.change(a,code);a.items=new HomeItems(a);a.refreshHome();render();
    }
    private void entry(String name,String subtitle,Runnable run){
        if(!page.equals("weather")&&!page.equals("background")&&!(page.equals("tv")&&name.equals("开机自动进入")))subtitle="";
        name=Language.text(a,name);subtitle=Language.text(a,subtitle);
        TextView v=button(name+(subtitle.isEmpty()?"":"\n"+subtitle),run);
        if(!subtitle.isEmpty()){
            SpannableString text=new SpannableString(v.getText());
            text.setSpan(new AbsoluteSizeSpan(13,true),name.length()+1,text.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            v.setText(text);v.setLineSpacing(a.dp(3),1);
        }
        v.setTag(Boolean.TRUE);
        Drawable arrow=a.getDrawable(R.drawable.mark_caret_right).mutate();arrow.setTint(0xAAFFFFFF);arrow.setBounds(0,0,a.dp(14),a.dp(14));v.setCompoundDrawables(null,null,arrow,null);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,a.dp(subtitle.isEmpty()?44:56));lp.bottomMargin=a.dp(4);if(overviewGrid!=null){int n=overviewGrid.getChildCount();GridLayout.LayoutParams gp=new GridLayout.LayoutParams(GridLayout.spec(n/2),GridLayout.spec(n%2,1f));gp.width=0;gp.height=a.dp(49);gp.rightMargin=a.dp(n%2==0?20:0);gp.bottomMargin=a.dp(8);overviewGrid.addView(v,gp);}else options.addView(v,lp);decorate(v,false);
    }
    private TextView button(String text,Runnable run){
        TextView v=label(text,19);v.setPadding(a.dp(14),a.dp(5),a.dp(16),a.dp(5));v.setGravity(Gravity.CENTER_VERTICAL);v.setId(View.generateViewId());v.setFocusable(true);v.setFocusableInTouchMode(true);v.setClickable(true);choices.add(v);
        v.setOnFocusChangeListener((view,focus)->{decorate(view,focus);if(focus&&!rendering)remembered.put(page,choices.indexOf(view));});
        v.setOnClickListener(view->run.run());v.setOnKeyListener((view,key,e)->(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)&&e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()>0);return v;
    }
    private void decorate(View view,boolean focus){
        TextView v=(TextView)view;
        v.setBackground(WallpaperMaterial.rounded(focus?0xF5F5F4EF:0x00000000,a.dp(10)));
        v.setTextColor(focus?0xFF202321:0xEBFFFFFF);
        String value=v.getText().toString();int line=value.indexOf('\n');
        if(line>=0){SpannableString text=new SpannableString(value);
            text.setSpan(new AbsoluteSizeSpan(13,true),line+1,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(focus?0xA6202321:0xA6FFFFFF),line+1,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);v.setText(text);}

        Drawable arrow=v.getCompoundDrawables()[2];if(arrow!=null)arrow.setTint(focus?0x99202321:0xAAFFFFFF);
    }
    private TextView label(String s,int size){TextView v=new TextView(a);v.setText(Language.text(a,s));v.setTextSize(size);v.setTextColor(0xF2FFFFFF);v.setTypeface(android.os.Build.VERSION.SDK_INT>=28?Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),300,false):Typeface.create("sans-serif-light",Typeface.NORMAL));v.setIncludeFontPadding(false);return v;}
    private void background(Drawable source){
        int request=++backdropRequest;
        WallpaperMaterial.request(source,42,image->{if(request==backdropRequest&&showing()&&image!=null)backdrop.setImageBitmap(image);});
    }
    private Drawable roundedThumb(Drawable source){
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(a.dp(48),a.dp(27),android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c=new android.graphics.Canvas(image);android.graphics.Path path=new android.graphics.Path();
        path.addRoundRect(0,0,image.getWidth(),image.getHeight(),a.dp(4),a.dp(4),android.graphics.Path.Direction.CW);c.clipPath(path);
        source.setBounds(0,0,image.getWidth(),image.getHeight());source.draw(c);return new android.graphics.drawable.BitmapDrawable(a.getResources(),image);
    }
    private void refreshPreview(){
        preview=a.preview();previewHost.removeAllViews();previewHost.addView(preview,new FrameLayout.LayoutParams(a.dp(960),a.dp(540)));
        preview.setPivotX(0);preview.setPivotY(0);scalePreview(previewHost.getWidth(),previewHost.getHeight());
        preview.clock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date()));
    }
    private void scalePreview(int w,int h){if(preview!=null){float scale=Math.min(w/(float)a.dp(960),h/(float)a.dp(540));preview.setScaleX(scale);preview.setScaleY(scale);}}
    private boolean save(List<String> next){List<String> old=new ArrayList<>(a.items.order);if(!a.items.save(next)){failed();return false;}undo.push(old);a.refreshHome();return true;}
    private void move(String id,int direction){List<String> next=new ArrayList<>(a.items.order);int index=next.indexOf(id);Collections.swap(next,index,index+direction);if(save(next))render();}
    private void undo(){if(undo.isEmpty())return;if(a.items.save(undo.peek())){undo.pop();a.refreshHome();render();}else failed();}
    private void failed(){a.message("保存失败，保留原设置，请重试");}
}

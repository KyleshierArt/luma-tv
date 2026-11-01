package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One direct HTTPS request shared by home and screensaver; no background service or location permission. */
final class WeatherStore {
    interface Listener {void changed();}
    interface Fetcher {Response get(String address)throws Exception;}
    static final class Response {
        final JSONObject body;final long expires;
        Response(JSONObject body,long expires){this.body=body;this.expires=expires;}
    }
    private static final class HttpFailure extends IOException {
        final int status;final long retryDelay;
        HttpFailure(int status,long delay){this.status=status;retryDelay=delay;}
    }
    static final class Place {
        final String name,region;final double latitude,longitude;
        Place(String name,String region,double lat,double lon){this.name=name;this.region=region;latitude=lat;longitude=lon;}
        String key(){return latitude+","+longitude;}
        JSONObject json()throws JSONException{return new JSONObject().put("name",name).put("region",region).put("latitude",latitude).put("longitude",longitude);}
        static Place parse(JSONObject o)throws JSONException{
            String name=o.getString("name").trim();double lat=o.getDouble("latitude"),lon=o.getDouble("longitude");
            if(name.isEmpty()||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("Invalid place");
            return new Place(name,o.optString("region",""),lat,lon);
        }
    }
    static final class Snapshot {
        final Place place;final double temperature,high,low;final int code;final boolean day;final long fetched;
        Snapshot(Place p,double t,double hi,double lo,int code,boolean day,long fetched){place=p;temperature=t;high=hi;low=lo;this.code=code;this.day=day;this.fetched=fetched;}
        String degrees(){return Math.round(temperature)+"°";}
        String condition(){return WeatherRules.condition(code,day);}
        String range(){return "H:"+Math.round(high)+"°  L:"+Math.round(low)+"°";}
        String condition(Context c){return Language.text(c,condition());}
        String range(Context c){return Language.pick(c,"24h H:"+Math.round(high)+"° L:"+Math.round(low)+"°","24h 高 "+Math.round(high)+"° 低 "+Math.round(low)+"°");}
        String city(Context c){return Language.place(c,place.name);}
        String summary(Context c){return city(c)+", "+condition(c)+", "+Math.round(temperature)+Language.pick(c," degrees, ","摄氏度，")+range(c);}
        boolean stale(){return !WeatherRules.fresh(System.currentTimeMillis(),fetched);}
        JSONObject json()throws JSONException{return new JSONObject().put("provider","met.no").put("place",place.json()).put("temperature",temperature).put("high",high).put("low",low).put("code",code).put("day",day).put("fetched",fetched);}
        static Snapshot parse(JSONObject o)throws JSONException{
            if(!"met.no".equals(o.optString("provider")))throw new JSONException("Weather provider changed");
            double t=o.getDouble("temperature"),hi=o.getDouble("high"),lo=o.getDouble("low");
            if(!Double.isFinite(t)||!Double.isFinite(hi)||!Double.isFinite(lo)||t< -90||t>65||lo>hi||lo< -90||hi>65)throw new JSONException("Invalid weather");
            return new Snapshot(Place.parse(o.getJSONObject("place")),t,hi,lo,o.getInt("code"),o.getBoolean("day"),o.getLong("fetched"));
        }
    }
    private static WeatherStore instance;
    static synchronized WeatherStore get(Context c){if(instance==null)instance=new WeatherStore(c.getApplicationContext());return instance;}
    private final Context context;
    private final SharedPreferences prefs;
    private long providerExpires;
    private final Fetcher fetcher;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Set<Listener> listeners=new HashSet<>();
    private Place place;private Snapshot cached;private boolean loading;private long retryAt,serverRetryAt,nextCheckAt;private int generation,failures,attempts;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Network activeNetwork;
    private boolean validated;
    private String error="";
    private final Runnable refresh=()->{notifyListeners();request(false);};
    private final Runnable networkRefresh=()->{retryAt=0;request(false);};
    private WeatherStore(Context c){this(c,null);}
    /** Package-private dependency seam for isolated instrumentation; never selected by UI or intents. */
    WeatherStore(Context c,Fetcher transport){
        context=c;prefs=c.getSharedPreferences("weather",0);
        fetcher=transport==null?WeatherStore::getJsonResponse:transport;
        try{String saved=prefs.getString("place","");
            place=Place.parse(new JSONObject(saved));
        }catch(Exception unavailable){place=null;}
        providerExpires=prefs.getLong("provider-expiry",0);
        try{cached=Snapshot.parse(new JSONObject(prefs.getString("cache","")));if(place==null||!place.key().equals(cached.place.key())||!place.name.equals(cached.place.name))cached=null;}catch(Exception absent){cached=null;}
    }
    Place place(){return place;}
    String status(){Snapshot s=snapshot();return loading?Language.text(context,"正在更新天气"):s==null?Language.text(context,"天气暂不可用"):s.condition(context)+"  "+s.degrees()+" · "+s.range(context);}
    boolean enabled(boolean screen){return prefs.getBoolean(screen?"screen":"home",true);}
    void enabled(boolean screen,boolean value){prefs.edit().putBoolean(screen?"screen":"home",value).apply();watchNetwork();notifyListeners();request(false);}
    Snapshot snapshot(){return cached!=null&&WeatherRules.visible(System.currentTimeMillis(),cached.fetched)?cached:null;}
    void listen(Listener l){listeners.add(l);watchNetwork();l.changed();request(false);}
    void unlisten(Listener l){listeners.remove(l);watchNetwork();if(listeners.isEmpty()){main.removeCallbacks(refresh);nextCheckAt=0;}}
    void foreground(){notifyListeners();request(false);}
    private void watchNetwork(){
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)return;
        boolean active=!listeners.isEmpty()&&(enabled(false)||enabled(true));
        if(!active){
            main.removeCallbacks(networkRefresh);main.removeCallbacks(refresh);nextCheckAt=0;
            if(networkCallback!=null){try{manager.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}networkCallback=null;}
            activeNetwork=null;validated=false;return;
        }
        if(networkCallback!=null)return;
        networkCallback=new ConnectivityManager.NetworkCallback(){
            private void restored(){if(networkCallback==this){main.removeCallbacks(networkRefresh);main.postDelayed(networkRefresh,750);}}
            @Override public void onAvailable(Network network){if(networkCallback!=this)return;activeNetwork=network;validated=false;restored();}
            @Override public void onCapabilitiesChanged(Network network,NetworkCapabilities capabilities){
                if(networkCallback!=this||!network.equals(activeNetwork))return;
                boolean ready=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if(ready&&!validated)restored();validated=ready;
            }
            @Override public void onLost(Network network){if(networkCallback==this&&network.equals(activeNetwork)){activeNetwork=null;validated=false;}}
        };
        try{manager.registerDefaultNetworkCallback(networkCallback,main);}catch(RuntimeException unavailable){networkCallback=null;}
    }
    private void notifyListeners(){for(Listener l:new ArrayList<>(listeners))l.changed();}
    void select(Place p){
        try{prefs.edit().putString("place",p.json().toString()).remove("cache").remove("provider-expiry").apply();}catch(JSONException impossible){return;}
        place=p;cached=null;providerExpires=0;generation++;loading=false;retryAt=0;failures=0;error="";notifyListeners();request(true);
    }
    private void schedule(long delay){main.removeCallbacks(refresh);nextCheckAt=0;if(!listeners.isEmpty()&&(enabled(false)||enabled(true))){delay=Math.max(1000,delay);nextCheckAt=SystemClock.elapsedRealtime()+delay;main.postDelayed(refresh,delay);}}
    void request(boolean force){
        if(place==null||listeners.isEmpty()||(!enabled(false)&&!enabled(true)))return;
        if(loading)return;
        long now=System.currentTimeMillis();
        long cacheDelay=cached==null?0:WeatherRules.refreshDelay(now,cached.fetched,providerExpires,force);
        if(cacheDelay>0){schedule(cacheDelay);return;}
        if(SystemClock.elapsedRealtime()<serverRetryAt){schedule(serverRetryAt-SystemClock.elapsedRealtime());return;}
        if(!force&&SystemClock.elapsedRealtime()<retryAt){schedule(retryAt-SystemClock.elapsedRealtime());return;}
        main.removeCallbacks(refresh);nextCheckAt=0;
        loading=true;attempts++;final int request=++generation;final Place location=place;notifyListeners();
        worker.execute(()->{
            Snapshot result=null;String failure="";long expires=0,cooldown=0;
            try{
                Response reply=fetcher.get(String.format(Locale.US,"https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",location.latitude,location.longitude));
                JSONObject response=reply.body;
                JSONArray series=response.getJSONObject("properties").getJSONArray("timeseries");
                java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
                long nowMillis=System.currentTimeMillis();int currentIndex=0;
                for(int i=0;i<series.length();i++){long t=format.parse(series.getJSONObject(i).getString("time")).getTime();if(t<=nowMillis)currentIndex=i;else break;}
                JSONObject current=series.getJSONObject(currentIndex).getJSONObject("data");
                double temperature=current.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");
                double hi=temperature,lo=temperature;long start=format.parse(series.getJSONObject(currentIndex).getString("time")).getTime();int samples=0;
                for(int i=currentIndex;i<series.length();i++){
                    JSONObject entry=series.getJSONObject(i);long t=format.parse(entry.getString("time")).getTime();if(t>=start+86400000L)break;
                    double v=entry.getJSONObject("data").getJSONObject("instant").getJSONObject("details").getDouble("air_temperature");hi=Math.max(hi,v);lo=Math.min(lo,v);samples++;
                }
                if(samples<4)throw new JSONException("Incomplete forecast");
                JSONObject interval=current.optJSONObject("next_1_hours");if(interval==null)interval=current.optJSONObject("next_6_hours");if(interval==null)throw new JSONException("Missing condition");
                String symbol=interval.getJSONObject("summary").getString("symbol_code");
                JSONObject value=new JSONObject().put("provider","met.no").put("place",location.json()).put("temperature",temperature)
                    .put("high",hi).put("low",lo).put("code",WeatherRules.metCode(symbol)).put("day",!symbol.endsWith("_night")).put("fetched",nowMillis);
                result=Snapshot.parse(value);
                expires=reply.expires;
            }catch(Exception unavailable){failure=unavailable instanceof HttpFailure?"HTTP "+((HttpFailure)unavailable).status:unavailable.getClass().getSimpleName();if(unavailable instanceof HttpFailure)cooldown=((HttpFailure)unavailable).retryDelay;}
            final Snapshot weather=result;final String problem=failure;final long expiry=expires,serverDelay=cooldown;
            main.post(()->{
                if(request!=generation)return;loading=false;error=problem;
                long delay;
                if(weather!=null){cached=weather;providerExpires=expiry;try{prefs.edit().putString("cache",weather.json().toString()).putLong("provider-expiry",expiry).apply();}catch(JSONException ignored){}retryAt=0;serverRetryAt=0;failures=0;delay=WeatherRules.refreshDelay(System.currentTimeMillis(),weather.fetched,expiry,false);}
                else{failures++;delay=Math.max(WeatherRules.retryDelay(failures),serverDelay);retryAt=SystemClock.elapsedRealtime()+delay;if(serverDelay>0)serverRetryAt=SystemClock.elapsedRealtime()+serverDelay;}
                notifyListeners();schedule(delay);
            });
        });
    }
    void search(String query,Consumer<List<Place>> success,Consumer<String> failed){
        worker.execute(()->{try{
            JSONObject response=fetcher.get("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query.trim(),"UTF-8")+"&count=8&language="+(Language.chinese(context)?"zh":"en")+"&format=json").body;
            List<Place> results=new ArrayList<>();JSONArray list=response.optJSONArray("results");
            if(list!=null)for(int i=0;i<list.length();i++){
                JSONObject o=list.getJSONObject(i);String region=o.optString("admin1","");String country=o.optString("country","");
                if(!country.isEmpty()&&!country.equals(region))region+=(region.isEmpty()?"":" · ")+country;
                results.add(new Place(o.getString("name"),region,o.getDouble("latitude"),o.getDouble("longitude")));
            }
            main.post(()->success.accept(results));
        }catch(Exception unavailable){main.post(()->failed.accept(Language.text(context,"暂时无法搜索，请稍后重试")));}});
    }
    private static Response getJsonResponse(String address)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setRequestProperty("Accept","application/json");connection.setRequestProperty("User-Agent","LumaTV/0.6.0 PublicEdition");
        try{
            int status=connection.getResponseCode();
            if(status!=200&&status!=203){
                long wait=Math.max(0,connection.getHeaderFieldDate("Retry-After",0)-System.currentTimeMillis());
                try{wait=Math.max(wait,Math.multiplyExact(Long.parseLong(connection.getHeaderField("Retry-After")),1000));}catch(RuntimeException ignored){}
                if(status==429||status==503)wait=Math.max(wait,WeatherRules.RETRY_MAX);
                throw new HttpFailure(status,wait);
            }
            try(InputStream in=connection.getInputStream()){return new Response(new JSONObject(read(in)),connection.getExpiration());}
        }finally{connection.disconnect();}
    }
    private static String read(InputStream in)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=in.read(buffer))!=-1){if(bytes.size()+n>262144)throw new IOException("Weather response too large");bytes.write(buffer,0,n);}return bytes.toString("UTF-8");
    }
    void dump(java.io.PrintWriter out){Snapshot s=snapshot();out.println("Weather: place="+(place==null?"unset":place.name)+" loading="+loading+" available="+(s!=null)+" cached="+(cached!=null)+" stale="+(s!=null&&s.stale())+" home="+enabled(false)+" screen="+enabled(true)+" listeners="+listeners.size()+" attempts="+attempts+" failures="+failures+" networkWatch="+(networkCallback!=null)+" nextCheckSeconds="+Math.max(0,(nextCheckAt-SystemClock.elapsedRealtime())/1000)+" cacheAgeMinutes="+(cached==null?-1:(System.currentTimeMillis()-cached.fetched)/60000)+" error="+error+(s==null?"":" temperature="+s.temperature+" code="+s.code));}
}

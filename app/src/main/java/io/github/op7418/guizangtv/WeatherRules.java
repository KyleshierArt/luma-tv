package io.github.op7418.guizangtv;

/** WMO conditions and cache limits, independent of the Android UI and transport. */
final class WeatherRules {
    static final long REFRESH=30*60*1000L, EXPIRES=6*60*60*1000L;
    static final long RETRY_MAX=5*60*1000L;
    static boolean visible(long now,long fetched){return fetched>0&&now-fetched>=-300000&&now-fetched<EXPIRES;}
    static boolean fresh(long now,long fetched){return visible(now,fetched)&&now-fetched<REFRESH;}
    /** Recompute against wall time after sleep; a paused Handler delay is not freshness. */
    static long refreshDelay(long now,long fetched,long providerExpiry,boolean force){
        if(!visible(now,fetched))return 0;
        long server=providerExpiry>fetched&&providerExpiry-fetched<=EXPIRES?Math.max(0,providerExpiry-now):0;
        return Math.max(server,force?0:Math.max(0,REFRESH-(now-fetched)));
    }
    static long retryDelay(int failures){return Math.min(RETRY_MAX,15000L<<Math.min(5,Math.max(0,failures-1)));}
    static String condition(int code,boolean day){
        switch(code){
            case 0:return day?"晴":"晴夜";
            case 1:return "少云";case 2:return "多云";case 3:return "阴";
            case 45:case 48:return "雾";
            case 51:return "小毛毛雨";case 53:case 55:return "毛毛雨";
            case 56:case 57:case 66:case 67:return "冻雨";
            case 68:return "雨夹雪";
            case 61:return "小雨";case 63:return "中雨";case 65:return "大雨";
            case 71:return "小雪";case 73:return "中雪";case 75:return "大雪";case 77:return "米雪";
            case 80:case 81:case 82:return "阵雨";case 85:case 86:return "阵雪";
            case 95:return "雷雨";case 96:case 99:return "雷雨冰雹";
            default:return "天气";
        }
    }
    static int icon(int code){
        if(code==0)return 0;
        if(code==1||code==2)return 1;
        if(code==45||code==48)return 3;
        if(code>=95)return 6;
        if((code>=71&&code<=77)||code==85||code==86)return 5;
        if(code>=51&&code<=82)return 4;
        return 2;
    }
    static int metCode(String symbol){
        if(symbol.contains("thunder"))return 95;
        if(symbol.startsWith("clearsky"))return 0;
        if(symbol.startsWith("fair"))return 1;
        if(symbol.startsWith("partlycloudy"))return 2;
        if(symbol.startsWith("fog"))return 45;
        if(symbol.contains("sleet"))return 68;
        if(symbol.contains("snow"))return symbol.contains("showers")?85:symbol.startsWith("heavy")?75:symbol.startsWith("light")?71:73;
        if(symbol.contains("rain"))return symbol.contains("showers")?80:symbol.startsWith("heavy")?65:symbol.startsWith("light")?61:63;
        return symbol.startsWith("cloudy")?3:-1;
    }
    private WeatherRules(){}
}

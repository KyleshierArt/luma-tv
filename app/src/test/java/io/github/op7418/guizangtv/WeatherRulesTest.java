package io.github.op7418.guizangtv;

import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherRulesTest {
    @Test public void wakingAfterOvernightSleepMustRefreshImmediately(){
        long fetched=100000000,now=fetched+9*60*60*1000L;
        assertEquals(0,WeatherRules.refreshDelay(now,fetched,fetched+WeatherRules.REFRESH,false));
    }
    @Test public void returningWithFreshCacheDoesNotRefetch(){
        long fetched=100000000;
        assertEquals(WeatherRules.REFRESH-1000,WeatherRules.refreshDelay(fetched+1000,fetched,0,false));
    }
    @Test public void providerExpiryIsRespectedEvenByManualRefresh(){
        long fetched=100000000,expiry=fetched+2*WeatherRules.REFRESH;
        assertEquals(expiry-fetched-1000,WeatherRules.refreshDelay(fetched+1000,fetched,expiry,true));
    }
    @Test public void expiryWithoutUsableCacheCannotHideWeatherIndefinitely(){
        long fetched=100000000,now=fetched+WeatherRules.EXPIRES;
        assertEquals(0,WeatherRules.refreshDelay(now,fetched,now+60000,false));
        assertEquals(0,WeatherRules.refreshDelay(now,0,now+60000,false));
    }
    @Test public void invalidClockOrExpiryFallsBackToLocalCacheRules(){
        long fetched=100000000;
        assertEquals(0,WeatherRules.refreshDelay(fetched-600000,fetched,fetched+WeatherRules.REFRESH,false));
        assertEquals(0,WeatherRules.refreshDelay(fetched+WeatherRules.REFRESH,fetched,Long.MAX_VALUE,false));
    }
    @Test public void earlyNetworkFailuresRetryQuicklyWithoutTightLoop(){
        assertEquals(15000,WeatherRules.retryDelay(1));
        assertEquals(30000,WeatherRules.retryDelay(2));
        assertEquals(60000,WeatherRules.retryDelay(3));
        assertEquals(WeatherRules.RETRY_MAX,WeatherRules.retryDelay(6));
        assertEquals(WeatherRules.RETRY_MAX,WeatherRules.retryDelay(100));
    }
    @Test public void metSymbolsPreserveSeverityAndPrecipitationType(){
        assertEquals(65,WeatherRules.metCode("heavyrain"));
        assertEquals(61,WeatherRules.metCode("lightrain"));
        assertEquals(68,WeatherRules.metCode("lightsleetshowers_night"));
        assertEquals(85,WeatherRules.metCode("heavysnowshowers_day"));
        assertEquals(95,WeatherRules.metCode("rainshowersandthunder_day"));
        assertEquals(0,WeatherRules.metCode("clearsky_night"));
        assertEquals(3,WeatherRules.metCode("cloudy"));
        assertEquals(-1,WeatherRules.metCode("unknown"));
    }
    @Test public void cacheRefreshAndExpiryAreDistinct(){
        long saved=100000000;
        assertTrue(WeatherRules.fresh(saved+WeatherRules.REFRESH-1,saved));
        assertFalse(WeatherRules.fresh(saved+WeatherRules.REFRESH,saved));
        assertTrue(WeatherRules.visible(saved+WeatherRules.EXPIRES-1,saved));
        assertFalse(WeatherRules.visible(saved+WeatherRules.EXPIRES,saved));
    }
    @Test public void missingAndFarFutureCacheIsNotShown(){
        assertFalse(WeatherRules.visible(10000000,0));
        assertFalse(WeatherRules.visible(10000000,11000000));
        assertTrue(WeatherRules.visible(10000000,10000001));
    }
    @Test public void clearNightAndFreezingRainHaveCorrectConditions(){
        assertEquals("晴夜",WeatherRules.condition(0,false));
        assertEquals("晴",WeatherRules.condition(0,true));
        assertEquals("冻雨",WeatherRules.condition(67,true));
        assertEquals("雷雨冰雹",WeatherRules.condition(99,true));
    }
    @Test public void snowShowersAndThunderDoNotUseRainOrClearSymbols(){
        assertEquals(5,WeatherRules.icon(85));assertEquals(5,WeatherRules.icon(86));
        assertEquals(6,WeatherRules.icon(95));assertEquals(6,WeatherRules.icon(99));
        assertEquals(4,WeatherRules.icon(80));assertEquals(3,WeatherRules.icon(48));
        assertEquals(2,WeatherRules.icon(-1));
    }
}

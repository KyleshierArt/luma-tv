package io.github.op7418.luma;
import org.junit.Test;
import static org.junit.Assert.*;

public class LanguageCatalogTest {
 @Test public void everyCatalogEntryResolvesWithoutChineseFallback(){
  for(String source:LanguageCatalog.EN.keySet()){
   String translated=LanguageCatalog.english(source);
   assertFalse(source,translated.matches(".*[\\u4e00-\\u9fff].*"));
  }
 }
 @Test public void longerPhrasesResolveBeforeFragments(){
  assertEquals("Home Menu / TV Settings",LanguageCatalog.english("首页内容 / 电视设置"));
  assertEquals("Heavy Rain",LanguageCatalog.english("大雨"));
 }
}

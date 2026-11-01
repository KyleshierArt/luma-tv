#!/usr/bin/env python3
"""Generate the standalone TV language catalog from the reviewed JSON source."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]
catalog=json.loads((ROOT/'translations.json').read_text())
lines=['package io.github.op7418.luma;','import java.util.*;',
       '/** Generated from translations.json. */','final class LanguageCatalog {',
       ' static final Map<String,String> EN=new LinkedHashMap<>();',' static {']
for key,value in sorted(catalog.items(),key=lambda row:-len(row[0])):
    lines.append('  EN.put('+json.dumps(key,ensure_ascii=False)+','+json.dumps(value,ensure_ascii=False)+');')
lines += [' }',' static String english(String value){',
          '  String exact=EN.get(value);if(exact!=null)return exact;',
          '  boolean chinese=false;for(int i=0;i<value.length();i++)if(value.charAt(i)>=0x4e00&&value.charAt(i)<=0x9fff){chinese=true;break;}if(!chinese)return value;',
          '  String result=value;for(Map.Entry<String,String> e:EN.entrySet())result=result.replace(e.getKey(),e.getValue());',
          '  return result;',' }','}']
target=ROOT/'app/src/main/java/io/github/op7418/luma/LanguageCatalog.java'
target.write_text('\n'.join(lines)+'\n')

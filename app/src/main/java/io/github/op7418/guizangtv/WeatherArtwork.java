package io.github.op7418.guizangtv;

import io.github.op7418.luma.Language;

import android.graphics.*;

/** Small filled weather symbols and a shared, quiet lock-screen line. */
final class WeatherArtwork {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    WeatherArtwork(){text.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));}
    void icon(Canvas canvas,float x,float y,float size,int code,boolean day,boolean muted){
        int kind=WeatherRules.icon(code);canvas.save();canvas.translate(x,y);canvas.scale(size/64,size/64);
        p.setShader(null);p.setStyle(Paint.Style.FILL);
        if(kind==0||kind==1){
            if(day){
                float cx=kind==1?23:32,cy=kind==1?22:30;
                p.setColor(muted?0xFF949DA1:0xFFFFCC68);p.setStrokeWidth(3);p.setStrokeCap(Paint.Cap.ROUND);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(cx+(float)Math.cos(a)*20,cy+(float)Math.sin(a)*20,cx+(float)Math.cos(a)*24,cy+(float)Math.sin(a)*24,p);}
                p.setShader(new LinearGradient(0,8,0,48,muted?new int[]{0xFFA1AAAF,0xFF747E86}:new int[]{0xFFFFE19C,0xFFFFAE47},null,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,14,p);p.setShader(null);
            }else{
                path.reset();path.moveTo(41,9);path.cubicTo(11,5,7,48,34,51);path.cubicTo(47,53,55,42,54,33);path.cubicTo(35,43,25,21,41,9);path.close();p.setColor(muted?0xFF969FA8:0xFFD5E8FF);canvas.drawPath(path,p);
                p.setColor(muted?0xFF727D88:0xFFEEF6FF);canvas.drawCircle(50,13,2,p);canvas.drawCircle(58,22,1.5f,p);
            }
        }
        if(kind!=0){
            p.setShader(new LinearGradient(0,20,0,49,muted?new int[]{0xFF9CA5AA,0xFF737F87}:new int[]{0xFFF6FBFF,0xFFBDD0DF},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(20,36,10,p);canvas.drawCircle(31,29,13,p);canvas.drawCircle(44,35,11,p);canvas.drawRoundRect(17,34,52,46,6,6,p);p.setShader(null);
            if(kind==3){p.setColor(muted?0xFF75818B:0xFFC9DAE4);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(3);canvas.drawLine(13,51,50,51,p);canvas.drawLine(20,58,44,58,p);}
            if(kind==4){p.setColor(muted?0xFF7E8D98:0xFF79BDFF);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(3);for(int i=0;i<3;i++)canvas.drawLine(23+i*10,51,20+i*10,57,p);}
            if(kind==5){p.setColor(muted?0xFF8999A3:0xFFD7EDFF);p.setStrokeWidth(2);for(int i=0;i<3;i++){float sx=21+i*12;canvas.drawLine(sx-3,54,sx+3,54,p);canvas.drawLine(sx,51,sx,57,p);canvas.drawLine(sx-2,52,sx+2,56,p);}}
            if(kind==6){path.reset();path.moveTo(33,43);path.lineTo(24,54);path.lineTo(32,54);path.lineTo(28,63);path.lineTo(42,49);path.lineTo(34,49);path.lineTo(39,43);path.close();p.setColor(muted?0xFF99A4AB:0xFFFFCE70);canvas.drawPath(path,p);}
        }
        canvas.restore();
    }
    /** Baseline matches the icon's optical centre. Width is bounded before drawing. */
    void inline(Canvas canvas,WeatherStore.Snapshot s,float x,float baseline,float size,float maxWidth,int ink,int alpha,boolean muted,android.content.Context context){
        if(s==null)return;
        String line=s.city(context)+"  "+s.degrees();
        text.setTextSize(size);float symbol=size*1.35f;
        String detail=s.condition(context)+" · "+s.range(context);
        float width=Math.max(text.measureText(line)+symbol+size*.45f,detail.length()*size*.48f);
        float fit=Math.min(1,maxWidth/Math.max(1,width));
        canvas.save();canvas.translate(x,baseline);canvas.scale(fit,fit);
        int layer=canvas.saveLayerAlpha(0,-size*1.65f,maxWidth/fit,size*1.25f,alpha);
        icon(canvas,0,-size*1.15f,symbol,s.code,s.day,muted);
        text.setColor(ink);canvas.drawText(line,symbol+size*.45f,0,text);
        text.setTextSize(size*.65f);text.setAlpha(Math.round(205*(Color.alpha(ink)/255f)));
        canvas.drawText(detail,symbol+size*.45f,size*.96f,text);
        canvas.restoreToCount(layer);canvas.restore();
    }
    static String updated(android.content.Context c,WeatherStore.Snapshot s){
        return Language.pick(c,"Updated ","更新于 ")+new java.text.SimpleDateFormat("HH:mm",Language.locale(c)).format(new java.util.Date(s.fetched));
    }
}

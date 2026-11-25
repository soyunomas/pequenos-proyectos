package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Original vector-style equipment icons drawn on Canvas; no font glyph dependencies.
 * All shapes use the same blue/ink palette as NetScope's B + D theme.
 */
public final class DeviceIconView extends View {
    private static final int BLUE=0xFF456CFF, INK=0xFF273C5E, BG=0xFFF0F3FF;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private String category;
    public DeviceIconView(Context ctx,String category){super(ctx);this.category=category;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=Math.min(getWidth(),getHeight());
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float k=size/60f;
        canvas.save();
        canvas.translate(cx-30*k,cy-30*k);canvas.scale(k,k);
        fill(canvas,8,8,52,52,15,BG);
        p.setStrokeWidth(2.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        String type=category==null?"":category;
        if(type.equals("Router"))router(canvas);
        else if(type.equals("Impresora"))printer(canvas);
        else if(type.equals("Este móvil")||type.equals("Móvil"))phone(canvas);
        else if(type.equals("Ordenador / NAS"))computer(canvas);
        else if(type.equals("TV / multimedia"))television(canvas);
        else if(type.equals("Domótica"))home(canvas);
        else if(type.equals("Servidor / red"))server(canvas);
        else generic(canvas);
        canvas.restore();
    }
    private void fill(Canvas c,float l,float t,float r,float b,float radius,int color){
        p.setStyle(Paint.Style.FILL);p.setColor(color);
        c.drawRoundRect(l,t,r,b,radius,radius,p);
    }
    private void stroke(Canvas c,float l,float t,float r,float b,float radius,int color){
        p.setStyle(Paint.Style.STROKE);p.setColor(color);
        c.drawRoundRect(l,t,r,b,radius,radius,p);p.setStyle(Paint.Style.FILL);
    }
    private void line(Canvas c,float x,float y,float x2,float y2,int color){
        p.setColor(color);p.setStyle(Paint.Style.STROKE);
        c.drawLine(x,y,x2,y2,p);p.setStyle(Paint.Style.FILL);
    }
    private void dot(Canvas c,float x,float y,float rad,int color){
        p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,rad,p);
    }
    private void router(Canvas c){
        line(c,20,29,17,15,INK);line(c,40,29,43,15,INK);
        fill(c,13,29,47,43,5,INK);
        dot(c,20,36,2,0xFF86E6DC);dot(c,27,36,2,0xFF86E6DC);
        line(c,17,45,17,47,INK);line(c,43,45,43,47,INK);
        p.setColor(BLUE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);
        c.drawArc(new RectF(22,12,38,25),205,130,false,p);
        c.drawArc(new RectF(26,17,34,24),205,130,false,p);
        p.setStyle(Paint.Style.FILL);
    }
    private void computer(Canvas c){
        stroke(c,12,17,48,42,4,INK);
        fill(c,16,21,44,38,2,0xFFDBE5FF);
        line(c,30,42,30,47,INK);line(c,22,48,38,48,INK);
        line(c,19,51,41,51,BLUE);
    }
    private void printer(Canvas c){
        stroke(c,17,12,43,30,2,INK);
        fill(c,13,25,47,43,5,INK);
        fill(c,19,36,41,50,2,WHITE());
        line(c,23,42,37,42,BLUE);line(c,23,46,35,46,BLUE);
        dot(c,40,32,2,0xFF86E6DC);
    }
    private int WHITE(){return 0xFFFFFFFF;}
    private void phone(Canvas c){
        stroke(c,21,11,39,50,5,INK);
        fill(c,24,16,36,43,2,0xFFDBE5FF);
        dot(c,30,46,1.7f,BLUE);
    }
    private void television(Canvas c){
        stroke(c,12,17,48,42,4,INK);
        fill(c,16,21,44,38,2,0xFFDCE4FF);
        line(c,24,47,36,47,INK);line(c,30,42,30,47,INK);
    }
    private void server(Canvas c){
        for(int y=15;y<=38;y+=12){
            stroke(c,17,y,43,y+9,3,INK);
            dot(c,23,y+4.5f,1.7f,BLUE);
            line(c,30,y+4.5f,38,y+4.5f,BLUE);
        }
    }
    private void home(Canvas c){
        line(c,15,29,30,16,INK);line(c,30,16,45,29,INK);
        line(c,20,28,20,45,INK);line(c,40,28,40,45,INK);
        line(c,20,45,40,45,INK);
        stroke(c,27,34,34,45,2,BLUE);
    }
    private void generic(Canvas c){
        fill(c,18,18,42,42,7,INK);
        dot(c,30,30,7,0xFFFFFFFF);
        dot(c,30,30,3,BLUE);
    }
}

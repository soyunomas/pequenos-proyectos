package com.soyunomas.horariolectivo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class HorarioWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH="com.soyunomas.horariolectivo.REFRESH_WIDGET";
    private static final Locale ES=new Locale("es","ES");
    private static final int GREEN=0xFF86EFAC;
    private static final int RED=0xFFFCA5A5;
    private static final int AMBER=0xFFFBBF24;

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        for(int id:ids)updateWidget(c,m,id);
        WidgetScheduler.schedule(c);
    }

    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){
        super.onAppWidgetOptionsChanged(c,m,id,options);
        updateWidget(c,m,id);
        WidgetScheduler.schedule(c);
    }

    @Override public void onReceive(Context c,Intent i){
        super.onReceive(c,i);
        String a=i.getAction();
        if(ACTION_REFRESH.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_DATE_CHANGED.equals(a))refreshAll(c);
    }

    static void refreshAll(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,HorarioWidgetProvider.class));
        for(int id:ids)updateWidget(c,m,id);
        if(ids.length>0)WidgetScheduler.schedule(c);
    }

    static void updateWidget(Context c,AppWidgetManager m,int id){
        Data d=new ScheduleRepository(c).load();
        ZonedDateTime now=ZonedDateTime.now();
        NowNext nn=ScheduleEngine.findNowNext(d,now);
        SlotRef next=findNextRelevant(d,now);
        boolean compact=isCompact(m.getAppWidgetOptions(id));
        RemoteViews v=new RemoteViews(c.getPackageName(),compact?R.layout.widget_horario_compact:R.layout.widget_horario);
        v.setTextViewText(R.id.widget_day,now.format(DateTimeFormatter.ofPattern("EEE d",ES)).toUpperCase(ES));
        bindCurrent(v,d,nn.current,now);
        bindNext(v,d,next,now);
        Intent open=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(id,v);
    }

    static boolean isCompact(Bundle options){
        if(options==null)return false;
        int h=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0);
        int min=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0);
        return(h>0&&h<100)||(h==0&&min>0&&min<80);
    }

    static void bindCurrent(RemoteViews v,Data d,SlotRef r,ZonedDateTime now){
        boolean lect=r!=null&&!r.slot.isBreak&&d.isLectiva(r.code);
        boolean comp=r!=null&&!r.slot.isBreak&&d.isComplementaria(r.code);
        int col=lect?GREEN:comp?AMBER:RED;
        v.setInt(R.id.current_card,"setBackgroundResource",lect?R.drawable.widget_highlight_active:comp?R.drawable.widget_highlight_complementary:R.drawable.widget_highlight);
        v.setTextColor(R.id.current_label,col);
        v.setTextColor(R.id.current_remaining,col);
        v.setTextViewText(R.id.current_label,lect?"AHORA · L":comp?"AHORA · C":"AHORA");

        if(r==null){
            v.setTextViewText(R.id.current_subject,"SIN CLASE");
            v.setTextViewText(R.id.current_time,"");
            v.setTextViewText(R.id.current_remaining,"");
            return;
        }

        // También en complementarias se muestran siempre las siglas configuradas.
        v.setTextViewText(R.id.current_subject,displayCode(r));
        v.setTextViewText(R.id.current_time,fmt(r.slot.start)+"–"+fmt(r.slot.end));
        ZonedDateTime end=ZonedDateTime.of(now.toLocalDate(),r.slot.end,now.getZone());
        long min=(Math.max(0L,Duration.between(now,end).getSeconds())+59L)/60L;
        v.setTextViewText(R.id.current_remaining,min==1?"queda 1 min":"quedan "+min+" min");
    }

    static void bindNext(RemoteViews v,Data d,SlotRef r,ZonedDateTime now){
        // v1.17: SIGUIENTE significa la siguiente actividad real, sea L o C.
        // Así dos complementarias consecutivas se sustituyen de forma natural al cambiar de franja.
        v.setViewVisibility(R.id.next_context,View.GONE);
        if(r==null){
            v.setTextViewText(R.id.next_label,"SIGUIENTE");
            v.setTextColor(R.id.next_label,RED);
            v.setTextViewText(R.id.next_subject,"FIN DEL DÍA");
            v.setTextViewText(R.id.next_time,"");
            v.setViewVisibility(R.id.next_countdown,View.GONE);
            return;
        }

        boolean lect=!r.slot.isBreak&&d.isLectiva(r.code);
        boolean comp=!r.slot.isBreak&&d.isComplementaria(r.code);
        int col=lect?GREEN:comp?AMBER:RED;
        v.setTextViewText(R.id.next_label,lect?"SIGUIENTE · L":comp?"SIGUIENTE · C":"SIGUIENTE");
        v.setTextColor(R.id.next_label,col);
        v.setTextColor(R.id.next_countdown,col);
        v.setTextViewText(R.id.next_subject,displayCode(r));
        v.setTextViewText(R.id.next_time,"Empieza "+fmt(r.slot.start));
        ZonedDateTime start=ZonedDateTime.of(now.toLocalDate(),r.slot.start,now.getZone());
        v.setViewVisibility(R.id.next_countdown,View.VISIBLE);
        v.setChronometer(R.id.next_countdown,SystemClock.elapsedRealtime()+Math.max(0L,Duration.between(now,start).toMillis()),"en %s",true);
        v.setChronometerCountDown(R.id.next_countdown,true);
    }

    static SlotRef findNextRelevant(Data d,ZonedDateTime now){
        int dow=now.getDayOfWeek().getValue();
        if(dow<1||dow>5)return null;
        int day=dow-1;
        LocalTime t=now.toLocalTime();
        for(Slot s:ScheduleEngine.generateAllSlots(d)){
            if(!s.start.isAfter(t))continue;
            if(s.isBreak)return new SlotRef(day,s,"RECREO");
            String code=d.getAssignment(day,s.shiftId,s.sessionIndex);
            if(code!=null&&!code.trim().isEmpty())return new SlotRef(day,s,code);
        }
        return null;
    }

    static String displayCode(SlotRef r){
        if(r.slot.isBreak)return "RECREO";
        return r.code==null||r.code.trim().isEmpty()?"SIN ASIGNAR":r.code;
    }

    static String fmt(LocalTime t){
        return String.format(Locale.US,"%02d:%02d",t.getHour(),t.getMinute());
    }
}

final class WidgetScheduler {
    private WidgetScheduler(){}
    static void schedule(Context c){
        Data d=new ScheduleRepository(c).load();
        ZonedDateTime now=ZonedDateTime.now();
        long at=Math.min(ScheduleEngine.nextBoundaryMillis(d,now),now.plusMinutes(1).withSecond(0).withNano(0).toInstant().toEpochMilli());
        PendingIntent p=PendingIntent.getBroadcast(c,9917,new Intent(c,HorarioWidgetProvider.class).setAction(HorarioWidgetProvider.ACTION_REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(a!=null){a.cancel(p);a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}
    }
}

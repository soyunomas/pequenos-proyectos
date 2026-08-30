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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class HorarioWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH="com.soyunomas.horariolectivo.REFRESH_WIDGET";
    private static final Locale ES=new Locale("es","ES");

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateWidget(c,m,id);WidgetScheduler.schedule(c);}

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
        int[]ids=m.getAppWidgetIds(new ComponentName(c,HorarioWidgetProvider.class));
        for(int id:ids)updateWidget(c,m,id);
        if(ids.length>0)WidgetScheduler.schedule(c);
    }

    private static void updateWidget(Context c,AppWidgetManager m,int id){
        Data d=new ScheduleRepository(c).load();
        ZonedDateTime now=ZonedDateTime.now();
        NowNext nn=ScheduleEngine.findNowNext(d,now);
        Bundle options=m.getAppWidgetOptions(id);
        boolean compact=isCompact(options);
        RemoteViews v=new RemoteViews(c.getPackageName(),compact?R.layout.widget_horario_compact:R.layout.widget_horario);
        DateTimeFormatter f=DateTimeFormatter.ofPattern("EEE d",ES);
        v.setTextViewText(R.id.widget_day,now.format(f).toUpperCase(ES));
        bindCurrent(v,nn.current);
        bindNext(v,nn.next,now);
        PendingIntent p=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root,p);
        m.updateAppWidget(id,v);
    }

    private static boolean isCompact(Bundle options){
        if(options==null)return false;
        int maxH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0);
        int minH=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0);
        return (maxH>0&&maxH<100)||(maxH==0&&minH>0&&minH<80);
    }

    private static void bindCurrent(RemoteViews v,SlotRef r){
        if(r==null){
            v.setTextViewText(R.id.current_subject,"SIN CLASE");
            v.setTextViewText(R.id.current_time,"");
            return;
        }
        v.setTextViewText(R.id.current_subject,displayCode(r));
        v.setTextViewText(R.id.current_time,String.format(Locale.US,"%02d:%02d–%02d:%02d",r.slot.start.getHour(),r.slot.start.getMinute(),r.slot.end.getHour(),r.slot.end.getMinute()));
    }

    private static void bindNext(RemoteViews v,SlotRef r,ZonedDateTime now){
        if(r==null){
            v.setTextViewText(R.id.next_subject,"FIN DEL DÍA");
            v.setTextViewText(R.id.next_time,"");
            v.setViewVisibility(R.id.next_countdown,View.GONE);
            v.setChronometer(R.id.next_countdown,SystemClock.elapsedRealtime(),null,false);
            return;
        }
        v.setTextViewText(R.id.next_subject,displayCode(r));
        v.setTextViewText(R.id.next_time,String.format(Locale.US,"Empieza %02d:%02d",r.slot.start.getHour(),r.slot.start.getMinute()));
        ZonedDateTime start=ZonedDateTime.of(now.toLocalDate(),r.slot.start,now.getZone());
        long remaining=Math.max(0L,Duration.between(now,start).toMillis());
        long base=SystemClock.elapsedRealtime()+remaining;
        v.setViewVisibility(R.id.next_countdown,View.VISIBLE);
        v.setChronometer(R.id.next_countdown,base,"en %s",true);
        v.setChronometerCountDown(R.id.next_countdown,true);
    }

    private static String displayCode(SlotRef r){
        if(r.slot.isBreak)return "RECREO";
        String code=r.code;
        return code==null||code.trim().isEmpty()?"SIN ASIGNAR":code;
    }
}

final class WidgetScheduler {
    private WidgetScheduler(){}
    static void schedule(Context c){
        Data d=new ScheduleRepository(c).load();
        long at=ScheduleEngine.nextBoundaryMillis(d,ZonedDateTime.now());
        Intent i=new Intent(c,HorarioWidgetProvider.class).setAction(HorarioWidgetProvider.ACTION_REFRESH);
        PendingIntent p=PendingIntent.getBroadcast(c,9917,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(a!=null){a.cancel(p);a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}
    }
}

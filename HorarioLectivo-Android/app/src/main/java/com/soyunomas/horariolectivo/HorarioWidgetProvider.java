package com.soyunomas.horariolectivo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class HorarioWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH="com.soyunomas.horariolectivo.REFRESH_WIDGET";
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)updateWidget(c,m,id);WidgetScheduler.schedule(c);}
    @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);String a=i.getAction();if(ACTION_REFRESH.equals(a)||Intent.ACTION_TIME_CHANGED.equals(a)||Intent.ACTION_TIMEZONE_CHANGED.equals(a)||Intent.ACTION_DATE_CHANGED.equals(a))refreshAll(c);}
    static void refreshAll(Context c){AppWidgetManager m=AppWidgetManager.getInstance(c);int[]ids=m.getAppWidgetIds(new ComponentName(c,HorarioWidgetProvider.class));for(int id:ids)updateWidget(c,m,id);if(ids.length>0)WidgetScheduler.schedule(c);}
    private static void updateWidget(Context c,AppWidgetManager m,int id){Data d=new ScheduleRepository(c).load();ZonedDateTime now=ZonedDateTime.now();NowNext nn=ScheduleEngine.findNowNext(d,now);RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_horario);DateTimeFormatter f=DateTimeFormatter.ofPattern("EEE d",new Locale("es","ES"));v.setTextViewText(R.id.widget_day,now.format(f).toUpperCase(new Locale("es","ES")));bind(v,R.id.current_subject,R.id.current_time,nn.current,"SIN CLASE");bind(v,R.id.next_subject,R.id.next_time,nn.next,"FIN DEL DÍA");PendingIntent p=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widget_root,p);m.updateAppWidget(id,v);}
    private static void bind(RemoteViews v,int subject,int time,SlotRef r,String fallback){if(r==null){v.setTextViewText(subject,fallback);v.setTextViewText(time,"");return;}String code=r.slot.isBreak?"RECREO":r.code;if(code==null||code.trim().isEmpty())code="SIN ASIGNAR";v.setTextViewText(subject,code);v.setTextViewText(time,String.format(Locale.US,"%02d:%02d - %02d:%02d",r.slot.start.getHour(),r.slot.start.getMinute(),r.slot.end.getHour(),r.slot.end.getMinute()));}
}
final class WidgetScheduler {
    private WidgetScheduler(){}
    static void schedule(Context c){Data d=new ScheduleRepository(c).load();long at=ScheduleEngine.nextBoundaryMillis(d,ZonedDateTime.now());Intent i=new Intent(c,HorarioWidgetProvider.class).setAction(HorarioWidgetProvider.ACTION_REFRESH);PendingIntent p=PendingIntent.getBroadcast(c,9917,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(a!=null){a.cancel(p);a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);}}
}

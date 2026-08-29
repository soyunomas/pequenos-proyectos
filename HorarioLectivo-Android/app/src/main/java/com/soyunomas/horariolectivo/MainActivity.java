package com.soyunomas.horariolectivo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.ZonedDateTime;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class MainActivity extends Activity {
  private static final int RED=Color.rgb(220,38,38), BORDER=Color.rgb(203,213,225);
  private ScheduleRepository repo; private LinearLayout root; private boolean openedSetup;
  @Override protected void onCreate(Bundle b){super.onCreate(b);repo=new ScheduleRepository(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(248,250,252));setContentView(root);}
  @Override protected void onResume(){super.onResume();render();if(!repo.isInitialized()&&!openedSetup){openedSetup=true;startActivity(new Intent(this,SettingsActivity.class));}}
  private void render(){root.removeAllViews();Data d=repo.load();NowNext nn=ScheduleEngine.findNowNext(d,ZonedDateTime.now());LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(14),dp(12),dp(10),dp(8));TextView title=t("Horario lectivo",23,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button cfg=new Button(this);cfg.setText("CONFIGURAR");cfg.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));top.addView(cfg);root.addView(top);TextView sum=t("AHORA  "+desc(d,nn.current,"Sin clase ahora")+"\nSIGUIENTE  "+desc(d,nn.next,"No hay más franjas hoy"),14,false);sum.setPadding(dp(14),dp(10),dp(14),dp(10));sum.setBackground(box(Color.WHITE,BORDER,1,12));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.setMargins(dp(10),0,dp(10),dp(10));root.addView(sum,slp);ScrollView sv=new ScrollView(this);HorizontalScrollView hs=new HorizontalScrollView(this);hs.addView(table(d,nn));sv.addView(hs);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));}
  private GridLayout table(Data d,NowNext nn){GridLayout g=new GridLayout(this);g.setColumnCount(6);String[] h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};for(int i=0;i<6;i++)add(g,cell(h[i],true,false,false),i==0?88:58,42,1);shift(g,d,d.morning,nn);shift(g,d,d.afternoon,nn);return g;}
  private void shift(GridLayout g,Data d,ShiftConfig s,NowNext nn){if(!s.enabled)return;TextView head=cell(s.label,true,false,false);head.setBackgroundColor(Color.rgb(226,232,240));GridLayout.LayoutParams hp=new GridLayout.LayoutParams();hp.columnSpec=GridLayout.spec(0,6);hp.width=dp(378);hp.height=dp(34);g.addView(head,hp);for(Slot slot:ScheduleEngine.generateSlots(d,s)){boolean rowCur=same(nn.current,slot),rowNext=same(nn.next,slot);add(g,cell(time(slot),false,rowCur||rowNext,rowCur),88,54,1);if(slot.isBreak){add(g,cell("RECREO"+(rowCur?"\nAHORA":rowNext?"\nSIG.":""),true,rowCur||rowNext,rowCur),290,54,5);}else for(int day=0;day<5;day++){boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);String c=d.getAssignment(day,s.id,slot.sessionIndex);if(c.isEmpty())c="—";if(cur)c+="\nAHORA";else if(next)c+="\nSIG.";add(g,cell(c,true,cur||next,cur),58,54,1);}}}
  private boolean same(SlotRef r,Slot s){return r!=null&&r.slot.identity().equals(s.identity())&&r.slot.start.equals(s.start)&&r.slot.end.equals(s.end);}
  private String desc(Data d,SlotRef r,String fallback){if(r==null)return fallback;String c=r.slot.isBreak?"RECREO":r.code;if(c==null||c.isEmpty())c="SIN ASIGNAR";String n=r.slot.isBreak?"":d.subjectName(c);return c+(n.isEmpty()||n.equals(c)?"":" · "+n)+"  "+inline(r.slot);}
  private String time(Slot s){return String.format(Locale.US,"%02d:%02d\n%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private String inline(Slot s){return String.format(Locale.US,"%02d:%02d–%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private TextView cell(String v,boolean bold,boolean hi,boolean current){TextView x=t(v,12,bold);x.setGravity(Gravity.CENTER);x.setPadding(dp(2),dp(2),dp(2),dp(2));x.setBackground(box(hi?(current?Color.rgb(255,241,242):Color.rgb(255,247,247)):Color.WHITE,hi?RED:BORDER,hi?2:1,0));return x;}
  private void add(GridLayout g,View v,int w,int h,int span){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(w);p.height=dp(h);if(span>1)p.columnSpec=GridLayout.spec(1,span);p.setMargins(1,1,1,1);g.addView(v,p);}
  private TextView t(String v,int sp,boolean bold){TextView x=new TextView(this);x.setText(v);x.setTextSize(sp);x.setTextColor(Color.rgb(15,23,42));if(bold)x.setTypeface(x.getTypeface(),android.graphics.Typeface.BOLD);return x;}
  private GradientDrawable box(int fill,int stroke,int sw,int rad){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setStroke(dp(sw),stroke);d.setCornerRadius(dp(rad));return d;}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

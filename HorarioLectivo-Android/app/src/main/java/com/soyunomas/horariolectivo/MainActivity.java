package com.soyunomas.horariolectivo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.time.ZonedDateTime;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class MainActivity extends Activity {
  private ScheduleRepository repo;
  private LinearLayout root;
  private boolean openedSetup;
  private AppTheme th;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    repo=new ScheduleRepository(this);
    root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    setContentView(root);
  }

  @Override protected void onResume(){
    super.onResume();
    render();
    if(!repo.isInitialized()&&!openedSetup){openedSetup=true;startActivity(new Intent(this,SettingsActivity.class));}
  }

  private void render(){
    th=new AppTheme(repo.isDarkMode());
    Data d=repo.load();
    th.bindSubjects(d.subjects);
    root.removeAllViews();
    root.setBackgroundColor(th.page);
    getWindow().setStatusBarColor(th.page);
    getWindow().setNavigationBarColor(th.page);
    getWindow().getDecorView().setSystemUiVisibility(th.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    root.setPadding(0,dp(8),0,dp(8));
    root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(0,dp(8)+i.getSystemWindowInsetTop(),0,dp(8)+i.getSystemWindowInsetBottom());return i;});
    root.requestApplyInsets();

    NowNext nn=ScheduleEngine.findNowNext(d,ZonedDateTime.now());
    LinearLayout top=new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(18),dp(12),dp(16),dp(12));
    TextView title=t("Horario lectivo",26,true);
    top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    Button cfg=button("Configurar");
    cfg.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
    top.addView(cfg);
    root.addView(top);

    LinearLayout summary=new LinearLayout(this);
    summary.setOrientation(LinearLayout.VERTICAL);
    summary.setPadding(dp(18),dp(16),dp(18),dp(16));
    summary.setBackground(box(th.surface,th.border,1,18));
    summary.addView(summaryLine("AHORA",desc(d,nn.current,"Sin clase ahora"),true));
    View divider=new View(this);
    divider.setBackgroundColor(th.border);
    LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(1));
    dlp.setMargins(0,dp(10),0,dp(10));
    summary.addView(divider,dlp);
    summary.addView(summaryLine("SIGUIENTE",desc(d,nn.next,"No hay más franjas hoy"),false));
    LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
    slp.setMargins(dp(14),0,dp(14),dp(14));
    root.addView(summary,slp);

    ScrollView sv=new ScrollView(this);
    HorizontalScrollView hs=new HorizontalScrollView(this);
    hs.setFillViewport(true);
    hs.setHorizontalScrollBarEnabled(false);
    hs.setPadding(dp(12),0,dp(12),dp(18));
    LinearLayout centered=new LinearLayout(this);
    centered.setGravity(Gravity.CENTER_HORIZONTAL);
    centered.addView(table(d,nn),new LinearLayout.LayoutParams(-2,-2));
    hs.addView(centered,new HorizontalScrollView.LayoutParams(-1,-2));
    sv.addView(hs);
    root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
  }

  private TextView summaryLine(String label,String value,boolean current){
    TextView x=t(label+"  "+value,current?14:13,current);
    x.setTextColor(current?th.primary:th.muted);
    return x;
  }

  private GridLayout table(Data d,NowNext nn){
    GridLayout g=new GridLayout(this);
    g.setColumnCount(6);
    g.setUseDefaultMargins(false);
    String[] h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};
    int timeW=timeColumnDp(), dayW=dayColumnDp();
    for(int i=0;i<6;i++) add(g,headerCell(h[i]),i==0?timeW:dayW,42,1);
    shift(g,d,d.morning,nn,timeW,dayW);
    shift(g,d,d.afternoon,nn,timeW,dayW);
    return g;
  }

  private void shift(GridLayout g,Data d,ShiftConfig s,NowNext nn,int timeW,int dayW){
    if(!s.enabled)return;
    TextView head=t(s.label.toUpperCase(Locale.ROOT),13,true);
    head.setGravity(Gravity.CENTER);
    head.setTextColor(th.primarySoftText);
    head.setBackground(box(th.primarySoft,th.primarySoft,0,10));
    GridLayout.LayoutParams hp=new GridLayout.LayoutParams();
    hp.columnSpec=GridLayout.spec(0,6);
    hp.width=dp(tableContentDp(timeW,dayW));
    hp.height=dp(38);
    hp.setMargins(dp(2),dp(8),dp(2),dp(2));
    hp.setGravity(Gravity.FILL);
    g.addView(head,hp);
    for(Slot slot:ScheduleEngine.generateSlots(d,s)){
      boolean rowCur=same(nn.current,slot),rowNext=same(nn.next,slot);
      add(g,timeCell(time(slot),rowCur||rowNext,rowCur),timeW,54,1);
      if(slot.isBreak) add(g,breakCell(rowCur,rowNext),breakWidthDp(dayW),54,5);
      else for(int day=0;day<5;day++){
        boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);
        add(g,subjectCell(d.getAssignment(day,s.id,slot.sessionIndex),cur,next),dayW,54,1);
      }
    }
  }

  private TextView headerCell(String v){TextView x=t(v,12,true);x.setGravity(Gravity.CENTER);x.setBackground(box(th.surfaceAlt,th.border,1,8));return x;}
  private TextView timeCell(String v,boolean highlighted,boolean current){TextView x=t(v,11,false);x.setGravity(Gravity.CENTER);x.setTextColor(th.muted);x.setBackground(box(current?th.primarySoft:highlighted?th.surfaceAlt:th.surface,highlighted?th.primary:th.border,highlighted?2:1,10));return x;}
  private TextView subjectCell(String raw,boolean current,boolean next){String code=(raw==null||raw.isEmpty())?"—":raw;String label=code;if(current)label+="\nAHORA";else if(next)label+="\nSIG.";TextView x=t(label,current||next?10:12,true);x.setGravity(Gravity.CENTER);boolean empty="—".equals(code);int fill=empty?th.surfaceAlt:th.subjectColor(code);x.setTextColor(empty?th.muted:th.subjectTextColor(code));if(current||next){fill=blend(fill,th.primarySoft);x.setTextColor(th.dark?Color.WHITE:th.ink);}x.setBackground(box(fill,current||next?th.primary:(empty?th.border:fill),current||next?2:1,10));return x;}
  private TextView breakCell(boolean current,boolean next){TextView x=t("RECREO"+(current?"\nAHORA":next?"\nSIG.":""),current||next?11:12,true);x.setGravity(Gravity.CENTER);x.setBackground(box(current||next?blend(th.breakBg,th.primarySoft):th.breakBg,current||next?th.primary:th.breakBorder,current||next?2:1,10));return x;}

  private int screenWidthDp(){return Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}
  private int timeColumnDp(){int available=Math.max(300,screenWidthDp()-24);return Math.max(66,Math.min(82,Math.round(available*0.22f)));}
  private int dayColumnDp(){int available=Math.max(300,screenWidthDp()-24);int time=timeColumnDp();return Math.max(46,(available-time-24)/5);}
  private int tableContentDp(int timeW,int dayW){return timeW+5*dayW+20;}
  private int breakWidthDp(int dayW){return 5*dayW+16;}

  private int blend(int a,int b){return Color.rgb((Color.red(a)*2+Color.red(b))/3,(Color.green(a)*2+Color.green(b))/3,(Color.blue(a)*2+Color.blue(b))/3);}
  private boolean same(SlotRef r,Slot s){return r!=null&&r.slot.identity().equals(s.identity())&&r.slot.start.equals(s.start)&&r.slot.end.equals(s.end);}
  private String desc(Data d,SlotRef r,String fallback){if(r==null)return fallback;String c=r.slot.isBreak?"RECREO":r.code;if(c==null||c.isEmpty())c="SIN ASIGNAR";String n=r.slot.isBreak?"":d.subjectName(c);return c+(n.isEmpty()||n.equals(c)?"":" · "+n)+"  "+inline(r.slot);}
  private String time(Slot s){return String.format(Locale.US,"%02d:%02d\n%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private String inline(Slot s){return String.format(Locale.US,"%02d:%02d–%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private void add(GridLayout g,View v,int w,int h,int span){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(w);p.height=dp(h);if(span>1)p.columnSpec=GridLayout.spec(1,span);p.setMargins(dp(2),dp(2),dp(2),dp(2));p.setGravity(Gravity.FILL);g.addView(v,p);}
  private TextView t(String v,int sp,boolean bold){TextView x=new TextView(this);x.setText(v);x.setTextSize(sp);x.setTextColor(th.ink);x.setGravity(Gravity.CENTER_VERTICAL);x.setIncludeFontPadding(false);if(bold)x.setTypeface(x.getTypeface(),Typeface.BOLD);return x;}
  private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(th.primaryText);b.setTypeface(b.getTypeface(),Typeface.BOLD);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(14),0,dp(14),0);b.setBackground(box(th.primary,th.primary,0,14));return b;}
  private GradientDrawable box(int fill,int stroke,int sw,int rad){GradientDrawable d=new GradientDrawable();d.setColor(fill);if(sw>0)d.setStroke(dp(sw),stroke);d.setCornerRadius(dp(rad));return d;}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

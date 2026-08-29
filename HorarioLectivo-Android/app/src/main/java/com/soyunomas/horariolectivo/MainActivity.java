package com.soyunomas.horariolectivo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
  private static final int RED=Color.rgb(220,38,38), BORDER=Color.rgb(203,213,225), INK=Color.rgb(15,23,42), MUTED=Color.rgb(71,85,105);
  private static final int[] SUBJECT_COLORS={
      Color.rgb(219,234,254), Color.rgb(220,252,231), Color.rgb(237,233,254),
      Color.rgb(255,237,213), Color.rgb(252,231,243), Color.rgb(254,249,195),
      Color.rgb(204,251,241), Color.rgb(224,242,254), Color.rgb(254,226,226)
  };
  private ScheduleRepository repo; private LinearLayout root; private boolean openedSetup;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    repo=new ScheduleRepository(this);
    root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Color.rgb(246,248,251));
    setContentView(root);
  }

  @Override protected void onResume(){
    super.onResume();
    render();
    if(!repo.isInitialized()&&!openedSetup){openedSetup=true;startActivity(new Intent(this,SettingsActivity.class));}
  }

  private void render(){
    root.removeAllViews();
    Data d=repo.load();
    NowNext nn=ScheduleEngine.findNowNext(d,ZonedDateTime.now());

    LinearLayout top=new LinearLayout(this);
    top.setGravity(Gravity.CENTER_VERTICAL);
    top.setPadding(dp(18),dp(14),dp(12),dp(10));
    TextView title=t("Horario lectivo",25,true);
    top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    Button cfg=new Button(this);
    cfg.setText("CONFIGURAR");
    cfg.setAllCaps(false);
    cfg.setTextSize(13);
    cfg.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
    top.addView(cfg);
    root.addView(top);

    LinearLayout summary=new LinearLayout(this);
    summary.setOrientation(LinearLayout.VERTICAL);
    summary.setPadding(dp(16),dp(12),dp(16),dp(12));
    summary.setBackground(box(Color.WHITE,Color.rgb(226,232,240),1,16));
    summary.addView(summaryLine("AHORA",desc(d,nn.current,"Sin clase ahora"),true));
    summary.addView(summaryLine("SIGUIENTE",desc(d,nn.next,"No hay más franjas hoy"),false));
    LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);
    slp.setMargins(dp(12),0,dp(12),dp(12));
    root.addView(summary,slp);

    ScrollView sv=new ScrollView(this);
    HorizontalScrollView hs=new HorizontalScrollView(this);
    hs.setHorizontalScrollBarEnabled(false);
    hs.addView(table(d,nn));
    sv.addView(hs);
    root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
  }

  private TextView summaryLine(String label,String value,boolean current){
    TextView x=t(label+"  "+value,current?14:13,current);
    x.setTextColor(current?INK:MUTED);
    x.setPadding(0,current?0:dp(5),0,0);
    x.setIncludeFontPadding(false);
    return x;
  }

  private GridLayout table(Data d,NowNext nn){
    GridLayout g=new GridLayout(this);
    g.setColumnCount(6);
    g.setUseDefaultMargins(false);
    String[] h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};
    for(int i=0;i<6;i++)add(g,headerCell(h[i]),i==0?88:58,42,1);
    shift(g,d,d.morning,nn);
    shift(g,d,d.afternoon,nn);
    return g;
  }

  private void shift(GridLayout g,Data d,ShiftConfig s,NowNext nn){
    if(!s.enabled)return;
    TextView head=t(s.label.toUpperCase(Locale.ROOT),13,true);
    head.setGravity(Gravity.CENTER);
    head.setIncludeFontPadding(false);
    head.setBackground(box(Color.rgb(232,238,246),Color.rgb(232,238,246),0,0));
    GridLayout.LayoutParams hp=new GridLayout.LayoutParams();
    hp.columnSpec=GridLayout.spec(0,6);
    hp.width=dp(378);
    hp.height=dp(36);
    hp.setGravity(Gravity.FILL);
    g.addView(head,hp);

    for(Slot slot:ScheduleEngine.generateSlots(d,s)){
      boolean rowCur=same(nn.current,slot),rowNext=same(nn.next,slot);
      add(g,timeCell(time(slot),rowCur||rowNext,rowCur),88,54,1);
      if(slot.isBreak){
        add(g,breakCell(rowCur,rowNext),290,54,5);
      }else{
        for(int day=0;day<5;day++){
          boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);
          String code=d.getAssignment(day,s.id,slot.sessionIndex);
          add(g,subjectCell(code,cur,next),58,54,1);
        }
      }
    }
  }

  private TextView headerCell(String v){
    TextView x=t(v,12,true);
    x.setGravity(Gravity.CENTER);
    x.setIncludeFontPadding(false);
    x.setPadding(dp(3),dp(3),dp(3),dp(3));
    x.setBackground(box(Color.WHITE,BORDER,1,0));
    return x;
  }

  private TextView timeCell(String v,boolean highlighted,boolean current){
    TextView x=t(v,11,false);
    x.setGravity(Gravity.CENTER);
    x.setIncludeFontPadding(false);
    x.setLineSpacing(0f,0.94f);
    x.setPadding(dp(3),0,dp(3),0);
    int fill=current?Color.rgb(255,241,242):highlighted?Color.rgb(255,247,247):Color.WHITE;
    x.setBackground(box(fill,highlighted?RED:BORDER,highlighted?2:1,8));
    return x;
  }

  private TextView subjectCell(String raw,boolean current,boolean next){
    String code=(raw==null||raw.isEmpty())?"—":raw;
    String label=code;
    if(current)label+="\nAHORA";else if(next)label+="\nSIG.";
    TextView x=t(label,current||next?11:12,true);
    x.setGravity(Gravity.CENTER);
    x.setIncludeFontPadding(false);
    x.setPadding(dp(2),0,dp(2),0);
    int fill="—".equals(code)?Color.WHITE:subjectColor(code);
    if(current)fill=blend(fill,Color.rgb(255,228,230));
    else if(next)fill=blend(fill,Color.rgb(255,241,242));
    x.setBackground(box(fill,current||next?RED:Color.rgb(226,232,240),current?2:next?2:1,8));
    return x;
  }

  private TextView breakCell(boolean current,boolean next){
    String label="RECREO"+(current?"\nAHORA":next?"\nSIG.":"");
    TextView x=t(label,current||next?11:12,true);
    x.setGravity(Gravity.CENTER);
    x.setIncludeFontPadding(false);
    x.setBackground(box(current?Color.rgb(255,241,242):Color.rgb(255,247,237),current||next?RED:Color.rgb(251,191,36),current||next?2:1,8));
    return x;
  }

  private int subjectColor(String code){
    return SUBJECT_COLORS[Math.floorMod(code.hashCode(),SUBJECT_COLORS.length)];
  }

  private int blend(int a,int b){
    return Color.rgb((Color.red(a)*2+Color.red(b))/3,(Color.green(a)*2+Color.green(b))/3,(Color.blue(a)*2+Color.blue(b))/3);
  }

  private boolean same(SlotRef r,Slot s){return r!=null&&r.slot.identity().equals(s.identity())&&r.slot.start.equals(s.start)&&r.slot.end.equals(s.end);}

  private String desc(Data d,SlotRef r,String fallback){
    if(r==null)return fallback;
    String c=r.slot.isBreak?"RECREO":r.code;
    if(c==null||c.isEmpty())c="SIN ASIGNAR";
    String n=r.slot.isBreak?"":d.subjectName(c);
    return c+(n.isEmpty()||n.equals(c)?"":" · "+n)+"  "+inline(r.slot);
  }

  private String time(Slot s){return String.format(Locale.US,"%02d:%02d\n%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private String inline(Slot s){return String.format(Locale.US,"%02d:%02d–%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}

  private void add(GridLayout g,View v,int w,int h,int span){
    GridLayout.LayoutParams p=new GridLayout.LayoutParams();
    p.width=dp(w);
    p.height=dp(h);
    if(span>1)p.columnSpec=GridLayout.spec(1,span);
    p.setMargins(dp(1),dp(1),dp(1),dp(1));
    p.setGravity(Gravity.FILL);
    v.setMinimumHeight(dp(h));
    g.addView(v,p);
  }

  private TextView t(String v,int sp,boolean bold){
    TextView x=new TextView(this);
    x.setText(v);
    x.setTextSize(sp);
    x.setTextColor(INK);
    x.setIncludeFontPadding(false);
    if(bold)x.setTypeface(x.getTypeface(),Typeface.BOLD);
    return x;
  }

  private GradientDrawable box(int fill,int stroke,int sw,int rad){
    GradientDrawable d=new GradientDrawable();
    d.setColor(fill);
    if(sw>0)d.setStroke(dp(sw),stroke);
    d.setCornerRadius(dp(rad));
    return d;
  }

  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

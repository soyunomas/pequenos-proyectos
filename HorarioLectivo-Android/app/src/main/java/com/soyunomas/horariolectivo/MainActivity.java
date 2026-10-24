package com.soyunomas.horariolectivo;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class MainActivity extends Activity {
  private ScheduleRepository repo;
  private ReminderStore reminderStore;
  private final Map<String,Integer> upcomingCounts=new HashMap<>();
  private LinearLayout root;
  private ScrollView weekScroll;
  private HorizontalScrollView weekHorizontal;
  private boolean openedSetup;
  private AppTheme th;

  @Override protected void onCreate(Bundle b){super.onCreate(b);repo=new ScheduleRepository(this);reminderStore=new ReminderStore(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);setContentView(root);}
  @Override protected void onResume(){super.onResume();render();if(!repo.isInitialized()&&!openedSetup){openedSetup=true;startActivity(new Intent(this,SettingsActivity.class));}}

  private void render(){
    int savedY=weekScroll==null?0:weekScroll.getScrollY(),savedX=weekHorizontal==null?0:weekHorizontal.getScrollX();
    upcomingCounts.clear();
    long currentMillis=System.currentTimeMillis();
    for(ReminderStore.Reminder reminder:reminderStore.all())
      if(reminder.atMillis>currentMillis&&!reminder.slotKey.isEmpty())
        upcomingCounts.merge(reminder.slotKey,1,Integer::sum);
    th=new AppTheme(repo.isDarkMode());Data d=repo.load();th.bindSubjects(d.subjects);root.removeAllViews();root.setBackgroundColor(th.page);getWindow().setStatusBarColor(th.page);getWindow().setNavigationBarColor(th.page);getWindow().getDecorView().setSystemUiVisibility(th.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);root.setPadding(0,dp(8),0,dp(8));root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(0,dp(8)+i.getSystemWindowInsetTop(),0,dp(8)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
    NowNext nn=ScheduleEngine.findNowNext(d,ZonedDateTime.now());
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(18),dp(12),dp(16),dp(12));TextView title=t("Horario lectivo",22,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button reminders=button("Avisos");reminders.setContentDescription("Abrir recordatorios");reminders.setOnClickListener(v->startActivity(new Intent(this,RemindersActivity.class)));top.addView(reminders);Button cfg=button("Editar");cfg.setContentDescription("Configurar el horario");cfg.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));top.addView(cfg);root.addView(top);TextView guide=t("Toca una casilla para ver sus detalles y recordatorios.",12,false);guide.setTextColor(th.muted);guide.setPadding(dp(18),0,dp(18),dp(12));root.addView(guide);
    LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(18),dp(16),dp(18),dp(16));summary.setBackground(box(th.surface,th.border,1,18));summary.addView(summaryLine(d,"AHORA",nn.current,"Sin clase ahora",true));View divider=new View(this);divider.setBackgroundColor(th.border);LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(1));dlp.setMargins(0,dp(10),0,dp(10));summary.addView(divider,dlp);summary.addView(summaryLine(d,"SIGUIENTE",nn.next,"No hay más franjas hoy",false));LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.setMargins(dp(14),0,dp(14),dp(14));root.addView(summary,slp);
    ScrollView sv=new ScrollView(this);HorizontalScrollView hs=new HorizontalScrollView(this);weekScroll=sv;weekHorizontal=hs;hs.setFillViewport(true);hs.setHorizontalScrollBarEnabled(false);hs.setPadding(dp(12),0,dp(12),dp(18));LinearLayout centered=new LinearLayout(this);centered.setGravity(Gravity.CENTER_HORIZONTAL);centered.addView(table(d,nn),new LinearLayout.LayoutParams(-2,-2));hs.addView(centered,new HorizontalScrollView.LayoutParams(-1,-2));sv.addView(hs);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));sv.post(()->{sv.scrollTo(0,savedY);hs.scrollTo(savedX,0);});
  }

  private TextView summaryLine(Data d,String label,SlotRef ref,String fallback,boolean current){String value=desc(d,ref,fallback);TextView x=t(label+"  "+value,current?14:13,current);x.setTextColor(current?th.primary:th.muted);if(ref!=null){x.setClickable(true);x.setFocusable(true);x.setContentDescription("Abrir detalles y recordatorios de "+label);x.setOnClickListener(v->showSlotDetails(d,ref.dayIndex,ref.slot,ref.code,current,!current));}return x;}

  private GridLayout table(Data d,NowNext nn){
    GridLayout g=new GridLayout(this);g.setColumnCount(6);g.setUseDefaultMargins(false);String[] h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};int timeW=timeColumnDp(),dayW=dayColumnDp();for(int i=0;i<6;i++)add(g,headerCell(h[i]),i==0?timeW:dayW,42,1);
    shift(g,d,d.morning,nn,timeW,dayW);shift(g,d,d.between,nn,timeW,dayW);shift(g,d,d.afternoon,nn,timeW,dayW);shift(g,d,d.betweenNight,nn,timeW,dayW);shift(g,d,d.night,nn,timeW,dayW);return g;
  }

  private void shift(GridLayout g,Data d,ShiftConfig s,NowNext nn,int timeW,int dayW){
    if(!s.enabled)return;boolean between=isBetweenShift(s.id);
    if(!between){TextView head=t(s.label.toUpperCase(Locale.ROOT),13,true);head.setGravity(Gravity.CENTER);head.setTextColor(th.primarySoftText);head.setBackground(box(th.primarySoft,th.primarySoft,0,10));GridLayout.LayoutParams hp=new GridLayout.LayoutParams();hp.columnSpec=GridLayout.spec(0,6);hp.width=dp(tableContentDp(timeW,dayW));hp.height=dp(38);hp.setMargins(dp(2),dp(8),dp(2),dp(2));hp.setGravity(Gravity.FILL);g.addView(head,hp);}
    for(Slot slot:ScheduleEngine.generateSlots(d,s)){
      boolean rowCur=same(nn.current,slot),rowNext=same(nn.next,slot);add(g,between?betweenTimeCell(time(slot),rowCur||rowNext,rowCur):timeCell(time(slot),rowCur||rowNext,rowCur),timeW,66,1);
      if(slot.isBreak){
        for(int day=0;day<5;day++){String code=d.getAssignment(day,s.id,slot.sessionIndex),room=d.getRoom(day,s.id,slot.sessionIndex);boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);add(g,code.isEmpty()?breakCell(d,day,slot,cur,next):subjectCell(d,day,slot,code,room,cur,next),dayW,66,1);}
      }else if(between){
        for(int day=0;day<5;day++){String code=d.getAssignment(day,s.id,slot.sessionIndex),room=d.getRoom(day,s.id,slot.sessionIndex);boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);add(g,code.isEmpty()?betweenCell(d,day,slot,cur,next):subjectCell(d,day,slot,code,room,cur,next),dayW,66,1);}
      }else for(int day=0;day<5;day++){boolean cur=ScheduleEngine.matches(nn.current,day,slot),next=ScheduleEngine.matches(nn.next,day,slot);add(g,subjectCell(d,day,slot,d.getAssignment(day,s.id,slot.sessionIndex),d.getRoom(day,s.id,slot.sessionIndex),cur,next),dayW,66,1);}
    }
  }

  private TextView headerCell(String v){TextView x=t(v,12,true);x.setGravity(Gravity.CENTER);x.setBackground(box(th.surfaceAlt,th.border,1,8));return x;}
  private TextView timeCell(String v,boolean highlighted,boolean current){TextView x=t(v,11,false);x.setGravity(Gravity.CENTER);x.setTextColor(th.muted);x.setBackground(box(current?th.primarySoft:highlighted?th.surfaceAlt:th.surface,highlighted?th.primary:th.border,highlighted?2:1,10));return x;}
  private TextView betweenTimeCell(String v,boolean highlighted,boolean current){TextView x=t(v,11,true);x.setGravity(Gravity.CENTER);x.setTextColor(th.breakBorder);x.setBackground(box(highlighted?blend(th.breakBg,th.primarySoft):th.breakBg,highlighted?th.primary:th.breakBorder,highlighted?2:1,10));return x;}
  private int reminderCount(int day,Slot slot){
    return upcomingCounts.getOrDefault(ReminderSlot.key(day,slot.shiftId,slot.sessionIndex),0);
  }

  private TextView subjectCell(Data d,int day,Slot slot,String raw,String room,boolean current,boolean next){
    String code=raw==null?"":raw;
    String group=d.getGroup(day,slot.shiftId,slot.sessionIndex);
    String g=group==null?"":group.trim(),r=room==null?"":room.trim();
    int count=reminderCount(day,slot);
    boolean empty=code.isEmpty();
    String label=empty?"—":code;
    if(!empty&&!g.isEmpty())label+="\n"+g;
    if(!empty&&!r.isEmpty())label+="\n"+r;
    if(g.isEmpty()&&r.isEmpty()){
      if(current)label+="\nAHORA";else if(next)label+="\nSIG.";
    }
    if(count>0)label+="  ⏰"+count;
    TextView x=t(label,(!g.isEmpty()||!r.isEmpty()||count>0)?10:(current||next?10:12),true);
    x.setGravity(Gravity.CENTER);
    x.setMaxLines(3);
    x.setEllipsize(android.text.TextUtils.TruncateAt.END);
    int fill=empty?th.surfaceAlt:th.subjectColor(code);
    x.setTextColor(empty?th.muted:th.subjectTextColor(code));
    if(current||next){fill=blend(fill,th.primarySoft);x.setTextColor(th.dark?Color.WHITE:th.ink);}
    x.setBackground(box(fill,current||next?th.primary:(empty?th.border:fill),current||next?2:1,10));
    x.setClickable(true);x.setFocusable(true);
    x.setContentDescription((empty?"Hora sin asignar":d.subjectName(code))+". "+dayName(day)+" "+inline(slot)+
      ". "+count+" recordatorios programados. Toca para ver detalles y crear avisos.");
    x.setOnClickListener(v->showSlotDetails(d,day,slot,code,current,next));
    return x;
  }

  private TextView breakCell(Data d,int day,Slot slot,boolean current,boolean next){
    return specialCell(d,day,slot,"RECREO",current,next);
  }

  private TextView betweenCell(Data d,int day,Slot slot,boolean current,boolean next){
    return specialCell(d,day,slot,"ENTRE",current,next);
  }

  private TextView specialCell(Data d,int day,Slot slot,String label,boolean current,boolean next){
    int count=reminderCount(day,slot);
    String text=label+(current?"\nAHORA":next?"\nSIG.":"")+(count>0?"  ⏰"+count:"");
    TextView x=t(text,current||next?10:11,true);
    x.setGravity(Gravity.CENTER);
    x.setTextColor(th.breakBorder);
    x.setBackground(box(current||next?blend(th.breakBg,th.primarySoft):th.breakBg,
      current||next?th.primary:th.breakBorder,current||next?2:1,10));
    x.setClickable(true);x.setFocusable(true);
    x.setContentDescription(label+" · "+dayName(day)+" "+inline(slot)+
      ". "+count+" recordatorios programados. Toca para añadir o consultar avisos.");
    x.setOnClickListener(v->showSlotDetails(d,day,slot,"",current,next));
    return x;
  }

  private void showSlotDetails(Data d,int day,Slot slot,String code,boolean current,boolean next){
    Subject subject=d.subject(code);
    String key=ReminderSlot.key(day,slot.shiftId,slot.sessionIndex);
    String name=subject!=null?subject.name:slot.isBreak?"Recreo":isBetweenShift(slot.shiftId)?"Entre turnos":"Sin asignatura";
    String group=d.getGroup(day,slot.shiftId,slot.sessionIndex);
    String room=d.getRoom(day,slot.shiftId,slot.sessionIndex);
    String override=d.getRoomOverride(day,slot.shiftId,slot.sessionIndex);

    Dialog dialog=new Dialog(this);
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(18),dp(18),dp(18),dp(16));
    panel.setBackground(box(th.surface,th.border,1,22));

    LinearLayout head=new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    String badgeText=subject==null?dayName(day).substring(0,3).toUpperCase(Locale.ROOT):subject.code;
    TextView badge=t(badgeText,15,true);
    badge.setGravity(Gravity.CENTER);
    badge.setTextColor(subject==null?th.primarySoftText:th.subjectTextColor(subject.code));
    int badgeBg=subject==null?th.primarySoft:th.subjectColor(subject.code);
    badge.setBackground(box(badgeBg,badgeBg,0,12));
    head.addView(badge,new LinearLayout.LayoutParams(dp(65),dp(50)));
    LinearLayout names=new LinearLayout(this);
    names.setOrientation(LinearLayout.VERTICAL);
    names.setPadding(dp(12),0,0,0);
    TextView title=t(name,18,true);
    names.addView(title);
    TextView subtitle=t(dayName(day)+" · "+inline(slot),13,false);
    subtitle.setTextColor(th.muted);
    subtitle.setPadding(0,dp(4),0,0);
    names.addView(subtitle);
    head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
    panel.addView(head);

    View line=new View(this);
    line.setBackgroundColor(th.border);
    LinearLayout.LayoutParams lineLp=new LinearLayout.LayoutParams(-1,dp(1));
    lineLp.setMargins(0,dp(14),0,dp(14));
    panel.addView(line,lineLp);
    detailRow(panel,"Turno",slot.shiftLabel);
    if(subject!=null)detailRow(panel,"Tipo",subject.isComplementaria()?"Complementaria":"Lectiva");
    if(!group.isEmpty())detailRow(panel,"Grupo",group);
    if(!room.isEmpty())detailRow(panel,override.isEmpty()?"Aula general":"Aula excepcional",room);
    if(current)detailRow(panel,"Estado","Ahora");else if(next)detailRow(panel,"Estado","Siguiente");

    View separate=new View(this);
    separate.setBackgroundColor(th.border);
    LinearLayout.LayoutParams space=new LinearLayout.LayoutParams(-1,dp(1));
    space.setMargins(0,dp(18),0,dp(14));
    panel.addView(separate,space);

    List<ReminderStore.Reminder> related=reminderStore.forSlot(key);
    long now=System.currentTimeMillis();
    int upcoming=0,previous=0;
    for(ReminderStore.Reminder reminder:related){
      if(reminder.atMillis>now)upcoming++;else previous++;
    }
    TextView section=t("Recordatorios  ·  "+upcoming+" pendientes",16,true);
    section.setTextColor(th.ink);
    panel.addView(section);
    TextView description=t("Avisos vinculados a esta hora del horario.",12,false);
    description.setTextColor(th.muted);
    description.setPadding(0,dp(5),0,dp(10));
    panel.addView(description);

    if(upcoming==0){
      TextView empty=t("No hay avisos programados para esta franja.",13,false);
      empty.setTextColor(th.muted);
      empty.setPadding(dp(10),dp(12),dp(10),dp(12));
      empty.setBackground(box(th.surfaceAlt,th.border,1,12));
      panel.addView(empty);
    }
    for(ReminderStore.Reminder reminder:related)if(reminder.atMillis>now)
      addReminderToDetails(panel,dialog,d,day,slot,code,current,next,reminder);

    if(previous>0){
      TextView history=t("Anteriores  ·  "+previous,13,true);
      history.setTextColor(th.muted);
      history.setPadding(0,dp(14),0,dp(8));
      panel.addView(history);
      for(ReminderStore.Reminder reminder:related)if(reminder.atMillis<=now)
        addReminderToDetails(panel,dialog,d,day,slot,code,current,next,reminder);
    }

    Button add=button("+ Añadir recordatorio");
    add.setContentDescription("Crear un recordatorio para "+dayName(day)+" "+inline(slot));
    long scheduled=ReminderSlot.nextReminderTime(day,slot.start,ZoneId.systemDefault(),ZonedDateTime.now());
    String proposed=subject!=null?"Recordar: "+subject.name:"Recordatorio · "+dayName(day);
    add.setOnClickListener(v->ReminderEditor.show(this,null,proposed,key,scheduled,()->{
      dialog.dismiss();
      render();
      showSlotDetails(d,day,slot,code,current,next);
    }));
    LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(50));
    action.setMargins(0,dp(16),0,0);
    panel.addView(add,action);

    TextView note=t("Se propone el próximo día correspondiente, 10 minutos antes de empezar. Puedes cambiar la fecha, la hora y el sonido.",12,false);
    note.setTextColor(th.muted);
    note.setPadding(0,dp(9),0,0);
    panel.addView(note);
    TextView close=t("Cerrar",14,true);
    close.setGravity(Gravity.CENTER);
    close.setTextColor(th.primary);
    close.setOnClickListener(v->dialog.dismiss());
    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(46));
    cp.setMargins(0,dp(12),0,0);
    panel.addView(close,cp);

    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(false);
    scroll.addView(panel);
    dialog.setContentView(scroll);
    Window w=dialog.getWindow();
    if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(0.45f);w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);}
    dialog.show();
    w=dialog.getWindow();
    if(w!=null)w.setLayout(Math.min(dp(440),getResources().getDisplayMetrics().widthPixels-dp(32)),-2);
    panel.setScaleX(0.96f);panel.setScaleY(0.96f);panel.setAlpha(0f);
    panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).setInterpolator(new OvershootInterpolator(0.7f)).start();
  }

  private void addReminderToDetails(LinearLayout parent,Dialog dialog,Data d,int day,Slot slot,
        String code,boolean current,boolean next,ReminderStore.Reminder reminder){
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(12),dp(12),dp(12),dp(12));
    card.setBackground(box(th.surfaceAlt,th.border,1,12));
    TextView title=t(reminder.title,14,true);card.addView(title);
    String time=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,Locale.getDefault()).format(new Date(reminder.atMillis));
    TextView meta=t(time+" · "+(reminder.sound?"Con sonido":"Silencioso"),12,false);
    meta.setTextColor(th.muted);
    meta.setPadding(0,dp(4),0,0);
    card.addView(meta);
    if(!reminder.notes.isEmpty()){
      TextView preview=t(reminder.notes,13,false);
      preview.setMaxLines(3);
      preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
      preview.setPadding(0,dp(8),0,dp(4));
      card.addView(preview);
    }
    LinearLayout controls=new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    TextView view=detailAction("Ver",th.primary);
    view.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle(reminder.title)
       .setMessage(reminder.notes.isEmpty()?time:reminder.notes).setPositiveButton("Cerrar",null).show());
    controls.addView(view,new LinearLayout.LayoutParams(0,dp(43),1));
    TextView edit=detailAction("Editar",th.primary);
    edit.setOnClickListener(v->ReminderEditor.show(this,reminder,"","",0,()->{
      dialog.dismiss();render();showSlotDetails(d,day,slot,code,current,next);
    }));
    controls.addView(edit,new LinearLayout.LayoutParams(0,dp(43),1));
    TextView remove=detailAction("Eliminar",th.muted);
    remove.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
       .setTitle("Eliminar recordatorio")
       .setMessage("¿Eliminar «"+reminder.title+"»?")
       .setNegativeButton("Cancelar",null)
       .setPositiveButton("Eliminar",(unused,which)->{
         ReminderScheduler.cancel(this,reminder.id);
         reminderStore.remove(reminder.id);
         dialog.dismiss();render();showSlotDetails(d,day,slot,code,current,next);
       }).show());
    controls.addView(remove,new LinearLayout.LayoutParams(0,dp(43),1));
    LinearLayout.LayoutParams actions=new LinearLayout.LayoutParams(-1,-2);
    actions.setMargins(0,dp(8),0,0);
    card.addView(controls,actions);
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,-2);
    cardLp.setMargins(0,0,0,dp(8));
    parent.addView(card,cardLp);
  }

  private TextView detailAction(String label,int color){
    TextView view=t(label,13,true);
    view.setTextColor(color);
    view.setGravity(Gravity.CENTER);
    view.setClickable(true);
    view.setFocusable(true);
    view.setContentDescription(label+" recordatorio");
    return view;
  }

  private void detailRow(LinearLayout panel,String label,String value){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.TOP);TextView l=t(label,12,true);l.setTextColor(th.muted);r.addView(l,new LinearLayout.LayoutParams(dp(105),-2));TextView v=t(value,14,false);v.setGravity(Gravity.END);r.addView(v,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));panel.addView(r,p);}
  private String dayName(int day){String[] days={"Lunes","Martes","Miércoles","Jueves","Viernes"};return day>=0&&day<days.length?days[day]:"";}

  private int screenWidthDp(){return Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}private int timeColumnDp(){int available=Math.max(300,screenWidthDp()-24);return Math.max(66,Math.min(82,Math.round(available*0.22f)));}private int dayColumnDp(){int available=Math.max(300,screenWidthDp()-24);int time=timeColumnDp();return Math.max(46,(available-time-24)/5);}private int tableContentDp(int timeW,int dayW){return timeW+5*dayW+20;}
  private int blend(int a,int b){return Color.rgb((Color.red(a)*2+Color.red(b))/3,(Color.green(a)*2+Color.green(b))/3,(Color.blue(a)*2+Color.blue(b))/3);}private boolean same(SlotRef r,Slot s){return r!=null&&r.slot.identity().equals(s.identity())&&r.slot.start.equals(s.start)&&r.slot.end.equals(s.end);}
  private String desc(Data d,SlotRef r,String fallback){if(r==null)return fallback;String c=r.code;if(c==null||c.isEmpty())c=r.slot.isBreak?"RECREO":isBetweenShift(r.slot.shiftId)?"ENTRE TURNOS":"SIN ASIGNAR";String n=("RECREO".equals(c)||"ENTRE TURNOS".equals(c))?"":d.subjectName(c);String group=d.getGroup(r.dayIndex,r.slot.shiftId,r.slot.sessionIndex),room=d.getRoom(r.dayIndex,r.slot.shiftId,r.slot.sessionIndex);return c+(group.isEmpty()?"":" · "+group)+(n.isEmpty()||n.equals(c)?"":" · "+n)+(room.isEmpty()?"":" · "+room)+"  "+inline(r.slot);}
  private String time(Slot s){return String.format(Locale.US,"%02d:%02d\n%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}private String inline(Slot s){return String.format(Locale.US,"%02d:%02d–%02d:%02d",s.start.getHour(),s.start.getMinute(),s.end.getHour(),s.end.getMinute());}
  private void add(GridLayout g,View v,int w,int h,int span){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(w);p.height=dp(h);if(span>1)p.columnSpec=GridLayout.spec(1,span);p.setMargins(dp(2),dp(2),dp(2),dp(2));p.setGravity(Gravity.FILL);g.addView(v,p);}private TextView t(String v,int sp,boolean bold){TextView x=new TextView(this);x.setText(v);x.setTextSize(sp);x.setTextColor(th.ink);x.setGravity(Gravity.CENTER_VERTICAL);x.setIncludeFontPadding(false);if(bold)x.setTypeface(x.getTypeface(),Typeface.BOLD);return x;}private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(th.primaryText);b.setTypeface(b.getTypeface(),Typeface.BOLD);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(14),0,dp(14),0);b.setBackground(box(th.primary,th.primary,0,14));return b;}private GradientDrawable box(int fill,int stroke,int sw,int rad){GradientDrawable d=new GradientDrawable();d.setColor(fill);if(sw>0)d.setStroke(dp(sw),stroke);d.setCornerRadius(dp(rad));return d;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}

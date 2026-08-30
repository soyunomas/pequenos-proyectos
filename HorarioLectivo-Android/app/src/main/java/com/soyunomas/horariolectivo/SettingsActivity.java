package com.soyunomas.horariolectivo;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.time.LocalTime;
import java.util.*;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class SettingsActivity extends Activity {
  private static final String ERASE="__ERASE__"; private static final String[] DAYS={"LUN","MAR","MIÉ","JUE","VIE"};
  private ScheduleRepository repo; private Data d; private LinearLayout root,editor; private String selected=ERASE; private boolean block; private Start start; private AppTheme th;
  @Override protected void onCreate(Bundle b){super.onCreate(b);repo=new ScheduleRepository(this);d=repo.load().copy();render();}

  private void render(){
    th=new AppTheme(repo.isDarkMode());getWindow().setStatusBarColor(th.page);getWindow().getDecorView().setSystemUiVisibility(th.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(th.page);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(18),dp(18),dp(104));root.setBackgroundColor(th.page);sv.addView(root);setContentView(sv);

    LinearLayout top=row();top.setPadding(dp(2),0,dp(2),dp(8));TextView title=text("Configurar horario",26,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button save=primaryButton("Guardar");save.setOnClickListener(v->save());top.addView(save);root.addView(top);
    TextView intro=text("Personaliza turnos, asignaturas y semana con una vista más clara y cómoda.",14,false);intro.setTextColor(th.muted);intro.setPadding(dp(2),0,dp(2),dp(8));root.addView(intro);

    section("Apariencia","Ajusta el tema para una lectura cómoda en cualquier entorno.");
    LinearLayout dc=card();Switch dark=new Switch(this);dark.setText("Modo oscuro");dark.setTextColor(th.ink);dark.setTextSize(15);dark.setChecked(repo.isDarkMode());dark.setOnCheckedChangeListener((b,on)->{repo.setDarkMode(on);render();});dc.addView(dark);addCard(dc);

    section("1 · Turnos","Define la jornada; las sesiones se calculan automáticamente.");
    LinearLayout duration=card();value(duration,"Duración de sesión",d.sessionMinutes+" min",v->number("Duración",10,180,d.sessionMinutes,n->{d.sessionMinutes=n;render();}));addCard(duration);
    shift(d.morning);shift(d.afternoon);

    section("2 · Asignaturas","Cada asignatura recibe un color estable y distinguible en claro y oscuro.");subjects();

    section("3 · Semana","Selecciona una asignatura y toca casillas. El modo bloque rellena un intervalo con dos toques.");
    editor=new LinearLayout(this);editor.setOrientation(LinearLayout.VERTICAL);root.addView(editor);renderEditor();
  }

  private void shift(ShiftConfig s){
    LinearLayout c=card();
    CheckBox on=new CheckBox(this);on.setText("Turno de "+s.label.toLowerCase(Locale.ROOT));on.setTextSize(16);on.setTypeface(on.getTypeface(),Typeface.BOLD);on.setTextColor(th.ink);on.setChecked(s.enabled);on.setOnCheckedChangeListener((b,x)->{s.enabled=x;render();});c.addView(on);
    if(s.enabled){
      value(c,"Comienzo",fmt(s.start),v->time(s.start,x->{s.start=x;render();}));divider(c);value(c,"Fin",fmt(s.end),v->time(s.end,x->{s.end=x;render();}));divider(c);value(c,"Recreo después de",s.breakAfterSession==0?"Sin recreo":"Sesión "+s.breakAfterSession,v->number("Posición del recreo",0,12,s.breakAfterSession,n->{s.breakAfterSession=n;render();}));
      if(s.breakAfterSession>0){divider(c);value(c,"Duración recreo",s.breakMinutes+" min",v->number("Duración recreo",5,90,Math.max(5,s.breakMinutes),n->{s.breakMinutes=n;render();}));}
      int n=0;for(Slot q:ScheduleEngine.generateSlots(d,s))if(!q.isBreak)n++;TextView meta=text(n+" sesiones · "+ScheduleEngine.remainingMinutes(d,s)+" min libres",13,true);meta.setTextColor(th.muted);meta.setPadding(0,dp(10),0,0);c.addView(meta);
    }
    addCard(c);
  }

  private void subjects(){
    LinearLayout list=card();
    boolean first=true;
    for(Subject s:new ArrayList<>(d.subjects)){
      if(!first)divider(list);first=false;
      LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));
      TextView badge=text(s.code,13,true);badge.setGravity(Gravity.CENTER);badge.setTextColor(th.subjectTextColor(s.code));badge.setBackground(box(th.subjectColor(s.code),th.subjectColor(s.code),0,12));
      LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(62),dp(38));bp.setMargins(0,0,dp(10),0);r.addView(badge,bp);
      TextView name=text(s.name,15,false);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
      Button del=iconButton("×");del.setContentDescription("Eliminar "+s.code);del.setOnClickListener(v->confirmDelete(s.code));r.addView(del,new LinearLayout.LayoutParams(dp(44),dp(44)));list.addView(r);
    }
    addCard(list);

    LinearLayout add=card();TextView addTitle=text("Añadir asignatura",15,true);addTitle.setPadding(0,0,0,dp(10));add.addView(addTitle);
    LinearLayout inputs=row();EditText code=input("APW");code.setAllCaps(true);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});inputs.addView(code,new LinearLayout.LayoutParams(dp(88),dp(52)));EditText name=input("Aplicaciones Web");LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,dp(52),1);np.setMargins(dp(8),0,0,0);inputs.addView(name,np);add.addView(inputs);
    Button plus=primaryButton("Añadir");LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(48));pp.setMargins(0,dp(10),0,0);add.addView(plus,pp);plus.setOnClickListener(v->addSubject(code.getText().toString(),name.getText().toString()));addCard(add);
  }

  private void renderEditor(){
    editor.removeAllViews();
    LinearLayout tools=card();
    TextView toolTitle=text("Asignatura activa",13,true);toolTitle.setTextColor(th.muted);tools.addView(toolTitle);
    HorizontalScrollView ps=new HorizontalScrollView(this);ps.setHorizontalScrollBarEnabled(false);LinearLayout p=row();p.setPadding(0,dp(8),0,dp(4));p.addView(pick("BORRAR",ERASE));for(Subject s:d.subjects)p.addView(pick(s.code,s.code));ps.addView(p);tools.addView(ps);
    divider(tools);CheckBox bm=new CheckBox(this);bm.setText("Modo bloque: toca inicio y fin");bm.setTextColor(th.ink);bm.setChecked(block);bm.setOnCheckedChangeListener((b,x)->{block=x;start=null;});tools.addView(bm);
    divider(tools);copyControls(tools);addCardTo(editor,tools,0,dp(12));

    LinearLayout gridCard=card();gridCard.setPadding(dp(8),dp(10),dp(8),dp(10));HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);GridLayout g=new GridLayout(this);g.setColumnCount(6);String[] h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};for(int i=0;i<6;i++)add(g,cell(h[i],true),i==0?88:58,42,1);editShift(g,d.morning);editShift(g,d.afternoon);hs.addView(g);gridCard.addView(hs);addCardTo(editor,gridCard,0,0);
  }

  private void editShift(GridLayout g,ShiftConfig s){if(!s.enabled)return;TextView head=cell(s.label,true);head.setTextColor(th.primarySoftText);head.setBackground(box(th.primarySoft,th.primarySoft,0,8));GridLayout.LayoutParams hp=new GridLayout.LayoutParams();hp.columnSpec=GridLayout.spec(0,6);hp.width=dp(378);hp.height=dp(38);hp.setMargins(dp(1),dp(6),dp(1),dp(2));hp.setGravity(Gravity.FILL);g.addView(head,hp);for(Slot q:ScheduleEngine.generateSlots(d,s)){add(g,cell(String.format(Locale.US,"%02d:%02d\n%02d:%02d",q.start.getHour(),q.start.getMinute(),q.end.getHour(),q.end.getMinute()),false),88,54,1);if(q.isBreak)add(g,breakCell(),290,54,5);else for(int day=0;day<5;day++){int dd=day;String c=d.getAssignment(day,s.id,q.sessionIndex);TextView x=subjectCell(c.isEmpty()?"·":c);if(start!=null&&start.day==day&&start.shift.equals(s.id)&&start.session==q.sessionIndex)x.setBackground(box(th.primarySoft,th.primary,2,10));x.setOnClickListener(v->assign(dd,q));add(g,x,58,54,1);}}}
  private TextView subjectCell(String code){TextView x=text(code,12,true);x.setGravity(Gravity.CENTER);x.setIncludeFontPadding(false);boolean empty="·".equals(code);int fill=empty?th.surfaceAlt:th.subjectColor(code);x.setTextColor(empty?th.muted:th.subjectTextColor(code));x.setBackground(box(fill,empty?th.border:fill,1,10));return x;}
  private TextView breakCell(){TextView x=text("RECREO",12,true);x.setGravity(Gravity.CENTER);x.setTextColor(th.dark?Color.rgb(255,231,173):Color.rgb(120,68,8));x.setBackground(box(th.breakBg,th.breakBorder,1,10));return x;}

  private void assign(int day,Slot q){if(!block){set(day,q.shiftId,q.sessionIndex);renderEditor();return;}if(start==null){start=new Start(day,q.shiftId,q.sessionIndex);renderEditor();return;}if(start.day!=day||!start.shift.equals(q.shiftId)){start=new Start(day,q.shiftId,q.sessionIndex);Toast.makeText(this,"El bloque debe estar en el mismo día y turno",Toast.LENGTH_SHORT).show();renderEditor();return;}for(int i=Math.min(start.session,q.sessionIndex);i<=Math.max(start.session,q.sessionIndex);i++)set(day,q.shiftId,i);start=null;renderEditor();}
  private void set(int day,String shift,int session){d.setAssignment(day,shift,session,ERASE.equals(selected)?"":selected);}
  private void copyControls(LinearLayout target){LinearLayout r=row();Spinner from=new Spinner(this),to=new Spinner(this);from.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,DAYS));String[] targets={"LUN","MAR","MIÉ","JUE","VIE","TODOS"};to.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,targets));r.addView(from,new LinearLayout.LayoutParams(0,dp(48),1));r.addView(to,new LinearLayout.LayoutParams(0,dp(48),1));Button b=tonalButton("Copiar");b.setOnClickListener(v->{int src=from.getSelectedItemPosition();String t=(String)to.getSelectedItem();if("TODOS".equals(t)){for(int day=0;day<5;day++)if(day!=src)copyDay(src,day);}else copyDay(src,Arrays.asList(DAYS).indexOf(t));renderEditor();});r.addView(b);target.addView(r);}
  private void copyDay(int src,int dst){if(dst<0||src==dst)return;for(ShiftConfig s:new ShiftConfig[]{d.morning,d.afternoon})for(Slot q:ScheduleEngine.generateSlots(d,s))if(!q.isBreak)d.setAssignment(dst,s.id,q.sessionIndex,d.getAssignment(src,s.id,q.sessionIndex));}
  private Button pick(String label,String value){Button b=tonalButton(label);boolean on=value.equals(selected);b.setTextColor(on?th.primaryText:th.buttonText);b.setBackground(box(on?th.primary:th.buttonBg,on?th.primary:th.border,1,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.setMargins(0,0,dp(8),0);b.setLayoutParams(p);b.setOnClickListener(v->{selected=value;start=null;renderEditor();});return b;}
  private void addSubject(String raw,String name){String c=SubjectCode.normalize(raw),n=name.trim();if(!SubjectCode.isValid(c)||n.isEmpty()){Toast.makeText(this,"Usa una abreviatura de 1 a 3 letras/números y un nombre",Toast.LENGTH_LONG).show();return;}for(Subject s:d.subjects)if(s.code.equals(c)){Toast.makeText(this,"Ya existe "+c,Toast.LENGTH_SHORT).show();return;}d.subjects.add(new Subject(c,n));selected=c;render();}
  private void confirmDelete(String code){new AlertDialog.Builder(this).setTitle("Eliminar "+code).setMessage("También se borrará de la semana.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(a,b)->{d.subjects.removeIf(s->s.code.equals(code));d.assignments.entrySet().removeIf(e->e.getValue().equals(code));if(selected.equals(code))selected=ERASE;render();}).show();}
  private void save(){List<String> e=ScheduleEngine.validate(d);if(!e.isEmpty()){new AlertDialog.Builder(this).setTitle("Revisa la configuración").setMessage(String.join("\n",e)).setPositiveButton("OK",null).show();return;}repo.save(d);HorarioWidgetProvider.refreshAll(this);finish();}

  private void section(String a,String b){TextView x=text(a,19,true);x.setPadding(dp(2),dp(22),dp(2),dp(4));root.addView(x);TextView dsc=text(b,13,false);dsc.setTextColor(th.muted);dsc.setPadding(dp(2),0,dp(2),dp(10));root.addView(dsc);}
  private void value(LinearLayout p,String label,String value,View.OnClickListener l){LinearLayout r=row();r.setPadding(0,dp(2),0,dp(2));TextView lab=text(label,15,false);r.addView(lab,new LinearLayout.LayoutParams(0,-2,1));Button b=tonalButton(value);b.setOnClickListener(l);r.addView(b);p.addView(r);}
  private void number(String title,int min,int max,int cur,java.util.function.IntConsumer done){NumberPicker p=new NumberPicker(this);p.setMinValue(min);p.setMaxValue(max);p.setValue(Math.max(min,Math.min(max,cur)));new AlertDialog.Builder(this).setTitle(title).setView(p).setNegativeButton("Cancelar",null).setPositiveButton("Aceptar",(a,b)->done.accept(p.getValue())).show();}
  private void time(LocalTime cur,java.util.function.Consumer<LocalTime> done){new TimePickerDialog(this,(v,h,m)->done.accept(LocalTime.of(h,m)),cur.getHour(),cur.getMinute(),true).show();}
  private String fmt(LocalTime t){return String.format(Locale.US,"%02d:%02d",t.getHour(),t.getMinute());}
  private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
  private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(box(th.surface,th.border,1,18));return c;}
  private void addCard(LinearLayout c){addCardTo(root,c,0,dp(12));}
  private void addCardTo(LinearLayout parent,LinearLayout c,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,top,0,bottom);parent.addView(c,p);}
  private void divider(LinearLayout p){View d=new View(this);d.setBackgroundColor(th.border);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(10),0,dp(10));p.addView(d,lp);}
  private Button tonalButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(th.buttonText);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(14),0,dp(14),0);b.setBackground(box(th.buttonBg,th.border,1,14));return b;}
  private Button primaryButton(String s){Button b=tonalButton(s);b.setTextColor(th.primaryText);b.setTypeface(b.getTypeface(),Typeface.BOLD);b.setBackground(box(th.primary,th.primary,0,14));return b;}
  private Button iconButton(String s){Button b=tonalButton(s);b.setTextSize(22);b.setTextColor(th.danger);b.setPadding(0,0,0,0);b.setBackground(box(th.dangerSoft,th.dangerSoft,0,14));return b;}
  private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setTextSize(14);e.setPadding(dp(12),0,dp(12),0);e.setTextColor(th.ink);e.setHintTextColor(th.muted);e.setBackground(box(th.surfaceAlt,th.border,1,14));return e;}
  private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(th.ink);t.setIncludeFontPadding(false);if(bold)t.setTypeface(t.getTypeface(),Typeface.BOLD);return t;}
  private TextView cell(String s,boolean bold){TextView t=text(s,12,bold);t.setGravity(Gravity.CENTER);t.setTextColor(bold?th.ink:th.muted);t.setBackground(box(th.surfaceAlt,th.border,1,8));return t;}
  private void add(GridLayout g,View v,int w,int h,int span){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(w);p.height=dp(h);if(span>1)p.columnSpec=GridLayout.spec(1,span);p.setMargins(dp(2),dp(2),dp(2),dp(2));p.setGravity(Gravity.FILL);v.setMinimumHeight(dp(h));g.addView(v,p);}
  private GradientDrawable box(int fill,int stroke,int sw,int rad){GradientDrawable g=new GradientDrawable();g.setColor(fill);if(sw>0)g.setStroke(dp(sw),stroke);g.setCornerRadius(dp(rad));return g;}
  private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
  private static final class Start{final int day,session;final String shift;Start(int d,String s,int n){day=d;shift=s;session=n;}}
}

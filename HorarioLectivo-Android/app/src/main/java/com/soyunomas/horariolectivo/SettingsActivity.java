package com.soyunomas.horariolectivo;

import android.app.*;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.*;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class SettingsActivity extends Activity {
  private static final String ERASE="__ERASE__";
  private static final int REQ_EXPORT_JSON=4101,REQ_IMPORT_JSON=4102;
  private static final String[] DAYS={"LUN","MAR","MIÉ","JUE","VIE"};
  private ScheduleRepository repo; private Data d; private LinearLayout root,editor; private String selected=ERASE; private boolean block; private Start start; private AppTheme th;
  private String pendingExportJson; private boolean pendingExportBlank;

  @Override protected void onCreate(Bundle b){super.onCreate(b);repo=new ScheduleRepository(this);d=repo.load().copy();render();}

  private void render(){
    th=new AppTheme(repo.isDarkMode());th.bindSubjects(d.subjects);
    getWindow().setStatusBarColor(th.page);getWindow().setNavigationBarColor(th.page);getWindow().getDecorView().setSystemUiVisibility(th.dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.setBackgroundColor(th.page);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(th.page);sv.addView(root);setContentView(sv);
    root.setPadding(dp(18),dp(18),dp(18),dp(104));root.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(18),dp(18)+i.getSystemWindowInsetTop(),dp(18),dp(104)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();

    LinearLayout top=row();top.setPadding(dp(2),0,dp(2),dp(8));TextView title=text("Configurar horario",26,true);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button save=primaryButton("Guardar");save.setOnClickListener(v->save());top.addView(save);root.addView(top);
    TextView intro=text("Personaliza turnos, asignaturas y semana.",14,false);intro.setTextColor(th.muted);root.addView(intro);

    section("Apariencia","Ajusta el tema para una lectura cómoda.");LinearLayout dc=card();Switch dark=new Switch(this);dark.setText("Modo oscuro");dark.setTextColor(th.ink);dark.setChecked(repo.isDarkMode());dark.setOnCheckedChangeListener((b,on)->{repo.setDarkMode(on);render();});dc.addView(dark);addCard(dc);

    section("Copia de seguridad","Exporta un JSON completo o restaura uno existente. Si aún no hay asignaturas ni casillas, se genera una plantilla IA autocontenida con todas las opciones, restricciones y ejemplos.");
    LinearLayout backup=card();LinearLayout br=row();Button exportJson=tonalButton("Exportar JSON");exportJson.setOnClickListener(v->exportJson());Button importJson=tonalButton("Importar JSON");importJson.setOnClickListener(v->importJson());br.addView(exportJson,new LinearLayout.LayoutParams(0,dp(48),1));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(48),1);ip.setMargins(dp(8),0,0,0);br.addView(importJson,ip);backup.addView(br);TextView bh=text("La importación sustituye la configuración completa después de pedir confirmación.",12,false);bh.setTextColor(th.muted);bh.setPadding(0,dp(8),0,0);backup.addView(bh);addCard(backup);

    section("1 · Franjas horarias","Mañana, tarde y noche son turnos independientes. Los intervalos entre turnos pueden usarse para reuniones, RETA, departamento u otras actividades.");
    LinearLayout duration=card();value(duration,"Duración de sesión",d.sessionMinutes+" min",v->number("Duración",10,180,d.sessionMinutes,n->{d.sessionMinutes=n;render();}));addCard(duration);
    shift(d.morning);betweenConfig(d.between,"Entre mañana y tarde");shift(d.afternoon);betweenConfig(d.betweenNight,"Entre tarde y noche");shift(d.night);

    section("2 · Asignaturas","L = lectiva · C = complementaria. Puedes editar siglas, nombre, tipo y color sin perder las casillas ya asignadas.");subjects();
    section("3 · Semana","Selecciona una asignatura y toca casillas. Recreos e intervalos entre turnos se muestran en ámbar cuando están vacíos y permiten asignar módulos.");editor=new LinearLayout(this);editor.setOrientation(LinearLayout.VERTICAL);root.addView(editor);renderEditor();
  }

  private void shift(ShiftConfig s){
    LinearLayout c=card();CheckBox on=new CheckBox(this);on.setText("Turno de "+s.label.toLowerCase(Locale.ROOT));on.setTextColor(th.ink);on.setChecked(s.enabled);on.setOnCheckedChangeListener((b,x)->{s.enabled=x;render();});c.addView(on);
    if(s.enabled){value(c,"Comienzo",fmt(s.start),v->time(s.start,x->{s.start=x;render();}));divider(c);value(c,"Fin",fmt(s.end),v->time(s.end,x->{s.end=x;render();}));divider(c);value(c,"Recreo después de",s.breakAfterSession==0?"Sin recreo":"Sesión "+s.breakAfterSession,v->number("Posición del recreo",0,12,s.breakAfterSession,n->{s.breakAfterSession=n;render();}));if(s.breakAfterSession>0){divider(c);value(c,"Duración recreo",s.breakMinutes+" min",v->number("Duración recreo",5,90,Math.max(5,s.breakMinutes),n->{s.breakMinutes=n;render();}));}}
    addCard(c);
  }

  private void betweenConfig(ShiftConfig gap,String title){
    LinearLayout c=card();CheckBox on=new CheckBox(this);on.setText(title);on.setTextColor(th.ink);on.setChecked(gap.enabled);on.setOnCheckedChangeListener((b,x)->{gap.enabled=x;render();});c.addView(on);
    TextView hint=text("Se verá como una fila horaria ámbar, similar al recreo, no como una cabecera independiente.",12,false);hint.setTextColor(th.muted);hint.setPadding(0,dp(4),0,0);c.addView(hint);
    if(gap.enabled){divider(c);value(c,"Comienzo",fmt(gap.start),v->time(gap.start,x->{gap.start=x;render();}));divider(c);value(c,"Fin",fmt(gap.end),v->time(gap.end,x->{gap.end=x;render();}));}
    addCard(c);
  }

  private void subjects(){
    LinearLayout list=card();if(d.subjects.isEmpty()){TextView empty=text("Aún no hay asignaturas.",14,false);empty.setTextColor(th.muted);list.addView(empty);}boolean first=true;
    for(Subject s:new ArrayList<>(d.subjects)){
      if(!first)divider(list);first=false;LinearLayout r=row();r.setPadding(0,dp(4),0,dp(4));
      Button color=tonalButton(s.code);color.setTextColor(th.subjectTextColor(s.code));color.setTypeface(color.getTypeface(),Typeface.BOLD);color.setBackground(box(th.subjectColor(s.code),th.subjectColor(s.code),0,14));color.setContentDescription("Cambiar color de "+s.code);color.setOnClickListener(v->chooseColor(s));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(68),dp(44));cp.setMargins(0,0,dp(10),0);r.addView(color,cp);
      LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);TextView name=text(s.name,15,true);meta.addView(name);TextView type=text(s.isComplementaria()?"C":"L",12,true);type.setContentDescription(s.isComplementaria()?"Complementaria":"Lectiva");type.setTextColor(s.isComplementaria()?th.breakBorder:th.muted);meta.addView(type);r.addView(meta,new LinearLayout.LayoutParams(0,-2,1));
      Button edit=tonalButton("Editar");edit.setContentDescription("Editar "+s.code);edit.setOnClickListener(v->editSubject(s));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(76),dp(44));ep.setMargins(dp(8),0,dp(6),0);r.addView(edit,ep);
      Button del=iconButton("×");del.setContentDescription("Eliminar "+s.code);del.setOnClickListener(v->confirmDelete(s.code));r.addView(del,new LinearLayout.LayoutParams(dp(44),dp(44)));list.addView(r);
    }
    addCard(list);Button add=primaryButton("+ Añadir asignatura");add.setOnClickListener(v->editSubject(null));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,dp(50));ap.setMargins(0,0,0,dp(12));root.addView(add,ap);
  }

  private void editSubject(Subject subject){
    boolean creating=subject==null;String oldCode=creating?"":subject.code;LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(20),dp(8),dp(20),0);
    TextView codeLabel=text("Siglas",12,true);codeLabel.setTextColor(th.muted);form.addView(codeLabel);EditText code=input("APW");code.setAllCaps(true);code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});if(!creating)code.setText(subject.code);form.addView(code,new LinearLayout.LayoutParams(-1,dp(52)));
    TextView nameLabel=text("Nombre",12,true);nameLabel.setTextColor(th.muted);nameLabel.setPadding(0,dp(14),0,0);form.addView(nameLabel);EditText name=input("Aplicaciones Web");if(!creating)name.setText(subject.name);form.addView(name,new LinearLayout.LayoutParams(-1,dp(52)));
    TextView typeLabel=text("Tipo · L = lectiva · C = complementaria",12,true);typeLabel.setTextColor(th.muted);typeLabel.setPadding(0,dp(14),0,dp(4));form.addView(typeLabel);RadioGroup types=new RadioGroup(this);types.setOrientation(RadioGroup.HORIZONTAL);RadioButton lect=new RadioButton(this);lect.setId(View.generateViewId());lect.setText("L");lect.setContentDescription("Lectiva");lect.setTextColor(th.ink);RadioButton comp=new RadioButton(this);comp.setId(View.generateViewId());comp.setText("C");comp.setContentDescription("Complementaria");comp.setTextColor(th.ink);types.addView(lect,new RadioGroup.LayoutParams(0,-2,1));types.addView(comp,new RadioGroup.LayoutParams(0,-2,1));if(!creating&&subject.isComplementaria())comp.setChecked(true);else lect.setChecked(true);form.addView(types);
    TextView hint=text("L: clase docente · C: guardia, reunión u otra franja no lectiva.",12,false);hint.setTextColor(th.muted);hint.setPadding(0,dp(6),0,0);form.addView(hint);
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(creating?"Nueva asignatura":"Editar asignatura").setView(form).setNegativeButton("Cancelar",null).setPositiveButton(creating?"Añadir":"Guardar",null).create();
    dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String c=SubjectCode.normalize(code.getText().toString()),n=name.getText().toString().trim();if(!SubjectCode.isValid(c)){code.setError("Usa entre 1 y 3 caracteres válidos");return;}if(n.isEmpty()){name.setError("Escribe un nombre");return;}for(Subject other:d.subjects)if(other!=subject&&other.code.equalsIgnoreCase(c)){code.setError("Estas siglas ya existen");return;}String type=comp.isChecked()?TYPE_COMPLEMENTARIA:TYPE_LECTIVA;if(creating){Subject s=new Subject(c,n,-1,type);d.subjects.add(s);selected=c;}else{d.renameSubjectCode(oldCode,c);subject.code=c;subject.name=n;subject.type=type;if(oldCode.equalsIgnoreCase(selected))selected=c;}dialog.dismiss();render();}));dialog.show();
  }

  private void chooseColor(Subject subject){int initial=th.subjectPaletteIndex(subject.code);final int[]pending={initial};GridLayout g=new GridLayout(this);g.setColumnCount(4);g.setPadding(dp(16),dp(12),dp(16),dp(12));Button[]swatches=new Button[th.paletteSize()];for(int i=0;i<th.paletteSize();i++){final int idx=i;Button sw=new Button(this);swatches[i]=sw;sw.setTextSize(18);sw.setMinHeight(0);sw.setMinimumHeight(0);sw.setPadding(0,0,0,0);boolean unavailable=th.paletteUsedByOther(d.subjects,subject,idx);sw.setEnabled(!unavailable);sw.setAlpha(unavailable?0.30f:1f);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(60);p.height=dp(52);p.setMargins(dp(5),dp(5),dp(5),dp(5));g.addView(sw,p);sw.setOnClickListener(v->{pending[0]=idx;refreshColorSwatches(swatches,pending[0]);});}refreshColorSwatches(swatches,pending[0]);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Color de "+subject.code).setMessage("Elige un color y confirma. Cancelar conserva el anterior.").setView(g).setNegativeButton("Cancelar",null).setPositiveButton("Aceptar",null).create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(th.paletteUsedByOther(d.subjects,subject,pending[0])){Toast.makeText(this,"Ese color ya está usado por otra asignatura",Toast.LENGTH_SHORT).show();return;}subject.colorIndex=pending[0];dialog.dismiss();render();}));dialog.show();}
  private void refreshColorSwatches(Button[]s,int selectedIndex){for(int i=0;i<s.length;i++){boolean sel=i==selectedIndex;s[i].setText(sel?"✓":"");s[i].setTextColor(th.paletteTextColor(i));s[i].setBackground(box(th.paletteColor(i),sel?th.primary:th.paletteColor(i),sel?3:0,16));}}

  private void renderEditor(){
    editor.removeAllViews();LinearLayout tools=card();TextView title=text("Asignatura activa",13,true);title.setTextColor(th.muted);tools.addView(title);HorizontalScrollView ps=new HorizontalScrollView(this);ps.setHorizontalScrollBarEnabled(false);LinearLayout p=row();p.setPadding(0,dp(8),0,dp(4));p.addView(pick("BORRAR",ERASE));for(Subject s:d.subjects)p.addView(pick(s.code,s.code));ps.addView(p);tools.addView(ps);divider(tools);CheckBox bm=new CheckBox(this);bm.setText("Modo bloque: toca inicio y fin");bm.setTextColor(th.ink);bm.setChecked(block);bm.setOnCheckedChangeListener((b,x)->{block=x;start=null;});tools.addView(bm);divider(tools);copyControls(tools);addCardTo(editor,tools,0,dp(12));
    LinearLayout gridCard=card();gridCard.setPadding(dp(8),dp(10),dp(8),dp(10));HorizontalScrollView hs=new HorizontalScrollView(this);hs.setFillViewport(true);hs.setHorizontalScrollBarEnabled(false);GridLayout g=new GridLayout(this);g.setColumnCount(6);g.setUseDefaultMargins(false);String[]h={"HORA","LUN","MAR","MIÉ","JUE","VIE"};int timeW=timeColumnDp(),dayW=dayColumnDp();for(int i=0;i<6;i++)add(g,cell(h[i],true),i==0?timeW:dayW,42,1);editShift(g,d.morning,timeW,dayW);editShift(g,d.between,timeW,dayW);editShift(g,d.afternoon,timeW,dayW);editShift(g,d.betweenNight,timeW,dayW);editShift(g,d.night,timeW,dayW);LinearLayout centered=new LinearLayout(this);centered.setGravity(Gravity.CENTER_HORIZONTAL);centered.addView(g,new LinearLayout.LayoutParams(-2,-2));hs.addView(centered,new HorizontalScrollView.LayoutParams(-1,-2));gridCard.addView(hs);addCardTo(editor,gridCard,0,0);
    Button bottomSave=primaryButton("Guardar");bottomSave.setContentDescription("Guardar configuración");bottomSave.setOnClickListener(v->save());LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(-1,dp(52));saveParams.setMargins(0,dp(16),0,0);editor.addView(bottomSave,saveParams);
  }

  private void editShift(GridLayout g,ShiftConfig s,int timeW,int dayW){
    if(!s.enabled)return;boolean between=isBetweenShift(s.id);
    if(!between){TextView head=cell(s.label,true);head.setTextColor(th.primarySoftText);head.setBackground(box(th.primarySoft,th.primarySoft,0,8));GridLayout.LayoutParams hp=new GridLayout.LayoutParams();hp.columnSpec=GridLayout.spec(0,6);hp.width=dp(tableContentDp(timeW,dayW));hp.height=dp(38);hp.setMargins(dp(1),dp(6),dp(1),dp(2));hp.setGravity(Gravity.FILL);g.addView(head,hp);}
    for(Slot q:ScheduleEngine.generateSlots(d,s)){
      TextView time=cell(String.format(Locale.US,"%02d:%02d\n%02d:%02d",q.start.getHour(),q.start.getMinute(),q.end.getHour(),q.end.getMinute()),between);if(between){time.setTextColor(th.breakBorder);time.setBackground(box(th.breakBg,th.breakBorder,1,8));}add(g,time,timeW,54,1);
      if(q.isBreak){
        for(int day=0;day<5;day++){int dd=day;String c=d.getAssignment(day,s.id,0);TextView x=c.isEmpty()?breakCell():subjectCell(c);x.setContentDescription(c.isEmpty()?"Recreo. Toca para asignar una actividad":c+" durante el recreo");x.setOnClickListener(v->{set(dd,q.shiftId,0);renderEditor();});add(g,x,dayW,54,1);}
      }else if(between){
        for(int day=0;day<5;day++){int dd=day;String c=d.getAssignment(day,s.id,q.sessionIndex);TextView x=c.isEmpty()?betweenCell():subjectCell(c);x.setContentDescription(c.isEmpty()?"Entre turnos. Toca para asignar una actividad":c+" entre turnos");x.setOnClickListener(v->{set(dd,q.shiftId,q.sessionIndex);renderEditor();});add(g,x,dayW,54,1);}
      }else for(int day=0;day<5;day++){int dd=day;String c=d.getAssignment(day,s.id,q.sessionIndex);TextView x=subjectCell(c.isEmpty()?"·":c);x.setOnClickListener(v->assign(dd,q));add(g,x,dayW,54,1);}
    }
  }

  private TextView subjectCell(String code){TextView x=text(code,12,true);x.setGravity(Gravity.CENTER);boolean empty="·".equals(code);x.setTextColor(empty?th.muted:th.subjectTextColor(code));x.setBackground(box(empty?th.surfaceAlt:th.subjectColor(code),empty?th.border:th.subjectColor(code),1,10));return x;}
  private TextView breakCell(){TextView x=text("RECREO",11,true);x.setGravity(Gravity.CENTER);x.setTextColor(th.breakBorder);x.setBackground(box(th.breakBg,th.breakBorder,1,10));return x;}
  private TextView betweenCell(){TextView x=text("ENTRE",11,true);x.setGravity(Gravity.CENTER);x.setTextColor(th.breakBorder);x.setBackground(box(th.breakBg,th.breakBorder,1,10));return x;}
  private void assign(int day,Slot q){if(!block){set(day,q.shiftId,q.sessionIndex);renderEditor();return;}if(start==null){start=new Start(day,q.shiftId,q.sessionIndex);renderEditor();return;}if(start.day!=day||!start.shift.equals(q.shiftId)){start=new Start(day,q.shiftId,q.sessionIndex);renderEditor();return;}for(int i=Math.min(start.session,q.sessionIndex);i<=Math.max(start.session,q.sessionIndex);i++)set(day,q.shiftId,i);start=null;renderEditor();}
  private void set(int day,String shift,int session){d.setAssignment(day,shift,session,ERASE.equals(selected)?"":selected);}

  private void copyControls(LinearLayout target){LinearLayout r=row();Spinner from=new Spinner(this),to=new Spinner(this);from.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,DAYS));String[]targets={"LUN","MAR","MIÉ","JUE","VIE","TODOS"};to.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,targets));r.addView(from,new LinearLayout.LayoutParams(0,dp(48),1));r.addView(to,new LinearLayout.LayoutParams(0,dp(48),1));Button b=tonalButton("Copiar");b.setOnClickListener(v->{int src=from.getSelectedItemPosition();String t=(String)to.getSelectedItem();if("TODOS".equals(t)){for(int day=0;day<5;day++)if(day!=src)copyDay(src,day);}else copyDay(src,Arrays.asList(DAYS).indexOf(t));renderEditor();});r.addView(b);target.addView(r);}
  private void copyDay(int src,int dst){if(dst<0||src==dst)return;for(ShiftConfig s:new ShiftConfig[]{d.morning,d.between,d.afternoon,d.betweenNight,d.night})for(Slot q:ScheduleEngine.generateSlots(d,s))d.setAssignment(dst,s.id,q.sessionIndex,d.getAssignment(src,s.id,q.sessionIndex));}

  private Button pick(String label,String value){Button b=tonalButton(label);boolean on=value.equals(selected);if(ERASE.equals(value)){b.setText(on?"✓ BORRAR":"BORRAR");b.setTextColor(on?th.primaryText:th.buttonText);b.setBackground(box(on?th.primary:th.buttonBg,on?th.primary:th.border,on?3:1,18));b.setContentDescription(on?"Borrar seleccionado":"Borrar");}else{int fill=th.subjectColor(value);b.setText(on?"✓ "+label:label);b.setTextColor(th.subjectTextColor(value));b.setBackground(box(fill,on?th.primary:fill,on?3:0,18));b.setContentDescription((on?"Asignatura seleccionada ":"Seleccionar asignatura ")+label);}b.setOnClickListener(v->{selected=value;start=null;renderEditor();});return b;}
  private void confirmDelete(String code){new AlertDialog.Builder(this).setTitle("Eliminar "+code).setMessage("También se borrará de la semana.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(a,b)->{d.subjects.removeIf(s->s.code.equals(code));d.assignments.entrySet().removeIf(e->e.getValue().equals(code));if(code.equals(selected))selected=ERASE;render();}).show();}
  private void save(){List<String>e=ScheduleEngine.validate(d);if(!e.isEmpty()){new AlertDialog.Builder(this).setTitle("Revisa la configuración").setMessage(String.join("\n",e)).setPositiveButton("OK",null).show();return;}repo.save(d);HorarioWidgetProvider.refreshAll(this);finish();}

  private void exportJson(){
    try{
      pendingExportBlank=d.subjects.isEmpty()&&d.assignments.isEmpty();
      pendingExportJson=repo.exportBackup(d);
      if(pendingExportJson==null||pendingExportJson.trim().isEmpty())throw new IOException("La aplicación generó un JSON vacío y ha cancelado la exportación.");
      byte[] check=pendingExportJson.getBytes(StandardCharsets.UTF_8);
      if(check.length<128)throw new IOException("La plantilla JSON generada es demasiado pequeña ("+check.length+" bytes).");
      Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,pendingExportBlank?"HorarioLectivo_plantilla_IA.json":"HorarioLectivo_backup.json");startActivityForResult(i,REQ_EXPORT_JSON);
    }catch(Exception e){pendingExportJson=null;showBackupError("No se pudo preparar la exportación",e);}
  }
  private void importJson(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/json","text/plain"});startActivityForResult(i,REQ_IMPORT_JSON);}

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
    super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null){if(requestCode==REQ_EXPORT_JSON)pendingExportJson=null;return;}Uri uri=data.getData();
    if(requestCode==REQ_EXPORT_JSON){writeBackup(uri);return;}
    if(requestCode==REQ_IMPORT_JSON)readBackup(uri);
  }

  private void writeBackup(Uri uri){
    String json=pendingExportJson;boolean blank=pendingExportBlank;pendingExportJson=null;
    try{
      if(json==null||json.trim().isEmpty())throw new IOException("No hay contenido JSON preparado para guardar.");
      byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
      try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(uri,"rwt")){
        if(pfd==null)throw new IOException("No se pudo abrir el archivo de destino.");
        try(FileOutputStream out=new FileOutputStream(pfd.getFileDescriptor())){
          out.getChannel().truncate(0);
          out.write(bytes);
          out.flush();
          out.getFD().sync();
        }
      }
      int verified=verifyExportedFile(uri,bytes);
      if(verified<=0)throw new IOException("Android ha creado el archivo, pero sigue teniendo 0 bytes.");
      Toast.makeText(this,(blank?"Plantilla JSON para IA exportada":"Copia JSON exportada")+" · "+verified+" bytes",Toast.LENGTH_LONG).show();
    }catch(Exception e){showBackupError("No se pudo exportar correctamente",e);}
  }

  private int verifyExportedFile(Uri uri,byte[] expected)throws IOException{
    try(InputStream in=getContentResolver().openInputStream(uri)){
      if(in==null)throw new IOException("No se pudo volver a abrir el archivo para verificarlo.");
      ByteArrayOutputStream copy=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
      while((n=in.read(buf))!=-1){copy.write(buf,0,n);if(copy.size()>2*1024*1024)throw new IOException("El archivo exportado supera 2 MB inesperadamente.");}
      byte[] actual=copy.toByteArray();
      if(actual.length==0)throw new IOException("El archivo guardado tiene 0 bytes.");
      if(!Arrays.equals(expected,actual))throw new IOException("El contenido guardado no coincide con el JSON generado.");
      return actual.length;
    }
  }

  private void readBackup(Uri uri){
    try(InputStream in=getContentResolver().openInputStream(uri)){
      if(in==null)throw new IOException("No se pudo abrir el archivo.");
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[]buf=new byte[8192];int n;while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>2*1024*1024)throw new IOException("El JSON supera el límite de 2 MB.");}
      ScheduleRepository.Backup imported=ScheduleRepository.importBackup(bytes.toString(StandardCharsets.UTF_8.name()));
      int subjects=imported.data.subjects.size(),assignments=imported.data.assignments.size();
      new AlertDialog.Builder(this).setTitle("Importar copia JSON").setMessage("Se sustituirá toda la configuración actual.\n\nAsignaturas: "+subjects+"\nAsignaciones: "+assignments).setNegativeButton("Cancelar",null).setPositiveButton("Importar",(a,b)->applyBackup(imported)).show();
    }catch(Exception e){showBackupError("No se pudo importar",e);}
  }

  private void applyBackup(ScheduleRepository.Backup imported){
    d=imported.data.copy();repo.setDarkMode(imported.darkMode);repo.save(d);selected=ERASE;block=false;start=null;HorarioWidgetProvider.refreshAll(this);render();Toast.makeText(this,"Copia JSON importada",Toast.LENGTH_SHORT).show();
  }
  private void showBackupError(String title,Exception e){String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();new AlertDialog.Builder(this).setTitle(title).setMessage(m).setPositiveButton("OK",null).show();}

  private int screenWidthDp(){return Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);}private int timeColumnDp(){int available=Math.max(300,screenWidthDp()-52);return Math.max(64,Math.min(80,Math.round(available*0.22f)));}private int dayColumnDp(){int available=Math.max(300,screenWidthDp()-52);int time=timeColumnDp();return Math.max(44,(available-time-24)/5);}private int tableContentDp(int timeW,int dayW){return timeW+5*dayW+20;}
  private void section(String a,String b){TextView x=text(a,19,true);x.setPadding(dp(2),dp(22),dp(2),dp(4));root.addView(x);TextView dsc=text(b,13,false);dsc.setTextColor(th.muted);dsc.setPadding(dp(2),0,dp(2),dp(10));root.addView(dsc);}private void value(LinearLayout p,String label,String value,View.OnClickListener l){LinearLayout r=row();r.addView(text(label,15,false),new LinearLayout.LayoutParams(0,-2,1));Button b=tonalButton(value);b.setOnClickListener(l);r.addView(b);p.addView(r);}private void number(String title,int min,int max,int cur,java.util.function.IntConsumer done){NumberPicker p=new NumberPicker(this);p.setMinValue(min);p.setMaxValue(max);p.setValue(Math.max(min,Math.min(max,cur)));new AlertDialog.Builder(this).setTitle(title).setView(p).setNegativeButton("Cancelar",null).setPositiveButton("Aceptar",(a,b)->done.accept(p.getValue())).show();}private void time(LocalTime cur,java.util.function.Consumer<LocalTime> done){new TimePickerDialog(this,(v,h,m)->done.accept(LocalTime.of(h,m)),cur.getHour(),cur.getMinute(),true).show();}private String fmt(LocalTime t){return String.format(Locale.US,"%02d:%02d",t.getHour(),t.getMinute());}
  private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(14),dp(16),dp(14));c.setBackground(box(th.surface,th.border,1,18));return c;}private void addCard(LinearLayout c){addCardTo(root,c,0,dp(12));}private void addCardTo(LinearLayout parent,LinearLayout c,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,top,0,bottom);parent.addView(c,p);}private void divider(LinearLayout p){View x=new View(this);x.setBackgroundColor(th.border);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(10),0,dp(10));p.addView(x,lp);}
  private Button tonalButton(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(th.buttonText);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(box(th.buttonBg,th.border,1,14));return b;}private Button primaryButton(String s){Button b=tonalButton(s);b.setTextColor(th.primaryText);b.setTypeface(b.getTypeface(),Typeface.BOLD);b.setBackground(box(th.primary,th.primary,0,14));return b;}private Button iconButton(String s){Button b=tonalButton(s);b.setTextSize(22);b.setTextColor(th.danger);b.setBackground(box(th.dangerSoft,th.dangerSoft,0,14));return b;}private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine();e.setTextColor(th.ink);e.setHintTextColor(th.muted);e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(th.surfaceAlt,th.border,1,14));return e;}private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(th.ink);t.setIncludeFontPadding(false);if(bold)t.setTypeface(t.getTypeface(),Typeface.BOLD);return t;}private TextView cell(String s,boolean bold){TextView t=text(s,12,bold);t.setGravity(Gravity.CENTER);t.setBackground(box(th.surfaceAlt,th.border,1,8));return t;}private void add(GridLayout g,View v,int w,int h,int span){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=dp(w);p.height=dp(h);if(span>1)p.columnSpec=GridLayout.spec(1,span);p.setMargins(dp(2),dp(2),dp(2),dp(2));p.setGravity(Gravity.FILL);g.addView(v,p);}private GradientDrawable box(int fill,int stroke,int sw,int rad){GradientDrawable g=new GradientDrawable();g.setColor(fill);if(sw>0)g.setStroke(dp(sw),stroke);g.setCornerRadius(dp(rad));return g;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private static final class Start{final int day,session;final String shift;Start(int d,String s,int n){day=d;shift=s;session=n;}}
}

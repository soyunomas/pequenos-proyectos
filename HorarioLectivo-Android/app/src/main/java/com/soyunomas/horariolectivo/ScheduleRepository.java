package com.soyunomas.horariolectivo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class ScheduleRepository {
    public static final String BACKUP_FORMAT="horario-lectivo-backup";
    public static final int BACKUP_SCHEMA_VERSION=1;
    private static final String PREFS="horario_lectivo_prefs",KEY_DATA="schedule_json",KEY_INITIALIZED="initialized",KEY_DARK="dark_mode";
    private static final String[] BACKUP_DAYS={"LUN","MAR","MIE","JUE","VIE"};
    private final SharedPreferences prefs;

    public static final class Backup {
        public final Data data;
        public final boolean darkMode;
        Backup(Data data,boolean darkMode){this.data=data;this.darkMode=darkMode;}
    }

    public ScheduleRepository(Context context){prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public boolean isInitialized(){return prefs.getBoolean(KEY_INITIALIZED,false);}
    public boolean isDarkMode(){return prefs.getBoolean(KEY_DARK,false);}
    public void setDarkMode(boolean dark){prefs.edit().putBoolean(KEY_DARK,dark).apply();}

    public Data load(){
        String raw=prefs.getString(KEY_DATA,null);
        if(raw==null||raw.trim().isEmpty())return new Data();
        try{
            JSONObject root=new JSONObject(raw);Data d=new Data();
            d.sessionMinutes=root.optInt("sessionMinutes",55);
            d.morning=readShift(root.optJSONObject("morning"),d.morning);
            d.between=readShift(root.optJSONObject("between"),d.between);
            d.afternoon=readShift(root.optJSONObject("afternoon"),d.afternoon);
            d.betweenNight=readShift(root.optJSONObject("betweenNight"),d.betweenNight);
            d.night=readShift(root.optJSONObject("night"),d.night);
            d.subjects.clear();
            JSONArray a=root.optJSONArray("subjects");
            if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){String c=o.optString("code","").trim().toUpperCase(),n=o.optString("name","").trim();String type=o.optString("type",TYPE_LECTIVA).trim().toUpperCase();if(!c.isEmpty())d.subjects.add(new Subject(c,n.isEmpty()?c:n,o.optInt("colorIndex",-1),type));}}
            d.assignments.clear();JSONObject as=root.optJSONObject("assignments");if(as!=null){Iterator<String>keys=as.keys();while(keys.hasNext()){String k=keys.next(),v=as.optString(k,"").trim().toUpperCase();if(!v.isEmpty())d.assignments.put(k,v);}}
            return d;
        }catch(Exception ignored){return new Data();}
    }

    public void save(Data d){
        try{
            JSONObject root=new JSONObject();root.put("sessionMinutes",d.sessionMinutes);root.put("morning",writeShift(d.morning));root.put("between",writeShift(d.between));root.put("afternoon",writeShift(d.afternoon));root.put("betweenNight",writeShift(d.betweenNight));root.put("night",writeShift(d.night));
            JSONArray a=new JSONArray();for(Subject s:d.subjects){JSONObject o=new JSONObject();o.put("code",s.code);o.put("name",s.name);o.put("colorIndex",s.colorIndex);o.put("type",s.type);a.put(o);}root.put("subjects",a);
            JSONObject as=new JSONObject();for(String k:d.assignments.keySet())as.put(k,d.assignments.get(k));root.put("assignments",as);
            prefs.edit().putString(KEY_DATA,root.toString()).putBoolean(KEY_INITIALIZED,true).apply();
        }catch(Exception e){throw new IllegalStateException("No se pudo guardar el horario",e);}
    }

    public String exportBackup(Data data){return exportBackup(data,isDarkMode());}

    public static String exportBackup(Data source,boolean darkMode){
        try{
            Data d=source==null?new Data():source;
            JSONObject root=new JSONObject();
            root.put("format",BACKUP_FORMAT);
            root.put("schemaVersion",BACKUP_SCHEMA_VERSION);
            root.put("contentState",d.subjects.isEmpty()&&d.assignments.isEmpty()?"BLANK_TEMPLATE":"BACKUP");
            root.put("schema",backupSchema());

            JSONObject appearance=new JSONObject();
            appearance.put("darkMode",darkMode);
            root.put("appearance",appearance);
            root.put("sessionMinutes",d.sessionMinutes);

            JSONObject shifts=new JSONObject();
            shifts.put("morning",writeBackupShift(d.morning));
            shifts.put("betweenMorningAfternoon",writeBackupShift(d.between));
            shifts.put("afternoon",writeBackupShift(d.afternoon));
            shifts.put("betweenAfternoonNight",writeBackupShift(d.betweenNight));
            shifts.put("night",writeBackupShift(d.night));
            root.put("shifts",shifts);

            JSONArray subjects=new JSONArray();
            for(Subject s:d.subjects){
                JSONObject o=new JSONObject();
                o.put("code",s.code);o.put("name",s.name);o.put("type",s.type);o.put("colorIndex",s.colorIndex);
                subjects.put(o);
            }
            root.put("subjects",subjects);

            JSONArray assignments=new JSONArray();
            List<String> keys=new ArrayList<>(d.assignments.keySet());
            keys.sort(String::compareTo);
            for(String key:keys){
                String[] p=key.split("\\|");
                if(p.length!=3)continue;
                int day=Integer.parseInt(p[0]),session=Integer.parseInt(p[2]);
                String shiftId=p[1],subject=d.assignments.get(key);
                JSONObject o=new JSONObject();
                o.put("day",dayName(day));
                o.put("shift",externalShiftKey(shiftId));
                o.put("session",session);
                Slot slot=findSlot(d,shiftId,session,true);
                if(slot!=null)o.put("start",slot.start.toString());
                o.put("subject",subject);
                assignments.put(o);
            }
            root.put("assignments",assignments);
            return root.toString(2);
        }catch(Exception e){throw new IllegalStateException("No se pudo generar la copia JSON",e);}
    }

    public static Backup importBackup(String raw){
        try{
            if(raw==null||raw.trim().isEmpty())throw new IllegalArgumentException("El archivo JSON está vacío.");
            JSONObject root=new JSONObject(raw);
            if(!BACKUP_FORMAT.equals(root.optString("format","")))throw new IllegalArgumentException("El campo format debe ser "+BACKUP_FORMAT+".");
            int version=root.optInt("schemaVersion",-1);
            if(version!=BACKUP_SCHEMA_VERSION)throw new IllegalArgumentException("Versión de esquema no compatible: "+version+".");

            Data d=new Data();
            if(!root.has("sessionMinutes"))throw new IllegalArgumentException("Falta sessionMinutes.");
            d.sessionMinutes=root.getInt("sessionMinutes");
            if(d.sessionMinutes<10||d.sessionMinutes>180)throw new IllegalArgumentException("sessionMinutes debe estar entre 10 y 180.");

            JSONObject shifts=root.optJSONObject("shifts");
            if(shifts==null)throw new IllegalArgumentException("Falta el objeto shifts.");
            d.morning=readBackupShift(shifts.optJSONObject("morning"),d.morning,"shifts.morning");
            d.between=readBackupShift(shifts.optJSONObject("betweenMorningAfternoon"),d.between,"shifts.betweenMorningAfternoon");
            d.afternoon=readBackupShift(shifts.optJSONObject("afternoon"),d.afternoon,"shifts.afternoon");
            d.betweenNight=readBackupShift(shifts.optJSONObject("betweenAfternoonNight"),d.betweenNight,"shifts.betweenAfternoonNight");
            d.night=readBackupShift(shifts.optJSONObject("night"),d.night,"shifts.night");

            JSONArray subjects=root.optJSONArray("subjects");
            if(subjects==null)throw new IllegalArgumentException("Falta el array subjects.");
            d.subjects.clear();
            Set<String> subjectCodes=new HashSet<>();
            for(int i=0;i<subjects.length();i++){
                JSONObject o=subjects.optJSONObject(i);
                if(o==null)throw new IllegalArgumentException("subjects["+i+"] debe ser un objeto.");
                String code=SubjectCode.normalize(o.optString("code",""));
                if(!SubjectCode.isValid(code))throw new IllegalArgumentException("Código de asignatura no válido en subjects["+i+"].");
                if(!subjectCodes.add(code))throw new IllegalArgumentException("Código de asignatura duplicado: "+code+".");
                String name=o.optString("name","").trim();if(name.isEmpty())throw new IllegalArgumentException("El nombre de "+code+" no puede estar vacío.");
                String type=o.optString("type",TYPE_LECTIVA).trim().toUpperCase(Locale.ROOT);
                if(!TYPE_LECTIVA.equals(type)&&!TYPE_COMPLEMENTARIA.equals(type))throw new IllegalArgumentException("Tipo no válido para "+code+": "+type+".");
                int color=o.optInt("colorIndex",-1);
                if(color<-1||color>23)throw new IllegalArgumentException("colorIndex debe ser -1 o estar entre 0 y 23 para "+code+".");
                d.subjects.add(new Subject(code,name,color,type));
            }

            JSONArray assignments=root.optJSONArray("assignments");
            if(assignments==null)throw new IllegalArgumentException("Falta el array assignments.");
            d.assignments.clear();
            Set<String> assignmentKeys=new HashSet<>();
            for(int i=0;i<assignments.length();i++){
                JSONObject o=assignments.optJSONObject(i);
                if(o==null)throw new IllegalArgumentException("assignments["+i+"] debe ser un objeto.");
                int day=parseDay(o.optString("day",""));
                String shiftKey=o.optString("shift","").trim();
                ShiftConfig shift=shiftByExternalKey(d,shiftKey);
                if(shift==null)throw new IllegalArgumentException("Turno no válido en assignments["+i+"]: "+shiftKey+".");
                String subject=SubjectCode.normalize(o.optString("subject",""));
                if(!subjectCodes.contains(subject))throw new IllegalArgumentException("La asignación "+(i+1)+" usa una asignatura no declarada: "+subject+".");
                int session;
                if(o.has("session")){
                    session=o.getInt("session");
                    if(session<0||session>99)throw new IllegalArgumentException("session fuera de rango en assignments["+i+"].");
                }else{
                    String start=o.optString("start","").trim();
                    if(start.isEmpty())throw new IllegalArgumentException("assignments["+i+"] necesita session o start.");
                    LocalTime startTime=LocalTime.parse(start);
                    Slot slot=findSlot(d,shift.id,-1,true,startTime);
                    if(slot==null)throw new IllegalArgumentException("No existe una franja que empiece a "+start+" en "+shiftKey+".");
                    session=slot.sessionIndex;
                }
                Slot target=findSlot(d,shift.id,session,true);
                if(target==null)throw new IllegalArgumentException("No existe la sesión "+session+" en "+shiftKey+" con la configuración indicada.");
                if(o.has("start")){String stated=o.optString("start","").trim();if(!stated.isEmpty()&&!target.start.equals(LocalTime.parse(stated)))throw new IllegalArgumentException("La hora start no coincide con la sesión "+session+" en assignments["+i+"].");}
                String key=Data.assignmentKey(day,shift.id,session);
                if(!assignmentKeys.add(key))throw new IllegalArgumentException("Asignación duplicada para "+o.optString("day","")+" / "+shiftKey+" / sesión "+session+".");
                d.setAssignment(day,shift.id,session,subject);
            }

            List<String> errors=ScheduleEngine.validate(d);
            if(!errors.isEmpty())throw new IllegalArgumentException(String.join("\n",errors));
            JSONObject appearance=root.optJSONObject("appearance");
            boolean dark=appearance!=null&&appearance.optBoolean("darkMode",false);
            return new Backup(d,dark);
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("JSON no válido: "+safeMessage(e),e);}
    }

    private static JSONObject backupSchema()throws Exception{
        JSONObject s=new JSONObject();
        s.put("purpose","Plantilla y copia de seguridad importable por Horario Lectivo. Si subjects y assignments están vacíos, rellénalos a partir de la imagen del horario sin eliminar el resto de campos.");

        JSONArray ai=new JSONArray();
        ai.put("Analiza la imagen del horario y conserva format, schemaVersion, appearance, sessionMinutes, shifts y la estructura general.");
        ai.put("Crea primero subjects y después assignments. Todas las asignaturas usadas en assignments deben existir previamente en subjects.");
        ai.put("subject.code es obligatorio, único y debe tener entre 1 y 3 caracteres, solo letras A-Z mayúsculas y dígitos 0-9. Ejemplos válidos: APW, BD, 2A.");
        ai.put("subject.name es obligatorio y no puede estar vacío.");
        ai.put("subject.type solo puede ser LECTIVA o COMPLEMENTARIA.");
        ai.put("Para colorIndex usa -1 si no quieres elegir un color concreto; los valores explícitos válidos son 0..23.");
        ai.put("Para assignments es preferible usar start con formato HH:mm y omitir session si la foto muestra las horas. La app calculará la sesión correspondiente.");
        ai.put("Para una actividad durante un recreo usa el shift del turno al que pertenece el recreo y su hora start. Si usas session explícitamente, el recreo es session=0.");
        ai.put("Para actividades entre turnos usa shift=betweenMorningAfternoon o shift=betweenAfternoonNight.");
        ai.put("No inventes días, tipos ni claves de turno. No dupliques dos assignments para la misma casilla.");
        ai.put("Devuelve únicamente JSON válido, sin Markdown, sin comentarios // o /* */ y sin texto fuera del JSON.");
        s.put("aiInstructions",ai);

        JSONObject fields=new JSONObject();
        fields.put("appearance.darkMode",field("boolean","true o false."));
        fields.put("sessionMinutes",numberField(10,180,"Duración común de una sesión, en minutos."));
        fields.put("shifts.*.enabled",field("boolean","Activa o desactiva esa franja."));
        fields.put("shifts.*.start",field("string HH:mm","Hora de inicio en formato de 24 horas, por ejemplo 08:00."));
        fields.put("shifts.*.end",field("string HH:mm","Hora de fin en formato de 24 horas y posterior a start."));
        fields.put("shifts.*.breakAfterSession",numberField(0,12,"0 = sin recreo; N = recreo después de la sesión N. En intervalos entre turnos debe ser 0."));
        fields.put("shifts.*.breakMinutes",numberField(0,90,"0 si no hay recreo; si hay recreo, entre 5 y 90. En intervalos entre turnos debe ser 0."));

        JSONObject code=field("string","Siglas que se muestran en el horario y widget.");
        code.put("required",true);code.put("minLength",1);code.put("maxLength",3);code.put("pattern","^[A-Z0-9]{1,3}$");code.put("unique",true);
        fields.put("subjects[].code",code);
        JSONObject name=field("string","Nombre completo de la asignatura o actividad.");name.put("required",true);name.put("minLength",1);fields.put("subjects[].name",name);
        JSONObject type=field("string","Clasificación de la asignatura.");JSONArray typeValues=new JSONArray();typeValues.put(TYPE_LECTIVA);typeValues.put(TYPE_COMPLEMENTARIA);type.put("enum",typeValues);fields.put("subjects[].type",type);
        JSONObject color=numberField(-1,23,"-1 = color automático; 0..23 = índice de la paleta.");fields.put("subjects[].colorIndex",color);

        JSONObject day=field("string","Día laborable.");JSONArray days=new JSONArray();for(String d:BACKUP_DAYS)days.put(d);day.put("enum",days);fields.put("assignments[].day",day);
        JSONObject shift=field("string","Franja en la que se encuentra la actividad.");JSONArray shifts=new JSONArray();shifts.put("morning");shifts.put("betweenMorningAfternoon");shifts.put("afternoon");shifts.put("betweenAfternoonNight");shifts.put("night");shift.put("enum",shifts);fields.put("assignments[].shift",shift);
        fields.put("assignments[].subject",field("string","Debe coincidir exactamente con un subjects[].code declarado."));
        JSONObject start=field("string HH:mm","Hora inicial de la casilla. Recomendado para generar el JSON desde una imagen.");start.put("optionalIf","session está presente");fields.put("assignments[].start",start);
        JSONObject session=numberField(0,99,"Índice de sesión. 0 identifica un recreo. Puede omitirse si se proporciona start.");session.put("optionalIf","start está presente");fields.put("assignments[].session",session);
        s.put("fields",fields);

        JSONArray rules=new JSONArray();
        rules.put("Todos los objetos de shifts deben estar presentes aunque enabled sea false.");
        rules.put("Las horas usan formato HH:mm de 24 horas y cada start debe ser anterior a end.");
        rules.put("sessionMinutes debe estar entre 10 y 180.");
        rules.put("breakAfterSession debe estar entre 0 y 12. Si es mayor que 0, breakMinutes debe estar entre 5 y 90.");
        rules.put("Los intervalos betweenMorningAfternoon y betweenAfternoonNight no tienen recreo: breakAfterSession=0 y breakMinutes=0.");
        rules.put("No puede haber dos asignaturas con el mismo code.");
        rules.put("Cada assignment debe apuntar a una asignatura declarada en subjects.");
        rules.put("Cada assignment debe corresponder a una casilla real generada por la configuración de shifts y sessionMinutes.");
        rules.put("Una misma casilla day+shift+session solo puede tener una asignación.");
        rules.put("Los intervalos entre turnos no deben solaparse con los turnos adyacentes que estén enabled.");
        s.put("rules",rules);

        JSONObject examples=new JSONObject();
        JSONObject subjectExample=new JSONObject();subjectExample.put("code","APW");subjectExample.put("name","Aplicaciones Web");subjectExample.put("type",TYPE_LECTIVA);subjectExample.put("colorIndex",-1);examples.put("subject",subjectExample);
        JSONObject assignmentExample=new JSONObject();assignmentExample.put("day","LUN");assignmentExample.put("shift","morning");assignmentExample.put("start","08:00");assignmentExample.put("subject","APW");examples.put("assignmentByStart",assignmentExample);
        JSONObject breakExample=new JSONObject();breakExample.put("day","MAR");breakExample.put("shift","morning");breakExample.put("start","10:45");breakExample.put("subject","RET");examples.put("assignmentDuringRecess",breakExample);
        JSONObject betweenExample=new JSONObject();betweenExample.put("day","JUE");betweenExample.put("shift","betweenMorningAfternoon");betweenExample.put("start","14:00");betweenExample.put("subject","DEP");examples.put("assignmentBetweenTurns",betweenExample);
        s.put("examples",examples);
        return s;
    }

    private static JSONObject field(String type,String description)throws Exception{
        JSONObject o=new JSONObject();o.put("type",type);o.put("description",description);return o;
    }

    private static JSONObject numberField(int min,int max,String description)throws Exception{
        JSONObject o=field("integer",description);o.put("min",min);o.put("max",max);return o;
    }

    private static JSONObject writeBackupShift(ShiftConfig s)throws Exception{
        JSONObject o=new JSONObject();o.put("enabled",s.enabled);o.put("start",s.start.toString());o.put("end",s.end.toString());o.put("breakAfterSession",s.breakAfterSession);o.put("breakMinutes",s.breakMinutes);return o;
    }

    private static ShiftConfig readBackupShift(JSONObject o,ShiftConfig fallback,String path)throws Exception{
        if(o==null)throw new IllegalArgumentException("Falta "+path+".");
        for(String field:new String[]{"enabled","start","end","breakAfterSession","breakMinutes"})if(!o.has(field))throw new IllegalArgumentException("Falta "+path+"."+field+".");
        int breakAfter=o.getInt("breakAfterSession"),breakMinutes=o.getInt("breakMinutes");
        if(breakAfter<0||breakAfter>12)throw new IllegalArgumentException(path+".breakAfterSession debe estar entre 0 y 12.");
        if(breakMinutes<0||breakMinutes>90)throw new IllegalArgumentException(path+".breakMinutes debe estar entre 0 y 90.");
        if(breakAfter>0&&breakMinutes<5)throw new IllegalArgumentException(path+".breakMinutes debe estar entre 5 y 90 cuando hay recreo.");
        if(isBetweenShift(fallback.id)&&(breakAfter!=0||breakMinutes!=0))throw new IllegalArgumentException(path+" es un intervalo entre turnos y debe tener breakAfterSession=0 y breakMinutes=0.");
        return new ShiftConfig(fallback.id,fallback.label,o.getBoolean("enabled"),LocalTime.parse(o.getString("start")),LocalTime.parse(o.getString("end")),breakAfter,breakMinutes);
    }

    private static ShiftConfig shiftByExternalKey(Data d,String raw){
        String key=raw==null?"":raw.trim();
        if("morning".equalsIgnoreCase(key)||MORNING.equalsIgnoreCase(key))return d.morning;
        if("betweenMorningAfternoon".equalsIgnoreCase(key)||BETWEEN.equalsIgnoreCase(key))return d.between;
        if("afternoon".equalsIgnoreCase(key)||AFTERNOON.equalsIgnoreCase(key))return d.afternoon;
        if("betweenAfternoonNight".equalsIgnoreCase(key)||BETWEEN_NIGHT.equalsIgnoreCase(key))return d.betweenNight;
        if("night".equalsIgnoreCase(key)||NIGHT.equalsIgnoreCase(key))return d.night;
        return null;
    }

    private static String externalShiftKey(String id){
        if(MORNING.equals(id))return "morning";
        if(BETWEEN.equals(id))return "betweenMorningAfternoon";
        if(AFTERNOON.equals(id))return "afternoon";
        if(BETWEEN_NIGHT.equals(id))return "betweenAfternoonNight";
        if(NIGHT.equals(id))return "night";
        return id;
    }

    private static int parseDay(String raw){
        String v=raw==null?"":raw.trim().toUpperCase(Locale.ROOT).replace('É','E');
        for(int i=0;i<BACKUP_DAYS.length;i++)if(BACKUP_DAYS[i].equals(v))return i;
        throw new IllegalArgumentException("Día no válido: "+raw+". Usa LUN, MAR, MIE, JUE o VIE.");
    }

    private static String dayName(int day){return day>=0&&day<BACKUP_DAYS.length?BACKUP_DAYS[day]:String.valueOf(day);}

    private static Slot findSlot(Data d,String shiftId,int session,boolean includeDisabled){return findSlot(d,shiftId,session,includeDisabled,null);}

    private static Slot findSlot(Data d,String shiftId,int session,boolean includeDisabled,LocalTime start){
        ShiftConfig source=shiftByExternalKey(d,shiftId);
        if(source==null)return null;
        ShiftConfig shift=source.copy();if(includeDisabled)shift.enabled=true;
        for(Slot slot:ScheduleEngine.generateSlots(d,shift)){
            if(start!=null&&slot.start.equals(start))return slot;
            if(start==null&&slot.sessionIndex==session){
                if(session!=0||slot.isBreak)return slot;
            }
        }
        return null;
    }

    private static String safeMessage(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?e.getClass().getSimpleName():m;}

    private static JSONObject writeShift(ShiftConfig s)throws Exception{JSONObject o=new JSONObject();o.put("id",s.id);o.put("label",s.label);o.put("enabled",s.enabled);o.put("start",s.start.toString());o.put("end",s.end.toString());o.put("breakAfterSession",s.breakAfterSession);o.put("breakMinutes",s.breakMinutes);return o;}
    private static ShiftConfig readShift(JSONObject o,ShiftConfig f){if(o==null)return f;try{return new ShiftConfig(o.optString("id",f.id),o.optString("label",f.label),o.optBoolean("enabled",f.enabled),LocalTime.parse(o.optString("start",f.start.toString())),LocalTime.parse(o.optString("end",f.end.toString())),o.optInt("breakAfterSession",f.breakAfterSession),o.optInt("breakMinutes",f.breakMinutes));}catch(Exception ignored){return f;}}
}

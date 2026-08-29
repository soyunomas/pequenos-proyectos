package com.soyunomas.horariolectivo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalTime;
import java.util.Iterator;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public final class ScheduleRepository {
    private static final String PREFS="horario_lectivo_prefs",KEY_DATA="schedule_json",KEY_INITIALIZED="initialized",KEY_DARK="dark_mode";
    private final SharedPreferences prefs;
    public ScheduleRepository(Context context){prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public boolean isInitialized(){return prefs.getBoolean(KEY_INITIALIZED,false);}
    public boolean isDarkMode(){return prefs.getBoolean(KEY_DARK,false);}
    public void setDarkMode(boolean dark){prefs.edit().putBoolean(KEY_DARK,dark).apply();}
    public Data load(){String raw=prefs.getString(KEY_DATA,null);if(raw==null||raw.trim().isEmpty())return new Data();try{JSONObject root=new JSONObject(raw);Data d=new Data();d.sessionMinutes=root.optInt("sessionMinutes",55);d.morning=readShift(root.optJSONObject("morning"),d.morning);d.afternoon=readShift(root.optJSONObject("afternoon"),d.afternoon);d.subjects.clear();JSONArray a=root.optJSONArray("subjects");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){String c=o.optString("code","").trim().toUpperCase(),n=o.optString("name","").trim();if(!c.isEmpty())d.subjects.add(new Subject(c,n.isEmpty()?c:n));}}d.assignments.clear();JSONObject as=root.optJSONObject("assignments");if(as!=null){Iterator<String>keys=as.keys();while(keys.hasNext()){String k=keys.next(),v=as.optString(k,"").trim().toUpperCase();if(!v.isEmpty())d.assignments.put(k,v);}}return d;}catch(Exception ignored){return new Data();}}
    public void save(Data d){try{JSONObject root=new JSONObject();root.put("sessionMinutes",d.sessionMinutes);root.put("morning",writeShift(d.morning));root.put("afternoon",writeShift(d.afternoon));JSONArray a=new JSONArray();for(Subject s:d.subjects){JSONObject o=new JSONObject();o.put("code",s.code);o.put("name",s.name);a.put(o);}root.put("subjects",a);JSONObject as=new JSONObject();for(String k:d.assignments.keySet())as.put(k,d.assignments.get(k));root.put("assignments",as);prefs.edit().putString(KEY_DATA,root.toString()).putBoolean(KEY_INITIALIZED,true).apply();}catch(Exception e){throw new IllegalStateException("No se pudo guardar el horario",e);}}
    private static JSONObject writeShift(ShiftConfig s)throws Exception{JSONObject o=new JSONObject();o.put("id",s.id);o.put("label",s.label);o.put("enabled",s.enabled);o.put("start",s.start.toString());o.put("end",s.end.toString());o.put("breakAfterSession",s.breakAfterSession);o.put("breakMinutes",s.breakMinutes);return o;}
    private static ShiftConfig readShift(JSONObject o,ShiftConfig f){if(o==null)return f;try{return new ShiftConfig(o.optString("id",f.id),o.optString("label",f.label),o.optBoolean("enabled",f.enabled),LocalTime.parse(o.optString("start",f.start.toString())),LocalTime.parse(o.optString("end",f.end.toString())),o.optInt("breakAfterSession",f.breakAfterSession),o.optInt("breakMinutes",f.breakMinutes));}catch(Exception ignored){return f;}}
}

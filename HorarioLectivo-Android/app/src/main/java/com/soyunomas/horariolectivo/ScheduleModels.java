package com.soyunomas.horariolectivo;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScheduleModels {
    private ScheduleModels() {}
    public static final String MORNING="M", AFTERNOON="T";
    public static final String TYPE_LECTIVA="LECTIVA", TYPE_COMPLEMENTARIA="COMPLEMENTARIA";

    public static final class ShiftConfig {
        public String id,label; public boolean enabled; public LocalTime start,end; public int breakAfterSession,breakMinutes;
        public ShiftConfig(String id,String label,boolean enabled,LocalTime start,LocalTime end,int breakAfterSession,int breakMinutes){this.id=id;this.label=label;this.enabled=enabled;this.start=start;this.end=end;this.breakAfterSession=breakAfterSession;this.breakMinutes=breakMinutes;}
        public ShiftConfig copy(){return new ShiftConfig(id,label,enabled,start,end,breakAfterSession,breakMinutes);}
    }

    public static final class Subject {
        public String code,name,type; public int colorIndex;
        public Subject(String code,String name){this(code,name,-1,TYPE_LECTIVA);}
        public Subject(String code,String name,int colorIndex){this(code,name,colorIndex,TYPE_LECTIVA);}
        public Subject(String code,String name,int colorIndex,String type){this.code=code;this.name=name;this.colorIndex=colorIndex;this.type=TYPE_COMPLEMENTARIA.equals(type)?TYPE_COMPLEMENTARIA:TYPE_LECTIVA;}
        public boolean isLectiva(){return !TYPE_COMPLEMENTARIA.equals(type);}
        public boolean isComplementaria(){return TYPE_COMPLEMENTARIA.equals(type);}
        public Subject copy(){return new Subject(code,name,colorIndex,type);}
    }

    public static final class Data {
        public int sessionMinutes=55;
        public ShiftConfig morning=new ShiftConfig(MORNING,"MAÑANA",true,LocalTime.of(8,0),LocalTime.of(14,0),3,30);
        public ShiftConfig afternoon=new ShiftConfig(AFTERNOON,"TARDE",false,LocalTime.of(15,0),LocalTime.of(21,0),3,30);
        public final List<Subject> subjects=new ArrayList<>(); public final Map<String,String> assignments=new HashMap<>();
        public Data copy(){Data d=new Data();d.sessionMinutes=sessionMinutes;d.morning=morning.copy();d.afternoon=afternoon.copy();d.subjects.clear();for(Subject s:subjects)d.subjects.add(s.copy());d.assignments.clear();d.assignments.putAll(assignments);return d;}
        public static String assignmentKey(int day,String shift,int session){return day+"|"+shift+"|"+session;}
        public String getAssignment(int day,String shift,int session){String v=assignments.get(assignmentKey(day,shift,session));return v==null?"":v;}
        public void setAssignment(int day,String shift,int session,String code){String k=assignmentKey(day,shift,session);if(code==null||code.trim().isEmpty())assignments.remove(k);else assignments.put(k,code.trim().toUpperCase());}
        public String subjectName(String code){Subject s=subject(code);return s==null?(code==null?"":code):s.name;}
        public Subject subject(String code){if(code==null||code.isEmpty())return null;for(Subject s:subjects)if(s.code.equalsIgnoreCase(code))return s;return null;}
        public boolean isLectiva(String code){Subject s=subject(code);return s!=null&&s.isLectiva();}
        public boolean isComplementaria(String code){Subject s=subject(code);return s!=null&&s.isComplementaria();}
        public void renameSubjectCode(String oldCode,String newCode){
            if(oldCode==null||newCode==null||oldCode.equalsIgnoreCase(newCode))return;
            String oldUpper=oldCode.trim().toUpperCase(),newUpper=newCode.trim().toUpperCase();
            for(Map.Entry<String,String> e:assignments.entrySet())if(oldUpper.equalsIgnoreCase(e.getValue()))e.setValue(newUpper);
        }
    }

    public static final class Slot {public final String shiftId,shiftLabel;public final LocalTime start,end;public final boolean isBreak;public final int sessionIndex;public Slot(String shiftId,String shiftLabel,LocalTime start,LocalTime end,boolean isBreak,int sessionIndex){this.shiftId=shiftId;this.shiftLabel=shiftLabel;this.start=start;this.end=end;this.isBreak=isBreak;this.sessionIndex=sessionIndex;}public String identity(){return shiftId+":"+(isBreak?"B":"S"+sessionIndex);}}
    public static final class SlotRef {public final int dayIndex;public final Slot slot;public final String code;public SlotRef(int dayIndex,Slot slot,String code){this.dayIndex=dayIndex;this.slot=slot;this.code=code==null?"":code;}}
    public static final class NowNext {public final SlotRef current,next;public NowNext(SlotRef current,SlotRef next){this.current=current;this.next=next;}}
}

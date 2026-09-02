package com.soyunomas.horariolectivo;

import static com.soyunomas.horariolectivo.ScheduleModels.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ScheduleEngine {
    private ScheduleEngine() {}

    public static List<Slot> generateSlots(Data data, ShiftConfig shift) {
        List<Slot> out=new ArrayList<>();
        if(data==null||shift==null||!shift.enabled||data.sessionMinutes<=0||shift.start==null||shift.end==null||!shift.start.isBefore(shift.end))return out;
        if(isBetweenShift(shift.id)){out.add(new Slot(shift.id,shift.label,shift.start,shift.end,false,1));return out;}
        LocalTime cursor=shift.start; int session=0;
        while(true){
            LocalTime sessionEnd=cursor.plusMinutes(data.sessionMinutes);
            if(sessionEnd.isAfter(shift.end)||sessionEnd.equals(cursor))break;
            session++; out.add(new Slot(shift.id,shift.label,cursor,sessionEnd,false,session)); cursor=sessionEnd;
            if(shift.breakAfterSession==session&&shift.breakMinutes>0){LocalTime breakEnd=cursor.plusMinutes(shift.breakMinutes);if(!breakEnd.isAfter(shift.end)&&!breakEnd.equals(cursor)){out.add(new Slot(shift.id,shift.label,cursor,breakEnd,true,0));cursor=breakEnd;}}
        }
        return out;
    }

    public static List<Slot> generateAllSlots(Data data){
        List<Slot> all=new ArrayList<>();
        all.addAll(generateSlots(data,data.morning));
        all.addAll(generateSlots(data,data.between));
        all.addAll(generateSlots(data,data.afternoon));
        all.addAll(generateSlots(data,data.betweenNight));
        all.addAll(generateSlots(data,data.night));
        all.sort(Comparator.comparing(s->s.start));
        return all;
    }

    public static int remainingMinutes(Data data,ShiftConfig shift){if(shift==null||!shift.enabled||shift.start==null||shift.end==null||!shift.start.isBefore(shift.end))return 0;List<Slot>s=generateSlots(data,shift);if(s.isEmpty())return(int)Duration.between(shift.start,shift.end).toMinutes();return(int)Duration.between(s.get(s.size()-1).end,shift.end).toMinutes();}

    public static List<String> validate(Data data){
        List<String>e=new ArrayList<>();
        if(data==null){e.add("No hay datos de horario.");return e;}
        if(data.sessionMinutes<10||data.sessionMinutes>240)e.add("La duración de cada sesión debe estar entre 10 y 240 minutos.");
        validateShift(data.morning,e);validateShift(data.between,e);validateShift(data.afternoon,e);validateShift(data.betweenNight,e);validateShift(data.night,e);
        if(!data.morning.enabled&&!data.afternoon.enabled&&!data.night.enabled&&!data.between.enabled&&!data.betweenNight.enabled)e.add("Activa al menos una franja horaria.");
        if(data.morning.enabled&&data.afternoon.enabled&&data.afternoon.start.isBefore(data.morning.end))e.add("El turno de tarde no puede empezar antes de que termine el de mañana.");
        if(data.afternoon.enabled&&data.night.enabled&&data.night.start.isBefore(data.afternoon.end))e.add("El turno de noche no puede empezar antes de que termine el de tarde.");
        validateBetween(data.between,data.morning,data.afternoon,"mañana","tarde",e);
        validateBetween(data.betweenNight,data.afternoon,data.night,"tarde","noche",e);
        return e;
    }

    private static void validateBetween(ShiftConfig gap,ShiftConfig before,ShiftConfig after,String beforeName,String afterName,List<String>e){
        if(!gap.enabled)return;
        if(before.enabled&&gap.start.isBefore(before.end))e.add("El intervalo entre "+beforeName+" y "+afterName+" no puede empezar antes de que termine el turno de "+beforeName+".");
        if(after.enabled&&gap.end.isAfter(after.start))e.add("El intervalo entre "+beforeName+" y "+afterName+" debe terminar antes de que empiece el turno de "+afterName+".");
    }

    private static void validateShift(ShiftConfig s,List<String>e){if(!s.enabled)return;if(s.start==null||s.end==null||!s.start.isBefore(s.end))e.add("En "+s.label.toLowerCase()+", la hora de inicio debe ser anterior a la de fin.");if(isBetweenShift(s.id))return;if(s.breakAfterSession<0)e.add("La posición del recreo no puede ser negativa.");if(s.breakMinutes<0||s.breakMinutes>180)e.add("La duración del recreo debe estar entre 0 y 180 minutos.");}

    public static NowNext findNowNext(Data data,ZonedDateTime now){int dow=now.getDayOfWeek().getValue();if(dow<1||dow>5)return new NowNext(null,null);int day=dow-1; LocalTime time=now.toLocalTime(); List<Slot>slots=generateAllSlots(data); SlotRef current=null,next=null;for(int i=0;i<slots.size();i++){Slot slot=slots.get(i);if(!time.isBefore(slot.start)&&time.isBefore(slot.end)){current=toRef(data,day,slot);if(i+1<slots.size())next=toRef(data,day,slots.get(i+1));break;}if(time.isBefore(slot.start)){next=toRef(data,day,slot);break;}}return new NowNext(current,next);}

    private static SlotRef toRef(Data d,int day,Slot s){String assigned=d.getAssignment(day,s.shiftId,s.sessionIndex);String code=assigned;if(code.isEmpty()&&s.isBreak)code="RECREO";else if(code.isEmpty()&&isBetweenShift(s.shiftId))code="ENTRE TURNOS";return new SlotRef(day,s,code);}
    public static boolean matches(SlotRef r,int day,Slot s){return r!=null&&r.dayIndex==day&&r.slot.identity().equals(s.identity())&&r.slot.start.equals(s.start)&&r.slot.end.equals(s.end);}

    public static long nextBoundaryMillis(Data data,ZonedDateTime now){ZoneId zone=now.getZone();for(int add=0;add<=7;add++){LocalDate date=now.toLocalDate().plusDays(add);if(date.getDayOfWeek().getValue()>5)continue;for(Slot slot:generateAllSlots(data)){LocalDateTime a=LocalDateTime.of(date,slot.start),b=LocalDateTime.of(date,slot.end);ZonedDateTime az=a.atZone(zone),bz=b.atZone(zone);if(az.isAfter(now))return az.toInstant().toEpochMilli()+1000L;if(bz.isAfter(now))return bz.toInstant().toEpochMilli()+1000L;}}return now.plusHours(6).toInstant().toEpochMilli();}
}

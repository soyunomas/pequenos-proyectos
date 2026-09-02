package com.soyunomas.horariolectivo;

import org.junit.Test;
import java.time.*;
import java.util.List;
import static org.junit.Assert.*;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

public class ScheduleEngineTest {
  private final ZoneId z=ZoneId.of("Atlantic/Canary");

  @Test public void scheduleAndBreak(){Data d=new Data();List<Slot>s=ScheduleEngine.generateSlots(d,d.morning);assertEquals(7,s.size());assertEquals(LocalTime.of(8,0),s.get(0).start);assertTrue(s.get(3).isBreak);assertEquals(LocalTime.of(10,45),s.get(3).start);assertEquals(0,ScheduleEngine.remainingMinutes(d,d.morning));}

  @Test public void currentAndNext(){Data d=new Data();d.setAssignment(2,MORNING,2,"APW");d.setAssignment(2,MORNING,3,"BDD");NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,9,10,0,0,z));assertEquals("APW",n.current.code);assertEquals("BDD",n.next.code);}

  @Test public void emptyBreakKeepsRecessLabel(){Data d=new Data();NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,10,50,0,0,z));assertTrue(n.current.slot.isBreak);assertEquals("RECREO",n.current.code);assertEquals(4,n.next.slot.sessionIndex);}

  @Test public void breakCanContainAssignedModule(){Data d=new Data();d.subjects.add(new Subject("RET","RETA",0,TYPE_COMPLEMENTARIA));d.setAssignment(2,MORNING,0,"RET");NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,10,50,0,0,z));assertTrue(n.current.slot.isBreak);assertEquals("RET",n.current.code);assertTrue(d.isComplementaria(n.current.code));}

  @Test public void morningAfternoonGapIsAmberPlaceholderWhenEmpty(){Data d=new Data();d.afternoon.enabled=false;NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,14,30,0,0,z));assertNotNull(n.current);assertEquals(BETWEEN,n.current.slot.shiftId);assertEquals("ENTRE TURNOS",n.current.code);}

  @Test public void betweenTurnsCanContainAssignedModule(){Data d=new Data();d.subjects.add(new Subject("DEP","Departamento",0,TYPE_COMPLEMENTARIA));d.setAssignment(2,BETWEEN,1,"DEP");NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,14,30,0,0,z));assertEquals("DEP",n.current.code);assertTrue(d.isComplementaria(n.current.code));}

  @Test public void nightShiftAndSecondGapAreScheduled(){Data d=new Data();d.between.enabled=false;d.afternoon.enabled=true;d.afternoon.end=LocalTime.of(21,0);d.betweenNight.enabled=true;d.night.enabled=true;NowNext gap=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,21,30,0,0,z));assertEquals(BETWEEN_NIGHT,gap.current.slot.shiftId);assertEquals("ENTRE TURNOS",gap.current.code);NowNext night=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,22,10,0,0,z));assertEquals(NIGHT,night.current.slot.shiftId);}

  @Test public void nightAssignmentIsCurrent(){Data d=new Data();d.between.enabled=false;d.night.enabled=true;d.subjects.add(new Subject("NOC","Nocturna",0,TYPE_LECTIVA));d.setAssignment(2,NIGHT,1,"NOC");NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,22,10,0,0,z));assertEquals("NOC",n.current.code);}

  @Test public void weekendEmpty(){Data d=new Data();NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,5,10,0,0,0,z));assertNull(n.current);assertNull(n.next);}
  @Test public void boundary(){Data d=new Data();long m=ScheduleEngine.nextBoundaryMillis(d,ZonedDateTime.of(2026,9,2,9,10,0,0,z));assertEquals(LocalTime.of(9,50,1),Instant.ofEpochMilli(m).atZone(z).toLocalTime());}
}

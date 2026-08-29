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
  @Test public void breakCurrent(){Data d=new Data();NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,10,50,0,0,z));assertTrue(n.current.slot.isBreak);assertEquals(4,n.next.slot.sessionIndex);}
  @Test public void afternoonAfterGap(){Data d=new Data();d.afternoon.enabled=true;NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,2,14,30,0,0,z));assertNull(n.current);assertEquals(AFTERNOON,n.next.slot.shiftId);}
  @Test public void weekendEmpty(){Data d=new Data();NowNext n=ScheduleEngine.findNowNext(d,ZonedDateTime.of(2026,9,5,10,0,0,0,z));assertNull(n.current);assertNull(n.next);}
  @Test public void boundary(){Data d=new Data();long m=ScheduleEngine.nextBoundaryMillis(d,ZonedDateTime.of(2026,9,2,9,10,0,0,z));assertEquals(LocalTime.of(9,50,1),Instant.ofEpochMilli(m).atZone(z).toLocalTime());}
}

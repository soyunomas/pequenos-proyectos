package com.soyunomas.horariolectivo;

import org.junit.Test;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import static org.junit.Assert.*;

public class ReminderSlotTest {
    private final ZoneId zone = ZoneId.of("Atlantic/Canary");

    @Test public void reminderIsTenMinutesBeforeNextClass() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 20, 10, 0, 0, 0, zone);
        long time = ReminderSlot.nextReminderTime(2, LocalTime.of(9, 0), zone, now);
        assertEquals(ZonedDateTime.of(2026, 10, 21, 8, 50, 0, 0, zone).toInstant().toEpochMilli(), time);
    }

    @Test public void elapsedSessionGoesToFollowingWeek() {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 21, 8, 55, 0, 0, zone);
        long time = ReminderSlot.nextReminderTime(2, LocalTime.of(9, 0), zone, now);
        assertEquals(ZonedDateTime.of(2026, 10, 28, 8, 50, 0, 0, zone).toInstant().toEpochMilli(), time);
    }

    @Test public void slotKeyIsStableAndReadable() {
        assertEquals("0|M|2", ReminderSlot.key(0, "M", 2));
        assertTrue(ReminderSlot.description("0|M|2").contains("Lunes"));
        assertEquals("", ReminderSlot.description(""));
    }

    @Test public void customBreakCanBeLinkedToReminder() {
        assertEquals("2|N|-1", ReminderSlot.key(2, "N", -1));
        assertTrue(ReminderSlot.description("2|N|-1").contains("recreo"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidDayIsRejected() {
        ReminderSlot.key(6, "M", 1);
    }
}

package com.soyunomas.horariolectivo;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Identifies a weekly timetable cell without coupling reminders to a subject's name. */
public final class ReminderSlot {
    private static final String[] DAYS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes"};
    private ReminderSlot() {}

    public static String key(int day, String shift, int session) {
        if (day < 0 || day > 4 || shift == null || shift.isEmpty())
            throw new IllegalArgumentException("Casilla horaria inválida");
        // Breaks use index zero or negative indices when the shift has custom slots.
        return day + "|" + shift + "|" + session;
    }

    public static String description(String key) {
        if (key == null || key.trim().isEmpty()) return "";
        String[] parts = key.split("\\|");
        if (parts.length != 3) return "Hora del horario";
        try {
            int day = Integer.parseInt(parts[0]);
            int session = Integer.parseInt(parts[2]);
            if (day < 0 || day > 4) return "Hora del horario";
            String segment = session > 0 ? "franja " + session : "recreo";
            return DAYS[day] + " · " + shiftName(parts[1]) + " · " + segment;
        } catch (NumberFormatException e) {
            return "Hora del horario";
        }
    }

    private static String shiftName(String id) {
        switch (id) {
            case "M": return "mañana";
            case "T": return "tarde";
            case "N": return "noche";
            case "E": return "entre mañana y tarde";
            case "EN": return "entre tarde y noche";
            default: return "horario";
        }
    }

    /** Next scheduled one-time notification, 10 minutes before the next occurrence of a cell. */
    public static long nextReminderTime(int day, LocalTime classStart, ZoneId zone, ZonedDateTime now) {
        if (day < 0 || day > 4) throw new IllegalArgumentException("Día inválido");
        DayOfWeek weekday = DayOfWeek.of(day + 1);
        int offset = (weekday.getValue() - now.getDayOfWeek().getValue() + 7) % 7;
        ZonedDateTime candidate = now.toLocalDate().plusDays(offset).atTime(classStart).atZone(zone).minusMinutes(10);
        if (!candidate.isAfter(now)) candidate = candidate.plusWeeks(1);
        return candidate.toInstant().toEpochMilli();
    }
}

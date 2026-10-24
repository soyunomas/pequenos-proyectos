package com.soyunomas.horariolectivo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Local storage for one-time reminders; independent of the timetable editor. */
public final class ReminderStore {
    private static final String PREFS = "horario_lectivo_reminders";
    private static final String KEY_ITEMS = "items", KEY_NEXT = "next_id";
    private final SharedPreferences preferences;

    public static final class Reminder {
        public final int id;
        public final String title, notes;
        public final long atMillis;
        public final boolean sound;

        public Reminder(int id, String title, String notes, long atMillis, boolean sound) {
            this.id = id;
            this.title = title == null ? "" : title.trim();
            this.notes = notes == null ? "" : notes.trim();
            this.atMillis = atMillis;
            this.sound = sound;
        }

        public static String validate(String title, String notes, long atMillis, long now) {
            if (title == null || title.trim().isEmpty()) return "Escribe un título.";
            if (title.trim().length() > 120) return "El título admite hasta 120 caracteres.";
            if (notes != null && notes.trim().length() > 5000) return "El texto admite hasta 5000 caracteres.";
            if (atMillis <= now) return "Selecciona una fecha y hora futuras.";
            return null;
        }
    }

    public ReminderStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Reminder> all() {
        List<Reminder> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(preferences.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                int id = o.optInt("id", 0);
                String title = o.optString("title", "");
                if (id > 0 && !title.trim().isEmpty()) {
                    result.add(new Reminder(id, title, o.optString("notes", ""), o.optLong("atMillis", 0L), o.optBoolean("sound", true)));
                }
            }
        } catch (Exception ignored) { /* A corrupt record must not crash the timetable. */ }
        result.sort(Comparator.comparingLong(r -> r.atMillis));
        return result;
    }

    public synchronized Reminder find(int id) {
        for (Reminder r : all()) if (r.id == id) return r;
        return null;
    }

    public synchronized Reminder put(Reminder value) {
        String error = Reminder.validate(value.title, value.notes, value.atMillis, System.currentTimeMillis());
        if (error != null) throw new IllegalArgumentException(error);
        int id = value.id == 0 ? Math.max(1, preferences.getInt(KEY_NEXT, 1)) : value.id;
        List<Reminder> list = all();
        list.removeIf(r -> r.id == id);
        Reminder saved = new Reminder(id, value.title, value.notes, value.atMillis, value.sound);
        list.add(saved);
        persist(list, Math.max(preferences.getInt(KEY_NEXT, 1), id + 1));
        return saved;
    }

    public synchronized void remove(int id) {
        List<Reminder> list = all();
        list.removeIf(r -> r.id == id);
        persist(list, preferences.getInt(KEY_NEXT, 1));
    }

    private void persist(List<Reminder> list, int next) {
        JSONArray arr = new JSONArray();
        try {
            for (Reminder r : list) {
                JSONObject o = new JSONObject();
                o.put("id", r.id);
                o.put("title", r.title);
                o.put("notes", r.notes);
                o.put("atMillis", r.atMillis);
                o.put("sound", r.sound);
                arr.put(o);
            }
            if (!preferences.edit().putString(KEY_ITEMS, arr.toString()).putInt(KEY_NEXT, next).commit()) {
                throw new IllegalStateException("No se pudieron guardar los recordatorios.");
            }
        } catch (org.json.JSONException e) {
            throw new IllegalStateException("No se pudieron serializar los recordatorios.", e);
        }
    }
}

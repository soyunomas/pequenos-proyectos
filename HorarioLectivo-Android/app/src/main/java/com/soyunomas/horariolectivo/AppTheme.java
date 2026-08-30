package com.soyunomas.horariolectivo;

import android.graphics.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static com.soyunomas.horariolectivo.ScheduleModels.*;

final class AppTheme {
    final boolean dark;
    final int page, surface, surfaceAlt, border, ink, muted, shift, breakBg, breakBorder, buttonBg, buttonText;
    final int primary, primaryText, primarySoft, primarySoftText, danger, dangerSoft;

    private static final int SUBJECT_SLOTS = 48;
    private final Map<String,Integer> subjectSlots = new HashMap<>();
    private final boolean[] usedSubjectSlots = new boolean[SUBJECT_SLOTS];

    AppTheme(boolean dark) {
        this.dark = dark;
        if (dark) {
            page = Color.rgb(13,18,28);
            surface = Color.rgb(22,29,42);
            surfaceAlt = Color.rgb(30,39,55);
            border = Color.rgb(55,65,81);
            ink = Color.rgb(244,247,251);
            muted = Color.rgb(166,177,196);
            shift = Color.rgb(26,35,50);
            breakBg = Color.rgb(82,57,20);
            breakBorder = Color.rgb(245,183,66);
            buttonBg = Color.rgb(36,46,64);
            buttonText = Color.rgb(232,237,246);
            primary = Color.rgb(139,140,251);
            primaryText = Color.rgb(17,18,54);
            primarySoft = Color.rgb(39,42,79);
            primarySoftText = Color.rgb(222,223,255);
            danger = Color.rgb(255,138,145);
            dangerSoft = Color.rgb(78,36,43);
        } else {
            page = Color.rgb(246,247,251);
            surface = Color.rgb(255,255,255);
            surfaceAlt = Color.rgb(241,244,249);
            border = Color.rgb(221,226,234);
            ink = Color.rgb(26,31,43);
            muted = Color.rgb(91,101,119);
            shift = Color.rgb(235,239,247);
            breakBg = Color.rgb(255,247,224);
            breakBorder = Color.rgb(217,145,24);
            buttonBg = Color.rgb(238,241,247);
            buttonText = Color.rgb(44,52,69);
            primary = Color.rgb(79,70,229);
            primaryText = Color.WHITE;
            primarySoft = Color.rgb(232,231,255);
            primarySoftText = Color.rgb(49,46,129);
            danger = Color.rgb(190,24,93);
            dangerSoft = Color.rgb(255,228,230);
        }
    }

    void bindSubjects(List<Subject> subjects) {
        subjectSlots.clear();
        for (int i = 0; i < usedSubjectSlots.length; i++) usedSubjectSlots[i] = false;
        if (subjects == null) return;
        for (Subject subject : subjects) {
            if (subject != null && subject.code != null) subjectSlot(subject.code);
        }
    }

    int subjectColor(String code) {
        int slot = subjectSlot(code);
        float hue = (slot * 137.508f) % 360f;
        float saturation = dark ? 0.64f : 0.42f;
        float value = dark ? 0.43f : 0.96f;
        return Color.HSVToColor(new float[]{hue, saturation, value});
    }

    int subjectTextColor(String code) {
        int bg = subjectColor(code);
        double luminance = relativeLuminance(bg);
        return luminance > 0.46 ? Color.rgb(24,28,38) : Color.WHITE;
    }

    private int subjectSlot(String code) {
        String key = code == null ? "" : code;
        Integer assigned = subjectSlots.get(key);
        if (assigned != null) return assigned;

        int hash = key.hashCode();
        int start = Math.floorMod(hash, SUBJECT_SLOTS);
        int step = Math.floorMod((hash >>> 8) | 1, SUBJECT_SLOTS);
        if ((step & 1) == 0) step++;
        int slot = start;
        for (int i = 0; i < SUBJECT_SLOTS; i++) {
            slot = Math.floorMod(start + i * step, SUBJECT_SLOTS);
            if (!usedSubjectSlots[slot]) {
                usedSubjectSlots[slot] = true;
                subjectSlots.put(key, slot);
                return slot;
            }
        }

        // Solo se alcanza con más de 48 asignaturas simultáneas.
        slot = Math.floorMod(subjectSlots.size(), SUBJECT_SLOTS);
        subjectSlots.put(key, slot);
        return slot;
    }

    private static double relativeLuminance(int color) {
        double r = channel(Color.red(color) / 255.0);
        double g = channel(Color.green(color) / 255.0);
        double b = channel(Color.blue(color) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}

package com.soyunomas.horariolectivo;

import android.graphics.Color;

final class AppTheme {
    final boolean dark;
    final int page, surface, surfaceAlt, border, ink, muted, shift, breakBg, breakBorder, buttonBg, buttonText;
    final int primary, primaryText, primarySoft, primarySoftText, danger, dangerSoft;

    private static final int[][] SUBJECT_LIGHT = {
        {219,234,254},{207,250,254},{204,251,241},{220,252,231},
        {236,252,203},{254,243,199},{255,237,213},{255,228,230},
        {252,231,243},{243,232,255},{237,233,254},{224,231,255}
    };
    private static final int[][] SUBJECT_LIGHT_TEXT = {
        {30,58,138},{21,94,117},{17,94,89},{22,101,52},
        {63,98,18},{146,64,14},{154,52,18},{159,18,57},
        {157,23,77},{107,33,168},{91,33,182},{55,48,163}
    };
    private static final int[][] SUBJECT_DARK = {
        {30,58,95},{22,78,99},{19,78,74},{20,83,45},
        {54,83,20},{120,72,15},{124,45,18},{136,19,55},
        {131,24,67},{88,28,135},{76,29,149},{49,46,129}
    };
    private static final int[][] SUBJECT_DARK_TEXT = {
        {219,234,254},{207,250,254},{204,251,241},{220,252,231},
        {236,252,203},{254,243,199},{255,237,213},{255,228,230},
        {252,231,243},{243,232,255},{237,233,254},{224,231,255}
    };

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

    int subjectColor(String code) {
        int index = subjectIndex(code);
        int[] c = dark ? SUBJECT_DARK[index] : SUBJECT_LIGHT[index];
        return Color.rgb(c[0], c[1], c[2]);
    }

    int subjectTextColor(String code) {
        int index = subjectIndex(code);
        int[] c = dark ? SUBJECT_DARK_TEXT[index] : SUBJECT_LIGHT_TEXT[index];
        return Color.rgb(c[0], c[1], c[2]);
    }

    private int subjectIndex(String code) {
        return Math.floorMod((code == null ? "" : code).hashCode(), SUBJECT_LIGHT.length);
    }
}

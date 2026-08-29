package com.soyunomas.horariolectivo;

import android.graphics.Color;

final class AppTheme {
    final boolean dark;
    final int page, surface, surfaceAlt, border, ink, muted, shift, breakBg, breakBorder, buttonBg, buttonText;

    AppTheme(boolean dark) {
        this.dark = dark;
        if (dark) {
            page = Color.rgb(15,23,42);
            surface = Color.rgb(30,41,59);
            surfaceAlt = Color.rgb(51,65,85);
            border = Color.rgb(71,85,105);
            ink = Color.rgb(241,245,249);
            muted = Color.rgb(203,213,225);
            shift = Color.rgb(30,41,59);
            breakBg = Color.rgb(69,55,28);
            breakBorder = Color.rgb(245,158,11);
            buttonBg = Color.rgb(51,65,85);
            buttonText = Color.rgb(241,245,249);
        } else {
            page = Color.rgb(246,248,251);
            surface = Color.WHITE;
            surfaceAlt = Color.rgb(248,250,252);
            border = Color.rgb(203,213,225);
            ink = Color.rgb(15,23,42);
            muted = Color.rgb(71,85,105);
            shift = Color.rgb(232,238,246);
            breakBg = Color.rgb(255,247,237);
            breakBorder = Color.rgb(251,191,36);
            buttonBg = Color.rgb(241,245,249);
            buttonText = Color.rgb(30,41,59);
        }
    }

    int subjectColor(String code) {
        int[][] light = {
            {219,234,254},{220,252,231},{237,233,254},{255,237,213},{252,231,243},
            {254,249,195},{204,251,241},{224,242,254},{254,226,226}
        };
        int[][] darkColors = {
            {30,58,95},{31,76,56},{72,55,112},{103,65,31},{102,45,78},
            {91,82,31},{24,78,74},{28,70,91},{99,46,46}
        };
        int index = Math.floorMod(code.hashCode(), light.length);
        int[] c = dark ? darkColors[index] : light[index];
        return Color.rgb(c[0], c[1], c[2]);
    }
}

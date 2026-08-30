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

    static final int PALETTE_SIZE = 24;
    private static final float[] HUES = {220,174,142,103,78,48,28,8,345,324,300,278,258,238,200,186,160,126,92,62,38,18,334,312};
    private final Map<String,Integer> subjectSlots = new HashMap<>();
    private final boolean[] used = new boolean[PALETTE_SIZE];

    AppTheme(boolean dark) {
        this.dark = dark;
        if (dark) {
            page=Color.rgb(13,18,28); surface=Color.rgb(22,29,42); surfaceAlt=Color.rgb(30,39,55); border=Color.rgb(55,65,81);
            ink=Color.rgb(244,247,251); muted=Color.rgb(166,177,196); shift=Color.rgb(26,35,50); breakBg=Color.rgb(82,57,20);
            breakBorder=Color.rgb(245,183,66); buttonBg=Color.rgb(36,46,64); buttonText=Color.rgb(232,237,246);
            primary=Color.rgb(139,140,251); primaryText=Color.rgb(17,18,54); primarySoft=Color.rgb(39,42,79); primarySoftText=Color.rgb(222,223,255);
            danger=Color.rgb(255,138,145); dangerSoft=Color.rgb(78,36,43);
        } else {
            page=Color.rgb(246,247,251); surface=Color.WHITE; surfaceAlt=Color.rgb(241,244,249); border=Color.rgb(221,226,234);
            ink=Color.rgb(26,31,43); muted=Color.rgb(91,101,119); shift=Color.rgb(235,239,247); breakBg=Color.rgb(255,247,224);
            breakBorder=Color.rgb(217,145,24); buttonBg=Color.rgb(238,241,247); buttonText=Color.rgb(44,52,69);
            primary=Color.rgb(79,70,229); primaryText=Color.WHITE; primarySoft=Color.rgb(232,231,255); primarySoftText=Color.rgb(49,46,129);
            danger=Color.rgb(190,24,93); dangerSoft=Color.rgb(255,228,230);
        }
    }

    void bindSubjects(List<Subject> subjects) {
        subjectSlots.clear();
        for(int i=0;i<used.length;i++) used[i]=false;
        if(subjects==null) return;
        for(Subject s:subjects) {
            if(s!=null && s.code!=null && s.colorIndex>=0) {
                int p=Math.floorMod(s.colorIndex,PALETTE_SIZE);
                if(!used[p]) {
                    subjectSlots.put(s.code,p);
                    used[p]=true;
                }
            }
        }
        for(Subject s:subjects) {
            if(s!=null && s.code!=null && !subjectSlots.containsKey(s.code)) {
                int p=firstFree(s.code);
                subjectSlots.put(s.code,p);
                used[p]=true;
            }
        }
    }

    int subjectColor(String code){ return paletteColor(subjectPaletteIndex(code)); }
    int subjectTextColor(String code){ return textFor(subjectColor(code)); }
    int subjectPaletteIndex(String code){ return slot(code); }
    int paletteColor(int index){ float hue=HUES[Math.floorMod(index,PALETTE_SIZE)]; return Color.HSVToColor(new float[]{hue,dark?0.62f:0.38f,dark?0.45f:0.97f}); }
    int paletteTextColor(int index){ return textFor(paletteColor(index)); }
    int paletteSize(){ return PALETTE_SIZE; }

    boolean paletteUsedByOther(List<Subject> subjects, Subject current, int index){
        if(subjects==null) return false;
        int p=Math.floorMod(index,PALETTE_SIZE);
        for(Subject s:subjects) {
            if(s!=null && s!=current && s.code!=null && slot(s.code)==p) return true;
        }
        return false;
    }

    private int slot(String code){
        String key=code==null?"":code;
        Integer p=subjectSlots.get(key);
        if(p!=null) return p;
        return firstFree(key);
    }

    private int firstFree(String key){
        int start=Math.floorMod(key.hashCode(),PALETTE_SIZE);
        for(int i=0;i<PALETTE_SIZE;i++) {
            int p=(start+i)%PALETTE_SIZE;
            if(!used[p]) return p;
        }
        return start;
    }

    private int textFor(int bg){ return relativeLuminance(bg)>0.46?Color.rgb(24,28,38):Color.WHITE; }
    private static double relativeLuminance(int c){ return 0.2126*ch(Color.red(c)/255.0)+0.7152*ch(Color.green(c)/255.0)+0.0722*ch(Color.blue(c)/255.0); }
    private static double ch(double c){ return c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4); }
}

package com.soyunomas.wifianalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gives each BSSID its own colour while the activity is alive.
 * New BSSIDs never reuse an assigned ARGB colour, even after the initial palette.
 * Line patterns add a second identification cue for crowded Wi-Fi charts.
 *
 * Pure Java so allocation and collision handling are independently unit tested.
 */
public final class SpectrumColorAllocator {
    private static final int[] STARTER={
        0xFF456CFF,0xFF168E9E,0xFFC97B32,0xFF8C60D9,
        0xFF167D5B,0xFFD35683,0xFF4D8DC4,0xFF9A7426,
        0xFF984B6B,0xFF218A74,0xFF6877C0,0xFFBD6652
    };

    public static final class Style {
        public final int color;
        public final int number;
        public final int linePattern;
        Style(int color,int number) {
            this.color=color;
            this.number=number;
            this.linePattern=(number-1)%4;
        }
    }

    private final Map<String,Style> allocated=new HashMap<>();
    private final Set<Integer> taken=new HashSet<>();

    public synchronized Style style(String identifier) {
        String key=identifier==null?"":identifier.trim().toUpperCase(Locale.ROOT);
        Style existing=allocated.get(key);
        if(existing!=null)return existing;

        int ordinal=allocated.size();
        int candidate=ordinal<STARTER.length ? STARTER[ordinal] : generated(ordinal,0);
        int retry=1;
        while(taken.contains(candidate))candidate=generated(ordinal,retry++);

        Style style=new Style(candidate,ordinal+1);
        allocated.put(key,style);
        taken.add(candidate);
        return style;
    }

    public synchronized List<String> assignedIdentifiers(){
        List<Map.Entry<String,Style>> entries=new ArrayList<>(allocated.entrySet());
        entries.sort((a,b)->Integer.compare(a.getValue().number,b.getValue().number));
        List<String> result=new ArrayList<>();
        for(Map.Entry<String,Style> e:entries)result.add(e.getKey());
        return result;
    }

    public synchronized void restore(List<String> orderedKeys) {
        if(orderedKeys==null)return;
        for(String key:orderedKeys)style(key);
    }

    private static int generated(int ordinal,int retry) {
        // Golden-angle spacing avoids clusters as a user encounters additional APs.
        double hue=(ordinal-STARTER.length)*137.50776405003785+25+retry*47.63;
        double saturation=0.65+0.09*(ordinal%3);
        double value=0.74+0.08*((ordinal/3)%3);
        return hsv(hue,saturation,value);
    }

    private static int hsv(double hue,double saturation,double value) {
        double angle=((hue%360)+360)%360;
        double chroma=value*saturation;
        double x=chroma*(1-Math.abs((angle/60)%2-1));
        double m=value-chroma;
        double r=0,g=0,b=0;
        if(angle<60){r=chroma;g=x;}
        else if(angle<120){r=x;g=chroma;}
        else if(angle<180){g=chroma;b=x;}
        else if(angle<240){g=x;b=chroma;}
        else if(angle<300){r=x;b=chroma;}
        else{r=chroma;b=x;}
        return 0xFF000000|((clamp(r+m)&255)<<16)|((clamp(g+m)&255)<<8)|(clamp(b+m)&255);
    }

    private static int clamp(double c){
        return Math.max(0,Math.min(255,(int)Math.round(c*255)));
    }
}

package com.soyunomas.wifianalyzer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Bounded session record, exported only by an explicit user action. */
public final class ToolHistory {
    private ToolHistory(){}
    private static final List<String> ENTRIES=new ArrayList<>();
    public static synchronized void add(String name,String target,String result){
        if(ENTRIES.size()>=30)ENTRIES.remove(0);
        String output=result==null?"":result;
        if(output.length()>6000)output=output.substring(0,6000)+"\n[truncado]";
        ENTRIES.add(new Date()+"\n"+name+" · "+target+"\n"+output);
    }
    public static synchronized int count(){return ENTRIES.size();}
    public static synchronized String report(){
        StringBuilder out=new StringBuilder("NetScope · informe de diagnóstico\nFecha: "+
            new Date()+"\nEntradas de esta sesión: "+ENTRIES.size()+
            "\n\nGenerado únicamente con medidas solicitadas por el usuario. "+
            "Las estimaciones y resultados no concluyentes se describen como tales.\n");
        for(String item:ENTRIES)out.append("\n-------------------------\n").append(item).append("\n");
        return out.toString();
    }
    public static synchronized void clear(){ENTRIES.clear();}
}

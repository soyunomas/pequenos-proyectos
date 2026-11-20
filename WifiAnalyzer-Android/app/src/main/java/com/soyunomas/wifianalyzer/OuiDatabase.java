package com.soyunomas.wifianalyzer;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Offline IEEE MA-L / MA-M / MA-S lookup.
 * Data is generated during CI from IEEE's public registration lists.
 * A vendor identifies the assignee of a MAC prefix, not necessarily the device manufacturer.
 */
public final class OuiDatabase {
    private final Map<String,String> vendors = new HashMap<>();
    private int records;

    public OuiDatabase(Context context) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                context.getAssets().open("oui.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                int tab=line.indexOf('\t');
                if(tab<0)continue;
                String key=line.substring(0,tab);
                String vendor=line.substring(tab+1);
                if((key.length()==6||key.length()==7||key.length()==9)
                   && !vendor.isEmpty()) vendors.put(key,vendor);
            }
            records=vendors.size();
        } catch (Exception e) {
            records=0; // UI reports absent dataset; never fabricates manufacturers.
        }
    }

    public int count(){return records;}
    public String lookup(String mac){return lookup(vendors,mac);}

    public static String lookup(Map<String,String> registry,String mac) {
        if(mac==null||mac.isEmpty())return "MAC no disponible";
        String canonical=mac.replace(":","").replace("-","").replace(".","")
            .toUpperCase(Locale.ROOT);
        if(!canonical.matches("[0-9A-F]{12}"))return "MAC no válida";
        int first=Integer.parseInt(canonical.substring(0,2),16);
        if((first&1)!=0)return "Dirección de grupo / multicast";
        if((first&2)!=0)return "MAC local o aleatoria";
        for(int size:new int[]{9,7,6}) {
            String vendor=registry.get(canonical.substring(0,size));
            if(vendor!=null&&!vendor.isEmpty())return vendor;
        }
        return "Fabricante desconocido";
    }
}

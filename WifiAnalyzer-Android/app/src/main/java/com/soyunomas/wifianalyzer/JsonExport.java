package com.soyunomas.wifianalyzer;

import java.util.List;

/** Offline JSON snapshots with optional redaction of identifying fields. */
public final class JsonExport {
    private JsonExport() {}
    static String quote(String text){
        if(text==null)return "null";
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='"'||c=='\\')b.append('\\').append(c);
            else if(c=='\n')b.append("\\n");
            else if(c=='\r')b.append("\\r");
            else if(c=='\t')b.append("\\t");
            else if(c<32)b.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
            else b.append(c);
        }
        return b.append('\"').toString();
    }
    public static String render(List<NetworkEntry> list,boolean anonymize){
        StringBuilder s=new StringBuilder();
        s.append("{\"format\":\"netscope-scan\",\"version\":2,\"redacted\":")
         .append(anonymize).append(",\"networks\":[");
        for(int i=0;i<list.size();i++){
            NetworkEntry n=list.get(i);
            if(i>0)s.append(',');
            s.append("{\"ssid\":").append(quote(anonymize?"Red "+(i+1):n.ssid))
             .append(",\"bssid\":").append(quote(anonymize?"redactado":n.bssid))
             .append(",\"frequencyMHz\":").append(n.mhz)
             .append(",\"channel\":").append(n.channel)
             .append(",\"band\":").append(quote(ChannelMath.bandLabel(n.band)))
             .append(",\"rssiDbm\":").append(n.dbm)
             .append(",\"widthMHz\":").append(n.width)
             .append(",\"security\":").append(quote(n.security))
             .append(",\"wifiStandard\":").append(quote(n.standard))
             .append(",\"connected\":").append(n.connected).append('}');
        }
        return s.append("]}").toString();
    }
}

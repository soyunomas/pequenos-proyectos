package com.soyunomas.wifianalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session-only Wi-Fi RSSI history. Deduplicates recycled Android scan results. */
public final class SurveyHistory {
    public static final class Sample {
        public final String bssid, ssid;
        public final long scanTimestampUs, wallTimeMs;
        public final int dbm, mhz;
        Sample(String bssid,String ssid,long scanTimestampUs,long wallTimeMs,int dbm,int mhz){
            this.bssid=bssid;this.ssid=ssid;this.scanTimestampUs=scanTimestampUs;
            this.wallTimeMs=wallTimeMs;this.dbm=dbm;this.mhz=mhz;
        }
    }
    private final Map<String,ArrayList<Sample>> byBssid=new LinkedHashMap<>();
    private final Map<String,Long> lastScanTimestamp=new HashMap<>();
    private int total=0;

    /** Returns true iff a genuinely new scan observation was recorded. */
    public boolean add(NetworkEntry n,long scanTimestampUs,long wallTimeMs) {
        if(n.bssid.isEmpty()||scanTimestampUs<=0)return false;
        Long previous=lastScanTimestamp.get(n.bssid);
        if(previous!=null&&scanTimestampUs<=previous)return false;
        if(!byBssid.containsKey(n.bssid)&&byBssid.size()>=200)removeOldestBssid();
        ArrayList<Sample> list=byBssid.get(n.bssid);
        if(list==null){list=new ArrayList<>();byBssid.put(n.bssid,list);}
        list.add(new Sample(n.bssid,n.ssid,scanTimestampUs,wallTimeMs,n.dbm,n.mhz));
        lastScanTimestamp.put(n.bssid,scanTimestampUs);
        total++;
        if(list.size()>100){list.remove(0);total--;}
        return true;
    }

    private void removeOldestBssid(){
        String key=byBssid.keySet().iterator().next();
        total-=byBssid.remove(key).size();
        lastScanTimestamp.remove(key);
    }
    public List<Integer> values(String bssid) {
        ArrayList<Integer> values=new ArrayList<>();
        ArrayList<Sample> list=byBssid.get(bssid);
        if(list!=null)for(Sample s:list)values.add(s.dbm);
        return values;
    }
    public int count(){return total;}
    public int points(String bssid){
        List<Sample> list=byBssid.get(bssid);return list==null?0:list.size();
    }
    public int min(String bssid){return measure(bssid,0);}
    public int max(String bssid){return measure(bssid,1);}
    public int avg(String bssid){return measure(bssid,2);}
    private int measure(String bssid,int mode) {
        List<Sample> list=byBssid.get(bssid);
        if(list==null||list.isEmpty())return 0;
        int min=999,max=-999,sum=0;
        for(Sample s:list){min=Math.min(min,s.dbm);max=Math.max(max,s.dbm);sum+=s.dbm;}
        return mode==0?min:mode==1?max:Math.round(sum/(float)list.size());
    }
    public String csv() {
        StringBuilder sb=new StringBuilder("fecha_unix_ms,SSID,BSSID,frecuencia_MHz,senal_dBm\r\n");
        for(List<Sample> list:byBssid.values())for(Sample s:list)
            sb.append(s.wallTimeMs).append(',').append(CsvExport.cell(s.ssid)).append(',')
                .append(CsvExport.cell(s.bssid)).append(',').append(s.mhz).append(',')
                .append(s.dbm).append("\r\n");
        return sb.toString();
    }
    public void clear(){byBssid.clear();lastScanTimestamp.clear();total=0;}
}

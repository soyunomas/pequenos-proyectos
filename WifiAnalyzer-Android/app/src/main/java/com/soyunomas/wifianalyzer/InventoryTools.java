package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.*;

/** Opt-in persistent inventory: only host IP, observed MAC, device hint and timestamp. */
public final class InventoryTools {
    private InventoryTools(){}
    public static String scan(Context ctx)throws Exception{
        LanScanner.Plan plan=LanScanner.plan(ctx);
        if(plan==null)return "Sin Wi-Fi privada IPv4 para inventariar.";
        CountDownLatch done=new CountDownLatch(1);
        List<LanScanner.Device> found=new ArrayList<>();
        LanScanner.ScanHandle handle=LanScanner.start(plan,new OuiDatabase(ctx),false,new LanScanner.Callbacks(){
            @Override public void progress(int checked,int total,int count){}
            @Override public void discovered(LanScanner.Device d){}
            @Override public void finished(List<LanScanner.Device> list,boolean readable,String issue){
                synchronized(found){found.clear();found.addAll(list);}
                done.countDown();
            }
        });
        if(!done.await(28,TimeUnit.SECONDS)){handle.cancel();return "Inventario incompleto: timeout. Vuelve a intentarlo.";}
        if(Thread.currentThread().isInterrupted()){handle.cancel();return "Exploración cancelada.";}
        SharedPreferences prefs=ctx.getSharedPreferences("netscope-inventory",Context.MODE_PRIVATE);
        Map<String,JSONObject> previous=new HashMap<>();
        String previousJson=prefs.getString("hosts","[]");
        JSONArray stored=new JSONArray(previousJson);
        for(int i=0;i<stored.length();i++){
            JSONObject obj=stored.optJSONObject(i);
            if(obj!=null)previous.put(obj.optString("ip"),obj);
        }
        JSONArray refreshed=new JSONArray();
        StringBuilder out=new StringBuilder("Inventario LAN · "+plan.scannedCidr+
            "\nVistos ahora: "+found.size()+" · guardados antes: "+previous.size()+"\n");
        Set<String> seen=new HashSet<>();
        List<LanScanner.Device> copy;
        synchronized(found){copy=new ArrayList<>(found);}
        copy.sort(Comparator.comparingLong(d->LanScanner.toLong(d.ip)));
        for(LanScanner.Device d:copy){
            if(!seen.add(d.ip))continue;
            JSONObject old=previous.get(d.ip);
            String status=old==null?"NUEVO":"conocido";
            if(old!=null&&!d.mac.isEmpty()&&!old.optString("mac").isEmpty()&&!old.optString("mac").equalsIgnoreCase(d.mac))
                status="CAMBIO DE MAC";
            out.append(status).append(" · ").append(d.ip);
            if(!d.mac.isEmpty())out.append(" · ").append(d.mac);
            out.append(" · ").append(d.detail).append("\n");
            JSONObject item=new JSONObject();
            item.put("ip",d.ip);item.put("mac",d.mac);
            item.put("hint",d.detail);item.put("seen",System.currentTimeMillis());
            refreshed.put(item);
        }
        for(String ip:previous.keySet())if(!seen.contains(ip))
            out.append("NO DETECTADO AHORA · ").append(ip).append("\n");
        prefs.edit().putString("hosts",refreshed.toString())
            .putLong("scanAt",System.currentTimeMillis()).apply();
        out.append("\nInventario guardado únicamente en este dispositivo. "+
            "No detectado no equivale a desconectado: algunos equipos bloquean el sondeo TCP.");
        return out.toString();
    }
}

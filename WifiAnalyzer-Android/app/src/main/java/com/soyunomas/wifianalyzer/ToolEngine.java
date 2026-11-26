package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Per-user-request dispatcher. Never starts background work automatically. */
public final class ToolEngine {
    private ToolEngine(){}
    public static String execute(Context ctx,int id,String input)throws Exception{
        if(id==25)return ToolHistory.report();
        NetworkDiagnostics.Snapshot snap=NetworkDiagnostics.capture(ctx);
        Network net=snap.network;
        if(net==null)return "Conéctate a una red Wi-Fi antes de utilizar esta herramienta.";
        if(id==24)return InventoryTools.scan(ctx);
        if(id==5||id==6||id==7)return dns(ctx,net,id,input);
        if(id==1||id==2||id==3||id==4||id==10||id==11||id==12||id==13||
           id==14||id==15||id==21||id==22||id==23)
            return LocalTools.execute(ctx,net,id,input);
        return PublicTools.execute(ctx,net,id,input);
    }
    private static String dns(Context ctx,Network net,int id,String input)throws Exception{
        String domain=input.trim();
        if(id==6)domain=DnsTools.reverse(domain);
        if(id==7){
            StringBuilder result=new StringBuilder("Comparación DNS A: "+domain+"\n");
            String[] servers={"1.1.1.1","8.8.8.8","9.9.9.9","8.8.8.8","1.1.1.1"};
            String[] protocols={"UDP","UDP","UDP","DoH","DoT"};
            for(int i=0;i<servers.length;i++){
                long start=System.nanoTime();
                try{
                    List<String> data=DnsTools.query(net,domain,1,servers[i],protocols[i]);
                    long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                    String joined=String.join(", ",data);
                    if(joined.length()>220)joined=joined.substring(0,220)+"…";
                    result.append(protocols[i]).append(" ").append(servers[i]).append(": ")
                        .append(ms).append(" ms · ").append(joined).append("\n");
                }catch(Exception e){
                    result.append(protocols[i]).append(" ").append(servers[i]).append(": ")
                        .append(e.getClass().getSimpleName()).append("\n");
                }
            }
            return result.append("\nDNS público directo puede eludir DNS privado o filtrado del router; "+
                "DoH y DoT autentican el servidor por certificado TLS.").toString();
        }
        String server="1.1.1.1";
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties lp=cm.getLinkProperties(net);
        if(lp!=null){
            for(InetAddress dns:lp.getDnsServers())if(dns.getAddress().length==4){
                server=dns.getHostAddress();break;
            }
        }
        if(id==6) {
            List<String> ptr=DnsTools.query(net,domain,12,server,"UDP");
            return "DNS inverso PTR\n"+domain+"\n"+(ptr.isEmpty()?"Sin registros PTR":String.join("\n",ptr));
        }
        return DnsTools.queryAll(net,domain,server,"UDP");
    }
}

package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unprivileged LAN host discovery using bounded TCP connection attempts.
 * This is NOT a raw ARP scan. MAC addresses are read from system ARP cache only if accessible.
 */
public final class LanScanner {
    public interface Callbacks {
        void progress(int scanned,int total,int found);
        void finished(List<Device> devices, boolean arpReadable, String issue);
    }
    public static final class Device {
        public final String ip, mac, detail, vendor;
        public final boolean gateway;
        public Device(String ip,String mac,String detail,String vendor,boolean gateway){
            this.ip=ip;this.mac=mac;this.detail=detail;this.vendor=vendor;this.gateway=gateway;
        }
    }
    public static final class Plan {
        public final Network network;
        public final String ownIp, gateway, scannedCidr, originalCidr;
        public final List<String> addresses;
        Plan(Network net,String ownIp,String gateway,String originalCidr,String scannedCidr,
             List<String> addresses){
            this.network=net;this.ownIp=ownIp;this.gateway=gateway;
            this.originalCidr=originalCidr;this.scannedCidr=scannedCidr;this.addresses=addresses;
        }
    }
    public static final class ScanHandle {
        private final AtomicBoolean stopped=new AtomicBoolean(false);
        private final ExecutorService executor=Executors.newSingleThreadExecutor();
        public void cancel(){stopped.set(true);executor.shutdownNow();}
        public boolean cancelled(){return stopped.get();}
    }
    private LanScanner(){}

    public static Plan plan(Context ctx) {
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            LinkProperties props=cm.getLinkProperties(n);
            if(props==null)continue;
            for(LinkAddress address:props.getLinkAddresses()){
                if(!(address.getAddress() instanceof Inet4Address))continue;
                String ip=address.getAddress().getHostAddress();
                if(!privateIpv4(ip))continue;
                int prefix=address.getPrefixLength();
                // Limit active probing to a maximum /24, preserving tighter network masks.
                int scanPrefix=Math.max(24,Math.min(32,prefix));
                SubnetTools.Subnet subnet=SubnetTools.calculate(ip+"/"+scanPrefix);
                ArrayList<String> ips=new ArrayList<>();
                long start=toLong(subnet.network),end=toLong(subnet.broadcast);
                long first=scanPrefix>=31?start:start+1;
                long last=scanPrefix>=31?end:end-1;
                for(long i=first;i<=last;i++){
                    String candidate=SubnetTools.fmt(i);
                    if(!candidate.equals(ip))ips.add(candidate);
                }
                String gateway="";
                for(RouteInfo route:props.getRoutes()){
                    if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address){
                        gateway=route.getGateway().getHostAddress();break;
                    }
                }
                return new Plan(n,ip,gateway,ip+"/"+prefix,subnet.network+"/"+scanPrefix,ips);
            }
        }
        return null;
    }

    static long toLong(String ip){
        String[] octets=ip.split("\\.");
        long x=0;
        for(String o:octets)x=(x<<8)|Integer.parseInt(o);
        return x;
    }
    static boolean privateIpv4(String ip){
        try {
            long x=toLong(ip);
            return ((x>>>24)==10)||((x>>>20)==0xAC1)||((x>>>16)==0xC0A8);
        }catch(Exception e){return false;}
    }

    public static ScanHandle start(Plan plan,OuiDatabase oui,Callbacks cb){
        ScanHandle handle=new ScanHandle();
        handle.executor.execute(()->run(plan,oui,cb,handle));
        return handle;
    }
    private static void run(Plan plan,OuiDatabase oui,Callbacks cb,ScanHandle handle){
        ExecutorService probes=Executors.newFixedThreadPool(18);
        ArrayList<Device> found=new ArrayList<>();
        boolean arpReadable=false;
        String issue="";
        try {
            CompletionService<Probe> done=new ExecutorCompletionService<>(probes);
            for(String ip:plan.addresses)
                done.submit(()->probe(plan.network,ip,handle.stopped));
            int count=0;
            while(count<plan.addresses.size()&&!handle.stopped.get()){
                Future<Probe> task=done.take();
                Probe result=task.get();
                count++;
                if(result.reachable)found.add(new Device(result.ip,"",result.detail,"",result.ip.equals(plan.gateway)));
                if(count%7==0 || count==plan.addresses.size())
                    cb.progress(count,plan.addresses.size(),found.size());
            }
            if(handle.stopped.get())issue="Exploración cancelada.";
            Map<String,String> arp=readArp();
            arpReadable=arp!=null;
            if(arp==null)arp=Collections.emptyMap();
            // Gateway can be inferred from Android route even with all ports filtered.
            if(!handle.stopped.get()&&!plan.gateway.isEmpty()&&!contains(found,plan.gateway)){
                found.add(new Device(plan.gateway,"","Puerta de enlace anunciada por Android","",true));
            }
            ArrayList<Device> enriched=new ArrayList<>();
            for(Device d:found) {
                String mac=arp.get(d.ip);
                enriched.add(new Device(d.ip,mac==null?"":mac,d.detail,
                    oui.lookup(mac),d.gateway));
            }
            enriched.sort(Comparator.comparingLong(d->toLong(d.ip)));
            cb.finished(Collections.unmodifiableList(enriched),arpReadable,issue);
        }catch(Exception e){
            issue=handle.stopped.get()?"Exploración cancelada.":"No se ha podido terminar: "+e.getClass().getSimpleName();
            cb.finished(Collections.unmodifiableList(new ArrayList<>(found)),arpReadable,issue);
        }finally{
            probes.shutdownNow();handle.executor.shutdown();
        }
    }
    private static boolean contains(List<Device> devices,String ip){
        for(Device d:devices)if(d.ip.equals(ip))return true;
        return false;
    }
    private static final class Probe {
        final String ip,detail;
        final boolean reachable;
        Probe(String ip,boolean ok,String detail){this.ip=ip;reachable=ok;this.detail=detail;}
    }
    private static Probe probe(Network network,String ip,AtomicBoolean stopped){
        int[] ports={80,443,22,445,53,8080,9100};
        for(int port:ports){
            if(stopped.get())break;
            try(Socket socket=network.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress(ip,port),180);
                return new Probe(ip,true,"Conexión TCP aceptada: "+port);
            }catch(ConnectException e){
                String message=e.getMessage()==null?"":e.getMessage().toLowerCase(Locale.ROOT);
                if(message.contains("refused")||message.contains("rechaz")||message.contains("refus"))
                    return new Probe(ip,true,"Respuesta TCP (conexión rechazada)");
            }catch(IOException|SecurityException ignored){}
        }
        return new Probe(ip,false,"");
    }

    /**
     * Reading this file is blocked for normal third-party apps on Android 10+.
     * A returned null is an access restriction, not a blank but valid ARP table.
     */
    static Map<String,String> readArp(){
        try(BufferedReader reader=new BufferedReader(new FileReader("/proc/net/arp"))){
            Map<String,String> items=new HashMap<>();
            String line;reader.readLine();
            while((line=reader.readLine())!=null){
                String[] fields=line.trim().split("\\s+");
                if(fields.length<6)continue;
                if(!fields[3].matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))continue;
                if("00:00:00:00:00:00".equals(fields[3]))continue;
                try{
                    int flags=Integer.decode(fields[2]);
                    if((flags&2)==0)continue;
                    items.put(fields[0],fields[3].toUpperCase(Locale.ROOT));
                }catch(NumberFormatException ignored){}
            }
            return items;
        }catch(IOException|SecurityException e){return null;}
    }
}

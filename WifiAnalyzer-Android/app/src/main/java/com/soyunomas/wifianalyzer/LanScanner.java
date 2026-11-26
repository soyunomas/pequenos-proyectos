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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fast, bounded discovery on the user's own connected RFC1918 or RFC6598 Wi-Fi subnet.
 * Uses TCP responses + SSDP/UPnP replies. Neither replaces privileged ARP or detects every host.
 * Never guesses MAC addresses or manufacturer names.
 */
public final class LanScanner {
    public interface Callbacks {
        void progress(int scanned, int total, int found);
        void discovered(Device device);
        void finished(List<Device> devices, boolean arpReadable, String issue);
    }

    public static final class Device {
        public final String ip, mac, detail, vendor;
        public final boolean gateway;
        public Device(String ip,String mac,String detail,String vendor,boolean gateway) {
            this.ip=ip;this.mac=mac;this.detail=detail;this.vendor=vendor;this.gateway=gateway;
        }
    }
    public static final class Plan {
        public final Network network;
        public final String ownIp, gateway, scannedCidr, originalCidr;
        public final List<String> addresses;
        Plan(Network net,String ownIp,String gateway,String originalCidr,String scannedCidr,List<String> addresses) {
            this.network=net;this.ownIp=ownIp;this.gateway=gateway;
            this.originalCidr=originalCidr;this.scannedCidr=scannedCidr;this.addresses=addresses;
        }
    }
    public static final class ScanHandle {
        final AtomicBoolean stopped=new AtomicBoolean(false);
        final ExecutorService manager=Executors.newSingleThreadExecutor();
        volatile ExecutorService workers;
        volatile Thread services;
        public void cancel() {
            stopped.set(true);
            ExecutorService current=workers;
            if(current!=null)current.shutdownNow();
            Thread service=services;
            if(service!=null)service.interrupt();
            manager.shutdownNow();
        }
        public boolean cancelled(){return stopped.get();}
    }
    private LanScanner(){}

    public static Plan plan(Context ctx) {
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        for(Network n:cm.getAllNetworks()) {
            NetworkCapabilities caps=cm.getNetworkCapabilities(n);
            if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
            LinkProperties props=cm.getLinkProperties(n);
            if(props==null)continue;
            for(LinkAddress address:props.getLinkAddresses()) {
                if(!(address.getAddress() instanceof Inet4Address))continue;
                String ip=address.getAddress().getHostAddress();
                if(!privateIpv4(ip))continue;
                int prefix=address.getPrefixLength();
                int scanPrefix=boundedPrefix(prefix);
                SubnetTools.Subnet subnet=SubnetTools.calculate(ip+"/"+scanPrefix);
                List<String> ips=candidateAddresses(ip,prefix);
                String gateway="";
                for(RouteInfo route:props.getRoutes()) {
                    if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address){
                        gateway=route.getGateway().getHostAddress();break;
                    }
                }
                return new Plan(n,ip,gateway,ip+"/"+prefix,subnet.network+"/"+scanPrefix,
                        prioritize(ips,gateway));
            }
        }
        return null;
    }

    static List<String> prioritize(List<String> addresses,String gateway) {
        List<String> list=new ArrayList<>(addresses);
        Collections.sort(list, Comparator.comparingInt((String ip)->priority(ip,gateway))
                .thenComparingLong(LanScanner::toLong));
        return list;
    }
    private static int priority(String ip,String gateway) {
        if(ip.equals(gateway))return 0;
        int last=Integer.parseInt(ip.substring(ip.lastIndexOf('.')+1));
        if(last==1||last==254)return 1;
        if(last==2||last==10||last==20||last==100)return 2;
        return 3;
    }
    /** Limit to 1024 addresses. Scan full /22 networks instead of silently cutting to /24. */
    static int boundedPrefix(int prefix){
        if(prefix<0||prefix>32)throw new IllegalArgumentException("Invalid prefix");
        return Math.max(22,prefix);
    }
    static List<String> candidateAddresses(String ip,int prefix){
        if(!privateIpv4(ip))throw new IllegalArgumentException("Not a local RFC1918/RFC6598 address");
        int scanPrefix=boundedPrefix(prefix);
        SubnetTools.Subnet subnet=SubnetTools.calculate(ip+"/"+scanPrefix);
        long first=toLong(subnet.network)+(scanPrefix>=31?0:1);
        long last=toLong(subnet.broadcast)-(scanPrefix>=31?0:1);
        List<String> addresses=new ArrayList<>();
        for(long n=first;n<=last;n++){
            String candidate=SubnetTools.fmt(n);
            if(!candidate.equals(ip))addresses.add(candidate);
        }
        return addresses;
    }
    /** Strict IPv4 parsing, so malformed 100.x addresses cannot pass local checks. */
    static long toLong(String ip) {
        if(ip==null)throw new IllegalArgumentException("Missing IPv4");
        String[] parts=ip.split("\\.",-1);
        if(parts.length!=4)throw new IllegalArgumentException("Invalid IPv4");
        long value=0;
        for(String part:parts){
            if(!part.matches("[0-9]{1,3}"))throw new IllegalArgumentException("Invalid IPv4 octet");
            int octet=Integer.parseInt(part);
            if(octet>255)throw new IllegalArgumentException("Invalid IPv4 octet");
            value=(value<<8)|octet;
        }
        return value;
    }
    /** RFC1918 private ranges and RFC6598 shared address space 100.64.0.0/10. */
    static boolean privateIpv4(String ip) {
        try {
            long n=toLong(ip);
            return (n>>>24)==10||(n>>>20)==0xAC1||(n>>>16)==0xC0A8||
                ((n>>>22)==((100L<<24|64L<<16)>>>22));
        }catch(RuntimeException e){return false;}
    }

    public static ScanHandle start(Plan plan,OuiDatabase oui,boolean deep,Callbacks callbacks) {
        ScanHandle handle=new ScanHandle();
        handle.manager.execute(()->scan(plan,oui,deep,callbacks,handle));
        return handle;
    }
    private static void scan(Plan plan,OuiDatabase oui,boolean deep,Callbacks cb,ScanHandle h) {
        Map<String,Device> visible=new ConcurrentHashMap<>();
        boolean arpReadable=false;
        String issue="";
        int checked=0;
        int total=plan.addresses.size();
        ExecutorService workers=Executors.newFixedThreadPool(deep?36:48);
        h.workers=workers;
        try {
            // The gateway and own phone are shown immediately and are not counted as TCP discoveries.
            if(!plan.ownIp.isEmpty())report(visible,new Device(plan.ownIp,"",
                    "Este teléfono (dirección local)","MAC no disponible",false),cb);
            if(!plan.gateway.isEmpty()&&privateIpv4(plan.gateway))
                report(visible,new Device(plan.gateway,"",
                    "Puerta de enlace (información de Android)","MAC no disponible",true),cb);

            h.services=new Thread(()->ssdp(plan,visible,cb,h.stopped),"netscope-ssdp");
            h.services.setDaemon(true);
            h.services.start();

            CompletionService<Probe> completion=new ExecutorCompletionService<>(workers);
            for(String address:plan.addresses)
                completion.submit(()->tcpProbe(plan.network,address,deep,h.stopped));

            while(checked<total&&!h.stopped.get()) {
                Future<Probe> task=completion.poll(250,TimeUnit.MILLISECONDS);
                if(task==null)continue;
                Probe result=task.get();
                checked++;
                if(result.reachable) {
                    Device existing=visible.get(result.ip);
                    boolean gateway=result.ip.equals(plan.gateway);
                    String detail=result.detail;
                    if(existing!=null&&existing.detail.contains("SSDP"))
                        detail=existing.detail+" · "+detail;
                    report(visible,new Device(result.ip,result.mac,detail,
                            result.mac.isEmpty()?"MAC no disponible":oui==null?"Base IEEE pendiente":oui.lookup(result.mac),gateway),cb);
                }
                if(checked%5==0||checked==total)cb.progress(checked,total,visible.size());
            }
            // Let a short SSDP discovery complete, but don't block results for a slow network.
            Thread service=h.services;
            if(service!=null&&service.isAlive()&&!h.stopped.get())service.join(1300);
            if(h.stopped.get())issue="Búsqueda detenida. Se conservan resultados parciales.";
            Map<String,String> arp=readArp();
            arpReadable=arp!=null;
            if(arp==null)arp=Collections.emptyMap();
            List<Device> finalList=new ArrayList<>();
            for(Device d:visible.values()){
                String mac=arp.get(d.ip);
                if(mac==null||mac.isEmpty())mac=d.mac;
                finalList.add(new Device(d.ip,mac,d.detail,oui==null?"Base IEEE pendiente":oui.lookup(mac),d.gateway));
            }
            finalList.sort(Comparator.comparingLong(d->toLong(d.ip)));
            cb.finished(Collections.unmodifiableList(finalList),arpReadable,issue);
        }catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            cb.finished(snapshot(visible),false,"Búsqueda detenida. Resultados parciales.");
        }catch(Exception e) {
            cb.finished(snapshot(visible),false,"Exploración incompleta: "+e.getClass().getSimpleName());
        }finally {
            h.stopped.set(true);
            if(h.services!=null)h.services.interrupt();
            workers.shutdownNow();
            h.manager.shutdown();
        }
    }
    private static List<Device> snapshot(Map<String,Device> visible) {
        List<Device> results=new ArrayList<>(visible.values());
        results.sort(Comparator.comparingLong(d->toLong(d.ip)));
        return results;
    }
    private static void report(Map<String,Device> map,Device device,Callbacks callbacks) {
        Device old=map.put(device.ip,device);
        if(old==null||!old.detail.equals(device.detail))callbacks.discovered(device);
    }
    private static final class Probe {
        final String ip,detail,mac;
        final boolean reachable;
        Probe(String ip,boolean reachable,String detail,String mac) {
            this.ip=ip;this.reachable=reachable;this.detail=detail;this.mac=mac;
        }
    }
    private static Probe tcpProbe(Network network,String ip,boolean deep,AtomicBoolean cancelled) {
        int[] ports=deep?new int[]{80,443,22,445,53,8080,9100}:
                          new int[]{80,443,22};
        int timeout=deep?160:115;
        for(int port:ports) {
            if(cancelled.get()||Thread.currentThread().isInterrupted())break;
            try(Socket socket=network.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress(ip,port),timeout);
                return withNetbios(network,ip,"TCP "+port+" responde");
            }catch(ConnectException e) {
                // Connection refused is proof of an IP host; no successful app connection needed.
                String detail=e.getMessage()==null?"":e.getMessage().toLowerCase(Locale.ROOT);
                if(detail.contains("refused")||detail.contains("rechaz")||detail.contains("refus"))
                    return withNetbios(network,ip,"TCP "+port+" rechazado (host activo)");
            }catch(IOException|SecurityException ignored){}
        }
        NetBiosProbe.Result nb=NetBiosProbe.query(network,ip,deep?180:90);
        if(nb!=null)return new Probe(ip,true,nb.name.isEmpty()?"NetBIOS activo":"NetBIOS: "+nb.name,nb.mac);
        return new Probe(ip,false,"","");
    }

    private static Probe withNetbios(Network net,String ip,String detail) {
        NetBiosProbe.Result nb=NetBiosProbe.query(net,ip,90);
        if(nb==null)return new Probe(ip,true,detail,"");
        return new Probe(ip,true,detail+(nb.name.isEmpty()?"":" · NetBIOS: "+nb.name),nb.mac);
    }

    /** Discover UPnP/SSDP responders with one bounded M-SEARCH on the connected Wi-Fi network. */
    private static void ssdp(Plan plan,Map<String,Device> map,Callbacks cb,AtomicBoolean cancelled) {
        byte[] request=("M-SEARCH * HTTP/1.1\r\n"+
                "HOST: 239.255.255.250:1900\r\n"+
                "MAN: \"ssdp:discover\"\r\n"+
                "MX: 1\r\n"+
                "ST: ssdp:all\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        try(DatagramSocket socket=new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            plan.network.bindSocket(socket);
            socket.setSoTimeout(200);
            socket.send(new DatagramPacket(request,request.length,
                    InetAddress.getByName("239.255.255.250"),1900));
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1150);
            byte[] buf=new byte[1600];
            while(!cancelled.get()&&!Thread.currentThread().isInterrupted()&&System.nanoTime()<until) {
                DatagramPacket incoming=new DatagramPacket(buf,buf.length);
                try{socket.receive(incoming);}catch(SocketTimeoutException e){continue;}
                String ip=incoming.getAddress().getHostAddress();
                if(!privateIpv4(ip)||ip.equals(plan.ownIp))continue;
                // Never accept responses outside the connected subnet being explored.
                if(!plan.addresses.contains(ip)&&!ip.equals(plan.gateway))continue;
                String reply=new String(incoming.getData(),incoming.getOffset(),
                        incoming.getLength(),StandardCharsets.ISO_8859_1);
                if(!reply.startsWith("HTTP/1.1 200")&&!reply.startsWith("HTTP/1.0 200"))continue;
                Device previous=map.get(ip);
                String detail="UPnP / SSDP";
                for(String line:reply.split("\\r?\\n")) {
                    if(line.regionMatches(true,0,"SERVER:",0,7)){
                        String announced=line.substring(7).trim();
                        if(announced.length()>95)announced=announced.substring(0,95);
                        if(!announced.isEmpty())detail+=" · "+announced;
                        break;
                    }
                }
                if(previous!=null)detail=previous.detail+" · "+detail;
                report(map,new Device(ip,previous==null?"":previous.mac,detail,"MAC no disponible",
                        ip.equals(plan.gateway)),cb);
            }
        }catch(IOException|SecurityException ignored){
            // Multicast may be disabled by the AP/Android. TCP discovery still works.
        }
    }

    /** /proc/net/arp remains unavailable to ordinary apps on most Android 10+ devices. */
    static Map<String,String> readArp() {
        try(BufferedReader reader=new BufferedReader(new FileReader("/proc/net/arp"))){
            Map<String,String> records=new HashMap<>();
            String line;reader.readLine();
            while((line=reader.readLine())!=null) {
                String[] fields=line.trim().split("\\s+");
                if(fields.length<6)continue;
                if(!fields[3].matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))continue;
                if("00:00:00:00:00:00".equals(fields[3]))continue;
                try{
                    int flags=Integer.decode(fields[2]);
                    if((flags&2)==0)
                        continue;
                    records.put(fields[0],fields[3].toUpperCase(Locale.ROOT));
                }catch(NumberFormatException ignored){}
            }
            return records;
        }catch(IOException|SecurityException e){return null;}
    }
}

package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** On-demand reachability and local network protocol probes, never background telemetry. */
public final class LocalTools {
    private LocalTools(){}

    public static String execute(Context ctx,Network net,int id,String argument)throws Exception{
        if(net==null)throw new IOException("No hay conexión Wi-Fi activa");
        switch(id){
            case 1:return ping(net,argument,false);
            case 2:return ping(net,argument,true);
            case 3:return trace(argument,false);
            case 4:return trace(argument,true);
            case 10:return monitor(net,argument);
            case 11:return ipv6(ctx,net,argument);
            case 12:{
                WifiManager wifi=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiManager.MulticastLock lock=wifi==null?null:wifi.createMulticastLock("netscope-mdns");
                if(lock!=null){lock.setReferenceCounted(false);lock.acquire();}
                try{return mdns(net,argument);}
                finally{if(lock!=null&&lock.isHeld())lock.release();}
            }
            case 13:return ssdp(net,argument);
            case 14:return snmp(net,argument);
            case 15:return wol(net,argument);
            case 21:return mtu(ctx,argument);
            case 22:return ntp(net,argument);
            case 23:return dhcp(ctx);
            default:throw new IllegalArgumentException("Herramienta local incorrecta");
        }
    }
    static String host(String s) {
        String value=s==null?"":s.trim();
        if(value.length()<1||value.length()>253||
            !value.matches("(?i)[a-z0-9:._%-]+"))
            throw new IllegalArgumentException("Host o IP no válido");
        return value;
    }
    private static InetAddress resolve(Network net,String domain)throws Exception{
        return net.getAllByName(host(domain))[0];
    }

    private static String ping(Network net,String domain,boolean continuous)throws Exception{
        InetAddress addr=resolve(net,domain);
        int count=continuous?10:4,received=0;
        long min=Long.MAX_VALUE,max=0,sum=0;
        StringBuilder out=new StringBuilder(continuous?
            "Alcanzabilidad repetida · 10 muestras\n":"Prueba de disponibilidad · 4 muestras\n");
        out.append("Destino: ").append(addr.getHostAddress()).append("\n");
        for(int i=0;i<count&&!Thread.currentThread().isInterrupted();i++){
            long start=System.nanoTime();
            boolean ok;
            try{ok=addr.isReachable(1100);}catch(IOException e){ok=false;}
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            if(ok){
                received++;min=Math.min(min,elapsed);max=Math.max(max,elapsed);sum+=elapsed;
                out.append("Sondeo ").append(i+1).append(": respuesta en ").append(elapsed).append(" ms\n");
            }else out.append("Sondeo ").append(i+1).append(": sin respuesta (filtrado o desconectado)\n");
            if(i<count-1&&continuous)Thread.sleep(240);
        }
        out.append("\nRecibidos: ").append(received).append("/").append(count)
           .append("\nPérdida aparente: ").append(100*(count-received)/count).append("%\n");
        if(received>0)out.append("RTT mín/media/máx (sondeo de alcanzabilidad): ")
            .append(min).append("/").append(sum/received).append("/").append(max).append(" ms\n");
        out.append("\nAndroid InetAddress.isReachable: puede utilizar ICMP o una alternativa TCP. "+
            "No es equivalente a una medición ICMP garantizada ni permite inferir jitter exacto.");
        return out.toString();
    }

    private static String trace(String target,boolean mtr)throws Exception{
        host(target);
        String program=findExecutable(mtr?"mtr":"traceroute");
        if(program!=null){
            List<String> args=new ArrayList<>();
            args.add(program);
            if(mtr){args.add("--report");args.add("--report-cycles");args.add("3");}
            else{args.add("-m");args.add("12");args.add("-w");args.add("1");}
            args.add(target);
            return runNative(args,16000);
        }
        if(mtr)return "MTR real requiere un ejecutable nativo compatible disponible para esta aplicación. "+
            "No hay uno instalado. No se simulan pérdida o jitter por salto. "+
            "Puedes usar MTR desde un equipo Linux o desde Termux con los permisos correspondientes.";
        String ping=findExecutable("ping");
        if(ping==null)return "Traceroute requiere ICMP/UDP TTL o un binario traceroute permitido por Android. "+
            "No se encontró uno compatible.";
        StringBuilder output=new StringBuilder("Trazado experimental con TTL usando ping del sistema.\n");
        output.append("Puede usar otra interfaz si hay VPN o datos móviles.\n\n");
        for(int ttl=1;ttl<=10&&!Thread.currentThread().isInterrupted();ttl++){
            String reply=runNative(Arrays.asList(ping,"-n","-c","1","-W","1","-t",Integer.toString(ttl),target),2500);
            String[] lines=reply.split("\\n");
            String hop="Sin respuesta";
            for(String line:lines){
                if(line.contains("From ")||line.contains("from ")||line.contains("bytes from")){
                    hop=line.length()>120?line.substring(0,120):line;break;
                }
                if(line.contains("invalid option")||line.contains("usage:")){
                    return "El ping del dispositivo no admite TTL. Traceroute no disponible sin soporte nativo.";
                }
            }
            output.append(ttl).append(". ").append(hop).append("\n");
            if(hop.contains("bytes from"))break;
        }
        return output.toString();
    }
    private static String findExecutable(String command){
        for(String path:new String[]{"/system/bin/","/system/xbin/","/vendor/bin/"}){
            File f=new File(path+command);
            if(f.exists()&&f.canExecute())return f.getAbsolutePath();
        }
        return null;
    }
    private static String runNative(List<String> command,long timeoutMs)throws Exception{
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Thread collector=new Thread(()->{
            try(InputStream stream=process.getInputStream()){
                byte[] b=new byte[512];int n;
                while((n=stream.read(b))>=0&&bytes.size()<8000)
                    bytes.write(b,0,Math.min(n,8000-bytes.size()));
            }catch(IOException ignored){}
        },"netscope-process");
        collector.setDaemon(true);collector.start();
        boolean finished=process.waitFor(timeoutMs,TimeUnit.MILLISECONDS);
        if(!finished)process.destroyForcibly();
        collector.join(400);
        return new String(bytes.toByteArray(),StandardCharsets.UTF_8)+
            (finished?"":"\n[Proceso detenido por tiempo máximo]");
    }
    private static String monitor(Network net,String target)throws Exception{
        String[] tokens=target.split(":",2);
        String host=host(tokens[0]);
        int port=tokens.length>1?PortCatalog.parse(tokens[1]):443;
        InetAddress dest=resolve(net,host);
        StringBuilder out=new StringBuilder("Monitor TCP · 5 lecturas\nHost: "+host+":"+port+"\n");
        int ok=0;String previous="";
        for(int i=0;i<5&&!Thread.currentThread().isInterrupted();i++){
            boolean active=false;long start=System.nanoTime();
            try(Socket socket=net.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress(dest,port),1400);active=true;
            }catch(IOException ignored){}
            String state=active?"DISPONIBLE":"SIN RESPUESTA";
            if(active)ok++;
            out.append("Lectura ").append(i+1).append(": ").append(state)
                .append(" · ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)).append(" ms");
            if(!previous.isEmpty()&&!previous.equals(state))out.append(" · CAMBIO DE ESTADO");
            out.append("\n");previous=state;
            if(i<4)Thread.sleep(600);
        }
        out.append("Respuestas TCP: ").append(ok).append("/5. No es una supervisión en segundo plano.");
        return out.toString();
    }
    private static String ipv6(Context ctx,Network net,String target)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties lp=cm.getLinkProperties(net);
        StringBuilder out=new StringBuilder("Diagnóstico IPv6\n");
        if(lp!=null){
            for(LinkAddress a:lp.getLinkAddresses())
                if(a.getAddress() instanceof Inet6Address)
                    out.append("Dirección: ").append(a).append("\n");
            for(InetAddress dns:lp.getDnsServers())
                if(dns instanceof Inet6Address)out.append("DNS IPv6: ").append(dns.getHostAddress()).append("\n");
            for(RouteInfo route:lp.getRoutes())
                if(route.isDefaultRoute()&&route.getGateway() instanceof Inet6Address)
                    out.append("Gateway IPv6: ").append(route.getGateway().getHostAddress()).append("\n");
        }
        try{
            InetAddress[] addresses=net.getAllByName(host(target));
            for(InetAddress address:addresses){
                if(address instanceof Inet6Address) {
                    long before=System.nanoTime();
                    boolean reachable=address.isReachable(1600);
                    out.append("Prueba a ").append(address.getHostAddress())
                        .append(": ").append(reachable?"respuesta":"sin respuesta")
                        .append(" en ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)).append(" ms\n");
                }
            }
        }catch(Exception e){out.append("Prueba IPv6 no disponible: ").append(e.getClass().getSimpleName());}
        return out.toString();
    }
    private static String mdns(Network net,String service)throws Exception{
        String domain=service.trim();
        if(!domain.matches("(?i)[a-z0-9_.-]{3,200}"))
            throw new IllegalArgumentException("Nombre mDNS no válido");
        byte[] label=question(domain,12);
        Set<String> answers=new LinkedHashSet<>();
        InetAddress multicast=InetAddress.getByName("224.0.0.251");
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.setReuseAddress(true);socket.bind(new InetSocketAddress(0));net.bindSocket(socket);
            socket.setSoTimeout(280);
            socket.send(new DatagramPacket(label,label.length,multicast,5353));
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1900);
            while(System.nanoTime()<until&&!Thread.currentThread().isInterrupted()){
                byte[] packet=new byte[8192];DatagramPacket dp=new DatagramPacket(packet,packet.length);
                try{socket.receive(dp);}catch(SocketTimeoutException e){continue;}
                byte[] response=Arrays.copyOf(packet,dp.getLength());
                try{
                    int records=DnsTools.u16(response,6);
                    int pos=12;
                    for(int q=0;q<DnsTools.u16(response,4);q++)pos=DnsTools.skipName(response,pos)+4;
                    for(int i=0;i<records;i++){
                        pos=DnsTools.skipName(response,pos);
                        if(pos+10>response.length)break;
                        int t=DnsTools.u16(response,pos),length=DnsTools.u16(response,pos+8);
                        pos+=10;
                        if(pos+length>response.length)break;
                        if(t==12)answers.add(DnsTools.name(response,pos)+" · "+dp.getAddress().getHostAddress());
                        pos+=length;
                    }
                }catch(Exception ignored){}
            }
        }
        return "Consulta mDNS/DNS-SD: "+domain+"\n"+
            (answers.isEmpty()?"Sin anuncios multicast recibidos. Algunas redes aíslan el tráfico mDNS.":String.join("\n",answers));
    }
    private static byte[] question(String domain,int type)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(0);out.writeShort(0);out.writeShort(1);
        out.writeShort(0);out.writeShort(0);out.writeShort(0);
        for(String segment:domain.split("\\.")){
            byte[] part=segment.getBytes(StandardCharsets.US_ASCII);
            if(part.length>63)throw new IOException("Etiqueta demasiado larga");
            out.writeByte(part.length);out.write(part);
        }
        out.writeByte(0);out.writeShort(type);out.writeShort(1);
        return bytes.toByteArray();
    }
    private static String ssdp(Network net,String st)throws Exception{
        if(st==null||st.length()>70||!st.matches("[a-zA-Z0-9:._-]+"))
            throw new IllegalArgumentException("Servicio ST incorrecto");
        byte[] data=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"+
            "MAN: \"ssdp:discover\"\r\nMX: 1\r\nST: "+st+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        Map<String,String> replies=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.setReuseAddress(true);socket.bind(new InetSocketAddress(0));
            net.bindSocket(socket);socket.setSoTimeout(250);
            socket.send(new DatagramPacket(data,data.length,InetAddress.getByName("239.255.255.250"),1900));
            long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1800);
            while(System.nanoTime()<until&&!Thread.currentThread().isInterrupted()){
                DatagramPacket packet=new DatagramPacket(new byte[2048],2048);
                try{socket.receive(packet);}catch(SocketTimeoutException e){continue;}
                String text=new String(packet.getData(),0,packet.getLength(),StandardCharsets.ISO_8859_1);
                if(text.startsWith("HTTP/1.")){
                    StringBuilder lines=new StringBuilder(packet.getAddress().getHostAddress());
                    for(String part:text.split("\\r?\\n"))if(part.matches("(?i)^(server|st|usn|location):.*"))
                        lines.append("\n  ").append(part.length()>160?part.substring(0,160):part);
                    replies.put(packet.getAddress().getHostAddress(),lines.toString());
                }
            }
        }
        return "Exploración SSDP\n"+(replies.isEmpty()?"Sin respuestas multicast.":String.join("\n\n",replies.values()));
    }
    private static String snmp(Network net,String addr)throws Exception{
        String ip=host(addr);
        if(!LanScanner.privateIpv4(ip))throw new IllegalArgumentException("SNMP limitado a la LAN privada");
        // SNMP v1 GET 1.3.6.1.2.1.1.1.0 (sysDescr.0), community public.
        byte[] oid={43,6,1,2,1,1,1,0};
        byte[] varbind=ber(0x30,concat(ber(0x06,oid),ber(0x05,new byte[0])));
        byte[] vars=ber(0x30,varbind);
        byte[] pdu=ber(0xA0,concat(ber(0x02,new byte[]{1}),ber(0x02,new byte[]{0}),
            ber(0x02,new byte[]{0}),vars));
        byte[] msg=ber(0x30,concat(ber(0x02,new byte[]{0}),
            ber(0x04,"public".getBytes(StandardCharsets.US_ASCII)),pdu));
        InetAddress host=net.getAllByName(ip)[0];
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(1700);
            socket.send(new DatagramPacket(msg,msg.length,host,161));
            DatagramPacket answer=new DatagramPacket(new byte[2048],2048);
            socket.receive(answer);
            byte[] resp=Arrays.copyOf(answer.getData(),answer.getLength());
            StringBuilder readable=new StringBuilder();
            for(byte b:resp){
                int c=b&255;
                if(c>=32&&c<=126)readable.append((char)c);
            }
            return "Respuesta SNMP v1 recibida de "+answer.getAddress().getHostAddress()+
                "\nOID: sysDescr.0\nDatos textuales extraídos de BER (parcial):\n"+
                readable.toString().substring(0,Math.min(readable.length(),500))+
                "\n\nLa comunidad public puede estar deshabilitada. No se consultan credenciales privadas.";
        }catch(SocketTimeoutException e){
            return "Sin respuesta SNMP UDP/161. El equipo puede no admitir SNMP v1 o la comunidad public.";
        }
    }
    private static byte[] ber(int tag,byte[] data)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(tag);
        if(data.length<128)out.write(data.length);
        else if(data.length<256){out.write(0x81);out.write(data.length);}
        else{out.write(0x82);out.write(data.length>>>8);out.write(data.length&255);}
        out.write(data);return out.toByteArray();
    }
    private static byte[] concat(byte[]... arrays)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        for(byte[] a:arrays)b.write(a);
        return b.toByteArray();
    }
    private static String wol(Network net,String mac)throws Exception{
        String clean=mac.replace(":","").replace("-","").replace(".","").toUpperCase(Locale.ROOT);
        if(!clean.matches("[A-F0-9]{12}"))throw new IllegalArgumentException("MAC de 12 hexadecimales incorrecta");
        byte[] address=new byte[6];
        for(int i=0;i<6;i++)address[i]=(byte)Integer.parseInt(clean.substring(i*2,i*2+2),16);
        byte[] magic=new byte[102];
        Arrays.fill(magic,0,6,(byte)0xff);
        for(int i=0;i<16;i++)System.arraycopy(address,0,magic,6+i*6,6);
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.setBroadcast(true);socket.bind(new InetSocketAddress(0));net.bindSocket(socket);
            socket.send(new DatagramPacket(magic,magic.length,InetAddress.getByName("255.255.255.255"),9));
        }
        return "Paquete Wake-on-LAN de 102 bytes enviado por UDP/9 a la MAC "+mac+
            "\nNo garantiza que el dispositivo despierte: requiere WoL habilitado, cableado y compatibilidad del router.";
    }
    private static String mtu(Context ctx,String domain)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkDiagnostics.Snapshot snap=NetworkDiagnostics.capture(ctx);
        LinkProperties props=cm.getLinkProperties(snap.network);
        int mtu=props==null?0:props.getMtu();
        StringBuilder s=new StringBuilder("MTU de interfaz: "+(mtu>0?mtu+" bytes":"no informada")+
            "\nPath MTU requiere sondas DF / ICMP que Android no garantiza en aplicaciones sin root.\n");
        String binary=findExecutable("ping");
        if(binary!=null&&mtu>28){
            String cmd=runNative(Arrays.asList(binary,"-c","1","-W","2","-M","do","-s",
                Integer.toString(mtu-28),host(domain)),4200);
            s.append("\nSonda ICMP IPv4 con DF (si el ejecutable la acepta):\n").append(cmd);
        }else s.append("\nNo existe un ejecutable ICMP con capacidad DF disponible.");
        return s.toString();
    }
    private static String ntp(Network net,String hostname)throws Exception{
        InetAddress ip=resolve(net,hostname);
        byte[] bytes=new byte[48];bytes[0]=0x1B;
        long start=System.currentTimeMillis();
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(2600);
            socket.send(new DatagramPacket(bytes,bytes.length,ip,123));
            DatagramPacket received=new DatagramPacket(new byte[48],48);
            socket.receive(received);
            long end=System.currentTimeMillis();
            if(received.getLength()<48)throw new IOException("Respuesta NTP incompleta");
            byte[] packet=received.getData();
            int mode=packet[0]&7;int stratum=packet[1]&255;
            if(mode!=4||stratum==0)throw new IOException("Respuesta NTP inválida");
            long seconds=0,fraction=0;
            for(int i=40;i<44;i++)seconds=(seconds<<8)|(packet[i]&255);
            for(int i=44;i<48;i++)fraction=(fraction<<8)|(packet[i]&255);
            long server=(seconds-2208988800L)*1000+(fraction*1000L>>>32);
            long mid=start+(end-start)/2;
            return "NTP "+hostname+"\nServidor: "+new Date(server)+
                "\nEstrato: "+stratum+"\nRTT aproximado: "+(end-start)+" ms"+
                "\nDesfase aproximado (servidor - teléfono): "+(server-mid)+" ms";
        }catch(SocketTimeoutException e){
            return "NTP no respondió en UDP/123. El proveedor o router puede bloquearlo.";
        }
    }
    @SuppressWarnings("deprecation")
    private static String dhcp(Context ctx){
        WifiManager wm=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo d=wm==null?null:wm.getDhcpInfo();
        if(d==null)return "Android no ha proporcionado datos DHCP (puede usar IP estática).";
        return "DHCP/IPv4 disponible en Android\nIP: "+addr(d.ipAddress)+
            "\nMáscara: "+addr(d.netmask)+"\nGateway: "+addr(d.gateway)+
            "\nDNS1: "+addr(d.dns1)+"\nDNS2: "+addr(d.dns2)+
            "\nServidor DHCP: "+addr(d.serverAddress)+
            "\nLease: "+d.leaseDuration+" segundos\n"+
            "La API DHCP antigua no ofrece todos los campos de DHCPv6 ni de las opciones avanzadas.";
    }
    private static String addr(int val){
        return (val&255)+"."+((val>>8)&255)+"."+((val>>16)&255)+"."+((val>>24)&255);
    }
}

package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.Network;
import android.net.wifi.WifiManager;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Probe responses from the connected Wi-Fi broadcast domain; never raw ARP or root. */
public final class FieldDiscovery {
    private FieldDiscovery(){}
    private static final String WS_PROBE="<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
        "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\" "+
        "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "+
        "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">"+
        "<s:Header><a:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</a:Action>"+
        "<a:MessageID>urn:uuid:8b71b284-3f18-498d-acad-29cb12b9d1c8</a:MessageID>"+
        "<a:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</a:To></s:Header>"+
        "<s:Body><d:Probe/></s:Body></s:Envelope>";
    public static String execute(Context context,Network net,int id,String input)throws Exception{
        if(net==null)return "Sin conexión Wi-Fi activa.";
        WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock=wifi==null?null:wifi.createMulticastLock("netscope-discovery");
        if(lock!=null){lock.setReferenceCounted(false);lock.acquire();}
        try {
            return id==41?ws(net):combine(net,input);
        }finally{if(lock!=null&&lock.isHeld())lock.release();}
    }
    private static String combine(Network net,String st)throws Exception{
        StringBuilder out=new StringBuilder("Descubrimiento de anuncios de capa de aplicación\n");
        out.append("No se envía ARP: un equipo fuera de subred solo aparece si anuncia servicios en la LAN.\n\n");
        out.append("SSDP / UPnP:\n").append(ssdp(net,st)).append("\n\n");
        out.append("WS-Discovery:\n").append(ws(net)).append("\n\n");
        out.append("mDNS / DNS-SD:\n").append(mdns(net)).append("\n");
        out.append("\nIP de respuesta es la observada en el datagrama; los XAddrs los declara el dispositivo.");
        return out.toString();
    }
    private static String ssdp(Network net,String service)throws Exception{
        String st=service==null||service.isEmpty()?"ssdp:all":service.trim();
        if(!st.matches("(?i)[a-z0-9:._-]{3,110}"))throw new IllegalArgumentException("Servicio SSDP inválido");
        byte[] request=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"+
            "MAN: \"ssdp:discover\"\r\nMX: 1\r\nST: "+st+"\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
        return listen(net,request,"239.255.255.250",1900,false);
    }
    private static String ws(Network net)throws Exception{
        return listen(net,WS_PROBE.getBytes(StandardCharsets.UTF_8),"239.255.255.250",3702,true);
    }
    private static String listen(Network net,byte[] request,String destination,int port,boolean xml)throws Exception{
        Map<String,String> responses=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);
            socket.setSoTimeout(250);
            socket.send(new DatagramPacket(request,request.length,InetAddress.getByName(destination),port));
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1800);
            while(System.nanoTime()<end&&!Thread.currentThread().isInterrupted()&&responses.size()<40){
                DatagramPacket reply=new DatagramPacket(new byte[5000],5000);
                try{socket.receive(reply);}catch(SocketTimeoutException e){continue;}
                String host=reply.getAddress().getHostAddress();
                String payload=new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8);
                if(xml){
                    if(!payload.contains("ProbeMatch")&&!payload.contains("Hello"))continue;
                    Matcher urls=Pattern.compile("(?is)<(?:[a-z0-9_]+:)?XAddrs[^>]*>([^<]{1,1200})</")
                        .matcher(payload);
                    String refs=urls.find()?urls.group(1).trim():"Sin XAddrs";
                    responses.put(host,host+" · "+refs);
                }else if(payload.startsWith("HTTP/1.")){
                    StringBuilder lines=new StringBuilder(host);
                    for(String line:payload.split("\\r?\\n")){
                        if(line.matches("(?i)^(ST|SERVER|USN|LOCATION):.*")){
                            lines.append("\n  ").append(line.substring(0,Math.min(190,line.length())));
                        }
                    }
                    responses.put(host,lines.toString());
                }
            }
        }
        return responses.isEmpty()?"Sin respuestas a consultas multicast.":String.join("\n\n",responses.values());
    }
    private static String mdns(Network net)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        DataOutputStream d=new DataOutputStream(output);
        d.writeShort(0);d.writeShort(0);d.writeShort(1);d.writeShort(0);d.writeShort(0);d.writeShort(0);
        for(String label:new String[]{"_services","_dns-sd","_udp","local"}){
            byte[] b=label.getBytes(StandardCharsets.US_ASCII);d.writeByte(b.length);d.write(b);
        }
        d.writeByte(0);d.writeShort(12);d.writeShort(1);
        Map<String,String> services=new LinkedHashMap<>();
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(220);
            byte[] query=output.toByteArray();
            socket.send(new DatagramPacket(query,query.length,InetAddress.getByName("224.0.0.251"),5353));
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1600);
            while(System.nanoTime()<end&&!Thread.currentThread().isInterrupted()&&services.size()<40){
                DatagramPacket reply=new DatagramPacket(new byte[8192],8192);
                try{socket.receive(reply);}catch(SocketTimeoutException e){continue;}
                byte[] p=Arrays.copyOf(reply.getData(),reply.getLength());
                try{
                    int qd=DnsTools.u16(p,4),answers=DnsTools.u16(p,6);
                    int pos=12;
                    if(qd>20||answers>80)continue;
                    for(int i=0;i<qd;i++)pos=DnsTools.skipName(p,pos)+4;
                    for(int i=0;i<answers;i++){
                        pos=DnsTools.skipName(p,pos);
                        if(pos+10>p.length)break;
                        int type=DnsTools.u16(p,pos),length=DnsTools.u16(p,pos+8);pos+=10;
                        if(pos+length>p.length)break;
                        if(type==12&&length>0){
                            String key=DnsTools.name(p,pos);
                            services.put(key,reply.getAddress().getHostAddress()+" · "+key);
                        }
                        pos+=length;
                    }
                }catch(Exception ignored){}
            }
        }
        return services.isEmpty()?"Sin servicios PTR anunciados.":String.join("\n",services.values());
    }
}

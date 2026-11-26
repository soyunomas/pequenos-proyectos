package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** STUN, PCP, NAT-PMP and read-only UPnP IGD; no mapping changes. */
public final class FieldNat {
    private FieldNat(){}
    private static String gateway(Context context,Network net)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LinkProperties lp=cm.getLinkProperties(net);
        if(lp==null)throw new IOException("Sin propiedades de enlace Wi-Fi");
        for(RouteInfo route:lp.getRoutes()){
            if(route.isDefaultRoute()&&route.getGateway()!=null&&
                route.getGateway().getAddress().length==4)return route.getGateway().getHostAddress();
        }
        throw new IOException("No hay gateway IPv4 en la Wi-Fi");
    }
    private static String local(String ip)throws Exception{
        if(ip==null||!LanScanner.privateIpv4(ip.trim()))
            throw new IllegalArgumentException("La prueba requiere gateway IP de la LAN");
        return ip.trim();
    }
    public static String execute(Context context,Network net,int id,String input)throws Exception{
        if(id==36)return stun(net,input);
        if(id==38)return natPmpAndPcp(net,input.isEmpty()?gateway(context,net):local(input));
        if(id==37)return igd(net);
        throw new IllegalArgumentException("Modo NAT incorrecto");
    }
    private static String stun(Network net,String target)throws Exception{
        String raw=target==null||target.isEmpty()?"stun.l.google.com:19302":target.trim();
        int at=raw.lastIndexOf(':');
        if(at<1)throw new IllegalArgumentException("Introduce host:puerto STUN");
        String host=raw.substring(0,at), portString=raw.substring(at+1);
        int port=PortCatalog.parse(portString);
        InetAddress server=net.getAllByName(LocalTools.host(host))[0];
        byte[] tx=new byte[12];new SecureRandom().nextBytes(tx);
        byte[] request=FieldWire.stunRequest(tx);
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);
            socket.setSoTimeout(3500);
            long start=System.nanoTime();
            socket.send(new DatagramPacket(request,request.length,server,port));
            DatagramPacket reply=new DatagramPacket(new byte[512],512);
            socket.receive(reply);
            if(!reply.getAddress().equals(server))throw new IOException("STUN respondió desde IP inesperada");
            String mapped=FieldWire.stunMapped(Arrays.copyOf(reply.getData(),reply.getLength()),tx);
            long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            return "STUN Binding\nServidor: "+host+":"+port+"\nDirección observada: "+mapped+
                "\nPuerto local UDP: "+socket.getLocalPort()+"\nRTT de solicitud: "+ms+" ms\n"+
                "La traducción de puerto visible no permite clasificar por sí sola el tipo de NAT, "+
                "ni demuestra CGNAT o NAT simétrico.";
        }
    }
    private static String natPmpAndPcp(Network net,String gateway)throws Exception{
        StringBuilder out=new StringBuilder("Gateway: "+gateway+"\n");
        InetAddress dest=InetAddress.getByName(local(gateway));
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(1300);
            byte[] request={0,0};
            socket.send(new DatagramPacket(request,request.length,dest,5351));
            DatagramPacket response=new DatagramPacket(new byte[128],128);
            socket.receive(response);
            if(!response.getAddress().equals(dest))throw new IOException("NAT-PMP de otra IP");
            out.append("NAT-PMP IP externa: ")
                .append(FieldWire.natPmpAddress(Arrays.copyOf(response.getData(),response.getLength()))).append("\n");
        }catch(Exception e){out.append("NAT-PMP: ").append(e.getClass().getSimpleName()).append("\n");}
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(1500);
            byte[] announce=new byte[24];
            announce[0]=2;announce[1]=0; // PCP ANNOUNCE with zero lifetime
            socket.send(new DatagramPacket(announce,announce.length,dest,5351));
            DatagramPacket response=new DatagramPacket(new byte[128],128);
            socket.receive(response);
            byte[] data=response.getData();
            if(!response.getAddress().equals(dest)||response.getLength()<24||
                data[0]!=2||(data[1]&255)!=0x80)throw new IOException("Respuesta PCP no válida");
            out.append("PCP ANNOUNCE: código ").append(data[3]&255)
                .append((data[3]==0?" (soportado)":" (rechazado)")).append("\n");
        }catch(Exception e){out.append("PCP: ").append(e.getClass().getSimpleName()).append("\n");}
        return out.append("No se crean ni cambian reglas NAT.").toString();
    }
    private static String igd(Network net)throws Exception{
        byte[] request=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"+
            "MAN: \"ssdp:discover\"\r\nMX: 1\r\n"+
            "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII);
        String location=null,origin=null;
        try(DatagramSocket socket=new DatagramSocket(null)){
            socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(280);
            socket.send(new DatagramPacket(request,request.length,
                InetAddress.getByName("239.255.255.250"),1900));
            long end=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(1800);
            while(System.nanoTime()<end&&!Thread.currentThread().isInterrupted()){
                DatagramPacket reply=new DatagramPacket(new byte[2200],2200);
                try{socket.receive(reply);}catch(SocketTimeoutException e){continue;}
                String data=new String(reply.getData(),0,reply.getLength(),StandardCharsets.UTF_8);
                Matcher m=Pattern.compile("(?im)^LOCATION:\\s*(https?://[^\\s]+)").matcher(data);
                if(m.find()){
                    URL url=new URL(m.group(1).trim());
                    if(url.getProtocol().equals("http")&&
                        LanScanner.privateIpv4(url.getHost())&&
                        url.getHost().equals(reply.getAddress().getHostAddress())){
                        location=url.toString();origin=url.getHost();break;
                    }
                }
            }
        }
        if(location==null)return "No se descubrió IGD con descripción en una IP privada local.";
        String xml=getLocal(net,new URL(location),8000);
        Matcher m=Pattern.compile("(?s)<serviceType>\\s*(urn:schemas-upnp-org:service:WAN(?:IP|PPP)Connection:1)\\s*</serviceType>.*?<controlURL>\\s*([^<]+)\\s*</controlURL>")
            .matcher(xml);
        if(!m.find())return "IGD anunciado: "+location+"\nNo se publicó WANIPConnection/WANPPPConnection.";
        String type=m.group(1),path=m.group(2).trim();
        URL control=new URL(new URL(location),path);
        if(!control.getHost().equals(origin)||!control.getProtocol().equals("http"))
            return "ControlURL fuera del propio gateway: se ha rechazado.";
        String action="GetExternalIPAddress";
        String envelope="<?xml version=\"1.0\"?>"+
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"+
            "<u:"+action+" xmlns:u=\""+type+"\"/></s:Body></s:Envelope>";
        HttpURLConnection conn=(HttpURLConnection)net.openConnection(control);
        conn.setConnectTimeout(2500);conn.setReadTimeout(2500);conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type","text/xml; charset=utf-8");
        conn.setRequestProperty("SOAPAction","\""+type+"#"+action+"\"");
        try{
            byte[] body=envelope.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(body.length);
            try(OutputStream o=conn.getOutputStream()){o.write(body);}
            int code=conn.getResponseCode();
            InputStream response=code<400?conn.getInputStream():conn.getErrorStream();
            String reply=response==null?"":new String(DnsTools.readLimited(response,6000),StandardCharsets.UTF_8);
            Matcher ip=Pattern.compile("<NewExternalIPAddress>([^<]{0,80})</NewExternalIPAddress>").matcher(reply);
            return "IGD: "+location+"\nControl: "+control+
                "\nSOAP GetExternalIPAddress HTTP "+code+"\nWAN externa anunciada: "+
                (ip.find()?ip.group(1):"sin dato")+
                "\nSolo lectura: no se crean port mappings ni se cambian ajustes.";
        }finally{conn.disconnect();}
    }
    private static String getLocal(Network net,URL url,int max)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)net.openConnection(url);
        conn.setConnectTimeout(2000);conn.setReadTimeout(2000);
        conn.setInstanceFollowRedirects(false);
        try(InputStream input=conn.getInputStream()){
            return new String(DnsTools.readLimited(input,max),StandardCharsets.UTF_8);
        }finally{conn.disconnect();}
    }
}

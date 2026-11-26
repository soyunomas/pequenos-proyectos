package com.soyunomas.wifianalyzer;

import android.net.Network;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLParameters;

/** Small RFC 1035 DNS client, bound to a chosen Android network. */
public final class DnsTools {
    private DnsTools(){}
    private static final Map<String,Integer> TYPES=new LinkedHashMap<>();
    static {
        TYPES.put("A",1);TYPES.put("AAAA",28);TYPES.put("MX",15);TYPES.put("TXT",16);
        TYPES.put("NS",2);TYPES.put("CNAME",5);TYPES.put("SOA",6);
        TYPES.put("SRV",33);TYPES.put("CAA",257);TYPES.put("PTR",12);
    }
    public static Map<String,Integer> types(){return Collections.unmodifiableMap(TYPES);}
    public static String reverse(String ip) {
        try {
            if(ip==null)return "Dirección vacía";
            if(ip.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
                String[] octets=ip.split("\\.");
                for(String o:octets)if(Integer.parseInt(o)>255)throw new IllegalArgumentException();
                return octets[3]+"."+octets[2]+"."+octets[1]+"."+octets[0]+".in-addr.arpa";
            }
            if(!ip.contains(":")||!ip.matches("[0-9A-Fa-f:.%]+"))
                throw new IllegalArgumentException("Introduce una dirección IP");
            InetAddress addr=InetAddress.getByName(ip);
            if(addr.getAddress().length!=16)throw new IllegalArgumentException("IPv6 incorrecta");
            StringBuilder out=new StringBuilder();
            byte[] bytes=addr.getAddress();
            for(int i=15;i>=0;i--){
                out.append(Integer.toHexString(bytes[i]&15)).append('.');
                out.append(Integer.toHexString((bytes[i]>>4)&15)).append('.');
            }
            return out.append("ip6.arpa").toString();
        }catch(Exception e){throw new IllegalArgumentException("Dirección IP incorrecta");}
    }
    public static String queryAll(Network net,String name,String server,String protocol) {
        StringBuilder out=new StringBuilder("Dominio: "+name+"\nResolutor: "+server+"\nTransporte: "+protocol+"\n");
        for(Map.Entry<String,Integer> type:TYPES.entrySet()){
            if(Thread.currentThread().isInterrupted())break;
            if(type.getKey().equals("PTR"))continue;
            try{
                List<String> values=query(net,name,type.getValue(),server,protocol);
                out.append("\n").append(type.getKey()).append(": ");
                out.append(values.isEmpty()?"sin respuesta":String.join(" | ",values));
            }catch(Exception e){
                out.append("\n").append(type.getKey()).append(": ").append(e.getClass().getSimpleName());
            }
        }
        return out.toString();
    }
    public static List<String> query(Network net,String domain,int type,String server,String protocol)throws Exception {
        if(net==null)throw new IOException("Sin red activa");
        if(domain==null||domain.length()>245||!domain.matches("(?i)[a-z0-9_*-]+(\\.[a-z0-9_*-]+)*\\.?"))
            throw new IllegalArgumentException("Nombre DNS incorrecto");
        if(protocol.equals("DoH")){
            URL url=new URL("https://dns.google/resolve?name="+
                java.net.URLEncoder.encode(domain,"UTF-8")+"&type="+type);
            HttpURLConnection conn=(HttpURLConnection)net.openConnection(url);
            conn.setConnectTimeout(3500);conn.setReadTimeout(3500);conn.setRequestProperty("Accept","application/dns-json");
            try(InputStream input=conn.getInputStream()) {
                byte[] body=readLimited(input,5000);
                String json=new String(body,StandardCharsets.UTF_8);
                return Collections.singletonList(json);
            }finally{conn.disconnect();}
        }
        byte[] packet=request(domain,type);
        byte[] response;
        if(protocol.equals("DoT")){
            String hostname=server.equals("8.8.8.8")?"dns.google":"cloudflare-dns.com";
            InetAddress[] addresses=net.getAllByName(hostname);
            try(Socket plain=net.getSocketFactory().createSocket()){
                plain.connect(new InetSocketAddress(addresses[0],853),3500);
                plain.setSoTimeout(3500);
                try(SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
                    .createSocket(plain,hostname,853,false)){
                    SSLParameters params=ssl.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm("HTTPS");
                    ssl.setSSLParameters(params);
                    ssl.setSoTimeout(3500);ssl.startHandshake();
                    DataOutputStream output=new DataOutputStream(ssl.getOutputStream());
                    output.writeShort(packet.length);output.write(packet);output.flush();
                    DataInputStream in=new DataInputStream(ssl.getInputStream());
                    int len=in.readUnsignedShort();
                    if(len<12||len>8192)throw new IOException("Tamaño DNS incorrecto");
                    response=new byte[len];in.readFully(response);
                }
            }
        }else{
            InetAddress address=net.getAllByName(server)[0];
            try(DatagramSocket socket=new DatagramSocket(null)){
                socket.bind(new InetSocketAddress(0));net.bindSocket(socket);
                socket.setSoTimeout(2700);
                socket.send(new DatagramPacket(packet,packet.length,address,53));
                byte[] data=new byte[8192];
                DatagramPacket responsePacket=new DatagramPacket(data,data.length);
                socket.receive(responsePacket);
                response=Arrays.copyOf(data,responsePacket.getLength());
            }
        }
        return parseResponse(response,packet,type);
    }
    private static byte[] request(String domain,int type)throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(output);
        out.writeShort(0x4E53);out.writeShort(0x0100);out.writeShort(1);
        out.writeShort(0);out.writeShort(0);out.writeShort(0);
        String clean=domain.endsWith(".")?domain.substring(0,domain.length()-1):domain;
        for(String label:clean.split("\\.")){
            byte[] bytes=label.getBytes(StandardCharsets.US_ASCII);
            if(bytes.length<1||bytes.length>63)throw new IOException("Etiqueta DNS incorrecta");
            out.writeByte(bytes.length);out.write(bytes);
        }
        out.writeByte(0);out.writeShort(type);out.writeShort(1);
        return output.toByteArray();
    }
    static List<String> parseResponse(byte[] packet,byte[] request,int type)throws IOException {
        if(packet.length<12||u16(packet,0)!=u16(request,0))throw new IOException("ID DNS incorrecto");
        if((packet[2]&0x80)==0)throw new IOException("No es una respuesta DNS");
        int rcode=packet[3]&15;
        if(rcode!=0&&rcode!=3)throw new IOException("DNS RCODE "+rcode);
        if(rcode==3)return Collections.singletonList("NXDOMAIN");
        int qd=u16(packet,4),an=u16(packet,6);
        if(qd>10||an>100)return Collections.singletonList("Demasiados registros");
        int pos=12;
        for(int i=0;i<qd;i++){pos=skipName(packet,pos);if(pos+4>packet.length)throw new IOException("Pregunta truncada");pos+=4;}
        List<String> result=new ArrayList<>();
        for(int i=0;i<an;i++){
            pos=skipName(packet,pos);
            if(pos+10>packet.length)throw new IOException("Registro truncado");
            int rt=u16(packet,pos),length=u16(packet,pos+8),start=pos+10;
            pos=start+length;
            if(pos>packet.length)throw new IOException("RDATA truncado");
            if(rt!=type&&rt!=5)continue;
            try{
                String value=decode(packet,start,length,rt);
                if(!value.isEmpty())result.add(value);
            }catch(Exception ignored){result.add("[registro no decodificable]");}
        }
        return result;
    }
    static String decode(byte[] p,int offset,int len,int type)throws IOException{
        if(type==1&&len==4)return (p[offset]&255)+"."+(p[offset+1]&255)+"."+(p[offset+2]&255)+"."+(p[offset+3]&255);
        if(type==28&&len==16){
            return InetAddress.getByAddress(Arrays.copyOfRange(p,offset,offset+16)).getHostAddress();
        }
        if(type==2||type==5||type==12)return name(p,offset);
        if(type==15&&len>=3)return u16(p,offset)+" "+name(p,offset+2);
        if(type==33&&len>=7)return u16(p,offset)+" "+u16(p,offset+2)+" "+u16(p,offset+4)+" "+name(p,offset+6);
        if(type==6){
            String mname=name(p,offset);int next=skipName(p,offset);
            String rname=name(p,next);next=skipName(p,next);
            if(next+20<=offset+len)return mname+" "+rname+" serial="+u32(p,next);
            return mname+" "+rname;
        }
        if(type==16){
            StringBuilder out=new StringBuilder();int i=offset;
            while(i<offset+len){
                int n=p[i++]&255;
                if(i+n>offset+len)break;
                out.append(new String(p,i,n,StandardCharsets.UTF_8));i+=n;
                if(i<offset+len)out.append(" | ");
            }
            return out.toString();
        }
        if(type==257&&len>=2){
            int tagLen=p[offset+1]&255;
            if(tagLen+2>len)return "";
            return (p[offset]&255)+" "+new String(p,offset+2,tagLen,StandardCharsets.US_ASCII)
                +" "+new String(p,offset+2+tagLen,len-2-tagLen,StandardCharsets.UTF_8);
        }
        return "hex:"+java.util.Arrays.toString(Arrays.copyOfRange(p,offset,Math.min(offset+len,offset+32)));
    }
    static String name(byte[] p,int pos)throws IOException{
        StringBuilder out=new StringBuilder();int hops=0;
        while(hops++<80){
            if(pos>=p.length)throw new IOException("Nombre truncado");
            int n=p[pos++]&255;
            if(n==0)return out.toString();
            if((n&0xc0)==0xc0){
                if(pos>=p.length)throw new IOException("Puntero truncado");
                pos=((n&63)<<8)|(p[pos]&255);continue;
            }
            if(n>63||pos+n>p.length)throw new IOException("Etiqueta inválida");
            if(out.length()>0)out.append('.');
            out.append(new String(p,pos,n,StandardCharsets.US_ASCII));pos+=n;
        }
        throw new IOException("Compresión circular DNS");
    }
    static int skipName(byte[] p,int pos)throws IOException {
        int hops=0;
        while(hops++<80){
            if(pos>=p.length)throw new IOException("Nombre truncado");
            int n=p[pos++]&255;
            if(n==0)return pos;
            if((n&0xc0)==0xc0)return pos+1;
            if(n>63||pos+n>p.length)throw new IOException("Etiqueta incorrecta");
            pos+=n;
        }
        throw new IOException("Nombre DNS demasiado largo");
    }
    static int u16(byte[] p,int i){return ((p[i]&255)<<8)|(p[i+1]&255);}
    static long u32(byte[] p,int i){return ((long)u16(p,i)<<16)|u16(p,i+2);}
    public static byte[] readLimited(InputStream in,int max)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[1024];
        while(out.size()<max){
            int n=in.read(buf,0,Math.min(buf.length,max-out.size()));
            if(n<0)break;
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }
}

package com.soyunomas.wifianalyzer;

import android.net.Network;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Standards-based NBSTAT UDP/137, when a device offers NetBIOS node status.
 * Windows/Samba may return hostname and a MAC in the response; most phones won't.
 */
public final class NetBiosProbe {
    private NetBiosProbe(){}
    public static final class Result {
        public final String name, mac;
        Result(String name,String mac){this.name=name;this.mac=mac;}
    }

    public static Result query(Network network,String ip,int timeoutMs) {
        if(network==null||!LanScanner.privateIpv4(ip))return null;
        int id=(int)(System.nanoTime()&65535);
        byte[] request=new byte[50];
        request[0]=(byte)(id>>>8); request[1]=(byte)id;
        request[5]=1;           // QDCOUNT
        request[12]=32;         // a 32-character NetBIOS-encoded wildcard
        request[13]='C'; request[14]='K';
        for(int i=15;i<45;i++)request[i]='A';
        request[45]=0;         // terminating QNAME label
        request[46]=0;request[47]=0x21; // NBSTAT type
        request[48]=0;request[49]=1;    // class IN
        try(DatagramSocket socket=new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            network.bindSocket(socket);
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(request,request.length,InetAddress.getByName(ip),137));
            byte[] buffer=new byte[1600];
            DatagramPacket response=new DatagramPacket(buffer,buffer.length);
            socket.receive(response);
            if(!response.getAddress().getHostAddress().equals(ip))return null;
            return parse(buffer,response.getLength(),id);
        }catch(Exception ignored){return null;}
    }

    static Result parse(byte[] bytes,int size,int expectedId) {
        if(bytes==null||size<24)return null;
        if(u16(bytes,0)!=expectedId)return null;
        if((bytes[2]&0x80)==0)return null; // must be a response
        int questions=u16(bytes,4),answers=u16(bytes,6);
        if(questions>5||answers>10||answers==0)return null;
        int p=12;
        for(int i=0;i<questions;i++) {
            p=skipName(bytes,size,p);
            if(p<0||p+4>size)return null;
            p+=4;
        }
        for(int i=0;i<answers;i++){
            p=skipName(bytes,size,p);
            if(p<0||p+10>size)return null;
            int type=u16(bytes,p),length=u16(bytes,p+8);
            p+=10;
            if(length<0||p+length>size)return null;
            if(type==0x21&&length>=7) {
                int number=bytes[p]&0xff;
                int macOffset=p+1+number*18;
                if(number>40||macOffset+6>p+length)return null;
                String name="";
                for(int n=0;n<number;n++){
                    int from=p+1+n*18;
                    if(from+18>p+length)return null;
                    String raw=new String(bytes,from,15,StandardCharsets.US_ASCII).trim();
                    if(!raw.isEmpty()&&!"*".equals(raw)) {
                        name=raw;
                        if((bytes[from+15]&0xff)==0)break;
                    }
                }
                StringBuilder mac=new StringBuilder();
                boolean zero=true;
                for(int x=0;x<6;x++){
                    int octet=bytes[macOffset+x]&0xff;
                    if(octet!=0)zero=false;
                    if(x>0)mac.append(':');
                    mac.append(String.format(Locale.ROOT,"%02X",octet));
                }
                // Multicast and zero addresses are never valid individual MACs.
                if(zero||(bytes[macOffset]&1)!=0)return new Result(name,"");
                return new Result(name,mac.toString());
            }
            p+=length;
        }
        return null;
    }
    private static int u16(byte[] b,int p){return ((b[p]&255)<<8)|(b[p+1]&255);}
    private static int skipName(byte[] bytes,int size,int at){
        int p=at;
        for(int hop=0;hop<128;hop++){
            if(p>=size)return -1;
            int length=bytes[p]&255;
            if(length==0)return p+1;
            if((length&0xC0)==0xC0)return p+2<=size?p+2:-1;
            if((length&0xC0)!=0)return -1;
            p+=1+length;
        }
        return -1;
    }
}

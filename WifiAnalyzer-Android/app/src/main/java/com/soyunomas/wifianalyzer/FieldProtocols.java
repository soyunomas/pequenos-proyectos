package com.soyunomas.wifianalyzer;

import android.net.Network;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.net.ssl.*;

/** One-message RFC6455 WebSocket client. No Java 11 java.net.http dependency on Android. */
public final class FieldProtocols {
    private FieldProtocols(){}
    public static String webSocket(Network net,String input)throws Exception{
        String[] parts=input.trim().split("\\|",2);
        URI url=new URI(parts[0]);
        String scheme=url.getScheme();
        if((!"ws".equalsIgnoreCase(scheme)&&!"wss".equalsIgnoreCase(scheme))||
            url.getHost()==null||url.getUserInfo()!=null)
            throw new IllegalArgumentException("Usa ws://host/ruta|texto o wss://host/ruta|texto");
        byte[] text=(parts.length==2?parts[1]:"NetScope").getBytes(StandardCharsets.UTF_8);
        if(text.length>512)throw new IllegalArgumentException("Máximo 512 bytes en WebSocket");
        boolean tls="wss".equalsIgnoreCase(scheme);
        int port=url.getPort()>0?url.getPort():(tls?443:80);
        if(port<1||port>65535)throw new IllegalArgumentException("Puerto incorrecto");
        String hostname=url.getHost();
        InetAddress ip=net.getAllByName(hostname)[0];
        try(Socket raw=net.getSocketFactory().createSocket()){
            raw.connect(new InetSocketAddress(ip,port),3500);raw.setSoTimeout(3500);
            Socket socket=raw;
            if(tls){
                SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
                    .createSocket(raw,hostname,port,false);
                SSLParameters params=ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");ssl.setSSLParameters(params);
                ssl.startHandshake();socket=ssl;
            }
            InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();
            byte[] nonce=new byte[16];new SecureRandom().nextBytes(nonce);
            String key=Base64.getEncoder().encodeToString(nonce);
            String path=url.getRawPath();
            if(path==null||path.isEmpty())path="/";
            if(url.getRawQuery()!=null)path+="?"+url.getRawQuery();
            String hostHeader=hostname+(url.getPort()>0?":"+port:"");
            String request="GET "+path+" HTTP/1.1\r\n"+
                "Host: "+hostHeader+"\r\n"+
                "Upgrade: websocket\r\nConnection: Upgrade\r\n"+
                "Sec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));out.flush();
            ByteArrayOutputStream headers=new ByteArrayOutputStream();
            int state=0;
            while(headers.size()<4096){
                int byteRead=in.read();if(byteRead<0)break;
                headers.write(byteRead);
                if(state==0&&byteRead==13)state=1;
                else if(state==1&&byteRead==10)state=2;
                else if(state==2&&byteRead==13)state=3;
                else if(state==3&&byteRead==10){state=4;break;}
                else state=0;
            }
            String handshake=new String(headers.toByteArray(),StandardCharsets.ISO_8859_1);
            if(!handshake.startsWith("HTTP/1.1 101")&&!handshake.startsWith("HTTP/1.0 101"))
                return "WebSocket: servidor no aceptó upgrade\n"+
                    handshake.substring(0,Math.min(700,handshake.length()));
            String expected=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            boolean valid=false,upgraded=false;
            for(String header:handshake.split("\\r?\\n")){
                int colon=header.indexOf(':');if(colon<0)continue;
                String name=header.substring(0,colon).trim(),value=header.substring(colon+1).trim();
                if(name.equalsIgnoreCase("Sec-WebSocket-Accept"))valid=expected.equals(value);
                if(name.equalsIgnoreCase("Upgrade"))upgraded=value.equalsIgnoreCase("websocket");
            }
            if(!valid||!upgraded)throw new IOException("Handshake WebSocket inválido");
            byte[] mask=new byte[4];new SecureRandom().nextBytes(mask);
            out.write(0x81);
            if(text.length<=125)out.write(0x80|text.length);
            else{out.write(0x80|126);out.write((text.length>>8)&255);out.write(text.length&255);}
            out.write(mask);
            for(int i=0;i<text.length;i++)out.write(text[i]^mask[i%4]);
            out.flush();
            StringBuilder result=new StringBuilder("WebSocket conectado "+scheme+"://"+hostname+
                "\nHandshake HTTP 101 validado\nEnviados "+text.length+" bytes de texto.\n");
            try{
                int first=in.read(),second=in.read();
                if(first<0||second<0)return result.append("Servidor cerró sin responder.").toString();
                int opcode=first&0x0F, length=second&0x7F;
                if(length==126)length=(in.read()<<8)|in.read();
                else if(length==127)return result.append("Frame demasiado grande.").toString();
                if(length<0||length>4096)return result.append("Respuesta WebSocket superior a 4 KB.").toString();
                boolean masked=(second&0x80)!=0;
                byte[] remoteMask=new byte[4];
                if(masked)new DataInputStream(in).readFully(remoteMask);
                byte[] body=new byte[length];new DataInputStream(in).readFully(body);
                if(masked)for(int i=0;i<length;i++)body[i]^=remoteMask[i%4];
                return result.append("Opcode recibido: ").append(opcode).append("\nRespuesta: \n")
                    .append(FieldWire.printable(body)).toString();
            }catch(SocketTimeoutException e){
                return result.append("Sin frame de respuesta en 3,5 s.").toString();
            }
        }
    }
}

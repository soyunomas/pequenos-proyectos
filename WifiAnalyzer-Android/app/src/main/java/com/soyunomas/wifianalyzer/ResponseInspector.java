package com.soyunomas.wifianalyzer;

import android.net.Network;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * On-demand netcat-like TCP probe: passive read, HTTP HEAD or explicitly entered text.
 * The service may interpret custom text as commands; no payload is sent without user action.
 */
public final class ResponseInspector {
    private ResponseInspector(){}
    public static final int BANNER=0, HTTP_HEAD=1, CUSTOM=2;
    public static String inspect(Network network,String ip,int port,int mode,String userText){
        if(network==null||!LanScanner.privateIpv4(ip)||port<1||port>65535)
            return "Objetivo no permitido: conecta la Wi-Fi local.";
        if(mode==HTTP_HEAD&&PortCatalog.tls(port))
            return "Es un puerto HTTPS probable. Ábrelo en el navegador para negociar TLS; no se envía HTTP en claro.";
        if(mode==CUSTOM&&(userText==null||userText.isEmpty()||userText.length()>512))
            return "Introduce de 1 a 512 caracteres para enviar.";
        try(Socket socket=network.getSocketFactory().createSocket()){
            socket.connect(new InetSocketAddress(ip,port),1000);
            socket.setSoTimeout(900);
            OutputStream output=socket.getOutputStream();
            if(mode==HTTP_HEAD){
                String head="HEAD / HTTP/1.0\r\nHost: "+ip+"\r\nUser-Agent: NetScope/2.6\r\nConnection: close\r\n\r\n";
                output.write(head.getBytes(StandardCharsets.US_ASCII));
                output.flush();
            }else if(mode==CUSTOM){
                output.write(userText.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            InputStream input=socket.getInputStream();
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            byte[] piece=new byte[512];
            boolean timedOut=false;
            while(buffer.size()<2048){
                int read;
                try{read=input.read(piece,0,Math.min(piece.length,2048-buffer.size()));}
                catch(SocketTimeoutException e){timedOut=true;break;}
                if(read<0)break;
                if(read==0)continue;
                buffer.write(piece,0,read);
                // One small response is sufficient for interactive inspection.
                if(input.available()==0)break;
            }
            if(buffer.size()==0)return "TCP conectado, pero el servicio no devolvió texto "+
                (timedOut?"antes del tiempo límite.":"de forma espontánea.")+
                "\n\nMuchos protocolos esperan una petición específica; no significa que el puerto esté cerrado.";
            return "Recibidos "+buffer.size()+" bytes:\n\n"+printable(buffer.toByteArray());
        }catch(IOException|SecurityException e){
            return "Error de comunicación TCP: "+e.getClass().getSimpleName()+
                    "\nComprueba que el equipo sigue conectado y el puerto continúa abierto.";
        }
    }

    /** Render binary/control characters safely as escaped hex; never interpret terminal controls. */
    public static String printable(byte[] bytes){
        StringBuilder text=new StringBuilder();
        for(byte b:bytes){
            int c=b&255;
            if(c==10)text.append('\n');
            else if(c==13)text.append("\\r");
            else if(c==9)text.append("\\t");
            else if(c>=32&&c<=126)text.append((char)c);
            else text.append(String.format(java.util.Locale.ROOT,"\\x%02X",c));
        }
        return text.toString();
    }
}

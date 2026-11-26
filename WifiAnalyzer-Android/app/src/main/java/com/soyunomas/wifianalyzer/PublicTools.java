package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import org.json.*;

/** Read-only HTTPS/TLS, public lookup and bounded speed probes. */
public final class PublicTools {
    private PublicTools(){}
    public static String execute(Context ctx,Network net,int id,String arg)throws Exception {
        if(net==null)throw new IOException("Conéctate primero a una red Wi-Fi.");
        switch(id){
            case 8:return tls(net,arg);
            case 9:return http(net,arg);
            case 16:return iperf(net,arg);
            case 17:return speed(net,arg);
            case 18:return rdap(net,arg);
            case 19:return bgp(net,arg);
            case 20:return publicIp(ctx,net);
            default:throw new IllegalArgumentException("Herramienta pública desconocida");
        }
    }

    private static String read(Network net,String target,int limit)throws Exception {
        URL url=new URL(target);
        if(!"https".equals(url.getProtocol()))
            throw new IllegalArgumentException("Solo se permiten servicios HTTPS externos");
        HttpURLConnection c=(HttpURLConnection)net.openConnection(url);
        c.setConnectTimeout(3800);c.setReadTimeout(4000);
        c.setRequestProperty("Accept","application/json");
        c.setInstanceFollowRedirects(true);
        try{
            int code=c.getResponseCode();
            InputStream stream=code<400?c.getInputStream():c.getErrorStream();
            if(stream==null)return "HTTP "+code+" sin respuesta";
            return new String(DnsTools.readLimited(stream,limit),StandardCharsets.UTF_8);
        }finally{c.disconnect();}
    }

    private static String tls(Network net,String text)throws Exception {
        String[] parts=text.trim().split(":",2);
        String hostname=LocalTools.host(parts[0]);
        int port=parts.length>1?PortCatalog.parse(parts[1]):443;
        InetAddress destination=net.getAllByName(hostname)[0];
        long start=System.nanoTime();
        try(Socket raw=net.getSocketFactory().createSocket()){
            raw.connect(new InetSocketAddress(destination,port),4000);
            raw.setSoTimeout(4000);
            SSLSocketFactory factory=(SSLSocketFactory)SSLSocketFactory.getDefault();
            try(SSLSocket socket=(SSLSocket)factory.createSocket(raw,hostname,port,false)){
                SSLParameters parameters=socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                socket.setSSLParameters(parameters);
                socket.startHandshake();
                SSLSession session=socket.getSession();
                long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                StringBuilder result=new StringBuilder("TLS validado · "+hostname+":"+port+
                    "\nConexión: "+millis+" ms\nVersión: "+session.getProtocol()+
                    "\nCifrado: "+session.getCipherSuite()+"\n");
                for(java.security.cert.Certificate c:session.getPeerCertificates()){
                    if(!(c instanceof X509Certificate))continue;
                    X509Certificate x=(X509Certificate)c;
                    result.append("\nSujeto: ").append(x.getSubjectX500Principal().getName())
                        .append("\nEmisor: ").append(x.getIssuerX500Principal().getName())
                        .append("\nVálido desde: ").append(x.getNotBefore())
                        .append("\nVálido hasta: ").append(x.getNotAfter()).append("\n");
                    if(c==session.getPeerCertificates()[0]){
                        Collection<List<?>> names=x.getSubjectAlternativeNames();
                        if(names!=null) {
                            int i=0;
                            for(List<?> n:names){
                                if(i++>=12)break;
                                result.append("SAN: ").append(n.size()>1?n.get(1):"").append("\n");
                            }
                        }
                    }
                }
                return result.toString();
            }
        }catch(SSLHandshakeException e){
            return "El certificado o la negociación TLS no son válidos: "+e.getMessage()+
                "\nNo se deshabilita la validación de certificados.";
        }
    }
    private static String http(Network net,String userUrl)throws Exception{
        URL url=new URL(userUrl.trim());
        if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))
            throw new IllegalArgumentException("Escribe una URL HTTP o HTTPS");
        long start=System.nanoTime();
        HttpURLConnection c=(HttpURLConnection)net.openConnection(url);
        c.setConnectTimeout(4000);c.setReadTimeout(4000);
        c.setInstanceFollowRedirects(true);
        c.setRequestMethod("HEAD");
        try{
            int code=c.getResponseCode();
            long ms=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            StringBuilder output=new StringBuilder("HEAD "+url+"\nHTTP "+code+" "+c.getResponseMessage()+
                "\nTotal: "+ms+" ms\nURL final: "+c.getURL()+"\n");
            if(url.getProtocol().equals("http"))output.append("\nAviso: sin cifrado TLS.\n");
            int count=0;
            for(Map.Entry<String,List<String>> h:c.getHeaderFields().entrySet()){
                if(h.getKey()==null)continue;
                if(count++>=24){output.append("[cabeceras adicionales omitidas]\n");break;}
                output.append(h.getKey()).append(": ").append(String.join(", ",h.getValue())).append("\n");
            }
            return output.toString();
        }finally{c.disconnect();}
    }
    private static String rdap(Network net,String resource)throws Exception{
        String s=resource.trim();
        if(!s.matches("(?i)[a-z0-9.:-]{2,250}"))
            throw new IllegalArgumentException("IP o dominio incorrecto");
        boolean ip=s.contains(":")||s.matches("(?:[0-9]+\\.){3}[0-9]+");
        String url="https://rdap.org/"+(ip?"ip/":"domain/")+s;
        JSONObject root=new JSONObject(read(net,url,14000));
        StringBuilder out=new StringBuilder("RDAP: "+s+"\n");
        for(String key:new String[]{"handle","name","type","country","startAddress","endAddress","ldhName"})
            if(root.has(key))out.append(key).append(": ").append(root.optString(key)).append("\n");
        JSONArray status=root.optJSONArray("status");
        if(status!=null)out.append("Estado: ").append(status).append("\n");
        JSONArray events=root.optJSONArray("events");
        if(events!=null)for(int i=0;i<Math.min(8,events.length());i++){
            JSONObject e=events.optJSONObject(i);if(e!=null)
                out.append(e.optString("eventAction")).append(": ").append(e.optString("eventDate")).append("\n");
        }
        JSONArray entities=root.optJSONArray("entities");
        if(entities!=null)for(int i=0;i<Math.min(5,entities.length());i++){
            JSONObject e=entities.optJSONObject(i);
            if(e!=null)out.append("Entidad: ").append(e.optString("handle")).append(" ")
                .append(e.optJSONArray("roles")).append("\n");
        }
        return out.append("\nConsulta externa a rdap.org.").toString();
    }
    private static String bgp(Network net,String resource)throws Exception{
        String r=resource.trim();
        if(!r.matches("(?i)[a-f0-9.:/]{3,85}"))
            throw new IllegalArgumentException("Introduce una IP o prefijo CIDR válido.");
        String base="https://stat.ripe.net/data/network-info/data.json?resource="+
            java.net.URLEncoder.encode(r,"UTF-8");
        JSONObject obj=new JSONObject(read(net,base,10000));
        JSONObject data=obj.optJSONObject("data");
        if(data==null)return "RIPEstat no devolvió datos para este recurso.";
        StringBuilder out=new StringBuilder("RIPEstat · "+r+"\n");
        out.append("Prefijo: ").append(data.optString("prefix","sin anuncio")).append("\n");
        out.append("ASN origen: ").append(data.optJSONArray("asns")).append("\n");
        out.append("Fuente: RIPE NCC\nLa atribución ASN puede variar y no equivale al proveedor del usuario.");
        return out.toString();
    }
    private static String publicIp(Context ctx,Network net)throws Exception{
        StringBuilder out=new StringBuilder("Salida Wi-Fi e IP pública\n");
        NetworkDiagnostics.Snapshot snap=NetworkDiagnostics.capture(ctx);
        for(String key:new String[]{"Red predeterminada","Conexión Wi-Fi","Direcciones IP",
                "DNS privado","Puerta de enlace IPv4"}) {
            String value=snap.rows.get(key);
            if(value!=null)out.append(key).append(": ").append(value).append("\n");
        }
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=cm.getActiveNetwork();
        NetworkCapabilities cap=active==null?null:cm.getNetworkCapabilities(active);
        out.append("VPN predeterminada: ")
            .append(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"detectada":"no detectada").append("\n");
        try{
            out.append("IPv4 pública vía Wi-Fi: ").append(new JSONObject(read(net,
                "https://api.ipify.org?format=json",250)).optString("ip")).append("\n");
        }catch(Exception e){out.append("IPv4 externa: ").append(e.getClass().getSimpleName()).append("\n");}
        try{
            out.append("IPv4/IPv6 de salida: ").append(new JSONObject(read(net,
                "https://api64.ipify.org?format=json",250)).optString("ip")).append("\n");
        }catch(Exception e){out.append("IPv6 externa: ").append(e.getClass().getSimpleName()).append("\n");}
        out.append("\nCGNAT: no determinable con certeza solo desde el móvil. "+
            "Compara la IP WAN del router con la IP externa, y comprueba si está en 100.64.0.0/10.");
        return out.toString();
    }

    private static String speed(Network net,String urlString)throws Exception{
        URL url=new URL(urlString.trim());
        if(!url.getProtocol().equals("https"))
            throw new IllegalArgumentException("Se exige URL HTTPS para descarga de rendimiento.");
        HttpURLConnection download=(HttpURLConnection)net.openConnection(url);
        download.setConnectTimeout(5000);download.setReadTimeout(5000);
        download.setUseCaches(false);
        int n=0;long start=System.nanoTime();
        try(InputStream input=download.getInputStream()){
            byte[] block=new byte[8192];int received;
            while(n<3_000_000&&!Thread.currentThread().isInterrupted()&&
                (received=input.read(block,0,Math.min(block.length,3_000_000-n)))!=-1)n+=received;
        }finally{download.disconnect();}
        double seconds=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)/1000.0;
        if(n<50000||seconds<=0)
            return "Descarga demasiado pequeña para estimar caudal: "+n+" bytes.";
        StringBuilder out=new StringBuilder(String.format(Locale.ROOT,
            "Descarga HTTPS: %d bytes en %.2f s\nCaudal medio observado: %.2f Mbps\n",
            n,seconds,(n*8)/(seconds*1_000_000)));
        out.append("Estimación corta de hasta 3 MB, no equivalente a Speedtest multi-hilo.\n");
        // A small controlled upload only to a compatible Cloudflare test endpoint.
        if(url.getHost().equalsIgnoreCase("speed.cloudflare.com")){
            HttpURLConnection upload=null;
            try{
                upload=(HttpURLConnection)net.openConnection(new URL("https://speed.cloudflare.com/__up"));
                upload.setDoOutput(true);upload.setRequestMethod("POST");upload.setConnectTimeout(4500);
                upload.setReadTimeout(4500);upload.setFixedLengthStreamingMode(262144);
                upload.setRequestProperty("Content-Type","application/octet-stream");
                byte[] payload=new byte[8192];
                long begin=System.nanoTime();
                try(OutputStream os=upload.getOutputStream()){
                    for(int i=0;i<32;i++)os.write(payload);
                }
                int code=upload.getResponseCode();
                double secs=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)/1000.0;
                if(code<300&&secs>0)
                    out.append(String.format(Locale.ROOT,"Subida pequeña (256 KiB): %.2f Mbps\n",
                        262144*8/(secs*1_000_000)));
                else out.append("Subida: servidor respondió HTTP ").append(code).append("\n");
            }catch(Exception e){out.append("Subida: no disponible (").append(e.getClass().getSimpleName()).append(")\n");}
            finally{if(upload!=null)upload.disconnect();}
        }else out.append("Subida: no se envía tráfico a un servidor personalizado sin endpoint autorizado.\n");
        return out.toString();
    }
    private static String iperf(Network net,String hostname)throws Exception {
        String target=LocalTools.host(hostname);
        // A socket check does not constitute a throughput benchmark.
        InetAddress address=net.getAllByName(target)[0];
        boolean reachable=false;
        try(Socket socket=net.getSocketFactory().createSocket()){
            socket.connect(new InetSocketAddress(address,5201),1500);reachable=true;
        }catch(IOException ignored){}
        return "iPerf3 (protocolo específico, requiere cliente y servidor compatibles)\n"+
            "Servidor: "+target+":5201\n"+
            "Puerto TCP 5201: "+(reachable?"conexión aceptada":"sin conexión")+
            "\n\nNetScope no contiene el motor nativo de iPerf3. "+
            "La conexión TCP no representa una medición de caudal iPerf3. "+
            "Para obtener resultados iPerf3 reales ejecuta:\n"+
            "iperf3 -c "+target+" -t 10\n"+
            "En el servidor: iperf3 -s\n"+
            "Esta función sirve para verificar disponibilidad y preparar la prueba, no para falsificarla.";
    }
}

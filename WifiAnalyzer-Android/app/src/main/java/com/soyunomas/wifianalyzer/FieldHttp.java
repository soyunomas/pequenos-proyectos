package com.soyunomas.wifianalyzer;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;

/** Portal diagnostics and trustworthy TLS ALPN / HTTP-3 advertising report. */
public final class FieldHttp{
 private FieldHttp(){}
 public static String execute(Context context,Network net,int id,String input)throws Exception{
  if(id==32)return portal(context,net);
  if(id==45)return alpn(net,input);
  throw new IllegalArgumentException("HTTP herramienta incorrecta");
 }
 private static String portal(Context context,Network net)throws Exception{
  ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
  NetworkCapabilities cap=cm.getNetworkCapabilities(net);
  StringBuilder out=new StringBuilder("Portal cautivo · transporte Wi-Fi\n");
  out.append("VALIDATED Android: ").append(cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
   .append("\nCAPTIVE_PORTAL Android: ")
   .append(cap!=null&&cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)).append("\n");
  HttpURLConnection conn=(HttpURLConnection)net.openConnection(
   new URL("http://connectivitycheck.gstatic.com/generate_204"));
  conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(3200);conn.setReadTimeout(3200);
  conn.setUseCaches(false);
  try{
   int code=conn.getResponseCode();
   out.append("GET HTTP de prueba: ").append(code).append("\n");
   String location=conn.getHeaderField("Location");
   if(location!=null)out.append("Redirección: ").append(location.substring(0,Math.min(180,location.length()))).append("\n");
   if(code==204)out.append("Respuesta esperada de comprobación, sin portal detectado en esta solicitud.");
   else out.append("HTTP inesperado: posible portal cautivo, proxy o interceptación. No prueba infección.");
  }catch(Exception e){out.append("Conectividad HTTP: ").append(e.getClass().getSimpleName());}
  finally{conn.disconnect();}
  return out.toString();
 }
 private static String alpn(Network net,String target)throws Exception{
  URL url=new URL(target.trim());
  if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null)
   throw new IllegalArgumentException("Escribe una URL HTTPS sin credenciales");
  String hostname=url.getHost();int port=url.getPort()>0?url.getPort():443;
  StringBuilder out=new StringBuilder("Negociación HTTP/2 sobre TLS\nHost: "+hostname+":"+port+"\n");
  InetAddress remote=net.getAllByName(hostname)[0];
  try(Socket transport=net.getSocketFactory().createSocket()){
   transport.connect(new InetSocketAddress(remote,port),3500);transport.setSoTimeout(3500);
   try(SSLSocket ssl=(SSLSocket)((SSLSocketFactory)SSLSocketFactory.getDefault())
       .createSocket(transport,hostname,port,false)){
    SSLParameters parameters=ssl.getSSLParameters();
    parameters.setEndpointIdentificationAlgorithm("HTTPS");
    if(Build.VERSION.SDK_INT>=29)parameters.setApplicationProtocols(new String[]{"h2","http/1.1"});
    ssl.setSSLParameters(parameters);
    ssl.startHandshake();
    out.append("TLS: ").append(ssl.getSession().getProtocol()).append("\n");
    if(Build.VERSION.SDK_INT>=29){
     String protocol=ssl.getApplicationProtocol();
     out.append("ALPN real: ").append(protocol==null||protocol.isEmpty()?"sin selección":protocol).append("\n");
     if("h2".equals(protocol))out.append("HTTP/2 seleccionado mediante ALPN.\n");
    }else out.append("ALPN no accesible en la versión de Android.\n");
   }
  }
  HttpsURLConnection conn=(HttpsURLConnection)net.openConnection(url);
  conn.setRequestMethod("HEAD");
  conn.setConnectTimeout(3000);conn.setReadTimeout(3000);
  try{
   out.append("Consulta HTTPS independiente: ").append(conn.getResponseCode()).append("\n");
   String alt=conn.getHeaderField("Alt-Svc");
   if(alt==null)out.append("Alt-Svc: no anunciado.\n");
   else out.append("Alt-Svc anunciado: ").append(alt.substring(0,Math.min(220,alt.length()))).append("\n");
  }catch(IOException e){out.append("Consulta cabeceras no disponible: ").append(e.getClass().getSimpleName()).append("\n");}
  finally{conn.disconnect();}
  return out.append("\nAlt-Svc h3 indica soporte anunciado, NO una conexión HTTP/3 realizada. "+
   "HTTP/3 necesita un cliente QUIC compatible (por ejemplo Cronet); esta versión no negocia QUIC.").toString();
 }
}

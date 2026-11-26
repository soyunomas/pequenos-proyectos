package com.soyunomas.wifianalyzer;
import android.net.Network;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class FieldServices{
 private FieldServices(){}
 public static String execute(Network net,int id,String input)throws Exception{
  if(id==39)return udp(net,input);
  if(id==40)return ipp(net,input);
  if(id==42)return smb(net,input);
  if(id==43)return ssh(net,input);
  throw new IllegalArgumentException("Servicio inválido");
 }
 private static String local(String ip){
  if(!LanScanner.privateIpv4(ip))throw new IllegalArgumentException("Solo direcciones LAN RFC1918/RFC6598");
  return ip;
 }
 private static String udp(Network net,String raw)throws Exception{
  String[] p=raw.trim().split("\\|",2),host=p[0].split(":",2);
  if(host.length!=2)throw new IllegalArgumentException("Formato: IP:puerto|texto");
  String ip=local(host[0]);int port=PortCatalog.parse(host[1]);
  byte[] sent=(p.length>1?p[1]:"").getBytes(StandardCharsets.UTF_8);
  if(sent.length>512)throw new IllegalArgumentException("Máximo 512 bytes");
  try(DatagramSocket socket=new DatagramSocket(null)){
   socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(1800);
   socket.send(new DatagramPacket(sent,sent.length,InetAddress.getByName(ip),port));
   DatagramPacket reply=new DatagramPacket(new byte[2048],2048);
   try{socket.receive(reply);
    return "Datagrama UDP a "+ip+":"+port+"\nRespuesta desde "+reply.getAddress()+":"+reply.getPort()+
     " · "+reply.getLength()+" bytes\n"+FieldWire.printable(Arrays.copyOf(reply.getData(),reply.getLength()));
   }catch(SocketTimeoutException e){
    return "UDP "+ip+":"+port+": no responde (1,8 s). Ausencia de respuesta NO indica puerto cerrado.";
   }
  }
 }
 private static String ipp(Network net,String raw)throws Exception{
  URI uri=new URI(raw.trim());
  if(!"ipp".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)
   throw new IllegalArgumentException("Formato: ipp://IP:631/ruta");
  String ip=local(uri.getHost());int port=uri.getPort()<0?631:uri.getPort();
  if(port<1||port>65535)throw new IllegalArgumentException("Puerto incorrecto");
  String path=uri.getRawPath()==null?"/":uri.getRawPath();
  URL url=new URL("http://"+ip+":"+port+path);
  byte[] request=FieldWire.ippGetPrinterAttributes(raw.trim());
  HttpURLConnection conn=(HttpURLConnection)net.openConnection(url);
  conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(2500);conn.setReadTimeout(3000);
  conn.setRequestMethod("POST");conn.setDoOutput(true);
  conn.setRequestProperty("Content-Type","application/ipp");
  conn.setFixedLengthStreamingMode(request.length);
  try{
   try(OutputStream o=conn.getOutputStream()){o.write(request);}
   int status=conn.getResponseCode();
   if(status>=400)return "IPP: error HTTP "+status;
   byte[] reply;
   try(InputStream in=conn.getInputStream()){reply=DnsTools.readLimited(in,8192);}
   Map<String,String> attrs=FieldWire.ippResponse(reply);
   StringBuilder b=new StringBuilder("Get-Printer-Attributes IPP · HTTP "+status+"\n");
   for(Map.Entry<String,String> e:attrs.entrySet())b.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
   return b.append("Solo lectura, sin trabajos de impresión.").toString();
  }finally{conn.disconnect();}
 }
 private static String smb(Network net,String raw)throws Exception{
  String ip=local(raw.trim());
  byte[] header=new byte[64];header[0]=(byte)254;header[1]='S';header[2]='M';header[3]='B';
  header[4]=64;header[14]=1;header[36]=1;
  byte[] negotiate=new byte[40];negotiate[0]=36;negotiate[2]=2;negotiate[8]=1;
  negotiate[36]=2;negotiate[37]=2;negotiate[38]=0x10;negotiate[39]=2;
  byte[] request=new byte[108];request[3]=104;
  System.arraycopy(header,0,request,4,64);System.arraycopy(negotiate,0,request,68,40);
  try(Socket socket=net.getSocketFactory().createSocket()){
   socket.connect(new InetSocketAddress(ip,445),2400);socket.setSoTimeout(2700);
   socket.getOutputStream().write(request);socket.getOutputStream().flush();
   DataInputStream in=new DataInputStream(socket.getInputStream());
   byte[] prefix=new byte[4];in.readFully(prefix);
   int n=((prefix[1]&255)<<16)|((prefix[2]&255)<<8)|(prefix[3]&255);
   if(n<72||n>4096)throw new IOException("Longitud SMB fuera de límite");
   byte[] response=new byte[n];in.readFully(response);
   if(response[0]!=(byte)254||response[1]!='S'||response[2]!='M'||response[3]!='B')
    return "Conexión TCP establecida pero el servicio no respondió como SMB2";
   int status=(response[8]&255)|((response[9]&255)<<8)|((response[10]&255)<<16)|((response[11]&255)<<24);
   int dialect=n>=70?((response[69]&255)<<8)|(response[68]&255):0;
   return "SMB2 NEGOTIATE en "+ip+"\nNTSTATUS: 0x"+Integer.toHexString(status)+
    "\nDialecto: 0x"+Integer.toHexString(dialect)+
    "\nLa enumeración de recursos compartidos requiere sesión y permisos SMB; no se ha autenticado.";
  }
 }
 private static String ssh(Network net,String raw)throws Exception{
  String[] p=raw.trim().split(":",2);String ip=local(p[0]);
  int port=p.length==2?PortCatalog.parse(p[1]):22;
  try(Socket socket=net.getSocketFactory().createSocket()){
   socket.connect(new InetSocketAddress(ip,port),2400);socket.setSoTimeout(2500);
   InputStream in=socket.getInputStream();
   StringBuilder banner=new StringBuilder();int c;
   while(banner.length()<255&&(c=in.read())!=-1){
    if(c==10)break;if(c>=32&&c<=126)banner.append((char)c);
   }
   if(!banner.toString().startsWith("SSH-"))return "Sin banner SSH válido: "+banner;
   socket.getOutputStream().write("SSH-2.0-NetScope_4.0\r\n".getBytes(StandardCharsets.US_ASCII));
   StringBuilder b=new StringBuilder("SSH "+ip+":"+port+"\nBanner: "+banner+"\n");
   try{
    DataInputStream d=new DataInputStream(in);
    int length=d.readInt(),padding=d.readUnsignedByte();
    if(length<20||length>16384||padding<4||length-padding<16)
     return b.append("KEXINIT: longitud inesperada").toString();
    byte[] body=new byte[length-1];d.readFully(body);
    if(body[0]!=20)return b.append("Tipo SSH: ").append(body[0]&255).toString();
    DataInputStream payload=new DataInputStream(new ByteArrayInputStream(body,17,body.length-17));
    String[] labels={"KEX","Host key","Cifrado C→S","Cifrado S→C","MAC C→S","MAC S→C",
     "Compresión C→S","Compresión S→C","Idioma C→S","Idioma S→C"};
    for(String label:labels){
     int n=payload.readInt();
     if(n<0||n>3500)throw new IOException("Algoritmos SSH malformados");
     byte[] bytes=new byte[n];payload.readFully(bytes);
     if(n>0)b.append(label).append(": ").append(new String(bytes,StandardCharsets.US_ASCII)
      .substring(0,Math.min(n,280))).append("\n");
    }
   }catch(SocketTimeoutException e){b.append("No se recibió KEXINIT a tiempo.\n");}
   return b.append("No se autentica, negocia KEX completo ni extrae una clave host verificada.").toString();
  }
 }
}

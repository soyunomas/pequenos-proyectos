package com.soyunomas.wifianalyzer;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Explicit DNS comparisons and EDNS(0) probes, never claims independent DNSSEC validation. */
public final class FieldDns{
 private FieldDns(){}
 public static String execute(Context context,Network net,int id,String input)throws Exception{
  if(id==33)return split(context,net,input);
  if(id==34)return dnssec(net,input);
  if(id==35)return edns(context,net,input);
  throw new IllegalArgumentException("DNS herramienta inválida");
 }
 private static String dnsServer(Context ctx,Network network){
  ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
  LinkProperties props=cm==null?null:cm.getLinkProperties(network);
  if(props!=null)for(InetAddress dns:props.getDnsServers())
   if(dns.getAddress().length==4)return dns.getHostAddress();
  return "1.1.1.1";
 }
 private static String split(Context context,Network net,String name)throws Exception{
  String local=dnsServer(context,net);
  StringBuilder out=new StringBuilder("Split DNS: "+name+"\n");
  Map<String,String> results=new LinkedHashMap<>();
  for(String server:new String[]{local,"1.1.1.1","8.8.8.8"}){
   long start=System.nanoTime();
   String label=server.equals(local)?"Wi-Fi ("+server+")":server;
   try{
    List<String> a=DnsTools.query(net,name,1,server,"UDP");
    List<String> aaaa=DnsTools.query(net,name,28,server,"UDP");
    String value="A="+String.join(",",a)+" | AAAA="+String.join(",",aaaa);
    results.put(label,value);
    out.append(label).append(" · ").append(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start))
     .append(" ms\n  ").append(value).append("\n");
   }catch(Exception e){out.append(label).append(": ").append(e.getClass().getSimpleName()).append("\n");}
  }
  out.append("\nLas diferencias pueden deberse a DNS interno, DNS64, CDN, caché o filtrado. "+
   "El DNS público directo puede no representar la VPN o DNS privado de Android.");
  return out.toString();
 }
 private static String dnssec(Network net,String name)throws Exception{
  StringBuilder out=new StringBuilder("DNSSEC · resolvedor validador 1.1.1.1\n");
  for(String domain:new String[]{name,"dnssec-failed.org"}){
   Packet p=ask(net,domain,"1.1.1.1",true,false);
   out.append("\n").append(domain).append("\n");
   out.append("RCODE: ").append(p.rcode).append(" · AD: ").append(p.ad)
    .append(" · TC: ").append(p.tc).append(" · bytes: ").append(p.data.length).append("\n");
   if(p.rcode==0&&p.ad)out.append("El resolvedor afirma datos autenticados (AD).\n");
   else if(p.rcode==2)out.append("SERVFAIL: potencial fallo de validación o indisponibilidad.\n");
   else out.append("Sin confirmación AD; no implica que DNSSEC sea incorrecto.\n");
  }
  return out.append("\nDO solicita registros DNSSEC, AD refleja el estado informado por un tercero. "+
   "NetScope no realiza validación criptográfica local de firmas ni de la cadena de confianza.").toString();
 }
 private static String edns(Context context,Network net,String name)throws Exception{
  String resolver=dnsServer(context,net);
  Packet packet=ask(net,name,resolver,true,true);
  StringBuilder out=new StringBuilder("EDNS / TCP · "+name+"\nServidor: "+resolver+
   "\nUDP: "+packet.data.length+" bytes · RCODE "+packet.rcode+
   " · TC "+packet.tc+" · AD "+packet.ad+"\n");
  out.append("EDNS(0): OPT y tamaño anunciado de 1232 bytes; DO activado.\n");
  if(packet.tc)out.append("La respuesta UDP indica truncamiento (TC=1).\n");
  try{
   byte[] tcp=sendTcp(net,resolver,packet.request);
   out.append("DNS TCP: "+tcp.length+" bytes · RCODE "+(tcp[3]&15)+
    " · TC "+((tcp[2]&2)!=0)+"\n");
   try{out.append("Respuesta A: ").append(DnsTools.parseResponse(tcp,packet.request,1)).append("\n");}
   catch(Exception e){out.append("Respuesta TCP obtenida, error de parsing: "+e.getMessage()).append("\n");}
  }catch(Exception e){out.append("DNS TCP no disponible: ").append(e.getClass().getSimpleName()).append("\n");}
  return out.append("Si un servidor bloquea EDNS o TCP, la prueba mostrará un error, no un resultado simulado.").toString();
 }
 private static final class Packet{
  final byte[] request,data;final int rcode;final boolean ad,tc;
  Packet(byte[] req,byte[] res){
   request=req;data=res;rcode=res[3]&15;ad=(res[3]&0x20)!=0;tc=(res[2]&2)!=0;
  }
 }
 private static Packet ask(Network net,String name,String server,boolean doBit,boolean edns)throws Exception{
  byte[] query=request(name,doBit||edns);
  InetAddress remote=InetAddress.getByName(server);
  try(DatagramSocket socket=new DatagramSocket(null)){
   socket.bind(new InetSocketAddress(0));net.bindSocket(socket);socket.setSoTimeout(2500);
   socket.send(new DatagramPacket(query,query.length,remote,53));
   DatagramPacket packet=new DatagramPacket(new byte[4096],4096);
   socket.receive(packet);
   if(!packet.getAddress().equals(remote))throw new IOException("Respuesta DNS de otro servidor");
   byte[] data=Arrays.copyOf(packet.getData(),packet.getLength());
   if(data.length<12||data[0]!=query[0]||data[1]!=query[1]||(data[2]&0x80)==0)
    throw new IOException("Respuesta DNS inválida");
   return new Packet(query,data);
  }
 }
 private static byte[] request(String name,boolean extended)throws Exception{
  if(name==null||name.length()>245||!name.matches("(?i)[a-z0-9_*-]+(\\.[a-z0-9_*-]+)*\\.?"))
   throw new IllegalArgumentException("Nombre DNS incorrecto");
  ByteArrayOutputStream mem=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(mem);
  d.writeShort(0x4E53);d.writeShort(0x0120);d.writeShort(1);d.writeShort(0);
  d.writeShort(0);d.writeShort(extended?1:0);
  String domain=name.endsWith(".")?name.substring(0,name.length()-1):name;
  for(String part:domain.split("\\.")){
   byte[] bytes=part.getBytes(StandardCharsets.US_ASCII);
   if(bytes.length>63||bytes.length==0)throw new IllegalArgumentException("Etiqueta DNS inválida");
   d.writeByte(bytes.length);d.write(bytes);
  }
  d.writeByte(0);d.writeShort(1);d.writeShort(1);
  if(extended){
   d.writeByte(0);d.writeShort(41);d.writeShort(1232);
   d.writeShort(0);d.writeShort(0x8000);d.writeShort(0); // DO flag
  }
  return mem.toByteArray();
 }
 private static byte[] sendTcp(Network net,String server,byte[] query)throws Exception{
  try(Socket socket=net.getSocketFactory().createSocket()){
   socket.connect(new InetSocketAddress(server,53),2500);socket.setSoTimeout(2800);
   DataOutputStream out=new DataOutputStream(socket.getOutputStream());
   out.writeShort(query.length);out.write(query);out.flush();
   DataInputStream in=new DataInputStream(socket.getInputStream());
   int length=in.readUnsignedShort();
   if(length<12||length>8192)throw new IOException("Longitud DNS TCP incorrecta");
   byte[] data=new byte[length];in.readFully(data);
   if(data[0]!=query[0]||data[1]!=query[1])throw new IOException("ID DNS TCP incorrecto");
   return data;
  }
 }
}

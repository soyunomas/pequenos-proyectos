package com.soyunomas.wifianalyzer;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only information for the actual Wi-Fi transport, even when a VPN is the default. */
public final class NetworkDiagnostics {
    private NetworkDiagnostics(){}

    public static final class Snapshot {
        public final Network network;
        public final Map<String,String> rows;
        public final boolean connected;
        Snapshot(Network n,Map<String,String> rows){network=n;this.rows=rows;connected=n!=null;}
    }

    @SuppressWarnings("deprecation")
    public static Snapshot capture(Context ctx) {
        Map<String,String> rows=new LinkedHashMap<>();
        ConnectivityManager cm=(ConnectivityManager)ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        WifiManager wm=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        Network wifiNetwork=null;
        NetworkCapabilities capabilities=null;
        LinkProperties props=null;
        if(cm!=null){
            Network active=cm.getActiveNetwork();
            if(active!=null){
                NetworkCapabilities ac=cm.getNetworkCapabilities(active);
                rows.put("Red predeterminada",ac==null?"Desconocida":
                    ac.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?"VPN":
                    ac.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Wi-Fi":
                    ac.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)?"Datos móviles":"Otra");
            }
            for(Network n:cm.getAllNetworks()){
                NetworkCapabilities c=cm.getNetworkCapabilities(n);
                if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
                    wifiNetwork=n;capabilities=c;props=cm.getLinkProperties(n);break;
                }
            }
        }
        if(wifiNetwork==null){
            rows.put("Conexión Wi-Fi","No hay conexión Wi-Fi activa");
            return new Snapshot(null,rows);
        }
        rows.put("Conexión Wi-Fi","Activa");
        rows.put("Acceso a Internet",capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                ?"Validado por Android":"Sin validación de Android");
        rows.put("Portal cautivo",capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)?"Posible":"No indicado");
        rows.put("Red de uso medido",capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?"No":"Sí o desconocido");
        WifiInfo info=null;
        if(Build.VERSION.SDK_INT>=29&&capabilities.getTransportInfo() instanceof WifiInfo)
            info=(WifiInfo)capabilities.getTransportInfo();
        if(info==null&&wm!=null){
            try{info=wm.getConnectionInfo();}catch(SecurityException ignored){}
        }
        if(info!=null){
            String ssid=info.getSSID();
            if(ssid!=null&&ssid.startsWith("\"")&&ssid.endsWith("\"")&&ssid.length()>=2)ssid=ssid.substring(1,ssid.length()-1);
            rows.put("SSID",ssid==null?"No disponible":ssid);
            rows.put("BSSID",info.getBSSID()==null?"No disponible":info.getBSSID());
            rows.put("Intensidad",info.getRssi()+" dBm");
            rows.put("Velocidad enlace",info.getLinkSpeed()+" Mbps (negociada, no Internet)");
            if(Build.VERSION.SDK_INT>=29){
                rows.put("Tx / Rx",info.getTxLinkSpeedMbps()+" / "+info.getRxLinkSpeedMbps()+" Mbps");
            }
            rows.put("Frecuencia",info.getFrequency()+" MHz · canal "+ChannelMath.channel(info.getFrequency()));
        }
        if(props!=null){
            rows.put("Interfaz",safe(props.getInterfaceName()));
            StringBuilder ips=new StringBuilder();
            for(LinkAddress a:props.getLinkAddresses()) {
                if(ips.length()>0)ips.append("\n");
                ips.append(a.getAddress().getHostAddress()).append('/').append(a.getPrefixLength());
            }
            rows.put("Direcciones IP",ips.length()==0?"No disponibles":ips.toString());
            StringBuilder dns=new StringBuilder();
            for(InetAddress a:props.getDnsServers()){
                if(dns.length()>0)dns.append("\n");
                dns.append(a.getHostAddress());
            }
            rows.put("Servidores DNS",dns.length()==0?"No disponibles":dns.toString());
            for(RouteInfo route:props.getRoutes())if(route.isDefaultRoute()&&route.getGateway()!=null){
                rows.put(route.getGateway().getAddress().length==4?"Puerta de enlace IPv4":"Puerta de enlace IPv6",
                        route.getGateway().getHostAddress());
            }
            if(Build.VERSION.SDK_INT>=28){
                rows.put("DNS privado",props.isPrivateDnsActive()?"Activo":"No detectado");
                if(props.getPrivateDnsServerName()!=null)
                    rows.put("Servidor DNS privado",props.getPrivateDnsServerName());
            }
            if(props.getMtu()>0)rows.put("MTU",Integer.toString(props.getMtu()));
        }
        return new Snapshot(wifiNetwork,rows);
    }
    private static String safe(String s){return s==null?"No disponible":s;}

    public interface Callback {void finish(String message);}

    /**
     * Runs only after an explicit user action. DNS and HTTPS contact external services;
     * neither task sends SSID, BSSID or a scan list.
     */
    public static void runTest(Network wifi,String kind,Callback callback) {
        new Thread(()->{
            String result;
            if(wifi==null)result="No hay transporte Wi-Fi conectado para la prueba.";
            else if("dns".equals(kind)){
                try{
                    long now=System.nanoTime();
                    InetAddress[] addresses=wifi.getAllByName("example.com");
                    long ms=(System.nanoTime()-now)/1000000;
                    result="Resolución DNS: "+ms+" ms\nexample.com → "+
                            (addresses.length>0?addresses[0].getHostAddress():"Sin direcciones");
                }catch(Exception e){result="Error en resolución DNS: "+e.getClass().getSimpleName();}
            }else{
                HttpURLConnection conn=null;
                try{
                    long now=System.nanoTime();
                    conn=(HttpURLConnection)wifi.openConnection(new URL("https://www.google.com/generate_204"));
                    conn.setConnectTimeout(5000);conn.setReadTimeout(5000);
                    conn.setUseCaches(false);
                    int code=conn.getResponseCode();
                    long ms=(System.nanoTime()-now)/1000000;
                    result="HTTPS (solicitud completa): "+ms+" ms\nRespuesta HTTP: "+code+
                        (code==204?" · conexión correcta":" · comprueba portal cautivo/conexión");
                }catch(Exception e){result="Error HTTPS: "+e.getClass().getSimpleName();}
                finally{if(conn!=null)conn.disconnect();}
            }
            callback.finish(result);
        },"netscope-diagnostic").start();
    }
}

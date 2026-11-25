package com.soyunomas.wifianalyzer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Service names are conventional IANA/well-known associations, not protocol identification.
 * No UDP ports or credentials are tested here.
 */
public final class PortCatalog {
    private PortCatalog() {}

    private static final int[] QUICK={
        21,22,23,53,80,139,443,445,554,631,1883,5000,5357,5900,
        8000,8008,8009,8080,8443,8888,9100,32400
    };
    private static final int[] EXTENDED={
        20,21,22,23,25,53,80,81,88,110,111,135,139,143,389,443,445,465,
        514,515,548,554,587,631,636,873,902,993,995,1080,1194,1433,
        1521,1723,1883,2049,2375,2376,3000,3306,3389,4000,4443,5000,
        5001,5060,5357,5432,5555,5683,5900,5985,6379,6443,7000,7443,
        8000,8008,8009,8080,8081,8123,8181,8443,8888,9000,9090,9100,
        9200,27017,32400
    };
    public static List<Integer> quick(){return list(QUICK);}
    public static List<Integer> extended(){return list(EXTENDED);}
    private static List<Integer> list(int[] ports){
        LinkedHashSet<Integer> unique=new LinkedHashSet<>();
        for(int n:ports)unique.add(n);
        return new ArrayList<>(unique);
    }

    public static int parse(String input){
        if(input==null||!input.matches("[0-9]{1,5}"))throw new IllegalArgumentException("Introduce un puerto TCP entre 1 y 65535.");
        int port=Integer.parseInt(input);
        if(port<1||port>65535)throw new IllegalArgumentException("Puerto TCP fuera de rango.");
        return port;
    }
    public static String service(int p){
        switch(p){
            case 20:case 21:return "FTP";
            case 22:return "SSH";
            case 23:return "Telnet";
            case 25:case 465:case 587:return "SMTP";
            case 53:return "DNS (TCP)";
            case 80:case 81:case 8000:case 8080:case 8081:case 8181:case 8888:case 9000:return "HTTP posible";
            case 443:case 4443:case 7443:case 8443:return "HTTPS posible";
            case 110:return "POP3";
            case 111:return "rpcbind";
            case 135:return "MS RPC";
            case 139:return "NetBIOS";
            case 143:return "IMAP";
            case 389:case 636:return "LDAP";
            case 445:return "SMB";
            case 514:return "RSH";
            case 515:case 631:case 9100:return "Impresión";
            case 548:return "AFP";
            case 554:return "RTSP";
            case 873:return "rsync";
            case 902:return "VMware";
            case 993:return "IMAPS";
            case 995:return "POP3S";
            case 1080:return "SOCKS";
            case 1194:return "OpenVPN (TCP)";
            case 1433:return "SQL Server";
            case 1521:return "Oracle";
            case 1723:return "PPTP";
            case 1883:return "MQTT";
            case 2049:return "NFS";
            case 2375:case 2376:return "Docker";
            case 3000:return "Servicio web posible";
            case 3306:return "MySQL";
            case 3389:return "RDP";
            case 5000:case 5001:return "Administración / web posible";
            case 5060:return "SIP (TCP)";
            case 5357:return "WSD";
            case 5432:return "PostgreSQL";
            case 5555:return "ADB / servicio";
            case 5683:return "CoAP (TCP)";
            case 5900:return "VNC";
            case 5985:return "WinRM";
            case 6379:return "Redis";
            case 6443:return "Kubernetes";
            case 7000:return "Servicio";
            case 8008:case 8009:return "Google Cast posible";
            case 8123:return "Home Assistant posible";
            case 9090:return "Administración posible";
            case 9200:return "Elasticsearch";
            case 27017:return "MongoDB";
            case 32400:return "Plex posible";
            default:return "Servicio desconocido";
        }
    }
    public static boolean web(int p){
        switch(p){
            case 80:case 81:case 443:case 4443:case 7443:case 8443:
            case 8000:case 8080:case 8081:case 8181:case 8888:case 9000:
            case 3000:case 5000:case 5001:case 8008:case 8123:case 9090:
                return true;
            default:return false;
        }
    }
    public static boolean tls(int p){
        return p==443||p==4443||p==7443||p==8443||p==5001;
    }
    public static String url(String ip,int port){
        if(!LanScanner.privateIpv4(ip))throw new IllegalArgumentException("Solo IPv4 privadas.");
        if(port<1||port>65535||!web(port))throw new IllegalArgumentException("No es un puerto web habitual.");
        return (tls(port)?"https://":"http://")+ip+":"+port+"/";
    }
}

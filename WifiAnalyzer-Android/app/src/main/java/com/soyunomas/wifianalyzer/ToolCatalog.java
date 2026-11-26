package com.soyunomas.wifianalyzer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** All NetScope diagnostics are explicit actions; no implicit Internet requests. */
public final class ToolCatalog {
    private ToolCatalog(){}
    public static final class Tool {
        public final int id;
        public final String group, title, description, hint, defaultValue;
        Tool(int id,String group,String title,String description,String hint,String defaultValue){
            this.id=id;this.group=group;this.title=title;this.description=description;this.hint=hint;this.defaultValue=defaultValue;
        }
    }
    public static final List<Tool> ALL=Collections.unmodifiableList(Arrays.asList(
        new Tool(1,"Conectividad","Ping / alcanzabilidad","ICMP cuando el sistema lo permite; alternativa de alcanzabilidad Android.","Host o IP","1.1.1.1"),
        new Tool(2,"Conectividad","Ping continuo","Variación de latencia, disponibilidad y pérdidas en 10 sondeos.","Host o IP","1.1.1.1"),
        new Tool(3,"Conectividad","Traceroute","Traza real si hay un ejecutable compatible en Android.","Host o IP","1.1.1.1"),
        new Tool(4,"Conectividad","MTR","Estadística por salto si existe un binario MTR compatible.","Host o IP","1.1.1.1"),
        new Tool(5,"DNS","DNS avanzado","A, AAAA, MX, TXT, NS, CNAME, SOA, SRV, CAA con DNS UDP.","Nombre de dominio","example.com"),
        new Tool(6,"DNS","DNS inverso PTR","Resuelve IP a nombres mediante el árbol in-addr.arpa o ip6.arpa.","Dirección IP","1.1.1.1"),
        new Tool(7,"DNS","Comparador DNS / DoH / DoT","Compara resolvedores UDP, DNS sobre HTTPS y DNS sobre TLS.","Dominio","example.com"),
        new Tool(8,"Servicios","Inspector TLS","Cadena X.509, versión TLS, cifrado, caducidad y nombres alternativos.","Host[:puerto]","example.com:443"),
        new Tool(9,"Servicios","Cliente HTTP","GET, HEAD, OPTIONS; cabeceras, código, redirección y latencia.","GET/HEAD/OPTIONS https://...","HEAD https://example.com"),
        new Tool(10,"Conectividad","Monitor de disponibilidad","Cinco comprobaciones y registro de cambios de estado.","Host[:puerto]","1.1.1.1:443"),
        new Tool(11,"Conectividad","Diagnóstico IPv6","Direcciones, rutas, DNS y conectividad IPv6 de la red activa.","Host IPv6","2606:4700:4700::1111"),
        new Tool(12,"Descubrimiento","mDNS / DNS-SD","Consulta multicast de servicios anunciados en la Wi-Fi.","Tipo de servicio","_services._dns-sd._udp.local"),
        new Tool(13,"Descubrimiento","UPnP / SSDP","Búsqueda multicast y lista de descripciones publicadas.","Servicio ST","ssdp:all"),
        new Tool(14,"Descubrimiento","SNMP GET","Lectura SNMP v1 GET; OID sysDescr.0 predeterminado.","IP de equipo LAN","192.168.1.1"),
        new Tool(15,"Descubrimiento","Wake-on-LAN","Paquete mágico UDP para una MAC indicada expresamente.","Dirección MAC","AA:BB:CC:DD:EE:FF"),
        new Tool(16,"Rendimiento","iPerf3","Disponibilidad e instrucciones para usar iPerf3 real en un terminal compatible.","IP servidor iPerf3","192.168.1.10"),
        new Tool(17,"Rendimiento","Prueba de velocidad","Descarga HTTPS acotada contra un servidor elegido; subida mediante endpoint propio.","URL descarga HTTPS","https://speed.cloudflare.com/__down?bytes=2000000"),
        new Tool(18,"Internet","RDAP / WHOIS","Registro RDAP de IP o dominio mediante servicio público.","IP o dominio","1.1.1.1"),
        new Tool(19,"Internet","ASN / BGP","Prefijos y ASN de recursos consultados a RIPEstat.","IP o prefijo","1.1.1.1"),
        new Tool(20,"Internet","IP pública, VPN y CGNAT","IPv4 pública y comparación con datos de salida de Android.","Sin parámetros",""),
        new Tool(21,"Conectividad","MTU y Path MTU","MTU anunciada y prueba de descubrimiento si ping admite modo DF.","Host o IP","1.1.1.1"),
        new Tool(22,"Conectividad","NTP / reloj","Tiempo, RTT y diferencia aproximada por UDP/123.","Servidor NTP","time.google.com"),
        new Tool(23,"Conectividad","Diagnóstico DHCP","Lease, puerta de enlace, máscara, DNS y enlace si disponibles.","Sin parámetros",""),
        new Tool(24,"Descubrimiento","Inventario de LAN","Detecta cambios de dispositivos conocidos, almacena IP en el teléfono.","Sin parámetros",""),
        new Tool(25,"Informes","Informe técnico","Exporta resultados anteriores de esta sesión como texto estructurado.","Sin parámetros","")
    ));
    public static Tool get(int id){
        for(Tool t:ALL)if(t.id==id)return t;
        throw new IllegalArgumentException("Herramienta desconocida");
    }
}

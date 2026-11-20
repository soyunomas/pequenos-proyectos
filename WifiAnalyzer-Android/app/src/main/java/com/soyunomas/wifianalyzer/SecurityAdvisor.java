package com.soyunomas.wifianalyzer;

import java.util.Locale;

/** Conservative, passive analysis of advertised Wi-Fi security. */
public final class SecurityAdvisor {
    private SecurityAdvisor() {}

    public static final class Evaluation {
        public final String protocol,risk,advice;
        public final int score;
        public Evaluation(String protocol,int score,String risk,String advice){
            this.protocol=protocol;this.score=score;this.risk=risk;this.advice=advice;
        }
    }

    public static Evaluation evaluate(String security) {
        String p=security==null?"":security.toUpperCase(Locale.ROOT);
        if(p.contains("WEP"))
            return new Evaluation("WEP",5,"crítico","WEP está obsoleto. Sustituye por WPA2-AES o WPA3.");
        if(p.contains("WPA2/WPA3")||(p.contains("SAE")&&p.contains("PSK")))
            return new Evaluation("WPA2/WPA3 mixto",80,"bajo","Considera WPA3 exclusivo si todos los dispositivos lo admiten.");
        if(p.contains("WPA3")||p.contains("SAE"))
            return new Evaluation("WPA3",100,"bajo","Mantén firmware y dispositivos actualizados.");
        if(p.contains("OWE"))
            return new Evaluation("Enhanced Open (OWE)",70,"medio","Cifrado sin autenticación del punto de acceso. Verifica la identidad de la red.");
        if(p.contains("ENTERPRISE")||p.contains("EAP")||p.contains("SUITE_B"))
            return new Evaluation("WPA-Enterprise",90,"bajo","Configura validación del certificado del servidor de autenticación.");
        if(p.contains("WPA2")||p.contains("PSK")) {
            if(p.contains("TKIP")&&!p.contains("CCMP"))
                return new Evaluation("TKIP",30,"alto","Desactiva TKIP; usa AES o WPA3.");
            return new Evaluation("WPA2-Personal",75,"medio","Usa clave larga y única, WPS desactivado y firmware actualizado.");
        }
        if(p.contains("WPA"))return new Evaluation("WPA antiguo",30,"alto","Migra a WPA2-AES o WPA3.");
        if(p.contains("ABIERTA")||p.contains("OPEN")||p.contains("ESS"))
            return new Evaluation("Abierta",0,"alto","Red abierta: prioriza HTTPS y verifica que sea la red legítima.");
        return new Evaluation("Desconocida",40,"indeterminado","La información anunciada es insuficiente para valorar su protección.");
    }
}

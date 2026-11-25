package com.soyunomas.wifianalyzer;

import java.util.Locale;

/** Best-effort category from actual observed service names, not guessed MACs. */
public final class DeviceProfile {
    private DeviceProfile() {}

    public static String category(String details, boolean gateway, boolean self) {
        if (gateway) return "Router";
        if (self) return "Este móvil";
        String d=details==null?"":details.toLowerCase(Locale.ROOT);
        if (has(d,"_ipp.","_printer.","printer","impressora","impresora","jetdirect","9100","_pdl-datastream.")) return "Impresora";
        if (has(d,"_googlecast.","chromecast","medienderer","mediarenderer","airplay","_raop.","smarttv","television")) return "TV / multimedia";
        if (has(d,"iphone","ipad","android phone","android mobile","smartphone","pixel phone","_companion-link.")) return "Móvil";
        if (has(d,"_smb.","_workstation.","netbios","samba","windows","445 responde")) return "Ordenador / NAS";
        if (has(d,"_hap.","homekit","_matter.","_ewelink.")) return "Domótica";
        if (has(d,"_ssh.","22 responde")) return "Servidor / red";
        if (has(d,"_http.","web server","80 responde","8080 responde")) return "Dispositivo web";
        if (has(d,"ssdp","upnp")) return "Equipo UPnP";
        return "Dispositivo";
    }
    public static String glyph(String category) {
        switch(category) {
            case "Router": return "◎";
            case "Este móvil": return "▣";
            case "Impresora": return "▤";
            case "TV / multimedia": return "▣";
            case "Ordenador / NAS": return "▥";
            case "Servidor / red": return "▦";
            case "Domótica": return "⌂";
            default: return "◉";
        }
    }
    public static String serviceHint(String details) {
        if(details==null)return "";
        String d=details.toLowerCase(Locale.ROOT);
        if(d.contains("samsung"))return "Samsung (anunciado)";
        if(d.contains("sonos"))return "Sonos (anunciado)";
        if(d.contains("chromecast")||d.contains("googlecast"))return "Google Cast (servicio)";
        if(d.contains("apple")||d.contains("airplay"))return "Apple / AirPlay (servicio)";
        if(d.contains("brother"))return "Brother (anunciado)";
        if(d.contains("epson"))return "Epson (anunciado)";
        if(d.contains("hewlett")||d.contains("hp printer"))return "HP (anunciado)";
        return "";
    }
    private static boolean has(String text,String... words) {
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
}

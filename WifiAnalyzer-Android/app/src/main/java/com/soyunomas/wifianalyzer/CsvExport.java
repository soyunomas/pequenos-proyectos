package com.soyunomas.wifianalyzer;

import java.util.List;
import java.util.Locale;

/** Escapes RFC-4180 values and guards against spreadsheet formula injection. */
public final class CsvExport {
    private CsvExport() {}

    static String cell(String input) {
        String value = input == null ? "" : input;
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String render(List<NetworkEntry> networks) {
        StringBuilder csv = new StringBuilder("SSID,BSSID,Banda,Canal,Frecuencia_MHz,Senal_dBm,Calidad,Seguridad,Ancho_MHz,Estandar,Conectada\r\n");
        for (NetworkEntry n : networks) {
            csv.append(cell(n.ssid)).append(',').append(cell(n.bssid)).append(',')
               .append(cell(ChannelMath.bandLabel(n.band))).append(',').append(n.channel).append(',')
               .append(n.mhz).append(',').append(n.dbm).append(',')
               .append(ChannelMath.quality(n.dbm)).append(',')
               .append(cell(n.security)).append(',').append(n.width).append(',')
               .append(cell(n.standard)).append(',').append(n.connected ? "si" : "no").append("\r\n");
        }
        return csv.toString();
    }
}

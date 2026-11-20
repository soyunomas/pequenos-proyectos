package com.soyunomas.wifianalyzer;

/** Immutable, UI-friendly snapshot. No credentials, GPS coordinates or history are persisted. */
public final class NetworkEntry {
    public final String ssid;
    public final String bssid;
    public final String security;
    public final String standard;
    public final int mhz;
    public final int dbm;
    public final int width;
    public final int channel;
    public final int band;
    public final boolean connected;

    public NetworkEntry(String ssid, String bssid, String security, String standard,
                        int mhz, int dbm, int width, boolean connected) {
        this.ssid = ssid == null || ssid.isEmpty() ? "(Red oculta)" : ssid;
        this.bssid = bssid == null ? "" : bssid;
        this.security = security;
        this.standard = standard;
        this.mhz = mhz;
        this.dbm = dbm;
        this.width = width;
        this.channel = ChannelMath.channel(mhz);
        this.band = ChannelMath.band(mhz);
        this.connected = connected;
    }
}

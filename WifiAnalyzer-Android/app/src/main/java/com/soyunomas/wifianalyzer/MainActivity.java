package com.soyunomas.wifianalyzer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.location.LocationManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Native Android UI. All scanning goes through WifiManager and is never simulated.
 * The Android system throttles scan requests; cached results are marked as such.
 */
public final class MainActivity extends Activity {
    private static final int BG = 0xFFF6F8FC;
    private static final int SURFACE = 0xFFFFFFFF;
    private static final int SURFACE_ALT = 0xFFF0F3FF;
    private static final int TEXT = 0xFF172235;
    private static final int MUTED = 0xFF66778D;
    private static final int ACCENT = 0xFF456CFF;
    private static final int PURPLE = 0xFF3659C9;
    private static final int ORANGE = 0xFFAA5B15;
    private static final int[] PALETTE = {ACCENT, 0xFF2887CC, 0xFF5378CE, 0xFF1C9EA8, 0xFF7790E4, 0xFF347AC7, 0xFF6474BB};
    private static final long UPDATE_MS = 15000L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<NetworkEntry> networks = new ArrayList<>();
    private final SurveyHistory survey = new SurveyHistory();
    private long scanAgeMs=-1;
    private String exportText="";
    private final java.util.Set<String> favourites=new java.util.HashSet<>();
    private boolean showFavourites=false;
    private String query="";
    private boolean automatic=true;
    private WifiManager wifi;
    private OuiDatabase oui;
    private LinearLayout root;
    private LinearLayout bottomNav;
    private LinearLayout content;
    private TextView status;
    private TextView summary;
    private TextView heroName;
    private TextView heroDetail;
    private TextView heroSignal;
    private ScrollView screenScroll;
    private int tab = 0;
    private int band = ChannelMath.BAND_ALL;
    private boolean running = false;
    private boolean scanning = false;
    private long lastResultMillis = 0L;
    private int sortMode = 0;
    private String selectedBssid = "";
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if(automatic)refresh(false);
            handler.postDelayed(this, UPDATE_MS);
        }
    };
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            scanning = false;
            updateFromWifi();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        oui = new OuiDatabase(this);
        favourites.addAll(getPreferences(MODE_PRIVATE).getStringSet("fav",java.util.Collections.emptySet()));
        automatic=getPreferences(MODE_PRIVATE).getBoolean("automatic",true);
        if (state != null) {
            tab = state.getInt("tab", 0);
            band = state.getInt("band", ChannelMath.BAND_ALL);
        }
        buildShell();
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
        out.putInt("band", band);
    }

    @Override protected void onResume() {
        super.onResume();
        running = true;
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(scanReceiver, filter);
        handler.removeCallbacks(ticker);
        if (hasPermissions() && isLocationOn()) {
            updateFromWifi();
            refresh(true);
        } else {
            render();
        }
        handler.postDelayed(ticker, UPDATE_MS);
    }

    @Override protected void onPause() {
        super.onPause();
        running = false;
        handler.removeCallbacks(ticker);
        try { unregisterReceiver(scanReceiver); } catch (IllegalArgumentException ignored) { }
    }

    private boolean hasPermissions() {
        boolean location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean nearby = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        return location && nearby;
    }

    private boolean isLocationOn() {
        LocationManager location = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return location != null && location.isLocationEnabled();
    }

    private void askPermissions() {
        ArrayList<String> needed = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 9);
        else if (!isLocationOn()) startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        else refresh(true);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == 9 && hasPermissions()) {
            if (!isLocationOn()) startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            else refresh(true);
        } else if (request == 9) {
            new AlertDialog.Builder(this).setTitle("Permiso necesario")
                .setMessage("Android requiere ubicación precisa para listar las redes Wi-Fi cercanas. La aplicación no guarda ni transmite tu ubicación.")
                .setPositiveButton("Entendido", (d,w) -> render()).show();
        }
        render();
    }

    private void refresh(boolean requested) {
        if (!hasPermissions() || !isLocationOn()) { render(); return; }
        if (wifi == null || !wifi.isWifiEnabled()) { render(); return; }
        if (scanning) return;
        try {
            scanning = wifi.startScan();
            if (!scanning) {
                // Android may throttle scanning; cached results still remain useful.
                updateFromWifi();
                if (requested) Toast.makeText(this, "Android ha limitado el escaneo; mostrando resultados disponibles", Toast.LENGTH_SHORT).show();
            } else {
                status.setText("Buscando redes Wi-Fi…");
            }
        } catch (SecurityException e) {
            status.setText("Sin permiso para escanear");
        } catch (Exception e) {
            status.setText("No se pudo iniciar el escaneo");
        }
    }

    @SuppressWarnings("deprecation")
    private void updateFromWifi() {
        if (!hasPermissions() || wifi == null) return;
        try {
            List<ScanResult> scans = wifi.getScanResults();
            if (scans == null) return;
            WifiInfo connection = wifi.getConnectionInfo();
            String activeBssid = connection == null ? "" : connection.getBSSID();
            networks.clear();
            scanAgeMs=-1;
            for (ScanResult s : scans) {
                if (s == null || s.frequency <= 0) continue;
                int width = width(s.channelWidth);
                boolean connected = activeBssid != null && activeBssid.equalsIgnoreCase(s.BSSID);
                NetworkEntry n = new NetworkEntry(s.SSID, s.BSSID, security(s.capabilities),
                    standard(s), s.frequency, s.level, width, connected);
                networks.add(n);
                survey.add(n,s.timestamp,System.currentTimeMillis());
                long age=Math.max(0,(android.os.SystemClock.elapsedRealtimeNanos()/1000-s.timestamp)/1000);
                if(scanAgeMs<0||age<scanAgeMs)scanAgeMs=age;
            }
            lastResultMillis = System.currentTimeMillis();
            render();
        } catch (SecurityException e) {
            status.setText("Permiso de Wi-Fi denegado");
        } catch (Exception e) {
            status.setText("Error leyendo redes");
        }
    }

    private static int width(int w) {
        switch (w) {
            case ScanResult.CHANNEL_WIDTH_40MHZ: return 40;
            case ScanResult.CHANNEL_WIDTH_80MHZ: return 80;
            case ScanResult.CHANNEL_WIDTH_160MHZ: return 160;
            default: return 20;
        }
    }

    private static String security(String c) {
        if (c == null || c.isEmpty()) return "Desconocida";
        if (c.contains("SAE") && c.contains("PSK")) return "WPA2/WPA3";
        if (c.contains("SAE")) return "WPA3";
        if (c.contains("OWE")) return "OWE";
        if (c.contains("EAP")) return "Enterprise";
        if (c.contains("PSK")) return "WPA2";
        if (c.contains("WEP")) return "WEP";
        return "Abierta";
    }

    private static String standard(ScanResult s) {
        if (Build.VERSION.SDK_INT >= 30) {
            switch (s.getWifiStandard()) {
                case ScanResult.WIFI_STANDARD_11N: return "Wi-Fi 4";
                case ScanResult.WIFI_STANDARD_11AC: return "Wi-Fi 5";
                case ScanResult.WIFI_STANDARD_11AX: return "Wi-Fi 6";
                default: return "Wi-Fi";
            }
        }
        return "Wi-Fi";
    }

    private void buildShell() {
        root=column();
        root.setBackgroundColor(BG);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom,
                    Math.max(insets.getInsets(android.view.WindowInsets.Type.systemGestures()).bottom,
                        insets.getInsets(android.view.WindowInsets.Type.tappableElement()).bottom));
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(16),top+dp(9),dp(16),0);
            if(bottomNav!=null)bottomNav.setPadding(0,dp(7),0,bottom+dp(14));
            return insets;
        });
        root.requestApplyInsets();

        LinearLayout header=line();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text("NetScope",25,TEXT,true);
        name.setLetterSpacing(-0.02f);
        header.addView(name,new LinearLayout.LayoutParams(0,dp(49),1));
        Button export=button("⇧",ACCENT);
        export.setTextSize(20);
        export.setContentDescription("Exportar datos Wi-Fi");
        export.setOnClickListener(v->exportCsv());
        header.addView(export,new LinearLayout.LayoutParams(dp(47),dp(43)));
        Button refresh=button("↻",ACCENT);
        refresh.setTextSize(23);
        refresh.setContentDescription("Actualizar redes Wi-Fi");
        refresh.setOnClickListener(v->refresh(true));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(47),dp(43));
        rp.leftMargin=dp(6);
        header.addView(refresh,rp);
        root.addView(header);

        LinearLayout hero=surface();
        hero.setPadding(dp(17),dp(13),dp(17),dp(13));
        LinearLayout top=line();top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=text("CONEXIÓN WI-FI",11,MUTED,true);
        label.setLetterSpacing(0.1f);
        top.addView(label,new LinearLayout.LayoutParams(0,dp(27),1));
        TextView live=text("●  EN DIRECTO",10,ACCENT,true);
        top.addView(live);
        hero.addView(top);
        LinearLayout middle=line();middle.setGravity(Gravity.CENTER_VERTICAL);
        TextView glyph=text("◉",35,ACCENT,true);
        middle.addView(glyph,new LinearLayout.LayoutParams(dp(46),dp(59)));
        LinearLayout naming=column();
        heroName=text("Buscando conexión…",20,TEXT,true);
        heroName.setSingleLine(true);
        heroName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        naming.addView(heroName);
        heroDetail=text("Sin resultados todavía",12,MUTED,false);
        naming.addView(heroDetail);
        middle.addView(naming,new LinearLayout.LayoutParams(0,-2,1));
        heroSignal=text("—",15,ACCENT,true);
        middle.addView(heroSignal);
        hero.addView(middle);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(7);
        root.addView(hero,hp);

        LinearLayout quick=line();
        quick.setPadding(0,dp(10),0,dp(2));
        LinearLayout lan=actionTile("◎","Dispositivos","Explorar red local");
        lan.setOnClickListener(v->startActivity(new Intent(this,LanActivity.class)));
        quick.addView(lan,new LinearLayout.LayoutParams(0,dp(75),1));
        LinearLayout diag=actionTile("⌁","Diagnóstico","IP, DNS y pruebas");
        diag.setOnClickListener(v->startActivity(new Intent(this,ToolkitActivity.class)));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,dp(75),1);
        qp.leftMargin=dp(9);
        quick.addView(diag,qp);
        root.addView(quick);

        LinearLayout glance=line();
        glance.setGravity(Gravity.CENTER_VERTICAL);
        summary=text("",12,TEXT,true);
        glance.addView(summary,new LinearLayout.LayoutParams(0,dp(35),1));
        Button auto=button(automatic?"Auto: ON":"Auto: OFF",automatic?ACCENT:MUTED);
        auto.setTextSize(11);
        auto.setOnClickListener(v->{
            automatic=!automatic;
            getPreferences(MODE_PRIVATE).edit().putBoolean("automatic",automatic).apply();
            buildShell();render();
        });
        glance.addView(auto,new LinearLayout.LayoutParams(dp(92),dp(34)));
        root.addView(glance);
        status=text("",11,MUTED,false);
        root.addView(status);

        screenScroll=new ScrollView(this);
        screenScroll.setFillViewport(true);
        screenScroll.setClipToPadding(false);
        content=column();
        content.setPadding(0,dp(7),0,dp(15));
        screenScroll.addView(content);
        root.addView(screenScroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout nav=line();
        bottomNav=nav;
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(0,dp(7),0,dp(1));
        String[] labels={"Redes","Canales","Señal","Seguridad","Consejos"};
        String[] icons={"◉","▥","⌁","◇","✦"};
        for(int i=0;i<labels.length;i++){
            final int index=i;
            LinearLayout cell=column();
            cell.setGravity(Gravity.CENTER);
            boolean selected=i==tab;
            TextView symbol=text(icons[i],20,selected?ACCENT:MUTED,true);
            symbol.setGravity(Gravity.CENTER);
            cell.addView(symbol);
            TextView caption=text(labels[i],10,selected?ACCENT:MUTED,selected);
            caption.setGravity(Gravity.CENTER);
            caption.setSingleLine(true);
            cell.addView(caption);
            cell.setContentDescription("Abrir "+labels[i]);
            cell.setClickable(true);
            cell.setOnClickListener(v->{tab=index;buildShell();render();});
            if(selected)cell.setBackground(shape(SURFACE_ALT,14,0));
            nav.addView(cell,new LinearLayout.LayoutParams(0,dp(52),1));
        }
        root.addView(nav);
        root.requestApplyInsets();
    }

    private LinearLayout actionTile(String symbol,String title,String sub){
        LinearLayout tile=line();
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(10),dp(8),dp(8),dp(8));
        tile.setBackground(shape(SURFACE,15,0xFFE2E8F1));
        TextView marker=text(symbol,26,ACCENT,true);
        marker.setGravity(Gravity.CENTER);
        marker.setBackground(shape(SURFACE_ALT,11,0));
        tile.addView(marker,new LinearLayout.LayoutParams(dp(42),dp(45)));
        LinearLayout desc=column();
        desc.setPadding(dp(8),0,0,0);
        TextView heading=text(title,14,TEXT,true);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        desc.addView(heading);
        TextView hint=text(sub,11,MUTED,false);
        hint.setSingleLine(true);
        hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        desc.addView(hint);
        tile.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
        return tile;
    }
    private LinearLayout surface(){
        LinearLayout result=column();
        result.setBackground(shape(SURFACE,17,0xFFE2E8F1));
        return result;
    }
    private android.graphics.drawable.GradientDrawable shape(int fill,int radius,int stroke){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(fill);bg.setCornerRadius(dp(radius));
        if(stroke!=0)bg.setStroke(dp(1),stroke);
        return bg;
    }

    private void render() {
        if (content == null) return;
        int savedScroll=screenScroll==null?0:screenScroll.getScrollY();
        status.setText(scanning ? "Explorando redes…" :
            lastResultMillis == 0 ? "Sin resultados todavía" :
            "Datos recibidos " + android.text.format.DateFormat.format("HH:mm:ss", lastResultMillis) +
            " · antigüedad aprox. " + (scanAgeMs<0 ? "desconocida" : (scanAgeMs/1000)+" s"));
        summary.setText(networks.size()+" redes · 2,4: "+count(ChannelMath.BAND_24)+
            "  · 5: "+count(ChannelMath.BAND_5)+"  · 6: "+count(ChannelMath.BAND_6));
        updateHero();
        content.removeAllViews();

        if (!hasPermissions()) {
            message("Permiso de ubicación", "Para mostrar redes cercanas, Android exige acceso a ubicación precisa y, desde Android 13, permiso de dispositivos Wi-Fi cercanos.", "Conceder permisos", () -> askPermissions());
            return;
        }
        if (!isLocationOn()) {
            message("Activa la ubicación", "Android no entrega resultados de exploración Wi-Fi con la ubicación del dispositivo desactivada.", "Abrir ajustes", () -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            return;
        }
        if (wifi == null || !wifi.isWifiEnabled()) {
            message("Wi-Fi desactivado", "Activa el Wi-Fi y vuelve a esta pantalla.", "Ajustes Wi-Fi", () -> startActivity(new Intent(Settings.Panel.ACTION_WIFI)));
            return;
        }

        addBands();
        if (tab == 0) showNetworks();
        else if (tab == 1) showChannels();
        else if (tab == 2) showHistory();
        else if(tab==3) showSecurity();
        else showAdvice();
        if(screenScroll!=null&&savedScroll>0)screenScroll.post(()->screenScroll.scrollTo(0,savedScroll));
    }

    @SuppressWarnings("deprecation")
    private void updateHero(){
        if(heroName==null)return;
        String ssid="Sin conexión Wi-Fi";
        String extra="Activa el Wi-Fi para ver la red";
        String strength="—";
        try {
            if(wifi!=null&&wifi.isWifiEnabled()){
                WifiInfo info=wifi.getConnectionInfo();
                if(info!=null&&info.getNetworkId()!=-1){
                    String reported=info.getSSID();
                    if(reported!=null&&reported.length()>1&&reported.charAt(0)==34
                            &&reported.charAt(reported.length()-1)==34)
                        reported=reported.substring(1,reported.length()-1);
                    if(reported==null||reported.isEmpty()||reported.equals("<unknown ssid>"))
                        reported="Wi-Fi conectada";
                    ssid=reported;
                    int mhz=info.getFrequency();
                    extra=(mhz>0?ChannelMath.bandLabel(ChannelMath.band(mhz))+"  ·  Canal "+ChannelMath.channel(mhz):"Conectada");
                    if(info.getRssi()<0&&info.getRssi()>-127)strength=info.getRssi()+" dBm";
                } else {
                    ssid="Wi-Fi activado";
                    extra="Sin conexión a una red";
                }
            }
        }catch(Exception ignored){extra="Información restringida por Android";}
        heroName.setText(ssid);
        heroDetail.setText(extra);
        heroSignal.setText(strength);
    }

    private void addBands() {
        LinearLayout bar=line();
        bar.setPadding(0,0,0,dp(7));
        int[] bands={ChannelMath.BAND_ALL,ChannelMath.BAND_24,ChannelMath.BAND_5,ChannelMath.BAND_6};
        String[] titles={"Todas","2,4 GHz","5 GHz","6 GHz"};
        for(int i=0;i<bands.length;i++){
            final int selectedBand=bands[i];
            boolean selected=band==selectedBand;
            TextView pill=text(titles[i],12,selected?ACCENT:MUTED,selected);
            pill.setGravity(Gravity.CENTER);
            pill.setBackground(shape(selected?SURFACE_ALT:SURFACE,11,selected?0:0xFFE2E8F1));
            pill.setOnClickListener(v->{band=selectedBand;render();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(37),1);
            if(i>0)p.leftMargin=dp(6);
            bar.addView(pill,p);
        }
        content.addView(bar);
    }

    private List<NetworkEntry> filtered() {
        ArrayList<NetworkEntry> list = new ArrayList<>();
        for (NetworkEntry n : networks)
            if((band==ChannelMath.BAND_ALL||n.band==band)
                &&(!showFavourites||favourites.contains(n.bssid))
                &&(query.isEmpty()||(n.ssid+" "+n.bssid+" "+n.security)
                    .toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))))
                list.add(n);
        if (sortMode == 0) Collections.sort(list, (a,b) -> Integer.compare(b.dbm,a.dbm));
        else Collections.sort(list, (a,b) -> {
            int cmp = Integer.compare(a.mhz,b.mhz);
            return cmp != 0 ? cmp : Integer.compare(b.dbm,a.dbm);
        });
        return list;
    }

    private int count(int b) {
        int num=0;
        for (NetworkEntry n : networks) if(n.band==b)num++;
        return num;
    }

    private void showNetworks() {
        LinearLayout heading=line();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Redes cercanas",18,TEXT,true),new LinearLayout.LayoutParams(0,dp(43),1));
        Button sort=button(sortMode==0?"↕ Por señal":"↕ Por canal",MUTED);
        sort.setOnClickListener(v->{sortMode=(sortMode+1)%2;render();});
        heading.addView(sort,new LinearLayout.LayoutParams(dp(96),dp(36)));
        content.addView(heading);
        LinearLayout filters=line();
        Button search=button(query.isEmpty()?"⌕  Buscar red":"⌕  "+query,ACCENT);
        search.setSingleLine(true);
        search.setEllipsize(android.text.TextUtils.TruncateAt.END);
        search.setOnClickListener(v->{
            android.widget.EditText input=new android.widget.EditText(this);
            input.setSingleLine(true);input.setText(query);
            input.setHint("SSID, BSSID o seguridad");
            new AlertDialog.Builder(this).setTitle("Buscar redes")
                .setView(input).setPositiveButton("Aplicar",(d,w)->{
                    query=input.getText().toString().trim();render();
                }).setNeutralButton("Limpiar",(d,w)->{query="";render();})
                .setNegativeButton("Cancelar",null).show();
        });
        filters.addView(search,new LinearLayout.LayoutParams(0,dp(39),2));
        Button fav=button(showFavourites?"★ Favoritas":"☆ Todas",showFavourites?ACCENT:MUTED);
        fav.setOnClickListener(v->{showFavourites=!showFavourites;render();});
        LinearLayout.LayoutParams fParams=new LinearLayout.LayoutParams(0,dp(39),1);
        fParams.leftMargin=dp(7);filters.addView(fav,fParams);
        content.addView(filters);

        List<NetworkEntry> items=filtered();
        if(items.isEmpty()){empty("No hay redes en esta banda. Actualiza el escaneo o ajusta los filtros.");return;}
        LinearLayout list=surface();
        list.setPadding(dp(11),dp(4),dp(11),dp(5));
        LinearLayout.LayoutParams listParams=new LinearLayout.LayoutParams(-1,-2);
        listParams.topMargin=dp(10);
        content.addView(list,listParams);
        for(int i=0;i<items.size();i++){
            NetworkEntry n=items.get(i);
            boolean expanded=n.bssid.equals(selectedBssid);
            LinearLayout group=column();
            LinearLayout line=line();line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(dp(2),dp(8),dp(2),dp(8));
            TextView icon=text(n.connected?"◉":"⌁",26,n.connected?ACCENT:0xFF8198B9,true);
            icon.setGravity(Gravity.CENTER);
            icon.setBackground(shape(SURFACE_ALT,11,0));
            line.addView(icon,new LinearLayout.LayoutParams(dp(45),dp(50)));
            LinearLayout details=column();
            details.setPadding(dp(10),0,dp(3),0);
            TextView ssid=text((favourites.contains(n.bssid)?"★ ":"")+n.ssid,15,TEXT,true);
            ssid.setSingleLine(true);
            ssid.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(ssid);
            details.addView(text(ChannelMath.bandLabel(n.band)+" · Canal "+n.channel+" · "+n.security,11,MUTED,false));
            TextView manufacturer=text(oui.lookup(n.bssid),10,MUTED,false);
            manufacturer.setSingleLine(true);
            manufacturer.setEllipsize(android.text.TextUtils.TruncateAt.END);
            details.addView(manufacturer);
            line.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout metrics=column();metrics.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            TextView power=text(n.dbm+" dBm",13,ACCENT,true);power.setGravity(Gravity.RIGHT);
            metrics.addView(power);
            TextView quality=text(ChannelMath.qualityLabel(n.dbm),10,MUTED,false);
            quality.setGravity(Gravity.RIGHT);
            metrics.addView(quality);
            line.addView(metrics,new LinearLayout.LayoutParams(dp(76),-2));
            line.setOnClickListener(v->{selectedBssid=expanded?"":n.bssid;render();});
            group.addView(line);
            if(expanded){
                LinearLayout extra=column();extra.setPadding(dp(13),0,dp(11),dp(12));
                extra.addView(text("BSSID: "+n.bssid,12,MUTED,false));
                extra.addView(text("Frecuencia "+n.mhz+" MHz · Ancho "+n.width+" MHz · "+n.standard,12,MUTED,false));
                extra.addView(text("Intensidad de señal, no velocidad de Internet.",11,MUTED,false));
                extra.addView(new SignalBar(this,ChannelMath.quality(n.dbm),ACCENT),
                    new LinearLayout.LayoutParams(-1,dp(15)));
                Button star=button(favourites.contains(n.bssid)?"★ Quitar favorita":"☆ Añadir favorita",ACCENT);
                star.setOnClickListener(v->{
                    if(!favourites.add(n.bssid))favourites.remove(n.bssid);
                    getPreferences(MODE_PRIVATE).edit()
                       .putStringSet("fav",new java.util.HashSet<>(favourites)).apply();
                    render();
                });
                LinearLayout.LayoutParams starP=new LinearLayout.LayoutParams(-1,dp(39));
                starP.topMargin=dp(7);extra.addView(star,starP);
                group.addView(extra);
            }
            list.addView(group);
            if(i<items.size()-1){
                View separator=new View(this);separator.setBackgroundColor(0xFFE7ECF4);
                LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(1));
                sp.leftMargin=dp(57);
                list.addView(separator,sp);
            }
        }
    }

    private void showChannels() {
        if(filtered().isEmpty()){ empty("Sin datos de canales. Actualiza el escaneo.");return; }
        content.addView(text("Distribución por canal",19,TEXT,true));
        content.addView(text("Curvas estimadas a partir de frecuencias, anchos y niveles RSSI.",12,MUTED,false));
        int[] bands= band==ChannelMath.BAND_ALL ?
            new int[]{ChannelMath.BAND_24,ChannelMath.BAND_5,ChannelMath.BAND_6} : new int[]{band};
        for(int b:bands){
            ArrayList<NetworkEntry> items=new ArrayList<>();
            for(NetworkEntry n:networks) if(n.band==b)items.add(n);
            if(items.isEmpty())continue;
            LinearLayout card=card();
            card.addView(text(ChannelMath.bandLabel(b)+" · "+items.size()+" redes",16,TEXT,true));
            card.addView(new SpectrumGraph(this, items, b),new LinearLayout.LayoutParams(-1,dp(245)));
            content.addView(card);
        }
    }

    private void showHistory() {
        List<NetworkEntry> items=filtered();
        content.addView(text("Historial de intensidad",19,TEXT,true));
        content.addView(text("Hasta 100 observaciones nuevas por BSSID. Las lecturas repetidas se deduplican mediante su timestamp.",12,MUTED,false));
        if(items.isEmpty()){ empty("Todavía no hay lecturas para dibujar.");return; }
        int shown=0;
        for(NetworkEntry n:items) {
            if(shown++>=8)break;
            LinearLayout box=card();
            box.addView(text(n.ssid+"   ·   "+n.dbm+" dBm",14,TEXT,true));
            ArrayList<Integer> readings=new ArrayList<>(survey.values(n.bssid));
            box.addView(text("Muestras "+survey.points(n.bssid)+" · mín "+survey.min(n.bssid)+" · media "+survey.avg(n.bssid)+" · máx "+survey.max(n.bssid)+" dBm",11,MUTED,false));
            box.addView(new HistoryGraph(this,readings,PALETTE[(shown-1)%PALETTE.length]),
                new LinearLayout.LayoutParams(-1,dp(105)));
            content.addView(box);
        }
    }

    private void showSecurity() {
        content.addView(text("Auditoría pasiva de seguridad",19,TEXT,true));
        content.addView(text("Evaluación orientativa por protocolo anunciado. No se prueban contraseñas ni se realizan ataques.",12,MUTED,false));
        List<NetworkEntry> items=new ArrayList<>(filtered());
        Collections.sort(items,(a,b)->Integer.compare(SecurityAdvisor.evaluate(a.security).score,SecurityAdvisor.evaluate(b.security).score));
        if(items.isEmpty()){empty("Sin redes que evaluar.");return;}
        for(NetworkEntry n:items){
            SecurityAdvisor.Evaluation e=SecurityAdvisor.evaluate(n.security);
            LinearLayout box=card();
            box.addView(text(n.ssid+" · "+e.score+"/100",15,e.score>=75?ACCENT:e.score>35?ORANGE:0xFFFF809C,true));
            box.addView(text(e.protocol+" · riesgo "+e.risk+" · canal "+n.channel,12,MUTED,false));
            box.addView(text(e.advice,12,MUTED,false));
            content.addView(box);
        }
    }

    private void showAdvice() {
        content.addView(text("Canales menos ocupados",19,TEXT,true));
        content.addView(text("Estimación orientativa por señal y solapamiento. No mide interferencias no Wi-Fi, ruido ni rendimiento real; tu router y la normativa local determinan los canales disponibles.",12,MUTED,false));
        int[] bands=band==ChannelMath.BAND_ALL ? new int[]{ChannelMath.BAND_24,ChannelMath.BAND_5,ChannelMath.BAND_6}:new int[]{band};
        ArrayList<ChannelMath.Observation> obs=new ArrayList<>();
        for(NetworkEntry n:networks)obs.add(new ChannelMath.Observation(n.mhz,n.dbm,n.width));
        for(int b:bands){
            LinearLayout card=card();
            card.addView(text(ChannelMath.bandLabel(b),17,TEXT,true));
            if(count(b)==0)card.addView(text("No hay redes detectadas en esta banda. Sin datos suficientes para recomendar un canal.",12,MUTED,false));
            else{
                List<ChannelMath.Recommendation> ranks=ChannelMath.recommendations(obs,b);
                for(int i=0;i<Math.min(4,ranks.size());i++){
                    ChannelMath.Recommendation r=ranks.get(i);
                    LinearLayout row=line();
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(text("Canal "+r.channel,15,TEXT,true),
                        new LinearLayout.LayoutParams(0,dp(37),1));
                    row.addView(text(r.score+" / 100  ·  "+r.conflicts+" solap.",12,i==0?ACCENT:MUTED,false));
                    card.addView(row);
                }
            }
            content.addView(card);
        }
        LinearLayout note=card();
        note.addView(text("Consejo de diagnóstico",16,TEXT,true));
        note.addView(text("En 2,4 GHz, compara primero los canales 1, 6 y 11 con ancho de 20 MHz. En 5 y 6 GHz hay más alternativas, pero algunas pueden requerir DFS o no estar disponibles en tu país. Esta app no cambia la configuración del router.",13,MUTED,false));
        content.addView(note);
    }

    private void exportCsv() {
        if(networks.isEmpty()){Toast.makeText(this,"Aún no hay redes",Toast.LENGTH_SHORT).show();return;}
        String[] choices={"CSV · redes","JSON · redes","JSON · anónimo","CSV · historial"};
        new AlertDialog.Builder(this).setTitle("Exportar").setItems(choices,(dialog,choice)->{
            String filename,mime;
            if(choice==0){exportText=CsvExport.render(networks);filename="netscope-redes.csv";mime="text/csv";}
            else if(choice==1){exportText=JsonExport.render(networks,false);filename="netscope-redes.json";mime="application/json";}
            else if(choice==2){exportText=JsonExport.render(networks,true);filename="netscope-anonimo.json";mime="application/json";}
            else{exportText=survey.csv();filename="netscope-historial.csv";mime="text/csv";}
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_TITLE,filename);
            startActivityForResult(intent,77);
        }).show();
    }

    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==77&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
                if(out==null)throw new java.io.IOException("Destino inaccesible");
                out.write(exportText.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this,"Archivo exportado",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Error de exportación",Toast.LENGTH_LONG).show();}
            finally{exportText="";}
        }
    }

    private void message(String title,String detail,String action,Runnable click) {
        LinearLayout card=card();
        card.addView(text(title,20,TEXT,true));
        TextView txt=text(detail,14,MUTED,false);
        txt.setPadding(0,dp(12),0,dp(12));
        card.addView(txt);
        Button button=button(action,ACCENT);
        button.setOnClickListener(v->click.run());
        card.addView(button);
        content.addView(card);
    }
    private void empty(String text){content.addView(text(text,14,MUTED,false));}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v+0.5f);}
    private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout card(){
        LinearLayout c=surface();
        c.setPadding(dp(15),dp(13),dp(15),dp(13));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(9);
        c.setLayoutParams(p);
        return c;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(2),dp(3),dp(2),dp(3));
        if(bold)v.setTypeface(null,1);
        return v;
    }
    private Button button(String text,int color){
        Button b=new Button(this);
        b.setText(text);b.setTextSize(12);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);
        b.setPadding(dp(8),0,dp(8),0);
        b.setTextColor(color);
        b.setBackground(shape(SURFACE,11,0xFFE2E8F1));
        return b;
    }

    private static final class SignalBar extends View {
        final Paint paint=new Paint(3);
        final int value,color;
        SignalBar(Context ctx,int value,int color){super(ctx);this.value=value;this.color=color;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float h=getHeight(),w=getWidth();
            paint.setColor(0xFFE8ECF5);c.drawRoundRect(0,dp(5),w,h-dp(5),dp(4),dp(4),paint);
            paint.setColor(color);c.drawRoundRect(0,dp(5),w*value/100f,h-dp(5),dp(4),dp(4),paint);
        }
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    }

    private static final class SpectrumGraph extends View {
        private final List<NetworkEntry> nets;
        private final int band;
        private final Paint p=new Paint(3);
        SpectrumGraph(Context ctx,List<NetworkEntry> nets,int band){super(ctx);this.nets=nets;this.band=band;}
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(),h=getHeight();
            float left=dp(29), right=w-dp(10), top=dp(20), bottom=h-dp(30);
            int fMin=band==ChannelMath.BAND_24?2400:band==ChannelMath.BAND_5?5000:5925;
            int fMax=band==ChannelMath.BAND_24?2500:band==ChannelMath.BAND_5?5900:7125;
            p.setStrokeWidth(dp(1));
            p.setTextSize(dp(10));p.setColor(MUTED);
            for(int db=-100;db<=-30;db+=20) {
                float y=map(db,-100,-30,bottom,top);
                p.setColor(0xFFE5EBF3);c.drawLine(left,y,right,y,p);
                p.setColor(MUTED);c.drawText(db+"",0,y+dp(3),p);
            }
            for(int j=0;j<6;j++) {
                int f=fMin+(fMax-fMin)*j/5;
                p.setColor(MUTED);
                c.drawText(ChannelMath.channel(f)+"",map(f,fMin,fMax,left,right)-dp(5),h-dp(7),p);
            }
            int index=0;
            for(NetworkEntry n:nets) {
                int color=PALETTE[index++%PALETTE.length];
                float x=map(n.mhz,fMin,fMax,left,right);
                float half=Math.max(10,n.width/2f);
                float x0=map(n.mhz-half,fMin,fMax,left,right);
                float x1=map(n.mhz+half,fMin,fMax,left,right);
                float peak=map(Math.max(-100,Math.min(-30,n.dbm)),-100,-30,bottom,top);
                Path path=new Path();path.moveTo(x0,bottom);
                path.cubicTo(x0+(x-x0)*0.55f,peak,x-(x-x0)*0.25f,peak,x,peak);
                path.cubicTo(x+(x1-x)*0.25f,peak,x1-(x1-x)*0.55f,peak,x1,bottom);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(color);
                c.drawPath(path,p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
        private float map(float x,float a,float b,float m,float n){return m+(x-a)*(n-m)/(b-a);}
    }

    private static final class HistoryGraph extends View {
        private final List<Integer> values;
        private final int color;
        private final Paint p=new Paint(3);
        HistoryGraph(Context ctx,List<Integer> readings,int color){
            super(ctx);values=new ArrayList<>(readings);this.color=color;
        }
        @Override protected void onDraw(Canvas c) {
            float w=getWidth(),h=getHeight(),left=dp(4),right=w-dp(4),top=dp(8),bottom=h-dp(8);
            for(int i=0;i<4;i++){
                float y=top+(bottom-top)*i/3f;
                p.setColor(0xFFE5EBF3);p.setStrokeWidth(dp(1));c.drawLine(left,y,right,y,p);
            }
            if(values.size()==0)return;
            Path line=new Path();
            for(int i=0;i<values.size();i++){
                float x=values.size()==1?left:(left+(right-left)*i/(values.size()-1));
                float y=bottom-(Math.max(-100,Math.min(-30,values.get(i)))+100)*(bottom-top)/70f;
                if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
            }
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2.5f));c.drawPath(line,p);
            p.setStyle(Paint.Style.FILL);
        }
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    }
}

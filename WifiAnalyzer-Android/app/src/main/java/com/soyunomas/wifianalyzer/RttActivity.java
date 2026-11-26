package com.soyunomas.wifianalyzer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real IEEE 802.11mc RTT measurements, only when hardware and APs support ranging. */
public final class RttActivity extends Activity{
    private static final int BLUE=0xFF456CFF,INK=0xFF172235;
    private TextView output;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFFF6F8FC);
        getWindow().setNavigationBarColor(0xFFF6F8FC);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setBackgroundColor(0xFFF6F8FC);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.systemGestures()).bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            int d=(int)(16*getResources().getDisplayMetrics().density);
            view.setPadding(d,top+d,d,bottom+d);return insets;
        });
        TextView header=new TextView(this);header.setText("Distancia Wi-Fi RTT");
        header.setTextSize(23);header.setTextColor(INK);root.addView(header);
        TextView hint=new TextView(this);
        hint.setText("Mide distancias con Fine Timing Measurement (802.11mc). "+
            "Necesita teléfono compatible, AP que responda RTT y ubicación activada. "+
            "La distancia es estimada y no representa un mapa de posiciones.");
        hint.setTextColor(0xFF66778D);hint.setTextSize(13);hint.setPadding(0,12,0,14);
        root.addView(hint);
        Button run=new Button(this);run.setText("Medir ahora");run.setAllCaps(false);run.setTextColor(BLUE);
        run.setOnClickListener(v->measure());root.addView(run);
        ScrollView scroll=new ScrollView(this);
        output=new TextView(this);output.setTextSize(14);output.setTextColor(INK);
        output.setTextIsSelectable(true);output.setPadding(0,18,0,0);
        output.setText("Aún no se ha realizado ninguna medida.");
        scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.requestApplyInsets();
    }
    private void measure(){
        if(Build.VERSION.SDK_INT<28||!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)){
            output.setText("Este móvil no ofrece Wi-Fi RTT (802.11mc).");return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},91);
            output.setText("Autoriza ubicación precisa y vuelve a pulsar Medir.");return;
        }
        WifiRttManager rtt=(WifiRttManager)getSystemService(WIFI_RTT_RANGING_SERVICE);
        if(rtt==null||!rtt.isAvailable()){
            output.setText("El servicio Wi-Fi RTT no está disponible ahora. Comprueba Wi-Fi y ubicación.");return;
        }
        WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        List<ScanResult> candidates=new ArrayList<>();
        try{
            for(ScanResult s:wifi.getScanResults())
                if(s.is80211mcResponder&&candidates.size()<8)candidates.add(s);
        }catch(SecurityException e){output.setText("No hay permisos suficientes para resultados Wi-Fi.");return;}
        if(candidates.isEmpty()){
            output.setText("Ningún punto de acceso detectado anuncia compatibilidad con RTT. "+
                "No se inventan distancias.");return;
        }
        StringBuilder head=new StringBuilder("Consultando "+candidates.size()+" APs compatibles…\n");
        for(ScanResult scan:candidates)head.append(scan.SSID).append(" · ").append(scan.BSSID).append("\n");
        output.setText(head.toString());
        try{
            RangingRequest request=new RangingRequest.Builder().addAccessPoints(candidates).build();
            rtt.startRanging(request,getMainExecutor(),new RangingResultCallback(){
                @Override public void onRangingFailure(int code){
                    output.setText("Ranging Wi-Fi RTT falló · código Android "+code);
                }
                @Override public void onRangingResults(List<RangingResult> results){
                    StringBuilder text=new StringBuilder("Medidas RTT obtenidas del hardware:\n\n");
                    int success=0;
                    for(RangingResult result:results){
                        text.append(result.getMacAddress()).append(": ");
                        if(result.getStatus()==RangingResult.STATUS_SUCCESS){
                            success++;
                            text.append(String.format(Locale.ROOT,"%.2f m ± %.2f m",
                                result.getDistanceMm()/1000.0,result.getDistanceStdDevMm()/1000.0));
                            text.append(" · ").append(result.getRssi()).append(" dBm");
                        }else text.append("sin medida válida (estado ").append(result.getStatus()).append(')');
                        text.append("\n");
                    }
                    text.append("\nÉxitos: ").append(success).append("/").append(results.size());
                    text.append("\nLa incertidumbre reportada es de la API; el entorno afecta la precisión.");
                    output.setText(text.toString());
                    ToolHistory.add("Wi-Fi RTT","APs compatibles",text.toString());
                }
            });
        }catch(SecurityException e){output.setText("Permiso de ubicación o Wi-Fi denegado.");}
        catch(Exception e){output.setText("No se pudo iniciar RTT: "+e.getClass().getSimpleName());}
    }
}

package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** Roaming and network-change session: bounded to two minutes, foreground only. */
public final class FieldSessionActivity extends Activity {
    private static final int BLUE=0xFF456CFF,INK=0xFF172235;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final StringBuilder log=new StringBuilder();
    private TextView output,status;
    private Button start,stop;
    private boolean running;
    private int id,samples,changes;
    private String previous="",previousBssid="";
    private long started;
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(!running)return;
            if(System.currentTimeMillis()-started>=120000){finishSession("Fin de sesión: 120 segundos");return;}
            record();
            handler.postDelayed(this,2500);
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);id=getIntent().getIntExtra("tool",28);
        getWindow().setStatusBarColor(0xFFF6F8FC);
        getWindow().setNavigationBarColor(0xFFF6F8FC);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(16,16,16,16);root.setBackgroundColor(0xFFF6F8FC);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.systemGestures()).bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            int d=(int)(getResources().getDisplayMetrics().density*16);
            view.setPadding(d,top+d,d,bottom+d);return insets;
        });
        TextView title=new TextView(this);
        title.setText(id==28?"Roaming Wi-Fi":"Registro de cambios de red");
        title.setTextSize(23);title.setTextColor(INK);title.setPadding(4,8,4,12);
        root.addView(title);
        TextView hint=new TextView(this);
        hint.setText("Medición local de hasta 2 minutos. No se ejecuta en segundo plano. "+
            "Android puede ocultar datos si faltan permisos de ubicación.");
        hint.setTextColor(0xFF66778D);hint.setTextSize(13);root.addView(hint);
        status=new TextView(this);status.setText("Sin iniciar.");
        status.setTextColor(BLUE);status.setPadding(4,20,4,12);root.addView(status);
        ScrollView scroll=new ScrollView(this);
        output=new TextView(this);output.setTextColor(INK);output.setTextSize(13);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setTextIsSelectable(true);
        output.setPadding(6,10,6,10);scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this);
        start=new Button(this);start.setText("Iniciar");start.setAllCaps(false);
        start.setTextColor(BLUE);
        start.setOnClickListener(v->begin());
        row.addView(start,new LinearLayout.LayoutParams(0,-2,1));
        stop=new Button(this);stop.setText("Detener");stop.setAllCaps(false);
        stop.setOnClickListener(v->finishSession("Detenido por el usuario"));
        row.addView(stop,new LinearLayout.LayoutParams(0,-2,1));
        Button copy=new Button(this);copy.setText("Copiar");copy.setAllCaps(false);
        copy.setOnClickListener(v->{
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("NetScope",log.toString()));
        });
        row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row);stop.setEnabled(false);root.requestApplyInsets();
    }
    private void begin(){
        if(running)return;
        log.setLength(0);previous="";previousBssid="";changes=0;samples=0;
        started=System.currentTimeMillis();running=true;start.setEnabled(false);stop.setEnabled(true);
        append("Inicio: "+new Date(started));
        record();handler.postDelayed(tick,2500);
    }
    @SuppressWarnings("deprecation")
    private void record(){
        samples++;
        NetworkDiagnostics.Snapshot snap=NetworkDiagnostics.capture(this);
        String now=new SimpleDateFormat("HH:mm:ss",Locale.ROOT).format(new Date());
        String current;
        if(id==28){
            WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info=wifi==null?null:wifi.getConnectionInfo();
            String bssid=info==null?"sin BSSID":info.getBSSID();
            if(bssid==null)bssid="sin BSSID";
            int rssi=info==null?0:info.getRssi();
            current=bssid;
            if(!previousBssid.equals(bssid)){
                if(!previousBssid.isEmpty())changes++;
                append(now+" BSSID: "+bssid+(previousBssid.isEmpty()?" (inicial)":" · ROAMING")+
                    " · RSSI "+rssi+" dBm");
                previousBssid=bssid;
            }else if(samples%3==0)append(now+" RSSI: "+rssi+" dBm · BSSID "+bssid);
        }else{
            StringBuilder values=new StringBuilder();
            for(String key:new String[]{"Red predeterminada","Conexión Wi-Fi","SSID",
                "BSSID","Direcciones IP","Servidores DNS","Puerta de enlace IPv4","DNS privado"}){
                String v=snap.rows.get(key);if(v!=null)values.append(key).append(": ").append(v).append("\n");
            }
            current=values.toString();
            if(!current.equals(previous)){
                if(!previous.isEmpty())changes++;
                append(now+(previous.isEmpty()?" Estado inicial":" · CAMBIO DETECTADO")+"\n"+current);
                previous=current;
            }
        }
        status.setText(samples+" observaciones · "+changes+" cambios");
    }
    private void append(String text){
        if(log.length()>26000)log.delete(0,Math.min(8000,log.length()));
        log.append(text).append("\n");
        output.setText(log.toString());
    }
    private void finishSession(String reason){
        if(!running)return;
        running=false;handler.removeCallbacks(tick);
        append(reason+" · "+changes+" cambios en "+samples+" observaciones.");
        ToolHistory.add(id==28?"Roaming Wi-Fi":"Cambios de red","Wi-Fi local",log.toString());
        start.setEnabled(true);stop.setEnabled(false);status.setText(reason);
    }
    @Override protected void onStop(){finishSession("Sesión detenida al salir de la aplicación");super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
}

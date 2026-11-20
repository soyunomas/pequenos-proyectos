package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Visible, consent-based LAN discovery. No elevated privileges or raw packet access. */
public final class LanActivity extends Activity {
    private static final int BG=0xFF090F1E, SURFACE=0xFF152139,
        TEXT=0xFFF0F6FF, MUTED=0xFF9CAFC7, ACCENT=0xFF57DED4,
        ORANGE=0xFFFFBD74, PURPLE=0xFFA896FA;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private OuiDatabase oui;
    private LanScanner.Plan plan;
    private LanScanner.ScanHandle current;
    private final List<LanScanner.Device> devices=new ArrayList<>();
    private TextView status;
    private LinearLayout body;
    private Button action;
    private boolean arpReadable=false,scanning=false;
    private String issue="";

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        oui=new OuiDatabase(this);
        try{plan=LanScanner.plan(this);}catch(SecurityException ignored){}
        build();
    }
    @Override protected void onDestroy(){
        if(current!=null)current.cancel();
        super.onDestroy();
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(1);root.setPadding(dp(17),dp(34),dp(17),dp(14));
        root.setBackgroundColor(BG);setContentView(root);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(0);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("LAN / ARP",26,TEXT,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(55),1));
        Button back=button("← Volver",MUTED);
        back.setOnClickListener(v->finish());
        header.addView(back);
        root.addView(header);

        TextView subtitle=text("Dispositivos de tu red local",15,MUTED,false);
        subtitle.setPadding(0,dp(4),0,dp(10));root.addView(subtitle);

        LinearLayout notice=card();
        notice.addView(text("DESCUBRIMIENTO DE RED",12,ACCENT,true));
        notice.addView(text("La aplicación prueba conexiones TCP breves con dispositivos de tu propia subred. No necesita root. La lectura de MAC desde ARP puede estar bloqueada por Android.",13,TEXT,false));
        root.addView(notice);

        if(plan==null){
            TextView no=text("Conéctate a una red Wi-Fi con IPv4 privada (10.x, 172.16–31.x o 192.168.x). No se explorarán redes móviles, VPN ni Internet.",14,ORANGE,false);
            no.setPadding(0,dp(14),0,dp(14));root.addView(no);
        }else{
            LinearLayout network=card();
            network.addView(text("RED ACTUAL",12,ACCENT,true));
            network.addView(text("Tu IP: "+plan.ownIp,14,TEXT,true));
            network.addView(text("Rango escaneado: "+plan.scannedCidr,13,MUTED,false));
            network.addView(text("Subred real: "+plan.originalCidr,12,MUTED,false));
            if(!plan.gateway.isEmpty())network.addView(text("Gateway: "+plan.gateway,13,MUTED,false));
            network.addView(text(plan.addresses.size()+" direcciones potenciales · máximo 254 hosts",12,MUTED,false));
            root.addView(network);
        }

        status=text("Inicia una búsqueda para detectar equipos de tu red.",13,MUTED,false);
        status.setPadding(0,dp(14),0,dp(10));root.addView(status);

        action=button("●  ESCANEAR DISPOSITIVOS",ACCENT);
        action.setTextSize(17);
        action.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        action.setMinimumHeight(dp(60));
        action.setHeight(dp(60));
        action.setEnabled(plan!=null);
        action.setOnClickListener(v->{if(scanning)stop();else start();});
        root.addView(action);

        LinearLayout tools=new LinearLayout(this);tools.setOrientation(0);
        Button export=button("Exportar CSV",PURPLE);
        export.setOnClickListener(v->export());
        tools.addView(export,new LinearLayout.LayoutParams(0,dp(46),1));
        Button details=button("Sobre las MAC",MUTED);
        details.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("¿Por qué faltan las MAC?")
            .setMessage("Android 10 y posteriores restringen /proc/net/arp a aplicaciones sin privilegios. "
                +"Sin acceso root ni permisos de sistema, un escaneo TCP no puede garantizar la MAC de cada host. "
                +"El fabricante solo se identifica si hay una MAC real y su prefijo coincide con la base IEEE integrada. "
                +"Una MAC aleatoria/local no permite atribuir un fabricante fiable. "
                +"La conexión rechazada también confirma actividad de un equipo.")
            .setPositiveButton("Entendido",null).show());
        tools.addView(details,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(tools);

        ScrollView scroll=new ScrollView(this);
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(9),0,dp(25));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        renderResults();
    }
    private void start(){
        if(plan==null||scanning)return;
        scanning=true;devices.clear();issue="";
        action.setText("■  DETENER ESCANEO");
        status.setText("Sondeando direcciones de la red local…");
        renderResults();
        current=LanScanner.start(plan,oui,new LanScanner.Callbacks(){
            @Override public void progress(int scanned,int total,int found){
                ui.post(()->{if(!isFinishing())
                    status.setText("Exploradas "+scanned+" / "+total+" direcciones · "+found+" respuestas");});
            }
            @Override public void finished(List<LanScanner.Device> found,boolean readable,String error){
                ui.post(()->{
                    if(isFinishing()||isDestroyed())return;
                    scanning=false;arpReadable=readable;issue=error;
                    devices.clear();devices.addAll(found);
                    action.setText("↻  VOLVER A ESCANEAR");
                    String count=devices.size()+" dispositivos o rutas detectadas";
                    status.setText(count+(issue.isEmpty()?"":" · "+issue));
                    renderResults();
                });
            }
        });
    }
    private void stop(){
        if(current!=null)current.cancel();
        scanning=false;
        action.setText("↻  VOLVER A ESCANEAR");
        status.setText("Se ha solicitado detener el escaneo.");
    }
    private void renderResults(){
        if(body==null)return;
        body.removeAllViews();
        TextView summary=text(devices.size()+" equipos identificados · base IEEE: "+oui.count()+" prefijos",15,TEXT,true);
        summary.setPadding(0,dp(7),0,dp(10));body.addView(summary);
        if(devices.isEmpty()){
            body.addView(text(scanning?"Explorando…":"Aún no hay equipos. Algunos dispositivos bloquean las conexiones TCP y pueden no aparecer.",13,MUTED,false));
            return;
        }
        if(!arpReadable)body.addView(text("Android no permite leer la tabla ARP aquí: por eso las MAC de los hosts pueden no estar disponibles.",12,ORANGE,false));
        for(LanScanner.Device d:devices){
            LinearLayout card=card();
            card.addView(text((d.gateway?"◈  ROUTER / GATEWAY":"●  DISPOSITIVO")+"  "+d.ip,17,TEXT,true));
            card.addView(text("MAC: "+(d.mac.isEmpty()?"No disponible":d.mac),13,MUTED,false));
            card.addView(text("Fabricante: "+d.vendor,14,ACCENT,false));
            card.addView(text("Detección: "+d.detail,12,MUTED,false));
            body.addView(card);
        }
        body.addView(text("OUI = titular del bloque MAC, no siempre la marca comercial del dispositivo. Un escaneo no detecta todos los equipos.",12,MUTED,false));
    }

    private void export(){
        if(devices.isEmpty()){
            Toast.makeText(this,"No hay datos para exportar",Toast.LENGTH_SHORT).show();return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,"netscope-lan.csv");
        startActivityForResult(i,81);
    }
    @Override protected void onActivityResult(int req,int result,Intent intent){
        super.onActivityResult(req,result,intent);
        if(req!=81||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        StringBuilder out=new StringBuilder("IP,MAC,Fabricante,Estado,Gateway\r\n");
        for(LanScanner.Device d:devices)
            out.append(CsvExport.cell(d.ip)).append(',').append(CsvExport.cell(d.mac))
                .append(',').append(CsvExport.cell(d.vendor)).append(',')
                .append(CsvExport.cell(d.detail)).append(',').append(d.gateway).append("\r\n");
        try(java.io.OutputStream stream=getContentResolver().openOutputStream(intent.getData())){
            if(stream==null)throw new java.io.IOException();
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"CSV guardado",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Error al exportar",Toast.LENGTH_SHORT).show();}
    }
    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);
        l.setPadding(dp(15),dp(14),dp(15),dp(14));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(SURFACE);bg.setCornerRadius(dp(17));bg.setStroke(dp(1),0xFF283B56);
        l.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(10);l.setLayoutParams(params);return l;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setPadding(dp(3),dp(5),dp(3),dp(5));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(String s,int color){
        Button b=new Button(this);b.setText(s);b.setTextColor(color);b.setAllCaps(false);
        b.setTextSize(12);b.setPadding(dp(12),0,dp(12),0);b.setMinHeight(0);b.setMinimumHeight(0);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFF1B2A44);bg.setCornerRadius(dp(13));b.setBackground(bg);
        return b;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}

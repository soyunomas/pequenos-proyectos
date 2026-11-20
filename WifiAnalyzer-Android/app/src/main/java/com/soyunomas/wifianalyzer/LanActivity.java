package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Fast LAN discovery with incremental results and an explicit deeper scan option. */
public final class LanActivity extends Activity {
    private static final int BG=0xFF090F1E, SURFACE=0xFF152139, SURFACE2=0xFF1B2A44,
            TEXT=0xFFF0F6FF, MUTED=0xFF9CAFC7, ACCENT=0xFF57DED4,
            ORANGE=0xFFFFBD74, PURPLE=0xFFA896FA, GREEN=0xFF94E8B5;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<LanScanner.Device> devices=new ArrayList<>();
    private OuiDatabase oui;
    private LanScanner.Plan plan;
    private LanScanner.ScanHandle current;
    private LinearLayout body,root;
    private TextView status,subtitle,bigCount,smallCount;
    private ProgressBar progress;
    private Button fastButton,deepButton,stopButton;
    private boolean arpReadable=false,scanning=false;
    private boolean deep=false;
    private long scanStartedMs=0;
    private int scanned=0,total=0,generation=0;
    private String issue="";
    private final Runnable redraw=()->renderResults();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try{plan=LanScanner.plan(this);}catch(SecurityException ignored){}
        build();
        // Opening LAN / ARP from the home screen is the user's explicit scan action.
        if(plan!=null) {
            ui.post(this::startFast);
            new Thread(()->{
                OuiDatabase loaded=new OuiDatabase(getApplicationContext());
                ui.post(()->{
                    if(!isFinishing()&&!isDestroyed()){
                        oui=loaded;
                        updateHeader();
                        scheduleRender();
                    }
                });
            },"netscope-oui").start();
        }
    }

    @Override protected void onDestroy(){
        generation++;
        if(current!=null)current.cancel();
        ui.removeCallbacks(redraw);
        super.onDestroy();
    }

    private void build() {
        root=new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(dp(18),dp(28),dp(18),dp(12));
        root.setBackgroundColor(BG);
        setContentView(root);

        LinearLayout header=row();
        TextView title=text("Equipos en tu red",24,TEXT,true);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(51),1));
        Button back=button("Volver",MUTED);
        back.setOnClickListener(v->finish());
        header.addView(back);
        root.addView(header);

        subtitle=text("Explorador LAN · estilo inventario",13,MUTED,false);
        subtitle.setPadding(dp(3),dp(2),0,dp(12));
        root.addView(subtitle);

        LinearLayout card=card();
        card.addView(text("TU RED WI-FI",11,ACCENT,true));
        if(plan==null){
            card.addView(text("No hay Wi-Fi privada IPv4 disponible.",15,TEXT,true));
            card.addView(text("Conéctate a una red 192.168.x, 10.x o 172.16–31.x para descubrir equipos.",12,MUTED,false));
        }else{
            card.addView(text(plan.scannedCidr,20,TEXT,true));
            card.addView(text("Este móvil: "+plan.ownIp+
                    (plan.gateway.isEmpty()?"":"    Router: "+plan.gateway),12,MUTED,false));
        }
        root.addView(card);

        LinearLayout counters=row();
        counters.setPadding(dp(5),dp(12),dp(5),dp(3));
        bigCount=text("0",31,ACCENT,true);
        counters.addView(bigCount);
        smallCount=text(" direcciones identificadas",13,MUTED,false);
        counters.addView(smallCount);
        root.addView(counters);

        status=text("Preparado",12,MUTED,false);
        root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams progressParams=new LinearLayout.LayoutParams(-1,dp(4));
        progressParams.topMargin=dp(8);progressParams.bottomMargin=dp(12);
        root.addView(progress,progressParams);

        LinearLayout controls=row();
        fastButton=button("Buscar rápido",ACCENT);
        fastButton.setTextSize(13);
        fastButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        fastButton.setOnClickListener(v->startFast());
        controls.addView(fastButton,new LinearLayout.LayoutParams(0,dp(49),1));
        deepButton=button("Más completo",PURPLE);
        deepButton.setTextSize(12);
        deepButton.setOnClickListener(v->startDeep());
        LinearLayout.LayoutParams deepParams=new LinearLayout.LayoutParams(0,dp(49),1);
        deepParams.leftMargin=dp(8);
        controls.addView(deepButton,deepParams);
        stopButton=button("■",ORANGE);
        stopButton.setContentDescription("Detener búsqueda");
        stopButton.setOnClickListener(v->stop());
        LinearLayout.LayoutParams stopParams=new LinearLayout.LayoutParams(dp(47),dp(49));
        stopParams.leftMargin=dp(8);
        controls.addView(stopButton,stopParams);
        root.addView(controls);

        LinearLayout extras=row();
        Button export=button("Exportar CSV",MUTED);
        export.setOnClickListener(v->export());
        extras.addView(export,new LinearLayout.LayoutParams(0,dp(44),1));
        Button information=button("Cómo funciona / MAC",MUTED);
        information.setOnClickListener(v->explain());
        LinearLayout.LayoutParams infop=new LinearLayout.LayoutParams(0,dp(44),1);
        infop.leftMargin=dp(7);
        extras.addView(information,infop);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(44));
        ep.topMargin=dp(8);ep.bottomMargin=dp(6);
        root.addView(extras,ep);

        ScrollView scroll=new ScrollView(this);
        body=new LinearLayout(this);
        body.setOrientation(1);
        body.setPadding(0,dp(5),0,dp(20));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setScanning(false);
        renderResults();
    }

    private void updateHeader() {
        if(oui!=null&&subtitle!=null)
            subtitle.setText("LAN · "+oui.count()+" prefijos IEEE offline");
    }

    private void setScanning(boolean value){
        scanning=value;
        fastButton.setEnabled(plan!=null&&!value);
        deepButton.setEnabled(plan!=null&&!value);
        stopButton.setEnabled(value);
        stopButton.setAlpha(value?1f:0.35f);
        fastButton.setAlpha(scanning?0.5f:1f);
        deepButton.setAlpha(scanning?0.5f:1f);
    }

    private void startFast(){start(false);}
    private void startDeep(){start(true);}

    private void start(boolean full){
        if(plan==null||scanning)return;
        ++generation;
        final int id=generation;
        if(current!=null)current.cancel();
        devices.clear();
        scanned=0;
        total=plan.addresses.size();
        scanStartedMs=android.os.SystemClock.elapsedRealtime();
        issue="";
        arpReadable=false;
        deep=full;
        progress.setProgress(0);
        setScanning(true);
        status.setText(full?"Analizando puertos adicionales…":"Identificando equipos de la LAN…");
        scheduleRender();
        // MAC vendors are attached by the scanner if the offline database has loaded.
        final OuiDatabase registry=oui;
        current=LanScanner.start(plan,registry,full,new LanScanner.Callbacks() {
            @Override public void discovered(LanScanner.Device d) {
                ui.post(()->{
                    if(generation!=id||isFinishing()||isDestroyed())return;
                    replaceDevice(d);
                    updateCounters();
                    scheduleRender();
                });
            }
            @Override public void progress(int n,int max,int count) {
                ui.post(()->{
                    if(generation!=id||isFinishing()||isDestroyed())return;
                    scanned=n;total=max;
                    progress.setProgress(max==0?100:Math.min(100,n*100/max));
                    status.setText(n+" de "+max+" IP examinadas · "+
                            (full?"búsqueda completa":"búsqueda rápida"));
                });
            }
            @Override public void finished(List<LanScanner.Device> results,boolean readable,String error) {
                ui.post(()->{
                    if(generation!=id||isFinishing()||isDestroyed())return;
                    setScanning(false);
                    arpReadable=readable;
                    issue=error;
                    devices.clear();
                    devices.addAll(results);
                    long seconds=Math.max(1,(android.os.SystemClock.elapsedRealtime()-scanStartedMs+500)/1000);
                    progress.setProgress(error.isEmpty()?100:progress.getProgress());
                    status.setText(devices.size()+" direcciones visibles · "+seconds+" s"+
                            (error.isEmpty()?"":" · "+error));
                    updateCounters();
                    renderResults();
                });
            }
        });
    }

    private void stop(){
        if(!scanning)return;
        if(current!=null)current.cancel();
        setScanning(false);
        status.setText("Cancelado · se conservan los equipos ya descubiertos");
        renderResults();
    }

    private void replaceDevice(LanScanner.Device d) {
        for(int i=0;i<devices.size();i++){
            if(devices.get(i).ip.equals(d.ip)){devices.set(i,d);return;}
        }
        devices.add(d);
    }
    private void updateCounters() {
        bigCount.setText(Integer.toString(devices.size()));
        smallCount.setText(devices.size()==1?" dirección identificada":" direcciones identificadas");
    }
    private void scheduleRender(){
        ui.removeCallbacks(redraw);
        ui.postDelayed(redraw,75);
    }
    private void renderResults(){
        if(body==null)return;
        body.removeAllViews();
        List<LanScanner.Device> shown=new ArrayList<>(devices);
        Collections.sort(shown,Comparator
            .comparingInt((LanScanner.Device d)->d.gateway?0:d.ip.equals(plan==null?"":plan.ownIp)?1:2)
            .thenComparingLong(d->LanScanner.toLong(d.ip)));

        if(shown.isEmpty()){
            TextView placeholder=text(scanning?
                "Buscando… Los dispositivos aparecerán aquí en cuanto respondan.":
                "No hay equipos visibles. Algunos bloquean todas las conexiones y no aparecen en este método.",14,MUTED,false);
            placeholder.setPadding(dp(4),dp(16),dp(4),dp(12));
            body.addView(placeholder);
            return;
        }
        for(LanScanner.Device d:shown){
            LinearLayout card=card();
            LinearLayout first=row();
            TextView ip=text(d.ip,18,TEXT,true);
            first.addView(ip,new LinearLayout.LayoutParams(0,dp(35),1));
            TextView tag=text(d.gateway?"ROUTER":d.ip.equals(plan==null?"":plan.ownIp)?
                "ESTE MÓVIL":"ACTIVO",11,d.gateway?ACCENT:GREEN,true);
            first.addView(tag);
            card.addView(first);
            card.addView(text(d.detail,13,MUTED,false));
            if(!d.mac.isEmpty()){
                card.addView(text("MAC: "+d.mac,12,TEXT,false));
                card.addView(text("Fabricante OUI: "+(oui==null?"Cargando base IEEE…":oui.lookup(d.mac)),12,ACCENT,true));
            }else{
                card.addView(text("MAC y fabricante: no accesibles desde Android",12,ORANGE,false));
            }
            body.addView(card);
        }
        if(!scanning&&!arpReadable)
            body.addView(text("Nota: Android limita el acceso a la tabla ARP. Solo se muestran fabricantes con una MAC verificable.",12,MUTED,false));
    }

    private void explain(){
        new AlertDialog.Builder(this)
            .setTitle("Descubrimiento rápido vs. ARP")
            .setMessage("Rápido: prueba unos pocos puertos TCP por IP en paralelo y escucha respuestas UPnP/SSDP. "
                +"Más completo: prueba más puertos, pero puede tardar algo más. Los resultados llegan mientras se escanea.\n\n"
                +"En Android 10 y posteriores, las aplicaciones sin privilegios normalmente no pueden leer /proc/net/arp. "
                +"Por ello no es un arp-scan con paquetes ARP reales y no puede garantizar detectar todos los equipos ni obtener sus MAC. "
                +"Los fabricantes solo se atribuyen cuando hay una MAC observada y reconocida en la base IEEE incluida.\n\n"
                +"No se envían datos de tu LAN a terceros. El escaneo solamente se ejecuta al entrar en esta sección o al pulsar los botones.")
            .setPositiveButton("Entendido",null).show();
    }

    private void export(){
        if(devices.isEmpty()){
            Toast.makeText(this,"No hay dispositivos para exportar",Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,"netscope-dispositivos.csv");
        startActivityForResult(intent,81);
    }
    @Override protected void onActivityResult(int req,int result,Intent intent){
        super.onActivityResult(req,result,intent);
        if(req!=81||result!=RESULT_OK||intent==null||intent.getData()==null)return;
        StringBuilder out=new StringBuilder("IP,MAC,Fabricante,Deteccion,Gateway\r\n");
        for(LanScanner.Device d:devices){
            out.append(CsvExport.cell(d.ip)).append(',').append(CsvExport.cell(d.mac)).append(',')
                .append(CsvExport.cell(d.mac.isEmpty()?"MAC no disponible":oui!=null?oui.lookup(d.mac):"Base OUI aún sin cargar")).append(',').append(CsvExport.cell(d.detail))
                .append(',').append(d.gateway).append("\r\n");
        }
        try(java.io.OutputStream stream=getContentResolver().openOutputStream(intent.getData())){
            if(stream==null)throw new java.io.IOException();
            stream.write(out.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"CSV guardado",Toast.LENGTH_SHORT).show();
        }catch(Exception e){
            Toast.makeText(this,"No se pudo exportar",Toast.LENGTH_LONG).show();
        }
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(0);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(1);
        l.setPadding(dp(14),dp(11),dp(14),dp(11));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(SURFACE);bg.setCornerRadius(dp(17));bg.setStroke(dp(1),0xFF283B56);
        l.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(9);
        l.setLayoutParams(params);
        return l;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(3),dp(4),dp(3),dp(4));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(String s,int color){
        Button b=new Button(this);
        b.setText(s);b.setTextSize(12);b.setAllCaps(false);
        b.setTextColor(color);b.setMinWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(9),0,dp(9),0);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(SURFACE2);bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        return b;
    }
    private int dp(float x){return (int)(getResources().getDisplayMetrics().density*x+0.5f);}
}

package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
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

/**
 * Compact inventory layout inspired by the readability of network inventory tools.
 * Distinct original UI; results are rendered incrementally as they're detected.
 */
public final class LanActivity extends Activity {
    private static final int BLUE=0xFF456CFF, WHITE=0xFFFFFFFF, BG=0xFFF6F8FC,
            TEXT=0xFF172235, MUTED=0xFF66778D, TEAL=0xFF3659C9, GREEN=0xFF238A70,
            BORDER=0xFFE6ECF2, ORANGE=0xFFAF6400;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<LanScanner.Device> devices=new ArrayList<>();
    private OuiDatabase oui;
    private LanScanner.Plan plan;
    private LanScanner.ScanHandle current;
    private LinearLayout list,root,navbar;
    private TextView status,countLabel,vendorCount;
    private ProgressBar progress;
    private Button fastButton,deepButton,stopButton;
    private boolean scanning=false,arpReadable=false;
    private int generation=0,scanned=0,total=0;
    private long started=0;
    private final Runnable draw=()->renderResults();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BLUE);
        getWindow().setNavigationBarColor(WHITE);
        if(Build.VERSION.SDK_INT>=26)getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try{plan=LanScanner.plan(this);}catch(Exception ignored){}
        build();
        new Thread(()->{
            OuiDatabase loaded=new OuiDatabase(getApplicationContext());
            ui.post(()->{
                if(isFinishing()||isDestroyed())return;
                oui=loaded;
                vendorCount.setText(loaded.count()+" fabricantes y prefijos registrados");
                scheduleDraw();
            });
        },"netscope-oui-load").start();
        if(plan!=null)ui.post(()->start(false));
    }
    @Override protected void onDestroy(){
        generation++;
        if(current!=null)current.cancel();
        ui.removeCallbacks(draw);
        super.onDestroy();
    }

    private void build(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        // Android 15 enforces edge-to-edge with target 35. Reserve real system bar insets.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    Math.max(insets.getInsets(WindowInsets.Type.systemGestures()).bottom,
                        insets.getInsets(WindowInsets.Type.tappableElement()).bottom));
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0,top,0,0);
            if(navbar!=null)navbar.setPadding(dp(12),dp(7),dp(12),bottom+dp(14));
            return insets;
        });
        root.requestApplyInsets();

        LinearLayout header=row();
        header.setBackgroundColor(BLUE);
        header.setPadding(dp(15),dp(10),dp(15),dp(10));
        Button back=headerButton("‹");
        back.setContentDescription("Volver");
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(47),dp(50)));
        TextView title=text("NetScope · Dispositivos",22,WHITE,true);
        title.setGravity(Gravity.CENTER);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(50),1));
        Button refresh=headerButton("↻");
        refresh.setContentDescription("Buscar otra vez");
        refresh.setOnClickListener(v->start(false));
        header.addView(refresh,new LinearLayout.LayoutParams(dp(47),dp(50)));
        root.addView(header);

        LinearLayout network=column();
        network.setBackgroundColor(WHITE);
        network.setPadding(dp(17),dp(14),dp(17),dp(12));
        LinearLayout netRow=row();
        TextView wifi=text("◉",31,BLUE,true);
        netRow.addView(wifi,new LinearLayout.LayoutParams(dp(49),dp(59)));
        LinearLayout netInfo=column();
        netInfo.addView(text("RED WI-FI ACTUAL",11,MUTED,true));
        netInfo.addView(text(plan==null?"Sin conexión Wi-Fi privada":plan.scannedCidr,20,TEXT,true));
        netInfo.addView(text(plan==null?"Conéctate a una red Wi-Fi":("Este móvil: "+plan.ownIp+
                (plan.gateway.isEmpty()?"":" · Router: "+plan.gateway)),12,MUTED,false));
        netRow.addView(netInfo,new LinearLayout.LayoutParams(0,-2,1));
        network.addView(netRow);
        root.addView(network);
        View divider=new View(this);divider.setBackgroundColor(BORDER);
        root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));

        LinearLayout stats=row();
        stats.setPadding(dp(18),dp(12),dp(18),dp(5));
        countLabel=text("0 dispositivos identificados",18,TEXT,true);
        stats.addView(countLabel,new LinearLayout.LayoutParams(0,dp(36),1));
        root.addView(stats);
        vendorCount=text("Cargando base de fabricantes…",11,MUTED,false);
        vendorCount.setPadding(dp(20),0,dp(20),dp(5));
        root.addView(vendorCount);

        status=text("Preparado",12,MUTED,false);
        status.setPadding(dp(20),dp(5),dp(18),dp(5));
        root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(4));
        pp.setMargins(dp(18),dp(3),dp(18),dp(9));
        root.addView(progress,pp);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        list=column();
        list.setBackgroundColor(WHITE);
        scroll.addView(list);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        navbar=row();
        navbar.setBackgroundColor(WHITE);
        navbar.setPadding(dp(12),dp(6),dp(12),dp(8));
        fastButton=footerButton("◉  Buscar",BLUE);
        fastButton.setOnClickListener(v->start(false));
        navbar.addView(fastButton,new LinearLayout.LayoutParams(0,dp(50),1));
        deepButton=footerButton("☷  Completa",TEAL);
        deepButton.setOnClickListener(v->start(true));
        navbar.addView(deepButton,new LinearLayout.LayoutParams(0,dp(50),1));
        stopButton=footerButton("■  Detener",ORANGE);
        stopButton.setOnClickListener(v->stop());
        navbar.addView(stopButton,new LinearLayout.LayoutParams(0,dp(50),1));
        Button export=footerButton("⇧  CSV",MUTED);
        export.setOnClickListener(v->exportCsv());
        navbar.addView(export,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(navbar);
        setScanning(false);
        renderResults();
        root.requestApplyInsets();
    }
    private void setScanning(boolean on){
        scanning=on;
        fastButton.setEnabled(!on&&plan!=null);
        deepButton.setEnabled(!on&&plan!=null);
        stopButton.setEnabled(on);
        stopButton.setAlpha(on?1:0.38f);
    }
    private void start(boolean full){
        if(scanning||plan==null)return;
        ++generation;
        final int run=generation;
        if(current!=null)current.cancel();
        devices.clear();
        started=android.os.SystemClock.elapsedRealtime();
        scanned=0;total=plan.addresses.size();arpReadable=false;
        countLabel.setText("Buscando equipos…");
        status.setText(full?"Exploración ampliada…":"Exploración rápida…");
        progress.setProgress(0);
        setScanning(true);
        scheduleDraw();
        current=LanScanner.start(plan,oui,full,new LanScanner.Callbacks(){
            @Override public void discovered(LanScanner.Device d){
                ui.post(()->{
                    if(generation!=run||isFinishing()||isDestroyed())return;
                    replace(d);
                    countLabel.setText(devices.size()+" dispositivos identificados");
                    scheduleDraw();
                });
            }
            @Override public void progress(int done,int count,int found){
                ui.post(()->{
                    if(generation!=run||isFinishing()||isDestroyed())return;
                    scanned=done;total=count;
                    progress.setProgress(count==0?100:done*100/count);
                    status.setText("Comprobadas "+done+" / "+count+" direcciones");
                });
            }
            @Override public void finished(List<LanScanner.Device> results,boolean readable,String message){
                ui.post(()->{
                    if(generation!=run||isFinishing()||isDestroyed())return;
                    arpReadable=readable;
                    devices.clear();
                    devices.addAll(results);
                    setScanning(false);
                    countLabel.setText(devices.size()+" dispositivos identificados");
                    long seconds=Math.max(1,(android.os.SystemClock.elapsedRealtime()-started+500)/1000);
                    status.setText("Finalizado · "+seconds+" s"+(message.isEmpty()?"":" · "+message));
                    if(message.isEmpty())progress.setProgress(100);
                    renderResults();
                });
            }
        });
    }
    private void stop(){
        if(!scanning)return;
        ++generation; // invalidates callbacks from cancelled scan
        if(current!=null)current.cancel();
        setScanning(false);
        status.setText("Exploración detenida · resultados parciales");
        renderResults();
    }
    private void replace(LanScanner.Device d){
        for(int i=0;i<devices.size();i++)
            if(devices.get(i).ip.equals(d.ip)){devices.set(i,d);return;}
        devices.add(d);
    }
    private void scheduleDraw(){
        ui.removeCallbacks(draw);ui.postDelayed(draw,90);
    }
    private void renderResults(){
        if(list==null)return;
        list.removeAllViews();
        List<LanScanner.Device> shown=new ArrayList<>(devices);
        Collections.sort(shown,Comparator
            .comparingInt((LanScanner.Device d)->d.gateway?0:d.ip.equals(plan==null?"":plan.ownIp)?1:2)
            .thenComparingLong(d->LanScanner.toLong(d.ip)));
        if(shown.isEmpty()){
            TextView message=text(scanning?"Identificando dispositivos…":
                "No se han encontrado equipos. Prueba «Completa».",15,MUTED,false);
            message.setGravity(Gravity.CENTER);
            message.setPadding(dp(20),dp(35),dp(20),dp(22));
            list.addView(message);
            return;
        }
        for(LanScanner.Device d:shown){
            boolean self=plan!=null&&d.ip.equals(plan.ownIp);
            String type=DeviceProfile.category(d.detail,d.gateway,self);
            // Dedicated vector-style icons, independent of font glyph availability.
            LinearLayout item=row();
            item.setPadding(dp(15),dp(8),dp(14),dp(8));
            item.setBackgroundColor(WHITE);
            DeviceIconView symbol=new DeviceIconView(this,type);
            item.addView(symbol,new LinearLayout.LayoutParams(dp(54),dp(67)));
            LinearLayout details=column();
            details.addView(text(d.ip,19,TEXT,true));
            details.addView(text(d.mac.isEmpty()?"MAC no disponible":d.mac,12,MUTED,false));
            item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout rhs=column();
            rhs.setGravity(Gravity.RIGHT);
            TextView category=text(type,14,TEXT,true);
            category.setGravity(Gravity.RIGHT);
            rhs.addView(category);
            String vendor=d.mac.isEmpty()?DeviceProfile.serviceHint(d.detail):
                (oui==null?"Buscando fabricante…":oui.lookup(d.mac));
            if(vendor.isEmpty())vendor=d.mac.isEmpty()?"Sin identificar":"OUI desconocido";
            TextView vendorText=text(vendor,11,MUTED,false);
            vendorText.setGravity(Gravity.RIGHT);
            vendorText.setMaxLines(2);
            rhs.addView(vendorText);
            item.addView(rhs,new LinearLayout.LayoutParams(dp(127),-2));
            item.setContentDescription("Examinar dispositivo "+d.ip+" · "+type);
            item.setOnClickListener(v->openDevice(d));
            list.addView(item);
            View sep=new View(this);
            sep.setBackgroundColor(BORDER);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(1));
            sp.leftMargin=dp(71);list.addView(sep,sp);
        }
        TextView info=text("La detección es orientativa. Algunas MAC y equipos están ocultos por Android o por el router.",12,MUTED,false);
        info.setPadding(dp(18),dp(16),dp(18),dp(30));
        list.addView(info);
    }
    private void openDevice(LanScanner.Device device){
        Intent intent=new Intent(this,DeviceDetailActivity.class);
        intent.putExtra(DeviceDetailActivity.EXTRA_IP,device.ip);
        intent.putExtra(DeviceDetailActivity.EXTRA_MAC,device.mac);
        intent.putExtra(DeviceDetailActivity.EXTRA_DETAIL,device.detail);
        intent.putExtra(DeviceDetailActivity.EXTRA_VENDOR,device.vendor);
        intent.putExtra(DeviceDetailActivity.EXTRA_GATEWAY,device.gateway);
        startActivity(intent);
    }

    private void showDetails(LanScanner.Device d,String type){
        String vendor=d.mac.isEmpty()?DeviceProfile.serviceHint(d.detail):
            (oui==null?"Base OUI aún sin cargar":oui.lookup(d.mac));
        if(vendor.isEmpty())vendor="No determinado";
        new AlertDialog.Builder(this)
            .setTitle(type+" · "+d.ip)
            .setMessage("IP: "+d.ip+"\n"+
                "MAC: "+(d.mac.isEmpty()?"No disponible":d.mac)+"\n"+
                "Fabricante/servicio: "+vendor+"\n"+
                "Detección: "+d.detail+"\n\n"+
                "Una marca anunciada por un servicio no demuestra la identidad del fabricante. "+
                "La MAC solo se muestra cuando se obtiene realmente de ARP o NetBIOS.")
            .setPositiveButton("Cerrar",null)
            .show();
    }
    private void exportCsv(){
        if(devices.isEmpty()){
            Toast.makeText(this,"Sin dispositivos para exportar",Toast.LENGTH_SHORT).show();return;
        }
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,"netscope-dispositivos.csv");
        startActivityForResult(i,81);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=81||result!=RESULT_OK||data==null||data.getData()==null)return;
        StringBuilder csv=new StringBuilder("IP,MAC,Fabricante_o_indicio,Tipo,Deteccion,Gateway\r\n");
        for(LanScanner.Device d:devices){
            boolean self=plan!=null&&d.ip.equals(plan.ownIp);
            String type=DeviceProfile.category(d.detail,d.gateway,self);
            String vendor=d.mac.isEmpty()?DeviceProfile.serviceHint(d.detail):
                (oui==null?"Base OUI no disponible":oui.lookup(d.mac));
            csv.append(CsvExport.cell(d.ip)).append(',').append(CsvExport.cell(d.mac))
                .append(',').append(CsvExport.cell(vendor)).append(',')
                .append(CsvExport.cell(type)).append(',').append(CsvExport.cell(d.detail))
                .append(',').append(d.gateway).append("\r\n");
        }
        try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new java.io.IOException();
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Archivo guardado",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"No se pudo exportar",Toast.LENGTH_LONG).show();}
    }

    private LinearLayout row(){
        LinearLayout r=new LinearLayout(this);r.setOrientation(0);
        r.setGravity(Gravity.CENTER_VERTICAL);return r;
    }
    private LinearLayout column(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(1);return c;
    }
    private Button headerButton(String label){
        Button b=new Button(this);b.setText(label);b.setTextSize(29);
        b.setTextColor(WHITE);b.setAllCaps(false);
        b.setPadding(0,0,0,0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setBackgroundColor(BLUE);return b;
    }
    private Button footerButton(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);
        b.setTextColor(color);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);
        b.setBackgroundColor(WHITE);return b;
    }
    private TextView text(String label,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(label);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(1),dp(2),dp(1),dp(2));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
}

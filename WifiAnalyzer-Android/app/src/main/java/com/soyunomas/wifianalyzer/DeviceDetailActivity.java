package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * All active operations require user initiation and are restricted to one
 * private IPv4 host on the currently connected Wi-Fi network.
 */
public final class DeviceDetailActivity extends Activity {
    public static final String EXTRA_IP="ip";
    public static final String EXTRA_MAC="mac";
    public static final String EXTRA_DETAIL="detail";
    public static final String EXTRA_VENDOR="vendor";
    public static final String EXTRA_GATEWAY="gateway";
    private static final int BG=0xFFF6F8FC, WHITE=0xFFFFFFFF, TEXT=0xFF172235,
            MUTED=0xFF66778D, BLUE=0xFF456CFF, BORDER=0xFFE2E8F1,
            SURFACE=0xFFF0F3FF, ORANGE=0xFFAA5B15;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final List<PortScanner.Result> ports=new ArrayList<>();
    private LanScanner.Plan plan;
    private PortScanner.Handle handle;
    private int generation=0;
    private String ip,mac,details,vendor,category;
    private boolean gateway,running=false;
    private LinearLayout root,results;
    private TextView progressText,summary;
    private ProgressBar progress;
    private Button quick,full,custom,stop;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        Intent intent=getIntent();
        ip=intent.getStringExtra(EXTRA_IP);
        mac=intent.getStringExtra(EXTRA_MAC);
        details=intent.getStringExtra(EXTRA_DETAIL);
        vendor=intent.getStringExtra(EXTRA_VENDOR);
        gateway=intent.getBooleanExtra(EXTRA_GATEWAY,false);
        if(ip==null||!LanScanner.privateIpv4(ip)){finish();return;}
        try{plan=LanScanner.plan(this);}catch(Exception ignored){}
        if(plan==null||!(ip.equals(plan.ownIp)||ip.equals(plan.gateway)||plan.addresses.contains(ip))){
            new AlertDialog.Builder(this).setTitle("Fuera de la Wi-Fi actual")
                .setMessage("El objetivo ya no pertenece a la subred privada Wi-Fi. Reconecta antes de escanear.")
                .setPositiveButton("Cerrar",(d,w)->finish())
                .setCancelable(false).show();
            return;
        }
        boolean self=ip.equals(plan.ownIp);
        category=DeviceProfile.category(details,gateway,self);
        build();
        if(state==null)ui.post(this::askFirstScan);
    }

    @Override protected void onDestroy(){
        generation++;
        if(handle!=null)handle.cancel();
        super.onDestroy();
    }

    private void build(){
        root=column();
        root.setBackgroundColor(BG);
        setContentView(root);
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
            view.setPadding(dp(16),top+dp(9),dp(16),bottom+dp(18));
            return insets;
        });

        LinearLayout header=row();
        Button back=button("‹",MUTED);
        back.setTextSize(25);
        back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        TextView heading=text("Ficha del dispositivo",21,TEXT,true);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(46),1);
        hp.leftMargin=dp(10);
        header.addView(heading,hp);
        root.addView(header);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body=column();
        body.setPadding(0,dp(10),0,dp(18));
        scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout identity=card();
        LinearLayout line=row();
        line.addView(new DeviceIconView(this,category),
            new LinearLayout.LayoutParams(dp(72),dp(72)));
        LinearLayout labels=column();
        labels.setPadding(dp(10),0,0,0);
        labels.addView(text(category,13,BLUE,true));
        labels.addView(text(ip,23,TEXT,true));
        labels.addView(text(mac==null||mac.isEmpty()?"MAC no disponible":mac,12,MUTED,false));
        line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        identity.addView(line);
        identity.addView(text("Fabricante / indicio: "+(vendor==null||vendor.isEmpty()?"No determinado":vendor),12,MUTED,false));
        identity.addView(text("Detección previa: "+(details==null?"Sin detalles":details),12,MUTED,false));
        body.addView(identity);

        TextView heading2=text("Análisis de puertos TCP",19,TEXT,true);
        heading2.setPadding(dp(2),dp(15),0,dp(8));body.addView(heading2);
        body.addView(text("Solo en esta IP de la Wi-Fi. Los puertos abiertos aparecerán conforme respondan; "+
            "un puerto sin respuesta puede estar cerrado o filtrado.",13,MUTED,false));

        LinearLayout first=row();
        quick=button("Rápido",BLUE);
        quick.setOnClickListener(v->confirmScan(false));
        first.addView(quick,new LinearLayout.LayoutParams(0,dp(48),1));
        full=button("Ampliado",BLUE);
        full.setOnClickListener(v->confirmScan(true));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,dp(48),1);
        fp.leftMargin=dp(8);first.addView(full,fp);
        custom=button("Puerto…",BLUE);
        custom.setOnClickListener(v->customPort());
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(48),1);
        cp.leftMargin=dp(8);first.addView(custom,cp);
        LinearLayout.LayoutParams controls=new LinearLayout.LayoutParams(-1,dp(48));
        controls.topMargin=dp(11);body.addView(first,controls);

        stop=button("Detener exploración",ORANGE);
        stop.setOnClickListener(v->cancelScan());
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(43));
        sp.topMargin=dp(6);
        body.addView(stop,sp);

        progressText=text("Toca «Rápido» para explorar los puertos más comunes.",12,MUTED,false);
        progressText.setPadding(0,dp(12),0,dp(8));body.addView(progressText);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(BLUE));
        body.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));

        summary=text("Servicios",19,TEXT,true);
        summary.setPadding(0,dp(16),0,dp(8));body.addView(summary);
        results=column();
        body.addView(results);
        setRunning(false);
        drawPorts();
        root.requestApplyInsets();
    }

    private void askFirstScan(){
        new AlertDialog.Builder(this)
            .setTitle("¿Escanear los puertos de "+ip+"?")
            .setMessage("Una exploración TCP breve y voluntaria de puertos habituales. "+
                "Solo se analizará este dispositivo de tu Wi-Fi.")
            .setPositiveButton("Escaneo rápido",(d,w)->startScan(PortCatalog.quick()))
            .setNeutralButton("Ampliado",(d,w)->startScan(PortCatalog.extended()))
            .setNegativeButton("Ahora no",null)
            .show();
    }
    private void confirmScan(boolean expanded){
        new AlertDialog.Builder(this)
            .setTitle(expanded?"Exploración ampliada":"Exploración rápida")
            .setMessage("Se probarán "+(expanded?PortCatalog.extended().size():PortCatalog.quick().size())+
                " puertos TCP del dispositivo "+ip+". ¿Continuar?")
            .setPositiveButton("Escanear",(d,w)->startScan(expanded?PortCatalog.extended():PortCatalog.quick()))
            .setNegativeButton("Cancelar",null).show();
    }

    private void customPort(){
        EditText field=new EditText(this);
        field.setHint("Ej. 8123");
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
            .setTitle("Escanear un puerto TCP")
            .setView(field)
            .setPositiveButton("Escanear",(dialog,which)->{
                try{startScan(Collections.singletonList(PortCatalog.parse(field.getText().toString())));}
                catch(IllegalArgumentException e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
            }).setNegativeButton("Cancelar",null).show();
    }

    private void startScan(List<Integer> selection){
        if(running||plan==null)return;
        ports.clear();
        final int thisRun=++generation;
        setRunning(true);
        progress.setProgress(0);
        progressText.setText("Probando "+selection.size()+" puertos TCP en "+ip+"…");
        drawPorts();
        handle=PortScanner.start(plan.network,ip,selection,new PortScanner.Callback(){
            @Override public void found(PortScanner.Result result){
                ui.post(()->{
                    if(generation!=thisRun||isFinishing()||isDestroyed())return;
                    ports.add(result);drawPorts();
                });
            }
            @Override public void progress(int checked,int total,int open){
                ui.post(()->{
                    if(generation!=thisRun||isFinishing()||isDestroyed())return;
                    progress.setProgress(total==0?100:checked*100/total);
                    progressText.setText(checked+"/"+total+" comprobados · "+open+" abiertos");
                });
            }
            @Override public void done(List<PortScanner.Result> result,String warning){
                ui.post(()->{
                    if(generation!=thisRun||isFinishing()||isDestroyed())return;
                    ports.clear();ports.addAll(result);
                    setRunning(false);
                    if(warning.isEmpty())progress.setProgress(100);
                    progressText.setText(result.size()+" abiertos · "+
                        (warning.isEmpty()?"Exploración finalizada":warning));
                    drawPorts();
                });
            }
        });
    }

    private void cancelScan(){
        if(!running)return;
        generation++;
        if(handle!=null)handle.cancel();
        setRunning(false);
        progressText.setText("Exploración detenida; se conservan resultados parciales.");
        drawPorts();
    }
    private void setRunning(boolean active){
        running=active;
        if(quick!=null)quick.setEnabled(!active);
        if(full!=null)full.setEnabled(!active);
        if(custom!=null)custom.setEnabled(!active);
        if(stop!=null){stop.setEnabled(active);stop.setAlpha(active?1:0.5f);}
    }
    private void drawPorts(){
        if(results==null)return;
        results.removeAllViews();
        summary.setText("Servicios · "+ports.size()+" puertos abiertos");
        if(ports.isEmpty()){
            results.addView(text(running?"Buscando respuestas TCP…":
                "Sin puertos abiertos detectados. Puedes probar el modo ampliado o uno personalizado.",
                13,MUTED,false));
            return;
        }
        List<PortScanner.Result> ordered=new ArrayList<>(ports);
        ordered.sort(Comparator.comparingInt(o->o.port));
        for(PortScanner.Result p:ordered){
            LinearLayout item=card();
            LinearLayout top=row();
            TextView label=text(p.port+" / TCP",17,TEXT,true);
            top.addView(label,new LinearLayout.LayoutParams(0,dp(35),1));
            top.addView(text("● ABIERTO",12,0xFF258368,true));
            item.addView(top);
            item.addView(text(p.service+" · conexión "+p.latencyMs+" ms",12,MUTED,false));
            Button actions=button("Examinar servicio  ›",BLUE);
            actions.setOnClickListener(v->portOptions(p));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(39));
            pp.topMargin=dp(7);
            item.addView(actions,pp);
            LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-1,-2);
            margins.bottomMargin=dp(9);
            results.addView(item,margins);
        }
        results.addView(text("La etiqueta de servicio depende del puerto; no confirma "+
            "el protocolo real ni demuestra una vulnerabilidad.",12,MUTED,false));
    }

    private void portOptions(PortScanner.Result result){
        List<String> actions=new ArrayList<>();
        if(PortCatalog.web(result.port))actions.add("Abrir en el navegador");
        if(PortCatalog.web(result.port)&&!PortCatalog.tls(result.port))
            actions.add("Leer cabeceras HTTP (HEAD)");
        actions.add("Leer banner / respuesta TCP");
        actions.add("Enviar texto manual (tipo nc)");
        actions.add("Copiar IP:puerto");
        new AlertDialog.Builder(this)
            .setTitle(ip+":"+result.port+" · "+result.service)
            .setItems(actions.toArray(new String[0]),(d,which)->{
                String action=actions.get(which);
                if(action.startsWith("Abrir"))openWeb(result.port);
                else if(action.startsWith("Leer cabeceras"))
                    executeInspector(result.port,ResponseInspector.HTTP_HEAD,null);
                else if(action.startsWith("Leer banner"))
                    executeInspector(result.port,ResponseInspector.BANNER,null);
                else if(action.startsWith("Enviar"))enterPayload(result.port);
                else {
                    ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Host TCP",ip+":"+result.port));
                    Toast.makeText(this,"Copiado",Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    private void openWeb(int port){
        try{
            String url=PortCatalog.url(ip,port);
            Intent browser=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
        }catch(Exception e){
            Toast.makeText(this,"No hay navegador disponible o URL incorrecta.",Toast.LENGTH_LONG).show();
        }
    }
    private void enterPayload(int port){
        EditText input=new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setHint("Texto literal a enviar por TCP (máx. 512 caracteres)");
        new AlertDialog.Builder(this)
            .setTitle("Enviar texto a "+ip+":"+port)
            .setMessage("La cadena se enviará literalmente a este servicio. "+
                "No escribas contraseñas ni comandos que no comprendas.")
            .setView(input)
            .setPositiveButton("Enviar",(d,w)->{
                String payload=input.getText().toString();
                if(payload.isEmpty()||payload.length()>512){
                    Toast.makeText(this,"La longitud debe ser de 1 a 512 caracteres.",Toast.LENGTH_LONG).show();
                    return;
                }
                executeInspector(port,ResponseInspector.CUSTOM,payload);
            })
            .setNegativeButton("Cancelar",null).show();
    }

    private void executeInspector(int port,int mode,String payload){
        TextView output=text("Conectando…",13,TEXT,false);
        output.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);
        scroll.setPadding(dp(12),dp(7),dp(12),dp(10));
        scroll.addView(output);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("TCP "+ip+":"+port)
            .setView(scroll)
            .setPositiveButton("Cerrar",null)
            .setNeutralButton("Copiar",(d,w)->{
                ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Respuesta TCP",output.getText()));
            }).create();
        dialog.show();
        new Thread(()->{
            String data=ResponseInspector.inspect(plan.network,ip,port,mode,payload);
            ui.post(()->{
                if(!isFinishing()&&!isDestroyed()&&dialog.isShowing())output.setText(data);
            });
        },"netscope-tcp-inspect").start();
    }

    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(1);return l;
    }
    private LinearLayout card(){
        LinearLayout l=column();
        l.setPadding(dp(14),dp(13),dp(14),dp(13));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(WHITE);bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1),BORDER);l.setBackground(bg);
        return l;
    }
    private Button button(String title,int color){
        Button b=new Button(this);
        b.setText(title);b.setTextSize(12);b.setTextColor(color);
        b.setAllCaps(false);b.setMinWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(8),0,dp(8),0);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(WHITE);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),BORDER);
        b.setBackground(bg);return b;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(2),dp(4),dp(2),dp(4));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}

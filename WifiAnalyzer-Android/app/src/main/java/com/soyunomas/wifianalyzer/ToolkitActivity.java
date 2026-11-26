package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Diagnostics run on demand: no unattended external requests or local-network probing. */
public final class ToolkitActivity extends Activity {
    static final int BG=0xFFF6F8FC, SURFACE=0xFFFFFFFF, TEXT=0xFF172235,
         MUTED=0xFF66778D, ACCENT=0xFF456CFF, PURPLE=0xFF3659C9, WARNING=0xFFAA5B15;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,content;
    private TextView diagnosticsOutput;
    private int tab=0;
    private NetworkDiagnostics.Snapshot snapshot;
    private boolean testing=false;
    private String resultText="Aún no se ha ejecutado una prueba externa.";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if(state!=null)tab=state.getInt("toolTab",0);
        setup();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        super.onSaveInstanceState(out);out.putInt("toolTab",tab);
    }
    private void setup(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BG);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
                bottom=insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            }else{
                top=insets.getSystemWindowInsetTop();
                bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(dp(16),top+dp(12),dp(16),bottom+dp(12));
            return insets;
        });
        root.requestApplyInsets();
        LinearLayout head=line();
        Button back=button("‹ Volver",MUTED);back.setOnClickListener(v->finish());
        head.addView(back);
        TextView title=text("Centro de red",23,TEXT,true);
        title.setGravity(Gravity.CENTER);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(head);
        Button advanced=button("45 HERRAMIENTAS · PING, DNS, TLS, LAN Y MÁS",ACCENT);
        advanced.setOnClickListener(v->startActivity(new Intent(this,AdvancedToolsActivity.class)));
        LinearLayout.LayoutParams toolsP=new LinearLayout.LayoutParams(-1,dp(49));
        toolsP.topMargin=dp(6);toolsP.bottomMargin=dp(7);
        root.addView(advanced,toolsP);

        LinearLayout bar=line();
        String[] labels={"Conexión","Pruebas","Subredes","Privacidad"};
        for(int i=0;i<labels.length;i++){
            int index=i;
            Button b=button(labels[i],tab==i?ACCENT:MUTED);
            b.setTextSize(12);b.setOnClickListener(v->{tab=index;setup();});
            bar.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));
        }
        root.addView(bar);

        ScrollView scroll=new ScrollView(this);
        content=new LinearLayout(this);content.setOrientation(1);
        content.setPadding(0,dp(10),0,dp(16));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0)showConnection();
        if(tab==1)showTests();
        if(tab==2)showSubnet();
        if(tab==3)showPrivacy();
    }

    private void showConnection(){
        title("Información de conexión");
        paragraph("Lee los datos que Android expone sobre la interfaz Wi-Fi, incluso si otra red o una VPN es la predeterminada.");
        Button refresh=button("Actualizar diagnóstico",ACCENT);
        refresh.setOnClickListener(v->setup());
        content.addView(refresh);
        try{
            snapshot=NetworkDiagnostics.capture(this);
            LinearLayout table=card();
            for(Map.Entry<String,String> e:snapshot.rows.entrySet()){
                TextView key=text(e.getKey(),12,MUTED,true);key.setPadding(dp(4),dp(10),dp(4),0);
                table.addView(key);
                TextView value=text(e.getValue(),15,TEXT,false);
                value.setTextIsSelectable(true);table.addView(value);
            }
            content.addView(table);
        }catch(SecurityException e){paragraph("Android no permite acceder a algunos datos. Comprueba los permisos y vuelve a probar.");}
        catch(Exception e){paragraph("No se puede leer el estado de conexión: "+e.getClass().getSimpleName());}
        LinearLayout note=card();
        note.addView(text("Cómo interpretarlo",16,ACCENT,true));
        note.addView(text("Las velocidades Tx/Rx son valores de enlace negociados, no mediciones de tu conexión a Internet. La validación es el resultado del sistema operativo.",13,MUTED,false));
        content.addView(note);
    }

    private void showTests(){
        title("Diagnóstico bajo demanda");
        paragraph("Elige una prueba. DNS consulta example.com; HTTPS contacta con www.google.com/generate_204 mediante la red Wi-Fi conectada. Una solicitud puede revelar tu IP pública a ese servidor.");
        LinearLayout box=card();
        box.addView(text("Pruebas de conectividad",17,TEXT,true));
        Button dns=button("Comprobar resolución DNS",ACCENT);
        dns.setOnClickListener(v->runTest("dns"));
        box.addView(dns);
        Button https=button("Comprobar HTTPS y latencia",PURPLE);
        https.setOnClickListener(v->runTest("https"));
        box.addView(https);
        diagnosticsOutput=text(resultText,14,TEXT,false);
        diagnosticsOutput.setTextIsSelectable(true);
        diagnosticsOutput.setPadding(0,dp(14),0,dp(10));
        box.addView(diagnosticsOutput);
        content.addView(box);
        LinearLayout help=card();
        help.addView(text("Qué mide",16,ACCENT,true));
        help.addView(text("DNS: tiempo de resolución para el nombre indicado. HTTPS: tiempo total de una petición corta (DNS, conexión TLS y respuesta, según cachés). No es un ping ICMP, un test de velocidad ni un diagnóstico completo de pérdidas de paquetes.",13,MUTED,false));
        content.addView(help);
    }

    private void runTest(String type){
        if(testing)return;
        try{
            snapshot=NetworkDiagnostics.capture(this);
            if(snapshot.network==null){
                resultText="Conéctate primero a una red Wi-Fi.";
                diagnosticsOutput.setText(resultText);return;
            }
            testing=true;
            diagnosticsOutput.setText("Realizando prueba…");
            NetworkDiagnostics.runTest(snapshot.network,type,value->handler.post(()->{
                if(isFinishing()||isDestroyed())return;
                testing=false;resultText=value;
                if(diagnosticsOutput!=null)diagnosticsOutput.setText(value);
            }));
        }catch(Exception e){
            testing=false;resultText="Error: "+e.getClass().getSimpleName();
            diagnosticsOutput.setText(resultText);
        }
    }

    private void showSubnet(){
        title("Calculadora IPv4 / CIDR");
        paragraph("Introduce una dirección y un prefijo para calcular la máscara, la red, el broadcast y el rango de hosts. Funciona totalmente sin conexión.");
        LinearLayout card=card();
        EditText input=new EditText(this);
        input.setSingleLine(true);input.setText("192.168.1.10/24");
        input.setTextSize(17);input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("192.168.1.10/24");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        card.addView(input);
        LinearLayout out=new LinearLayout(this);out.setOrientation(1);
        Button calculate=button("Calcular",ACCENT);
        calculate.setOnClickListener(v->{
            out.removeAllViews();
            try{
                SubnetTools.Subnet s=SubnetTools.calculate(input.getText().toString());
                addResult(out,"IP",s.ip);
                addResult(out,"Prefijo","/"+s.prefix);
                addResult(out,"Máscara",s.mask);
                addResult(out,"Red",s.network);
                addResult(out,"Broadcast",s.broadcast);
                addResult(out,"Primer host",s.first);
                addResult(out,"Último host",s.last);
                addResult(out,"Direcciones totales",""+s.totalAddresses);
                addResult(out,"Hosts utilizables",""+s.usableHosts);
                if(s.prefix==31)out.addView(text("En /31 se consideran dos extremos punto a punto (RFC 3021).",12,MUTED,false));
                if(s.prefix==32)out.addView(text("En /32 hay una sola dirección.",12,MUTED,false));
                InputMethodManager imm=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if(imm!=null)imm.hideSoftInputFromWindow(input.getWindowToken(),0);
            }catch(IllegalArgumentException e){out.addView(text(e.getMessage(),14,WARNING,true));}
        });
        card.addView(calculate);
        card.addView(out);
        content.addView(card);
        calculate.performClick();
    }
    private void addResult(LinearLayout container,String key,String value){
        TextView k=text(key,12,MUTED,false);k.setPadding(0,dp(10),0,0);container.addView(k);
        TextView v=text(value,16,TEXT,true);v.setTextIsSelectable(true);container.addView(v);
    }

    private void showPrivacy(){
        title("Privacidad y límites");
        LinearLayout c=card();
        c.addView(text("Local por defecto",17,ACCENT,true));
        c.addView(text("Las exploraciones de redes se mantienen en la memoria de la aplicación mientras está abierta. Las redes detectadas, los BSSID y la ubicación no se envían a servidores propios. Solo se exportan archivos cuando lo solicitas.",13,TEXT,false));
        c.addView(text("Permisos necesarios",17,TEXT,true));
        c.addView(text("Android requiere ubicación precisa para devolver puntos de acceso. La app también solicita información de estado de red y, según la versión, acceso a dispositivos Wi-Fi próximos.",13,MUTED,false));
        c.addView(text("Pruebas externas",17,TEXT,true));
        c.addView(text("El acceso a Internet se utiliza únicamente cuando ejecutas explícitamente las pruebas DNS o HTTPS; entonces el destino puede observar tu IP pública.",13,MUTED,false));
        c.addView(text("Limitaciones",17,TEXT,true));
        c.addView(text("Android puede limitar los escaneos y ocultar algunas propiedades. No se inspeccionan contraseñas, tráfico de otros dispositivos ni contenido de comunicaciones. Las recomendaciones de seguridad solo interpretan las capacidades anunciadas.",13,MUTED,false));
        content.addView(c);
    }

    private void title(String s){content.addView(text(s,21,TEXT,true));}
    private void paragraph(String s){TextView t=text(s,13,MUTED,false);t.setPadding(dp(2),dp(9),dp(2),dp(12));content.addView(t);}
    private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);
        l.setPadding(dp(15),dp(15),dp(15),dp(15));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(SURFACE);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xFFE2E8F1);
        l.setBackground(bg);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(11);params.bottomMargin=dp(5);
        l.setLayoutParams(params);
        return l;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s);t.setTextColor(color);t.setTextSize(size);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(3),dp(4),dp(3),dp(4));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(String s,int color){
        Button b=new Button(this);
        b.setText(s);b.setTextSize(12);b.setTextColor(color);b.setAllCaps(false);
        b.setPadding(dp(9),0,dp(9),0);b.setMinWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xFFF0F3FF);bg.setCornerRadius(dp(12));b.setBackground(bg);
        return b;
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}

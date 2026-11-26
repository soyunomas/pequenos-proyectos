package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Searchable professional toolbox: every network operation requires an explicit tap. */
public final class AdvancedToolsActivity extends Activity {
    private static final int BG=0xFFF6F8FC, WHITE=0xFFFFFFFF, TEXT=0xFF172235,
        MUTED=0xFF66778D, BLUE=0xFF456CFF, BORDER=0xFFE2E8F1, LIGHT=0xFFF0F3FF;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout root,items,footer,outputCard;
    private TextView title,output,label;
    private EditText search;
    private Button cancel,export;
    private Thread worker;
    private volatile int generation=0;
    private ToolCatalog.Tool selected;
    private boolean running=false;
    private String lastResult="";
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        build();
    }
    @Override protected void onDestroy(){
        generation++;
        if(worker!=null)worker.interrupt();
        super.onDestroy();
    }

    private void build(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
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
            view.setPadding(dp(16),top+dp(8),dp(16),0);
            if(footer!=null)footer.setPadding(dp(2),dp(7),dp(2),bottom+dp(13));
            return insets;
        });
        LinearLayout header=line();
        Button back=button("‹",MUTED);
        back.setTextSize(24);back.setOnClickListener(v->finish());
        header.addView(back,new LinearLayout.LayoutParams(dp(46),dp(49)));
        LinearLayout heading=column();
        heading.addView(text("Suite experta",23,TEXT,true));
        heading.addView(text("25 herramientas · bajo demanda",12,MUTED,false));
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(10);
        header.addView(heading,hp);
        root.addView(header);

        search=new EditText(this);
        search.setSingleLine(true);search.setTextSize(15);
        search.setTextColor(TEXT);search.setHintTextColor(MUTED);
        search.setHint("Buscar ping, DNS, SNMP, TLS…");
        search.setBackground(shape(WHITE,12));
        search.setPadding(dp(15),dp(9),dp(15),dp(9));
        LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(49));
        searchP.topMargin=dp(12);searchP.bottomMargin=dp(8);
        root.addView(search,searchP);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){populate(s.toString());}
            public void afterTextChanged(android.text.Editable e){}
        });

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        items=column();
        items.setPadding(0,0,0,dp(16));
        scroll.addView(items);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        footer=line();
        footer.setGravity(Gravity.CENTER_VERTICAL);
        cancel=button("■  Detener",MUTED);
        cancel.setOnClickListener(v->stop());
        footer.addView(cancel,new LinearLayout.LayoutParams(0,dp(45),1));
        export=button("⇧  Informe",BLUE);
        export.setOnClickListener(v->exportReport());
        LinearLayout.LayoutParams exp=new LinearLayout.LayoutParams(0,dp(45),1);
        exp.leftMargin=dp(8);footer.addView(export,exp);
        root.addView(footer);
        setRunning(false);
        populate("");
        root.requestApplyInsets();
    }
    private void populate(String filter){
        if(items==null)return;
        items.removeAllViews();
        String group="";
        String searchTerm=filter.toLowerCase(Locale.ROOT).trim();
        int visible=0;
        for(ToolCatalog.Tool tool:ToolCatalog.ALL){
            String haystack=(tool.title+" "+tool.group+" "+tool.description).toLowerCase(Locale.ROOT);
            if(!searchTerm.isEmpty()&&!haystack.contains(searchTerm))continue;
            visible++;
            if(!tool.group.equals(group)){
                group=tool.group;
                TextView header=text(group.toUpperCase(Locale.ROOT),11,MUTED,true);
                header.setLetterSpacing(0.07f);
                header.setPadding(dp(2),dp(13),0,dp(7));
                items.addView(header);
            }
            LinearLayout cell=line();
            cell.setGravity(Gravity.CENTER_VERTICAL);
            cell.setPadding(dp(12),dp(9),dp(12),dp(9));
            cell.setBackground(shape(WHITE,13));
            TextView number=text(String.format(Locale.ROOT,"%02d",tool.id),15,BLUE,true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(shape(LIGHT,10));
            cell.addView(number,new LinearLayout.LayoutParams(dp(43),dp(47)));
            LinearLayout description=column();
            description.setPadding(dp(12),0,0,0);
            description.addView(text(tool.title,15,TEXT,true));
            TextView sub=text(tool.description,11,MUTED,false);
            sub.setMaxLines(2);description.addView(sub);
            cell.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=text("›",25,BLUE,false);
            cell.addView(arrow);
            cell.setOnClickListener(v->ask(tool));
            LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2);
            layout.bottomMargin=dp(6);
            items.addView(cell,layout);
        }
        if(visible==0)items.addView(text("No hay herramientas con ese filtro.",13,MUTED,false));
    }

    private void ask(ToolCatalog.Tool tool){
        if(running){
            Toast.makeText(this,"Detén la prueba actual antes de ejecutar otra.",Toast.LENGTH_SHORT).show();
            return;
        }
        selected=tool;
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setText(tool.defaultValue);
        field.setHint(tool.hint);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        String message=tool.description;
        if(tool.id==1||tool.id==2||tool.id==3||tool.id==4)
            message+="\nAndroid no garantiza ICMP ni binarios traceroute/MTR.";
        if(tool.id==16)
            message+="\nLa prueba de caudal iPerf3 requiere un cliente nativo compatible; el puerto 5201 no mide velocidad.";
        if(tool.id==17)
            message+="\nDescarga limitada a 3 MB; con Cloudflare se intenta además una subida de 256 KiB.";
        if(tool.id==14)
            message+="\nConsulta UDP/161 SNMP v1 de solo lectura a la comunidad public.";
        if(tool.id==24)
            message+="\nGuarda solo el inventario local. Algunos equipos pueden no responder.";
        if(tool.id==25){
            new AlertDialog.Builder(this).setTitle("Informe técnico")
                .setMessage("Hay "+ToolHistory.count()+" resultados en esta sesión. Puedes previsualizarlos y exportarlos.")
                .setPositiveButton("Ver",(d,w)->runTool(tool,""))
                .setNeutralButton("Exportar",(d,w)->exportReport())
                .setNegativeButton("Cancelar",null).show();
            return;
        }
        AlertDialog.Builder dialog=new AlertDialog.Builder(this)
            .setTitle(tool.title).setMessage(message);
        if(!tool.defaultValue.isEmpty()||tool.id==1||tool.id==5||tool.id==18)
            dialog.setView(field);
        dialog.setPositiveButton("Ejecutar",(d,w)->runTool(tool,
            tool.defaultValue.isEmpty()?"":field.getText().toString().trim()));
        dialog.setNegativeButton("Cancelar",null).show();
    }

    private void runTool(ToolCatalog.Tool tool,String input){
        final int call=++generation;
        running=true;setRunning(true);
        lastResult="";
        // Show results in a standalone dialog so they remain readable without losing the search.
        TextView response=text("Ejecutando "+tool.title+"…",13,TEXT,false);
        response.setTextIsSelectable(true);
        response.setTypeface(android.graphics.Typeface.MONOSPACE);
        ScrollView viewport=new ScrollView(this);
        viewport.setPadding(dp(13),dp(12),dp(13),dp(12));
        viewport.addView(response);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(tool.title)
            .setView(viewport)
            .setPositiveButton("Cerrar",null)
            .setNeutralButton("Copiar",(d,w)->{
                ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(clipboard!=null)clipboard.setPrimaryClip(
                    ClipData.newPlainText("NetScope · "+tool.title,response.getText()));
            }).create();
        dialog.show();
        dialog.getWindow().setLayout(-1,Math.min(dp(520),getResources().getDisplayMetrics().heightPixels*3/4));
        worker=new Thread(()->{
            String result;
            try{result=ToolEngine.execute(getApplicationContext(),tool.id,input);}
            catch(InterruptedException e){result="Prueba detenida.";}
            catch(Exception e){result="Error: "+e.getClass().getSimpleName()+"\n"+e.getMessage();}
            final String completed=result;
            handler.post(()->{
                if(call!=generation||isFinishing()||isDestroyed())return;
                setRunning(false);
                lastResult=completed;
                if(tool.id!=25)ToolHistory.add(tool.title,input,completed);
                response.setText(completed);
            });
        },"netscope-tool-"+tool.id);
        worker.start();
        dialog.setOnDismissListener(d->{
            // Keep test running only if explicitly requested; dismissing a dialog cancels.
            if(running&&generation==call)stop();
        });
    }
    private void stop(){
        if(!running)return;
        generation++;
        if(worker!=null)worker.interrupt();
        setRunning(false);
        Toast.makeText(this,"Prueba detenida. Los resultados parciales pueden haberse perdido.",Toast.LENGTH_SHORT).show();
    }
    private void setRunning(boolean value){
        running=value;
        if(cancel!=null){cancel.setEnabled(value);cancel.setAlpha(value?1f:0.42f);}
    }
    private void exportReport(){
        if(ToolHistory.count()==0){
            Toast.makeText(this,"Ejecuta primero algún diagnóstico.",Toast.LENGTH_SHORT).show();return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE,"netscope-informe.txt");
        startActivityForResult(intent,102);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=102||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(java.io.OutputStream output=getContentResolver().openOutputStream(data.getData())){
            if(output==null)throw new java.io.IOException("Destino inválido");
            output.write(ToolHistory.report().getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this,"Informe guardado.",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"No se pudo guardar.",Toast.LENGTH_LONG).show();}
    }

    private LinearLayout line(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private android.graphics.drawable.GradientDrawable shape(int color,int radius){
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(color);bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),BORDER);
        return bg;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(2),dp(4),dp(2),dp(4));
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button button(String label,int color){
        Button b=new Button(this);b.setText(label);b.setTextSize(12);
        b.setTextColor(color);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);
        b.setPadding(dp(8),0,dp(8),0);
        b.setBackground(shape(WHITE,12));
        return b;
    }
    private int dp(float p){return (int)(p*getResources().getDisplayMetrics().density+0.5f);}
}

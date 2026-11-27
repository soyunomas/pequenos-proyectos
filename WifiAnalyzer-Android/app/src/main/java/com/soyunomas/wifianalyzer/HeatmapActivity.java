package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

/** Offline manual Wi-Fi survey. Heatmap interpolates only measured phone RSSI values. */
public final class HeatmapActivity extends Activity {
    private static final int INK=0xFF172235,BLUE=0xFF456CFF;
    private static final class Sample{
        final float x,y;final int dbm;
        Sample(float x,float y,int dbm){this.x=x;this.y=y;this.dbm=dbm;}
    }
    private final ArrayList<Sample> samples=new ArrayList<>();
    private Bitmap floor;
    private Chart chart;
    private TextView status, accessPoint, legend;
    private String fixedBssid="", fixedSsid="";
    private long lastMeasurementAt;
    private float radius=0.15f;
    private int radiusLevel=1;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFFF6F8FC);
        getWindow().setNavigationBarColor(0xFFF6F8FC);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(0xFFF6F8FC);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.systemGestures()).bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            int d=(int)(getResources().getDisplayMetrics().density*12);
            view.setPadding(d,top+d,d,bottom+d);return insets;
        });
        TextView heading=new TextView(this);
        heading.setText("Mapa de cobertura Wi-Fi");heading.setTextColor(INK);heading.setTextSize(23);
        heading.setPadding(5,6,5,8);root.addView(heading);
        TextView help=new TextView(this);help.setTextColor(0xFF66778D);help.setTextSize(13);
        help.setText("Abre un plano y toca cada lugar donde estés físicamente. "+
            "Solo se colorean zonas próximas a las mediciones; el resto permanece SIN DATOS. "+
            "No es un modelo de propagación radioeléctrica.");
        root.addView(help);
        accessPoint=new TextView(this);
        accessPoint.setTextColor(INK);accessPoint.setTextSize(12);
        accessPoint.setPadding(5,12,5,0);
        accessPoint.setText("Red medida: se fijará en el primer punto (SSID/BSSID).");
        root.addView(accessPoint);
        legend=new TextView(this);legend.setTextSize(12);legend.setTextColor(INK);
        legend.setPadding(5,7,5,0);
        legend.setText("RSSI: verde ≥ −53 · verde claro −65 · amarillo −78 · rojo ≤ −90 dBm; "+
            "sin color = SIN DATOS.");
        root.addView(legend);
        LinearLayout scale=new LinearLayout(this);
        int[] stops={-90,-78,-65,-53,-40};
        for(int dbm:stops){
            LinearLayout cell=new LinearLayout(this);cell.setOrientation(1);
            View swatch=new View(this);
            swatch.setBackgroundColor(CoverageSurface.color(dbm));
            cell.addView(swatch,new LinearLayout.LayoutParams(-1,8));
            TextView value=new TextView(this);value.setText(dbm+"");
            value.setTextSize(10);value.setTextColor(INK);
            value.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
            cell.addView(value);
            scale.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(scale);
        status=new TextView(this);status.setTextColor(BLUE);status.setTextSize(13);
        status.setPadding(5,14,5,10);status.setText("Sin plano. Sin mediciones.");
        root.addView(status);
        chart=new Chart();root.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this);
        addButton(row,"Plano",()->open());
        addButton(row,"Deshacer",()->{
            if(!samples.isEmpty())samples.remove(samples.size()-1);
            if(samples.isEmpty()){fixedBssid="";fixedSsid="";lastMeasurementAt=0;}
            update();
        });
        addButton(row,"Radio",()->{
            radiusLevel=(radiusLevel+1)%3;
            radius=radiusLevel==0?.09f:radiusLevel==1?.15f:.21f;
            update();
        });
        LinearLayout rowMore=new LinearLayout(this);
        addButton(rowMore,"Reiniciar",()->new AlertDialog.Builder(this)
            .setTitle("Borrar mediciones")
            .setMessage("Se borrarán los puntos de este plano para empezar otra medición.")
            .setPositiveButton("Borrar",(d,w)->{
                samples.clear();fixedBssid="";fixedSsid="";lastMeasurementAt=0;update();
            }).setNegativeButton("Cancelar",null).show());
        addButton(rowMore,"Exportar",()->export());
        root.addView(row);root.addView(rowMore);root.requestApplyInsets();
    }
    private void addButton(LinearLayout row,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(BLUE);
        b.setOnClickListener(v->action.run());
        row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void open(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,201);
    }
    private void export(){
        if(floor==null||samples.isEmpty()){
            Toast.makeText(this,"Carga un plano y toma mediciones.",Toast.LENGTH_SHORT).show();return;
        }
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("image/png");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"netscope-cobertura.png");
        startActivityForResult(intent,202);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==201){
            try{
                BitmapFactory.Options opts=new BitmapFactory.Options();
                opts.inJustDecodeBounds=true;
                try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
                opts.inJustDecodeBounds=false;opts.inSampleSize=1;
                while(opts.outWidth/opts.inSampleSize>1600||opts.outHeight/opts.inSampleSize>1600)
                    opts.inSampleSize*=2;
                Bitmap resultBitmap;
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    resultBitmap=BitmapFactory.decodeStream(in,null,opts);
                }
                if(resultBitmap==null)throw new IllegalArgumentException("La imagen está vacía");
                floor=resultBitmap;samples.clear();fixedBssid="";fixedSsid="";lastMeasurementAt=0;update();
            }catch(Exception e){Toast.makeText(this,"Error al cargar plano: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        }else if(request==202){
            try(OutputStream out=getContentResolver().openOutputStream(uri)){
                if(out==null)throw new IllegalArgumentException("Destino inválido");
                int w=Math.min(1400,Math.max(1,chart.getWidth()));
                int h=Math.min(1400,Math.max(1,chart.getHeight()));
                Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                Canvas c=new Canvas(bitmap);
                c.scale((float)w/chart.getWidth(),(float)h/chart.getHeight());
                chart.draw(c);
                bitmap.compress(Bitmap.CompressFormat.PNG,95,out);
                bitmap.recycle();
                Toast.makeText(this,"Mapa guardado",Toast.LENGTH_SHORT).show();
                ToolHistory.add("Mapa de cobertura","Wi-Fi local",
                    samples.size()+" mediciones en plano; archivo PNG generado.");
            }catch(Exception e){Toast.makeText(this,"Error al guardar mapa",Toast.LENGTH_LONG).show();}
        }
    }
    @SuppressWarnings("deprecation")
    private WifiInfo currentWifi(){
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        return wm==null?null:wm.getConnectionInfo();
    }

    private void update(){
        int min=0,max=0;
        if(!samples.isEmpty()){
            min=max=samples.get(0).dbm;
            for(Sample sample:samples){
                min=Math.min(min,sample.dbm);
                max=Math.max(max,sample.dbm);
            }
        }
        String level=radiusLevel==0?"corto":radiusLevel==1?"medio":"amplio";
        String message=samples.size()+" mediciones locales · radio "+level+
            (samples.isEmpty()?" · sin mediciones": " · RSSI "+min+"…"+max+" dBm");
        if(samples.size()>=3 && max-min<=2)
            message+="\nAviso: las señales son casi iguales; no existe contraste medido.";
        message+="\nLas áreas no medidas permanecen sin colorear.";
        status.setText(message);
        accessPoint.setText(fixedBssid.isEmpty()
            ?"Red medida: se fijará al tomar el primer punto."
            :"Red medida: "+fixedSsid+" · "+fixedBssid+
                "\nNo se mezclan lecturas de distintos BSSID.");
        chart.invalidate();
    }

    private final class Chart extends View{
        private final Paint p=new Paint(3);
        private final RectF area=new RectF();
        Chart(){super(HeatmapActivity.this);setBackgroundColor(0xFFFFFFFF);}
        private void bounds(){
            if(floor==null){area.set(0,0,getWidth(),getHeight());return;}
            float scale=Math.min((float)getWidth()/floor.getWidth(),(float)getHeight()/floor.getHeight());
            float w=floor.getWidth()*scale,h=floor.getHeight()*scale;
            area.set((getWidth()-w)/2f,(getHeight()-h)/2f,(getWidth()+w)/2f,(getHeight()+h)/2f);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);bounds();
            if(floor==null){
                p.setTextSize(36);p.setColor(0xFF66778D);
                canvas.drawText("Selecciona un plano",20,65,p);return;
            }
            p.setColor(0xFFFFFFFF);p.setAlpha(255);
            canvas.drawBitmap(floor,null,area,p);
            if(!samples.isEmpty()){
                // Never extrapolate coverage across unmeasured parts of the floor.
                int cols=38;
                int rows=Math.max(16,Math.round(cols*area.height()/Math.max(1f,area.width())));
                rows=Math.min(70,rows);
                float cw=area.width()/cols,ch=area.height()/rows;
                ArrayList<CoverageSurface.Sample> observed=new ArrayList<>(samples.size());
                for(Sample sample:samples)
                    observed.add(new CoverageSurface.Sample(sample.x,sample.y,sample.dbm));
                float aspect=area.width()/Math.max(1f,area.height());
                for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
                    float cx=(x+.5f)/cols,cy=(y+.5f)/rows;
                    CoverageSurface.Estimate estimate=
                        CoverageSurface.estimate(observed,cx,cy,aspect,radius);
                    if(estimate==null)continue; // Unknown area: keep the original plan.
                    p.setColor(CoverageSurface.color(estimate.dbm));
                    p.setAlpha(Math.max(8,Math.min(160,
                        Math.round(160f*estimate.confidence))));
                    canvas.drawRect(area.left+x*cw,area.top+y*ch,
                        area.left+(x+1)*cw+.6f,area.top+(y+1)*ch+.6f,p);
                }
            }
            p.setAlpha(255);
            for(Sample sample:samples){
                float x=area.left+sample.x*area.width(),y=area.top+sample.y*area.height();
                p.setColor(0xFFFFFFFF);canvas.drawCircle(x,y,15,p);
                p.setColor(CoverageSurface.color(sample.dbm));canvas.drawCircle(x,y,11,p);
                p.setColor(INK);p.setTextSize(15);
                canvas.drawText(sample.dbm+" dBm",x+17,y,p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()==MotionEvent.ACTION_DOWN)return true;
            if(event.getAction()!=MotionEvent.ACTION_UP)return true;
            bounds();
            if(floor==null||!area.contains(event.getX(),event.getY()))return true;
            if(samples.size()>=100){
                Toast.makeText(HeatmapActivity.this,"Máximo 100 mediciones",Toast.LENGTH_SHORT).show();
                return true;
            }
            WifiInfo info=currentWifi();
            String bssid=info==null?null:info.getBSSID();
            int dbm=info==null?0:info.getRssi();
            if(bssid==null||bssid.isEmpty()||bssid.equals("02:00:00:00:00:00")||
                    dbm>=0||dbm<=-127){
                Toast.makeText(HeatmapActivity.this,
                    "No hay RSSI/BSSID fiable; activa Wi-Fi y ubicación precisa.",
                    Toast.LENGTH_LONG).show();
                return true;
            }
            if(!fixedBssid.isEmpty()&&!fixedBssid.equalsIgnoreCase(bssid)){
                new AlertDialog.Builder(HeatmapActivity.this)
                    .setTitle("Has cambiado de punto de acceso")
                    .setMessage("La medición pertenece al BSSID "+bssid+
                        ". Este plano mide "+fixedBssid+
                        ". No se mezclarán las señales. Reinicia las mediciones "+
                        "para estudiar este nuevo punto de acceso.")
                    .setPositiveButton("Entendido",null).show();
                return true;
            }
            long now=android.os.SystemClock.elapsedRealtime();
            if(lastMeasurementAt!=0&&now-lastMeasurementAt<4000){
                Toast.makeText(HeatmapActivity.this,
                    "Mueve el móvil al nuevo lugar y espera 4 segundos entre puntos.",
                    Toast.LENGTH_SHORT).show();
                return true;
            }
            if(fixedBssid.isEmpty()){
                fixedBssid=bssid;
                String ssid=info.getSSID();
                fixedSsid=ssid==null||ssid.equals("<unknown ssid>")?
                    "SSID desconocido":ssid;
            }
            lastMeasurementAt=now;
            float x=(event.getX()-area.left)/area.width();
            float y=(event.getY()-area.top)/area.height();
            samples.add(new Sample(x,y,dbm));
            update();
            return true;
        }
    }
}

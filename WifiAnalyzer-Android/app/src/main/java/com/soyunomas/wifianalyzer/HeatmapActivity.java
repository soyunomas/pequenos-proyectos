package com.soyunomas.wifianalyzer;

import android.app.Activity;
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
    private TextView status;
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
        help.setText("Abre un plano y toca la posición real de cada medición. "+
            "Se usa el RSSI del punto de acceso actual, no una ubicación GPS.");
        root.addView(help);
        status=new TextView(this);status.setTextColor(BLUE);status.setTextSize(13);
        status.setPadding(5,14,5,10);status.setText("Sin plano. Sin mediciones.");
        root.addView(status);
        chart=new Chart();root.addView(chart,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout row=new LinearLayout(this);
        addButton(row,"Plano",()->open());
        addButton(row,"Deshacer",()->{
            if(!samples.isEmpty())samples.remove(samples.size()-1);
            update();
        });
        addButton(row,"Exportar",()->export());
        root.addView(row);root.requestApplyInsets();
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
                floor=resultBitmap;samples.clear();update();
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
    private int rssi(){
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        WifiInfo info=wm==null?null:wm.getConnectionInfo();
        if(info==null||info.getRssi()>=0||info.getRssi()<=-127)return 0;
        return info.getRssi();
    }
    private void update(){
        status.setText(samples.size()+" puntos registrados · "+
            (samples.size()<3?"necesita al menos tres mediciones para interpolar":
            "cobertura estimada por interpolación de RSSI"));
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
            if(samples.size()>=3){
                int cols=26,rows=26;
                float cw=area.width()/cols,ch=area.height()/rows;
                for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
                    float cx=(x+.5f)/cols,cy=(y+.5f)/rows;
                    double a=0,b=0;
                    for(Sample sample:samples){
                        double distance=Math.pow(cx-sample.x,2)+Math.pow(cy-sample.y,2);
                        double weight=1.0/(0.0007+distance);
                        a+=weight*sample.dbm;b+=weight;
                    }
                    p.setColor(tone((int)Math.round(a/b)));p.setAlpha(90);
                    canvas.drawRect(area.left+x*cw,area.top+y*ch,
                        area.left+(x+1)*cw+.6f,area.top+(y+1)*ch+.6f,p);
                }
            }
            p.setAlpha(255);
            for(Sample sample:samples){
                float x=area.left+sample.x*area.width(),y=area.top+sample.y*area.height();
                p.setColor(0xFFFFFFFF);canvas.drawCircle(x,y,15,p);
                p.setColor(tone(sample.dbm));canvas.drawCircle(x,y,11,p);
                p.setColor(INK);p.setTextSize(15);
                canvas.drawText(sample.dbm+" dBm",x+17,y,p);
            }
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(event.getAction()!=MotionEvent.ACTION_UP)return true;
            if(floor==null||!area.contains(event.getX(),event.getY()))return true;
            int dbm=rssi();
            if(dbm==0){Toast.makeText(HeatmapActivity.this,"Sin RSSI Wi-Fi válido; comprueba ubicación.",Toast.LENGTH_SHORT).show();return true;}
            if(samples.size()>=100){Toast.makeText(HeatmapActivity.this,"Máximo 100 mediciones",Toast.LENGTH_SHORT).show();return true;}
            float x=(event.getX()-area.left)/area.width(),y=(event.getY()-area.top)/area.height();
            samples.add(new Sample(x,y,dbm));update();return true;
        }
        private int tone(int dbm){
            if(dbm>=-55)return 0xFF238A70;
            if(dbm>=-68)return 0xFFB8B041;
            if(dbm>=-80)return 0xFFDA8A34;
            return 0xFFD25E66;
        }
    }
}

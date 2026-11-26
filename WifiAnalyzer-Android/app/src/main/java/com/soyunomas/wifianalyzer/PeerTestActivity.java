package com.soyunomas.wifianalyzer;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** TCP+UDP throughput testing between two opted-in Android phones on one Wi-Fi segment. */
public final class PeerTestActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile boolean listening=false;
    private volatile ServerSocket tcpServer;
    private volatile DatagramSocket udpServer;
    private volatile Thread clientThread;
    private TextView output,status;
    private EditText target;
    private LanScanner.Plan plan;
    private long started;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFFF6F8FC);
        getWindow().setNavigationBarColor(0xFFF6F8FC);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        try{plan=LanScanner.plan(this);}catch(Exception ignored){}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setBackgroundColor(0xFFF6F8FC);setContentView(root);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                top=insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottom=Math.max(insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsets.Type.systemGestures()).bottom);
            }else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}
            int d=(int)(14*getResources().getDisplayMetrics().density);
            view.setPadding(d,top+d,d,bottom+d);return insets;
        });
        TextView heading=new TextView(this);heading.setText("Rendimiento entre móviles");
        heading.setTextSize(23);heading.setTextColor(0xFF172235);root.addView(heading);
        TextView help=new TextView(this);help.setTextSize(13);help.setTextColor(0xFF66778D);
        help.setText("En el móvil A, inicia el servidor. En el móvil B, escribe su IP Wi-Fi "+
            "y pulsa Cliente. Se miden 4 MiB TCP y 80 datagramas UDP. "+
            "Solo funcionan si la Wi-Fi permite comunicación entre clientes.");
        help.setPadding(0,12,0,10);root.addView(help);
        status=new TextView(this);status.setTextSize(14);status.setTextColor(0xFF456CFF);
        status.setText(plan==null?"Sin Wi-Fi local compatible.":"IP Wi-Fi local: "+plan.ownIp);
        root.addView(status);
        target=new EditText(this);target.setSingleLine(true);target.setTextSize(15);
        target.setHint("IP Wi-Fi del otro móvil (por ejemplo 192.168.1.50)");
        root.addView(target);
        LinearLayout actions=new LinearLayout(this);
        addButton(actions,"Servidor",()->startServer());
        addButton(actions,"Cliente",()->startClient());
        addButton(actions,"Detener",()->stopAll());
        root.addView(actions);
        ScrollView scroll=new ScrollView(this);
        output=new TextView(this);output.setTextSize(13);output.setTextColor(0xFF172235);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setTextIsSelectable(true);output.setPadding(0,16,0,10);
        output.setText("Todavía no se ha iniciado ninguna prueba.");
        scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root.requestApplyInsets();
    }
    private void addButton(LinearLayout row,String title,Runnable cb){
        Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(0xFF456CFF);
        b.setOnClickListener(v->cb.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void log(String message){
        ui.post(()->{if(isFinishing()||isDestroyed())return;
            CharSequence old=output.getText();
            String text=old.length()>9000?old.toString().substring(old.length()-6000):old.toString();
            output.setText(text+"\n"+message);
        });
    }
    private void startServer(){
        if(plan==null){status.setText("Conecta la Wi-Fi antes de probar.");return;}
        if(listening){status.setText("Servidor ya activo.");return;}
        listening=true;started=System.currentTimeMillis();
        output.setText("Iniciando servidor en "+plan.ownIp+" TCP:5202 / UDP:5203\n");
        new Thread(()->{
            try(ServerSocket server=new ServerSocket()){
                tcpServer=server;
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(plan.ownIp,5202));server.setSoTimeout(650);
                log("TCP 5202 escuchando en "+plan.ownIp);
                while(listening&&System.currentTimeMillis()-started<120000){
                    try(Socket peer=server.accept()){
                        peer.setSoTimeout(3000);
                        long begin=System.nanoTime();long received=0;
                        InputStream in=peer.getInputStream();byte[] buffer=new byte[8192];
                        int n;
                        while(listening&&received<4*1024*1024&&(n=in.read(buffer))>0)received+=n;
                        double sec=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)/1000d;
                        log(String.format(Locale.ROOT,"TCP de %s: %d bytes en %.2f s · %.2f Mbps",
                            peer.getInetAddress().getHostAddress(),received,sec,
                            sec>0?received*8/sec/1e6:0));
                    }catch(SocketTimeoutException ignored){}
                }
            }catch(Exception e){log("Servidor TCP: "+e.getClass().getSimpleName());}
            finally{tcpServer=null;}
        },"netscope-peer-server-tcp").start();
        new Thread(()->{
            try(DatagramSocket socket=new DatagramSocket(null)){
                udpServer=socket;
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(plan.ownIp,5203));
                plan.network.bindSocket(socket);
                socket.setSoTimeout(700);
                log("UDP 5203 escuchando en "+plan.ownIp);
                while(listening&&System.currentTimeMillis()-started<120000){
                    DatagramPacket incoming=new DatagramPacket(new byte[512],512);
                    try{socket.receive(incoming);}catch(SocketTimeoutException e){continue;}
                    InetAddress address=incoming.getAddress();int port=incoming.getPort();
                    Set<Integer> seqs=new HashSet<>();long begin=System.nanoTime();
                    seqs.add(sequence(incoming));
                    for(int i=1;i<80&&listening;i++){
                        socket.setSoTimeout(700);
                        try{
                            DatagramPacket next=new DatagramPacket(new byte[512],512);
                            socket.receive(next);
                            if(next.getAddress().equals(address)&&next.getPort()==port)
                                seqs.add(sequence(next));
                        }catch(SocketTimeoutException e){break;}
                    }
                    String report="UDP paquetes recibidos: "+seqs.size()+"/80";
                    byte[] data=report.getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(data,data.length,address,port));
                    log("UDP de "+address.getHostAddress()+": "+report);
                }
            }catch(Exception e){log("Servidor UDP: "+e.getClass().getSimpleName());}
            finally{udpServer=null;}
        },"netscope-peer-server-udp").start();
        status.setText("Servidor activo durante un máximo de 2 minutos.");
    }
    private int sequence(DatagramPacket packet){
        if(packet.getLength()<2)return -1;
        return ((packet.getData()[0]&255)<<8)|(packet.getData()[1]&255);
    }
    private void startClient(){
        if(plan==null){status.setText("Sin Wi-Fi local.");return;}
        String ip=target.getText().toString().trim();
        if(!LanScanner.privateIpv4(ip)||(!plan.addresses.contains(ip)&&!ip.equals(plan.gateway))){
            status.setText("La IP debe pertenecer a la subred Wi-Fi actual.");return;
        }
        if(clientThread!=null&&clientThread.isAlive()){
            status.setText("Ya hay una prueba de cliente ejecutándose.");return;
        }
        output.setText("Midiendo contra "+ip+"…\n");
        clientThread=new Thread(()->{
            try(Socket socket=plan.network.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress(ip,5202),3000);socket.setSoTimeout(3000);
                long begin=System.nanoTime();byte[] block=new byte[8192];
                OutputStream out=socket.getOutputStream();
                for(int i=0;i<512&&!Thread.currentThread().isInterrupted();i++)out.write(block);
                out.flush();
                double sec=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin)/1000d;
                log(String.format(Locale.ROOT,"TCP 4 MiB · %.2f s · %.2f Mbps (envío observado)",
                    sec,sec>0?4*1024*1024*8/sec/1e6:0));
            }catch(Exception e){log("TCP no disponible: "+e.getClass().getSimpleName());}
            try(DatagramSocket socket=new DatagramSocket(null)){
                socket.bind(new InetSocketAddress(0));plan.network.bindSocket(socket);
                socket.setSoTimeout(3000);
                InetAddress dest=InetAddress.getByName(ip);
                byte[] packet=new byte[256];
                long begin=System.nanoTime();
                for(int i=0;i<80&&!Thread.currentThread().isInterrupted();i++){
                    packet[0]=(byte)(i>>8);packet[1]=(byte)i;
                    socket.send(new DatagramPacket(packet,packet.length,dest,5203));
                    Thread.sleep(4);
                }
                DatagramPacket reply=new DatagramPacket(new byte[256],256);
                socket.receive(reply);
                long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin);
                if(!reply.getAddress().equals(dest))throw new IOException("Respuesta UDP de otro dispositivo");
                log("UDP RTT total "+elapsed+" ms · "+
                    new String(reply.getData(),0,reply.getLength(),StandardCharsets.US_ASCII));
            }catch(Exception e){log("UDP no disponible: "+e.getClass().getSimpleName());}
            ui.post(()->{if(!isFinishing()&&!isDestroyed()){
                ToolHistory.add("Prueba entre móviles",ip,output.getText().toString());
                status.setText("Prueba finalizada; consulta resultados.");
            }});
        },"netscope-peer-client");
        clientThread.start();
    }
    private void stopAll(){
        listening=false;
        if(clientThread!=null)clientThread.interrupt();
        try{if(tcpServer!=null)tcpServer.close();}catch(Exception ignored){}
        if(udpServer!=null)udpServer.close();
        status.setText("Servidor y pruebas detenidos.");
    }
    @Override protected void onStop(){stopAll();super.onStop();}
}

package com.soyunomas.wifianalyzer;

import android.net.Network;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, explicit TCP connect scan on a single host in the connected private Wi-Fi subnet. */
public final class PortScanner {
    private PortScanner(){}
    public interface Callback {
        void found(Result open);
        void progress(int checked,int total,int open);
        void done(List<Result> open,String warning);
    }
    public static final class Result {
        public final int port, latencyMs;
        public final String service;
        public Result(int port,int latencyMs){
            this.port=port;this.latencyMs=latencyMs;
            this.service=PortCatalog.service(port);
        }
    }
    public static final class Handle {
        private final AtomicBoolean cancelled=new AtomicBoolean(false);
        private final ExecutorService manager=Executors.newSingleThreadExecutor();
        private volatile ExecutorService workers;
        public void cancel(){
            cancelled.set(true);
            if(workers!=null)workers.shutdownNow();
            manager.shutdownNow();
        }
    }

    public static Handle start(Network network,String ip,List<Integer> ports,Callback callbacks){
        if(network==null||!LanScanner.privateIpv4(ip)||ports==null||ports.isEmpty()||ports.size()>80)
            throw new IllegalArgumentException("El objetivo debe pertenecer a una LAN privada y la lista tener 1–80 puertos.");
        for(int port:ports)if(port<1||port>65535)throw new IllegalArgumentException("Puerto fuera de rango.");
        final List<Integer> plan=new ArrayList<>(ports);
        Handle handle=new Handle();
        handle.manager.execute(()->execute(network,ip,plan,callbacks,handle));
        return handle;
    }

    private static void execute(Network network,String ip,List<Integer> ports,Callback cb,Handle h){
        List<Result> found=new ArrayList<>();
        ExecutorService workers=Executors.newFixedThreadPool(Math.min(16,ports.size()));
        h.workers=workers;
        String message="";
        try{
            CompletionService<Result> completion=new ExecutorCompletionService<>(workers);
            for(int port:ports)completion.submit(()->test(network,ip,port,h.cancelled));
            int checked=0;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(18);
            while(checked<ports.size()&&!h.cancelled.get()){
                if(System.nanoTime()>deadline){message="Se alcanzó el límite de tiempo (18 s).";break;}
                Future<Result> future=completion.poll(250,TimeUnit.MILLISECONDS);
                if(future==null)continue;
                Result result=future.get();
                checked++;
                if(result!=null){
                    found.add(result);
                    cb.found(result);
                }
                cb.progress(checked,ports.size(),found.size());
            }
            if(h.cancelled.get())message="Exploración interrumpida; resultados parciales.";
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            message="Exploración cancelada; resultados parciales.";
        }catch(Exception e){
            message="Exploración incompleta: "+e.getClass().getSimpleName();
        }finally{
            workers.shutdownNow();
            h.manager.shutdown();
            found.sort(Comparator.comparingInt(x->x.port));
            cb.done(Collections.unmodifiableList(new ArrayList<>(found)),message);
        }
    }
    private static Result test(Network network,String ip,int port,AtomicBoolean cancelled){
        if(cancelled.get()||Thread.currentThread().isInterrupted())return null;
        long start=System.nanoTime();
        try(Socket socket=network.getSocketFactory().createSocket()){
            socket.connect(new InetSocketAddress(ip,port),380);
            long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            return new Result(port,(int)Math.min(Integer.MAX_VALUE,elapsed));
        }catch(IOException|SecurityException ignored){
            // No successful TCP connection: could be closed, filtered or unreachable.
            return null;
        }
    }
}

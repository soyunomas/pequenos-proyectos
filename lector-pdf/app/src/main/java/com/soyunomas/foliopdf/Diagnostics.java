package com.soyunomas.foliopdf;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Debug;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class Diagnostics {
    private static final String TAG = "FolioPDF";
    private static final String LOG_FILE = "diagnostics.log";
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static final long KEEP_BYTES = 320 * 1024;

    private static Context appContext;
    private static File logFile;

    private Diagnostics() {}

    static synchronized void init(Context context) {
        if (appContext != null) return;
        appContext = context.getApplicationContext();
        logFile = new File(appContext.getFilesDir(), LOG_FILE);
        rotateIfNeeded();
        separator("NEW SESSION");
        logEnvironment();
    }

    static void i(String area, String message) { write("I", area, message, null); }
    static void w(String area, String message) { write("W", area, message, null); }
    static void e(String area, String message, Throwable error) { write("E", area, message, error); }

    static synchronized String readAll() {
        if (logFile == null || !logFile.exists()) return "No hay registros todavía.";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        } catch (Exception error) {
            return "No se pudo leer el log: " + error;
        }
        return out.toString();
    }

    static synchronized void clear() {
        if (logFile == null) return;
        try (FileOutputStream ignored = new FileOutputStream(logFile, false)) {
            // Truncate.
        } catch (Exception error) {
            Log.e(TAG, "Cannot clear diagnostic log", error);
        }
        separator("LOG CLEARED");
        logEnvironment();
    }

    static void memory(String reason) {
        if (appContext == null) return;
        Runtime runtime = Runtime.getRuntime();
        ActivityManager manager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo system = new ActivityManager.MemoryInfo();
        if (manager != null) manager.getMemoryInfo(system);
        i("MEMORY", reason
                + " javaUsed=" + bytes(runtime.totalMemory() - runtime.freeMemory())
                + " javaFree=" + bytes(runtime.freeMemory())
                + " javaTotal=" + bytes(runtime.totalMemory())
                + " javaMax=" + bytes(runtime.maxMemory())
                + " nativeAllocated=" + bytes(Debug.getNativeHeapAllocatedSize())
                + " systemAvail=" + bytes(system.availMem)
                + " systemLow=" + system.lowMemory
                + " threshold=" + bytes(system.threshold));
    }

    private static synchronized void write(String level, String area, String message, Throwable error) {
        if (appContext == null || logFile == null) return;
        rotateIfNeeded();
        StringBuilder line = new StringBuilder();
        line.append(timestamp()).append(' ').append(level).append('/').append(area)
                .append(" [").append(Thread.currentThread().getName()).append("] ")
                .append(message == null ? "" : message).append('\n');
        if (error != null) {
            StringWriter stack = new StringWriter();
            error.printStackTrace(new PrintWriter(stack));
            line.append(stack).append('\n');
        }
        try (FileOutputStream stream = new FileOutputStream(logFile, true)) {
            stream.write(line.toString().getBytes(StandardCharsets.UTF_8));
            stream.flush();
        } catch (Exception fileError) {
            Log.e(TAG, "Cannot persist diagnostic log", fileError);
        }
        if ("E".equals(level)) Log.e(TAG + "/" + area, message, error);
        else if ("W".equals(level)) Log.w(TAG + "/" + area, message);
        else Log.i(TAG + "/" + area, message);
    }

    private static synchronized void separator(String title) {
        if (logFile == null) return;
        String text = "\n========== " + title + " " + timestamp() + " ==========\n";
        try (FileOutputStream stream = new FileOutputStream(logFile, true)) {
            stream.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            Log.e(TAG, "Cannot write separator", error);
        }
    }

    private static void logEnvironment() {
        if (appContext == null) return;
        try {
            PackageManager pm = appContext.getPackageManager();
            PackageInfo info = pm.getPackageInfo(appContext.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            i("APP", "package=" + appContext.getPackageName()
                    + " versionName=" + info.versionName
                    + " versionCode=" + versionCode
                    + " debuggable=" + ((appContext.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0));
        } catch (Exception error) {
            e("APP", "Unable to read package info", error);
        }

        i("DEVICE", "manufacturer=" + Build.MANUFACTURER
                + " brand=" + Build.BRAND
                + " model=" + Build.MODEL
                + " device=" + Build.DEVICE
                + " product=" + Build.PRODUCT
                + " android=" + Build.VERSION.RELEASE
                + " sdk=" + Build.VERSION.SDK_INT
                + " incremental=" + Build.VERSION.INCREMENTAL
                + " fingerprint=" + Build.FINGERPRINT
                + " abis=" + String.join(",", Build.SUPPORTED_ABIS));

        DisplayMetrics metrics = appContext.getResources().getDisplayMetrics();
        i("DISPLAY", "pixels=" + metrics.widthPixels + "x" + metrics.heightPixels
                + " density=" + metrics.density
                + " densityDpi=" + metrics.densityDpi
                + " scaledDensity=" + metrics.scaledDensity
                + " locale=" + Locale.getDefault().toLanguageTag()
                + " timezone=" + TimeZone.getDefault().getID());

        ActivityManager manager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            i("PROCESS", "memoryClassMb=" + manager.getMemoryClass()
                    + " largeMemoryClassMb=" + manager.getLargeMemoryClass()
                    + " lowRamDevice=" + manager.isLowRamDevice()
                    + " processors=" + Runtime.getRuntime().availableProcessors());
        }

        try {
            StatFs stat = new StatFs(appContext.getFilesDir().getAbsolutePath());
            i("STORAGE", "internalAvailable=" + bytes(stat.getAvailableBytes())
                    + " internalTotal=" + bytes(stat.getTotalBytes()));
        } catch (Exception error) {
            e("STORAGE", "Unable to read storage stats", error);
        }
        memory("session-start");
    }

    private static synchronized void rotateIfNeeded() {
        if (logFile == null || !logFile.exists() || logFile.length() <= MAX_LOG_BYTES) return;
        try {
            byte[] data;
            try (FileInputStream in = new FileInputStream(logFile)) {
                long skip = Math.max(0, logFile.length() - KEEP_BYTES);
                while (skip > 0) {
                    long skipped = in.skip(skip);
                    if (skipped <= 0) break;
                    skip -= skipped;
                }
                data = new byte[(int) Math.min(KEEP_BYTES, logFile.length())];
                int offset = 0;
                while (offset < data.length) {
                    int read = in.read(data, offset, data.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
                if (offset < data.length) {
                    byte[] trimmed = new byte[offset];
                    System.arraycopy(data, 0, trimmed, 0, offset);
                    data = trimmed;
                }
            }
            try (FileOutputStream out = new FileOutputStream(logFile, false)) {
                out.write("[older diagnostic entries truncated]\n".getBytes(StandardCharsets.UTF_8));
                out.write(data);
            }
        } catch (Exception error) {
            Log.e(TAG, "Cannot rotate diagnostic log", error);
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date());
    }

    private static String bytes(long value) {
        if (value < 0) return String.valueOf(value);
        double mb = value / (1024d * 1024d);
        return String.format(Locale.US, "%.1fMB", mb);
    }
}

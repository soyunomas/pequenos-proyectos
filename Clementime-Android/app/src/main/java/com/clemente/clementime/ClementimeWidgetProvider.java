package com.clemente.clementime;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.BroadcastReceiver.PendingResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.widget.RemoteViews;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;

public class ClementimeWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "clementime";
    private static final String ACTION_REVEAL_GEAR = "com.clemente.clementime.action.REVEAL_GEAR";
    private static final long GEAR_VISIBLE_MS = 1000L;
    private static final int MAX_THEME = 20;
    private static final int DEFAULT_IMAGE_OPACITY = 100;

    private static final ConcurrentHashMap<Integer, Integer> GEAR_GENERATIONS = new ConcurrentHashMap<Integer, Integer>();

    private static final int[] FRAMES = {
            0, R.drawable.theme_01, R.drawable.theme_02, R.drawable.theme_03,
            R.drawable.theme_04, R.drawable.theme_05, R.drawable.theme_06,
            R.drawable.theme_07, R.drawable.theme_08, R.drawable.theme_09,
            R.drawable.theme_10
    };

    private static final int[] AI_THEME_CHUNKS = {
            R.raw.ai_theme_1, R.raw.ai_theme_2, R.raw.ai_theme_3,
            R.raw.ai_theme_4, R.raw.ai_theme_5, R.raw.ai_theme_6
    };

    private static Bitmap aiThemeSheet;

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (!ACTION_REVEAL_GEAR.equals(intent.getAction())) return;

        final int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return;

        final Context appContext = context.getApplicationContext();
        final AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        final int generation;

        synchronized (GEAR_GENERATIONS) {
            Integer previous = GEAR_GENERATIONS.get(appWidgetId);
            generation = previous == null ? 1 : previous + 1;
            GEAR_GENERATIONS.put(appWidgetId, generation);
        }

        render(appContext, manager, appWidgetId, true);
        final PendingResult pendingResult = goAsync();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    boolean shouldHide = false;
                    synchronized (GEAR_GENERATIONS) {
                        Integer latest = GEAR_GENERATIONS.get(appWidgetId);
                        if (latest != null && latest == generation) {
                            GEAR_GENERATIONS.remove(appWidgetId);
                            shouldHide = true;
                        }
                    }
                    if (shouldHide) render(appContext, manager, appWidgetId, false);
                } finally {
                    pendingResult.finish();
                }
            }
        }, GEAR_VISIBLE_MS);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) render(context, manager, id, false);
    }

    static void render(Context context, AppWidgetManager manager, int appWidgetId, boolean showGear) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int theme = prefs.getInt("theme_" + appWidgetId, prefs.getInt("default_theme", 0));
        if (theme < 0 || theme > MAX_THEME) theme = 0;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        if (theme == 0) {
            views.setViewVisibility(R.id.theme_frame, View.GONE);
        } else if (theme <= 10) {
            views.setImageViewResource(R.id.theme_frame, FRAMES[theme]);
            views.setInt(R.id.theme_frame, "setImageAlpha", 255);
            views.setViewVisibility(R.id.theme_frame, View.VISIBLE);
        } else {
            Bitmap aiTheme = getAiTheme(context, theme - 11);
            if (aiTheme != null) {
                int opacity = getImageOpacity(context, appWidgetId);
                views.setImageViewBitmap(R.id.theme_frame, aiTheme);
                views.setInt(R.id.theme_frame, "setImageAlpha", Math.round(255f * opacity / 100f));
                views.setViewVisibility(R.id.theme_frame, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.theme_frame, View.GONE);
            }
        }

        views.setViewVisibility(R.id.settings_gear, showGear ? View.VISIBLE : View.GONE);

        Intent reveal = new Intent(context, ClementimeWidgetProvider.class);
        reveal.setAction(ACTION_REVEAL_GEAR);
        reveal.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_root, pendingBroadcast(context, 200000 + appWidgetId, reveal));

        if (showGear) {
            Intent settings = new Intent(context, ThemeConfigActivity.class);
            settings.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setOnClickPendingIntent(R.id.settings_gear, pendingActivity(context, 100000 + appWidgetId, settings));
        }
        manager.updateAppWidget(appWidgetId, views);
    }

    private static Bitmap getAiTheme(Context context, int index) {
        Bitmap sheet = getAiThemeSheet(context);
        if (sheet == null || index < 0 || index > 9) return null;
        int tileWidth = sheet.getWidth() / 2;
        int tileHeight = sheet.getHeight() / 5;
        return Bitmap.createBitmap(sheet, (index % 2) * tileWidth, (index / 2) * tileHeight, tileWidth, tileHeight);
    }

    private static synchronized Bitmap getAiThemeSheet(Context context) {
        if (aiThemeSheet != null) return aiThemeSheet;
        StringBuilder encoded = new StringBuilder();
        byte[] buffer = new byte[4096];
        try {
            for (int rawId : AI_THEME_CHUNKS) {
                InputStream input = context.getResources().openRawResource(rawId);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                input.close();
                encoded.append(out.toString("UTF-8"));
            }
            byte[] image = Base64.decode(encoded.toString(), Base64.DEFAULT);
            aiThemeSheet = BitmapFactory.decodeByteArray(image, 0, image.length);
        } catch (IOException | IllegalArgumentException ignored) {
            aiThemeSheet = null;
        }
        return aiThemeSheet;
    }

    static int getImageOpacity(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int defaultOpacity = clampOpacity(prefs.getInt("default_image_opacity", DEFAULT_IMAGE_OPACITY));
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return defaultOpacity;
        return clampOpacity(prefs.getInt("opacity_" + appWidgetId, defaultOpacity));
    }

    static void setImageOpacity(Context context, AppWidgetManager manager, int appWidgetId, int opacity) {
        opacity = clampOpacity(opacity);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("opacity_" + appWidgetId, opacity).apply();
        render(context, manager, appWidgetId, false);
    }

    static void setDefaultImageOpacity(Context context, int opacity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("default_image_opacity", clampOpacity(opacity)).apply();
    }

    private static int clampOpacity(int opacity) { return Math.max(0, Math.min(100, opacity)); }

    static void setTheme(Context context, AppWidgetManager manager, int appWidgetId, int theme) {
        if (theme < 0 || theme > MAX_THEME) theme = 0;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("theme_" + appWidgetId, theme).apply();
        render(context, manager, appWidgetId, false);
    }

    static void setDefaultTheme(Context context, int theme) {
        if (theme < 0 || theme > MAX_THEME) theme = 0;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("default_theme", theme).apply();
    }

    private static PendingIntent pendingActivity(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }

    private static PendingIntent pendingBroadcast(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}

package com.clemente.clementime;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

public class ClementimeWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "clementime";

    private static final int[] FRAMES = {
        0, R.drawable.theme_01, R.drawable.theme_02, R.drawable.theme_03,
        R.drawable.theme_04, R.drawable.theme_05, R.drawable.theme_06,
        R.drawable.theme_07, R.drawable.theme_08, R.drawable.theme_09,
        R.drawable.theme_10
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) render(context, manager, id, false);
    }

    static void render(Context context, AppWidgetManager manager, int appWidgetId, boolean showGear) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int theme = prefs.getInt("theme_" + appWidgetId, prefs.getInt("default_theme", 0));
        if (theme < 0 || theme > 10) theme = 0;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_clock);
        if (theme == 0) {
            views.setViewVisibility(R.id.theme_frame, View.GONE);
        } else {
            views.setImageViewResource(R.id.theme_frame, FRAMES[theme]);
            views.setViewVisibility(R.id.theme_frame, View.VISIBLE);
        }
        views.setViewVisibility(R.id.settings_gear, showGear ? View.VISIBLE : View.GONE);

        Intent reveal = new Intent(context, RevealSettingsActivity.class);
        reveal.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_root, pending(context, appWidgetId, reveal));

        if (showGear) {
            Intent settings = new Intent(context, ThemeConfigActivity.class);
            settings.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setOnClickPendingIntent(R.id.settings_gear, pending(context, 100000 + appWidgetId, settings));
        }
        manager.updateAppWidget(appWidgetId, views);
    }

    static void setTheme(Context context, AppWidgetManager manager, int appWidgetId, int theme) {
        if (theme < 0 || theme > 10) theme = 0;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("theme_" + appWidgetId, theme).apply();
        render(context, manager, appWidgetId, false);
    }

    static void setDefaultTheme(Context context, int theme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt("default_theme", theme).apply();
    }

    private static PendingIntent pending(Context context, int requestCode, Intent intent) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, requestCode, intent, flags);
    }
}

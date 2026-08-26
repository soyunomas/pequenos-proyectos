package com.clemente.clementime;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

public class ThemeConfigActivity extends Activity {
    private int id = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AppWidgetManager manager;
    private TextView opacityValue;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.config_theme);

        id = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        manager = AppWidgetManager.getInstance(this);

        opacityValue = findViewById(R.id.image_opacity_value);
        SeekBar opacity = findViewById(R.id.image_opacity);
        int currentOpacity = ClementimeWidgetProvider.getImageOpacity(this, id);
        opacity.setProgress(currentOpacity);
        updateOpacityLabel(currentOpacity);

        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateOpacityLabel(progress);
                if (fromUser) applyImageOpacity(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void updateOpacityLabel(int opacity) {
        opacityValue.setText("Transparencia de imagen: " + opacity + "% de opacidad");
    }

    private void applyImageOpacity(int opacity) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ClementimeWidgetProvider.setImageOpacity(this, manager, id, opacity);
        } else {
            ClementimeWidgetProvider.setDefaultImageOpacity(this, opacity);
            int[] widgetIds = manager.getAppWidgetIds(
                    new ComponentName(this, ClementimeWidgetProvider.class)
            );
            for (int widgetId : widgetIds) {
                ClementimeWidgetProvider.setImageOpacity(this, manager, widgetId, opacity);
            }
        }
    }

    public void selectTheme(View view) {
        int theme;
        try {
            theme = Integer.parseInt(String.valueOf(view.getTag()));
        } catch (Exception ignored) {
            theme = 0;
        }

        ClementimeWidgetProvider.setDefaultTheme(this, theme);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ClementimeWidgetProvider.setTheme(this, manager, id, theme);
        } else {
            int[] widgetIds = manager.getAppWidgetIds(
                    new ComponentName(this, ClementimeWidgetProvider.class)
            );
            for (int widgetId : widgetIds) {
                ClementimeWidgetProvider.setTheme(this, manager, widgetId, theme);
            }
        }
        finish();
    }
}

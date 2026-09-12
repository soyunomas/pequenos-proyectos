package com.soyunomas.pdflimpio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class DiagnosticsActivity extends AppCompatActivity {
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Diagnostics.init(this);
        Diagnostics.i("DIAGNOSTICS_UI", "Diagnostics screen opened");
        setTitle("Log de diagnóstico");
        setContentView(buildUi());
        refresh();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18, 21, 26));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(8), dp(8), dp(8), dp(8));

        Button back = button("Volver", "Volver al lector");
        back.setOnClickListener(v -> finish());
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button copy = button("Copiar todo", "Copiar todo el registro al portapapeles");
        copy.setOnClickListener(v -> copyAll());
        actions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1f));

        Button clear = button("Limpiar", "Borrar el registro de diagnóstico");
        clear.setOnClickListener(v -> {
            Diagnostics.clear();
            refresh();
            Toast.makeText(this, "Log limpiado", Toast.LENGTH_SHORT).show();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(actions);

        TextView note = new TextView(this);
        note.setText("El registro no contiene el texto del PDF. Sí incluye nombre/URI del archivo, metadatos técnicos, tiempos, memoria, permisos y errores para poder diagnosticar bloqueos.");
        note.setTextColor(Color.rgb(210, 214, 220));
        note.setTextSize(13);
        note.setPadding(dp(12), dp(4), dp(12), dp(10));
        root.addView(note);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        logView = new TextView(this);
        logView.setTextColor(Color.rgb(235, 238, 242));
        logView.setTextSize(12);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(logView, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private Button button(String text, String description) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setContentDescription(description);
        return button;
    }

    private void refresh() {
        if (logView != null) logView.setText(Diagnostics.readAll());
    }

    private void copyAll() {
        String text = Diagnostics.readAll();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("PDF Limpio - log", text));
            Diagnostics.i("DIAGNOSTICS_UI", "Diagnostic log copied to clipboard chars=" + text.length());
            Toast.makeText(this, "Log copiado. Ya puedes pegarlo en ChatGPT.", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

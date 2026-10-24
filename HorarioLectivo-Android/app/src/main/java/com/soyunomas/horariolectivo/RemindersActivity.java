package com.soyunomas.horariolectivo;

import android.Manifest;
import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Single destination for reminder creation, editing, deletion and permission guidance. */
public final class RemindersActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 3107;
    private ReminderStore store;
    private AppTheme th;
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new ReminderStore(this);
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
    }

    @Override protected void onResume() {
        super.onResume();
        ReminderScheduler.rescheduleAll(this);
        render();
        int id = getIntent().getIntExtra(ReminderReceiver.EXTRA_ID, 0);
        getIntent().removeExtra(ReminderReceiver.EXTRA_ID);
        if (id > 0) {
            ReminderStore.Reminder item = store.find(id);
            if (item != null) new AlertDialog.Builder(this).setTitle(item.title)
                .setMessage(item.notes.isEmpty() ? format(item.atMillis) : item.notes)
                .setPositiveButton("Cerrar", null)
                .setNeutralButton("Editar", (d,w) -> edit(item)).show();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == REQUEST_NOTIFICATIONS) render();
    }

    private void render() {
        th = new AppTheme(new ScheduleRepository(this).isDarkMode());
        getWindow().setStatusBarColor(th.page);
        getWindow().setNavigationBarColor(th.page);
        getWindow().getDecorView().setSystemUiVisibility(th.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(th.page);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(content);
        setContentView(scroll);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(dp(18), dp(18) + insets.getSystemWindowInsetTop(),
                    dp(18), dp(28) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        content.requestApplyInsets();

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("‹", false);
        back.setContentDescription("Volver al horario");
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("Recordatorios", 24, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMargins(dp(8), 0, 0, 0);
        top.addView(title, titleParams);
        content.addView(top);

        TextView help = label("Crea avisos para una fecha y hora. Puedes añadir notas largas y decidir si suenan.", 14, false);
        help.setTextColor(th.muted);
        help.setPadding(0, dp(10), 0, dp(14));
        content.addView(help);
        Button add = button("+ Nuevo recordatorio", true);
        add.setOnClickListener(v -> edit(null));
        content.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissionCard("Las notificaciones están desactivadas. Actívalas para recibir los avisos.",
                "Permitir notificaciones", () -> {
                    Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(intent);
                });
        }
        if (!ReminderScheduler.canBeExact(this)) {
            permissionCard("Para avisos puntuales, permite «Alarmas y recordatorios». Sin ese permiso, Android puede retrasarlos.",
                "Permitir alarmas exactas", () -> {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(this, "Abre Ajustes y habilita Alarmas y recordatorios", Toast.LENGTH_LONG).show();
                    }
                });
        }

        List<ReminderStore.Reminder> items = store.all();
        long now = System.currentTimeMillis();
        int future = 0;
        for (ReminderStore.Reminder item : items) if (item.atMillis > now) future++;
        TextView heading = label("Próximos · " + future, 17, true);
        heading.setPadding(0, dp(24), 0, dp(10));
        content.addView(heading);
        if (future == 0) empty("Todavía no hay avisos pendientes. Crea uno para empezar.");
        for (ReminderStore.Reminder item : items) if (item.atMillis > now) itemCard(item, false);
        if (future < items.size()) {
            TextView previous = label("Anteriores", 17, true);
            previous.setPadding(0, dp(20), 0, dp(10));
            content.addView(previous);
            for (ReminderStore.Reminder item : items) if (item.atMillis <= now) itemCard(item, true);
        }
    }

    private void permissionCard(String message, String action, Runnable callback) {
        LinearLayout card = panel();
        LinearLayout.LayoutParams margin = new LinearLayout.LayoutParams(-1, -2);
        margin.setMargins(0, dp(12), 0, 0);
        TextView messageView = label(message, 13, false);
        card.addView(messageView);
        Button button = button(action, false);
        button.setOnClickListener(v -> callback.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
        p.setMargins(0, dp(10), 0, 0);
        card.addView(button, p);
        content.addView(card, margin);
    }

    private void empty(String message) {
        TextView view = label(message, 14, false);
        view.setTextColor(th.muted);
        view.setPadding(dp(6), dp(8), dp(6), dp(16));
        content.addView(view);
    }

    private void itemCard(ReminderStore.Reminder item, boolean past) {
        LinearLayout card = panel();
        TextView title = label(item.title, 17, true);
        card.addView(title);
        TextView when = label(format(item.atMillis) + "  ·  " + (item.sound ? "Con sonido" : "Silencioso"), 13, false);
        when.setTextColor(th.muted);
        when.setPadding(0, dp(6), 0, dp(6));
        card.addView(when);
        if (!item.slotKey.isEmpty()) {
            TextView location = label("Horario · " + ReminderSlot.description(item.slotKey), 12, false);
            location.setTextColor(th.primary);
            location.setPadding(0, 0, 0, dp(6));
            card.addView(location);
        }
        if (!item.notes.isEmpty()) {
            TextView note = label(item.notes, 14, false);
            note.setMaxLines(4);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(note);
            if (item.notes.length() > 150 || item.notes.contains("\n")) {
                Button full = button("Ver texto completo", false);
                full.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(item.title).setMessage(item.notes).setPositiveButton("Cerrar", null).show());
                card.addView(full, new LinearLayout.LayoutParams(-1, dp(44)));
            }
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button edit = button("Editar", false);
        edit.setOnClickListener(v -> edit(item));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button delete = button("Eliminar", false);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Eliminar recordatorio")
                .setMessage("¿Eliminar «" + item.title + "»? Esta acción no se puede deshacer.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Eliminar", (d,w) -> {
                    ReminderScheduler.cancel(this, item.id);
                    store.remove(item.id);
                    render();
                }).show());
        LinearLayout.LayoutParams del = new LinearLayout.LayoutParams(0, dp(48), 1);
        del.setMargins(dp(8), 0, 0, 0);
        actions.addView(delete, del);
        LinearLayout.LayoutParams buttons = new LinearLayout.LayoutParams(-1, -2);
        buttons.setMargins(0, dp(12), 0, 0);
        card.addView(actions, buttons);
        if (past) card.setAlpha(0.78f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(10));
        content.addView(card, lp);
    }

    private void edit(ReminderStore.Reminder existing) {
        ReminderEditor.show(this, existing, "", "", 0, this::render);
    }

    private LinearLayout panel() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(background(th.surface, th.border, 16));
        return card;
    }

    private TextView label(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(th.ink);
        if (bold) view.setTypeface(view.getTypeface(), Typeface.BOLD);
        return view;
    }

    private Button button(String value, boolean primary) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextSize(14);
        view.setMinHeight(0);
        view.setMinimumHeight(0);
        view.setPadding(dp(8), 0, dp(8), 0);
        view.setTextColor(primary ? th.primaryText : th.ink);
        view.setBackground(background(primary ? th.primary : th.surfaceAlt, primary ? th.primary : th.border, 12));
        return view;
    }

    private GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(1), stroke);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private String format(long at) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(new Date(at));
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }
}

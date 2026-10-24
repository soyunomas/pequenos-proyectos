package com.soyunomas.horariolectivo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Shared reminder form, usable inside a timetable cell or from the reminders list. */
public final class ReminderEditor {
    private ReminderEditor() {}

    public static void show(Activity activity, ReminderStore.Reminder existing,
            String suggestedTitle, String slotKey, long suggestedAt, Runnable onSaved) {
        AppTheme theme = new AppTheme(new ScheduleRepository(activity).isDarkMode());
        ReminderStore store = new ReminderStore(activity);
        String association = existing == null ? (slotKey == null ? "" : slotKey) : existing.slotKey;
        Calendar selected = Calendar.getInstance();
        if (existing != null) selected.setTimeInMillis(existing.atMillis);
        else if (suggestedAt > System.currentTimeMillis()) selected.setTimeInMillis(suggestedAt);
        else {
            selected.add(Calendar.HOUR_OF_DAY, 1);
            selected.set(Calendar.MINUTE, 0);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
        }

        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 8));
        if (!association.isEmpty()) {
            TextView context = text(activity, "Vinculado al horario · " + ReminderSlot.description(association), 13, theme.muted);
            context.setPadding(0, 0, 0, dp(activity, 14));
            form.addView(context);
        }

        form.addView(text(activity, "Título", 13, theme.ink));
        EditText title = new EditText(activity);
        title.setSingleLine(true);
        title.setTextColor(theme.ink);
        title.setHintTextColor(theme.muted);
        title.setHint("¿Qué necesitas recordar?");
        title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        title.setText(existing != null ? existing.title : suggestedTitle);
        form.addView(title, new LinearLayout.LayoutParams(-1, dp(activity, 52)));

        TextView notesTitle = text(activity, "Mensaje y detalles", 13, theme.ink);
        notesTitle.setPadding(0, dp(activity, 14), 0, 0);
        form.addView(notesTitle);
        EditText notes = new EditText(activity);
        notes.setTextColor(theme.ink);
        notes.setHintTextColor(theme.muted);
        notes.setHint("Escribe las instrucciones o notas. Se mostrarán al abrir la notificación.");
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setMinLines(4);
        notes.setMaxLines(7);
        notes.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        notes.setFilters(new InputFilter[]{new InputFilter.LengthFilter(5000)});
        if (existing != null) notes.setText(existing.notes);
        form.addView(notes);

        TextView when = text(activity, "¿Cuándo quieres recibirlo?", 13, theme.ink);
        when.setPadding(0, dp(activity, 16), 0, dp(activity, 8));
        form.addView(when);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button date = button(activity);
        Button time = button(activity);
        Runnable update = () -> {
            date.setText(DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(selected.getTime()));
            time.setText(String.format(Locale.getDefault(), "%02d:%02d",
                    selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE)));
        };
        update.run();
        date.setOnClickListener(v -> new DatePickerDialog(activity, (picker,y,m,d) -> {
            selected.set(Calendar.YEAR, y);
            selected.set(Calendar.MONTH, m);
            selected.set(Calendar.DAY_OF_MONTH, d);
            update.run();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show());
        time.setOnClickListener(v -> new TimePickerDialog(activity, (picker,h,m) -> {
            selected.set(Calendar.HOUR_OF_DAY, h);
            selected.set(Calendar.MINUTE, m);
            selected.set(Calendar.SECOND, 0);
            selected.set(Calendar.MILLISECOND, 0);
            update.run();
        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show());
        row.addView(date, new LinearLayout.LayoutParams(0, dp(activity, 48), 2));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, dp(activity, 48), 1);
        timeParams.setMargins(dp(activity, 8), 0, 0, 0);
        row.addView(time, timeParams);
        form.addView(row);

        Switch sound = new Switch(activity);
        sound.setText("Sonido de notificación");
        sound.setTextColor(theme.ink);
        sound.setChecked(existing == null || existing.sound);
        sound.setPadding(0, dp(activity, 16), 0, dp(activity, 12));
        form.addView(sound);
        TextView hint = text(activity,
                "Este aviso se envía una sola vez. Se mostrará en el móvil, incluso con la app cerrada, si las notificaciones están permitidas.",
                12, theme.muted);
        form.addView(hint);
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? "Nuevo recordatorio" : "Editar recordatorio")
                .setView(scroll)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null).create();
        dialog.setOnShowListener(x ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = title.getText().toString(), body = notes.getText().toString();
                String error = ReminderStore.Reminder.validate(name, body,
                        selected.getTimeInMillis(), System.currentTimeMillis());
                if (error != null) {
                    if (name.trim().isEmpty()) title.setError(error);
                    else Toast.makeText(activity, error, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    ReminderStore.Reminder saved = store.put(new ReminderStore.Reminder(
                            existing == null ? 0 : existing.id, name, body, selected.getTimeInMillis(),
                            sound.isChecked(), association));
                    boolean exact = ReminderScheduler.schedule(activity, saved);
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                    if (Build.VERSION.SDK_INT >= 33
                            && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 3110);
                    } else if (!exact) {
                        Toast.makeText(activity,
                                "Guardado. Activa «Alarmas y recordatorios» en los ajustes para avisos puntuales.",
                                Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(activity, "No se pudo guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }));
        dialog.show();
    }

    private static TextView text(Activity activity, String value, int sp, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        return view;
    }

    private static Button button(Activity activity) {
        Button button = new Button(activity);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(activity, 4), 0, dp(activity, 4), 0);
        return button;
    }

    private static int dp(Activity activity, int size) {
        return Math.round(size * activity.getResources().getDisplayMetrics().density);
    }
}

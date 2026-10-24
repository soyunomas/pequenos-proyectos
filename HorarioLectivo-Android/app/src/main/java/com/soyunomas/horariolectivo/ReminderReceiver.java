package com.soyunomas.horariolectivo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;

/** Displays the full note as an expandable local notification. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_FIRE = "com.soyunomas.horariolectivo.REMINDER_FIRE";
    public static final String EXTRA_ID = "reminder_id";
    private static final String SOUND = "reminders_sound_v1";
    private static final String SILENT = "reminders_silent_v1";

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || AlarmManagerAction.EXACT_GRANTED.equals(action)) {
            ReminderScheduler.rescheduleAll(context);
            return;
        }
        if (!ACTION_FIRE.equals(action)) return;
        int id = intent.getIntExtra(EXTRA_ID, 0);
        ReminderStore.Reminder reminder = new ReminderStore(context).find(id);
        if (reminder == null || reminder.atMillis > System.currentTimeMillis() + 60000L) return;
        if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        createChannels(manager);
        Intent open = new Intent(context, RemindersActivity.class);
        open.putExtra(EXTRA_ID, id);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String preview = reminder.notes.isEmpty() ? "Toca para ver el recordatorio." : reminder.notes;
        Notification notification = new Notification.Builder(context, reminder.sound ? SOUND : SILENT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(reminder.title)
                .setContentText(preview)
                .setStyle(new Notification.BigTextStyle().bigText(preview).setBigContentTitle(reminder.title))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setWhen(reminder.atMillis)
                .setShowWhen(true)
                .build();
        manager.notify(id, notification);
    }

    public static void createChannels(NotificationManager manager) {
        NotificationChannel audible = new NotificationChannel(SOUND, "Recordatorios con sonido", NotificationManager.IMPORTANCE_HIGH);
        audible.setDescription("Avisos de recordatorios con sonido");
        NotificationChannel silent = new NotificationChannel(SILENT, "Recordatorios silenciosos", NotificationManager.IMPORTANCE_DEFAULT);
        silent.setDescription("Avisos de recordatorios sin sonido");
        silent.setSound(null, (AudioAttributes) null);
        manager.createNotificationChannel(audible);
        manager.createNotificationChannel(silent);
    }

    /** Avoids a hard-coded API action string in other classes. */
    private static final class AlarmManagerAction {
        static final String EXACT_GRANTED = android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED;
    }
}

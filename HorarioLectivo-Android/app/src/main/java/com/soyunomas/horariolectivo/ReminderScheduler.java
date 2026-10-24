package com.soyunomas.horariolectivo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Schedules reminders even when the activity or application is not open. */
public final class ReminderScheduler {
    private ReminderScheduler() {}

    private static PendingIntent pending(Context context, int id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ReminderReceiver.ACTION_FIRE);
        intent.putExtra(ReminderReceiver.EXTRA_ID, id);
        return PendingIntent.getBroadcast(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canBeExact(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarm != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms());
    }

    /** Returns false when Android only allows an approximate delivery time. */
    public static boolean schedule(Context context, ReminderStore.Reminder reminder) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return false;
        PendingIntent pi = pending(context, reminder.id);
        alarm.cancel(pi);
        if (reminder.atMillis <= System.currentTimeMillis()) return canBeExact(context);
        if (canBeExact(context)) {
            try {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pi);
                return true;
            } catch (SecurityException denied) {
                // Permission may have changed between checking and scheduling.
            }
        }
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.atMillis, pi);
        return false;
    }

    public static void cancel(Context context, int id) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pending(context, id));
    }

    public static void rescheduleAll(Context context) {
        for (ReminderStore.Reminder r : new ReminderStore(context).all())
            if (r.atMillis > System.currentTimeMillis()) schedule(context, r);
    }
}

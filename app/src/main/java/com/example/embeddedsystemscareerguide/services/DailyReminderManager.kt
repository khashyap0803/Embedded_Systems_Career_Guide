package com.example.embeddedsystemscareerguide.services

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.embeddedsystemscareerguide.MainActivity
import com.example.embeddedsystemscareerguide.R

/**
 * A genuine daily practice reminder, toggled from Settings.
 *
 * fragment_settings.xml previously showed a static, always-on "Notifications:
 * Enabled" line with no notification system behind it anywhere in the app -
 * the claim was simply false. This is the real implementation: an
 * AlarmManager-scheduled repeating alarm that fires once a day and posts a
 * notification, with an explicit on/off switch whose state is persisted and
 * genuinely reflects whether the alarm is scheduled.
 *
 * Uses AlarmManager rather than WorkManager because WorkManager is not a
 * dependency of this project and pulling one in for a single daily alarm
 * would be a heavier, riskier change than the feature warrants; AlarmManager
 * is framework-level and sufficient here. `setInexactRepeating` is used
 * deliberately - an exact daily alarm needs the heavily-gated
 * SCHEDULE_EXACT_ALARM permission on API 31+, which is unjustified for a
 * "sometime around 9am" reminder.
 */
object DailyReminderManager {

    const val CHANNEL_ID = "daily_reminder"
    private const val REQUEST_CODE = 1001
    private const val NOTIFICATION_ID = 2001

    /** First fire time: 9 AM local. Approximate is fine - see class doc. */
    private const val REMINDER_HOUR = 9

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(com.example.embeddedsystemscareerguide.PrefsKeys.PREFS_APP, Context.MODE_PRIVATE)
            .getBoolean(com.example.embeddedsystemscareerguide.PrefsKeys.DAILY_REMINDER_ENABLED, false)

    private fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(com.example.embeddedsystemscareerguide.PrefsKeys.PREFS_APP, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(com.example.embeddedsystemscareerguide.PrefsKeys.DAILY_REMINDER_ENABLED, enabled)
            .apply()
    }

    /**
     * Turn the reminder on: create the channel, schedule the alarm, persist the choice.
     * Caller is responsible for having POST_NOTIFICATIONS granted on API 33+ first.
     */
    fun enable(context: Context) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = SystemClock.elapsedRealtime() + firstFireDelayMs()
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context)
        )
        setEnabled(context, true)
    }

    fun disable(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
        setEnabled(context, false)
    }

    /** Roughly how long until the next REMINDER_HOUR o'clock, at least 60s out. */
    private fun firstFireDelayMs(): Long {
        val now = java.time.LocalDateTime.now()
        var target = now.withHour(REMINDER_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis().coerceAtLeast(60_000L)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts the actual notification. Called by [DailyReminderReceiver] when the alarm fires. */
    fun showNotification(context: Context) {
        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val contentIntent = PendingIntent.getActivity(context, REQUEST_CODE, openAppIntent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fire_streak)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(context.getString(R.string.reminder_notification_body))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.reminder_notification_body)))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // NotificationManagerCompat.areNotificationsEnabled() covers both the API 33+
        // runtime permission and the classic per-app notification toggle; if either is
        // off, posting would silently no-op anyway, so skip it explicitly for clarity.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

/** Fired by the daily AlarmManager alarm scheduled in [DailyReminderManager]. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DailyReminderManager.showNotification(context)
    }
}

/**
 * AlarmManager alarms are cleared on reboot. If the user had the reminder on,
 * re-arm it; otherwise a device restart would silently and permanently turn
 * the feature off with no indication to the user.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (DailyReminderManager.isEnabled(context)) {
            DailyReminderManager.enable(context)
        }
    }
}

package com.timesince.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Alerts for the Event Countdown widget: when an event's date arrives, a notification, or a
 * notification that rings with the phone's ringtone.
 *
 * One alarm is kept for the next alert of all widgets. When it fires, [process] sends what is due
 * and sets the alarm for the one after. [process] is also called whenever a widget updates, after a
 * reboot and when the clock or time zone changes, so a lost alarm is always put back.
 */
object EventAlerts {
    const val ACTION_FIRE = "com.timesince.app.EVENT_ALERT"

    const val MODE_OFF = 0
    const val MODE_NOTIFY = 1
    const val MODE_RINGTONE = 2

    private const val CHANNEL_NOTIFY = "event_alerts"
    private const val CHANNEL_RINGTONE = "event_alerts_ringtone"

    /** An alert that was missed (phone off) is still shown if it is less than a day late. */
    private const val LATE_LIMIT_MS = 24L * 60L * 60L * 1000L

    fun modeLabel(mode: Int): String = when (mode) {
        MODE_NOTIFY -> "Notification"
        MODE_RINGTONE -> "Ringtone"
        else -> "Off"
    }

    /** Sends the alerts that are due, then sets the alarm for the next one. Safe to call at any time. */
    fun process(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, EventsWidget::class.java))
        val now = System.currentTimeMillis()
        var next = Long.MAX_VALUE

        for (id in ids) {
            val cfg = ExtraStore.readEvents(context, id)
            val cursor = ExtraStore.alertCursor(context, id, now)
            for (event in cfg.events) {
                if (event.alert == MODE_OFF) continue
                for (moment in EventMath.triggersBetween(event, cfg.notifyMinutes, cursor, now)) {
                    if (now - moment <= LATE_LIMIT_MS) {
                        post(context, event.name, event.alert, "Your countdown is over. It's today!")
                    }
                }
                val upcoming = EventMath.nextTrigger(event, cfg.notifyMinutes, now)
                if (upcoming != null && upcoming < next) next = upcoming
            }
            ExtraStore.setAlertCursor(context, id, now)
        }

        schedule(context, if (next == Long.MAX_VALUE) null else next)
    }

    /** Shows one alert right now. The settings screen uses it for the test button. */
    fun post(context: Context, title: String, mode: Int, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        createChannels(nm)

        val ringtone = mode == MODE_RINGTONE
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(context, if (ringtone) CHANNEL_RINGTONE else CHANNEL_NOTIFY)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
        // The ringtone repeats until the notification is opened or swiped away, at most one minute.
        if (ringtone) builder.setTimeoutAfter(60_000L)

        val notification = builder.build()
        if (ringtone) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify((System.nanoTime() and 0x7FFFFFFFL).toInt(), notification)
    }

    private fun createChannels(nm: NotificationManager) {
        val normal = NotificationChannel(
            CHANNEL_NOTIFY, "Event alerts", NotificationManager.IMPORTANCE_HIGH
        )
        normal.description = "Tells you when an Event Countdown reaches its date."
        nm.createNotificationChannel(normal)

        val ring = NotificationChannel(
            CHANNEL_RINGTONE, "Event alerts with ringtone", NotificationManager.IMPORTANCE_HIGH
        )
        ring.description = "Rings with your phone's ringtone when an Event Countdown reaches its date."
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ring.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), attributes)
        ring.enableVibration(true)
        ring.vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
        nm.createNotificationChannel(ring)
    }

    private fun alarmIntent(context: Context): PendingIntent {
        val intent = Intent(context, EventAlarmReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Sets the one alarm, exact when the phone allows it. null means there is nothing left to wait for. */
    private fun schedule(context: Context, at: Long?) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = alarmIntent(context)
        if (at == null) {
            alarms.cancel(pending)
            return
        }
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        try {
            if (exact) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}

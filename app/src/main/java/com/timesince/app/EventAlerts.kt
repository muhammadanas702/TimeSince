package com.timesince.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Alerts for the Event Countdown widget (when an event's date or time arrives) and for Time Since widgets
 * that count down to a future date: a notification, or a notification that rings with the phone's ringtone.
 *
 * One alarm is kept for the next alert (or the next change of the widget text) of all widgets. When it
 * fires, [process] sends what is due and sets the alarm for the one after. [process] is also called whenever a widget updates, after a
 * reboot and when the clock or time zone changes, so a lost alarm is always put back.
 */
object EventAlerts {
    const val ACTION_FIRE = "com.timesince.app.EVENT_ALERT"
    /** Sent by the "Stop" button of a ringing alert. */
    const val ACTION_STOP = "com.timesince.app.EVENT_ALERT_STOP"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    const val MODE_OFF = 0
    const val MODE_NOTIFY = 1
    const val MODE_RINGTONE = 2

    private const val CHANNEL_NOTIFY = "event_alerts"
    private const val CHANNEL_RINGTONE = "event_alerts_ringtone"

    private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH)

    /** An alert that was missed (phone off) is still shown if it is less than a day late. */
    private const val LATE_LIMIT_MS = 24L * 60L * 60L * 1000L

    fun modeLabel(mode: Int): String = when (mode) {
        MODE_NOTIFY -> "Notification"
        MODE_RINGTONE -> "Ringtone"
        else -> "Off"
    }

    /** The ready-made choices for how long before an event the alert goes off, in minutes (0 = at the time). */
    val LEAD_CHOICES = intArrayOf(0, 10, 30, 60, 120, 1440, 2880, 10080)

    /** "30 minutes", "1 hour", "2 days", "1 week", "90 minutes": a lead time in words. */
    fun leadText(minutes: Int): String {
        val (number, unit) = when {
            minutes % 10080 == 0 -> Pair(minutes / 10080, "week")
            minutes % 1440 == 0 -> Pair(minutes / 1440, "day")
            minutes % 60 == 0 -> Pair(minutes / 60, "hour")
            else -> Pair(minutes, "minute")
        }
        return number.toString() + " " + unit + (if (number == 1) "" else "s")
    }

    /** The text of the lead button: "at the time", "1 hour before"; [timed] tells if the event has a time. */
    fun leadLabel(minutes: Int, timed: Boolean): String =
        if (minutes <= 0) (if (timed) "at the time" else "on the day") else leadText(minutes) + " before"

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
                // An event with a time alerts at that time; one without uses the widget's alert time.
                val minutes = if (event.minutes >= 0) event.minutes else cfg.notifyMinutes
                for (moment in EventMath.triggersBetween(event, minutes, cursor, now)) {
                    if (now - moment <= LATE_LIMIT_MS) post(context, event.name, event.alert, alertText(event, moment))
                }
                val upcoming = EventMath.nextTrigger(event, minutes, now)
                if (upcoming != null && upcoming < next) next = upcoming
            }
            // The widget text changes by itself too (hours, then "Now"), so the same alarm wakes it up.
            val refresh = EventMath.nextRefresh(cfg.events, LocalDateTime.now(), now)
            if (refresh != null && refresh < next) next = refresh
            ExtraStore.setAlertCursor(context, id, now)
        }

        // Time Since widgets that count down to a date in the future can alert when it arrives.
        val sinceIds = manager.getAppWidgetIds(ComponentName(context, TimeSinceWidget::class.java))
        for (id in sinceIds) {
            val cfg = Store.read(context, id) ?: continue
            if (cfg.alert == MODE_OFF) continue
            val cursor = ExtraStore.alertCursor(context, id, now)
            val moment = cfg.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (moment > cursor && moment <= now && now - moment <= LATE_LIMIT_MS) {
                post(context, cfg.label.ifBlank { "Time Since" }, cfg.alert, "Your countdown is over.")
            }
            if (moment > now && moment < next) next = moment
            ExtraStore.setAlertCursor(context, id, now)
        }

        schedule(context, if (next == Long.MAX_VALUE) null else next)
    }

    /**
     * What an alert says. At the time of the event: "It's time!". Earlier: "In 1 hour · 12 Dec 2026, 9:00 AM",
     * with the date and time of the event, which is [EventItem.lead] minutes after the alert at [moment].
     */
    private fun alertText(event: EventItem, moment: Long): String {
        if (event.lead <= 0) {
            return if (event.minutes >= 0) "It's time!" else "Your countdown is over. It's today!"
        }
        val at = Instant.ofEpochMilli(moment).atZone(ZoneId.systemDefault()).toLocalDateTime()
            .plusMinutes(event.lead.toLong())
        val dateText = if (event.minutes >= 0) DATE_TIME_FMT.format(at) else DATE_FMT.format(at.toLocalDate())
        return "In " + leadText(event.lead) + " · " + dateText
    }

    /**
     * True when this app may show notifications. Android 13 and newer ask first, and on every version the
     * user can switch an app's notifications off in the phone's settings.
     */
    fun notificationsAllowed(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val nm = context.getSystemService(NotificationManager::class.java)
        return granted && nm.areNotificationsEnabled()
    }

    /** Tells the user, when saving a widget that has an alert, that the alert cannot show while notifications are off. */
    fun warnIfBlocked(context: Context) {
        if (notificationsAllowed(context)) return
        Toast.makeText(
            context,
            "Notifications are off for Time Since, so alerts will not show. Turn them on in the phone's app settings.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun askNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    /** Shows one alert right now. The settings screen uses it for the test button. */
    fun post(context: Context, title: String, mode: Int, text: String) {
        try {
            show(context, title, mode, text)
        } catch (e: RuntimeException) {
            // For example a phone that refuses the notification: the widgets must keep working.
            Log.w("TimeSince", "Could not show the alert", e)
        }
    }

    private fun show(context: Context, title: String, mode: Int, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        createChannels(nm)

        val ringtone = mode == MODE_RINGTONE
        val id = (System.nanoTime() and 0x7FFFFFFFL).toInt()
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
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setWhen(System.currentTimeMillis())
        if (ringtone) {
            // The ringtone repeats until the notification is opened, stopped or swiped away, at most one minute.
            builder.setTimeoutAfter(60_000L)
            val stop = PendingIntent.getBroadcast(
                context, id,
                Intent(context, EventAlarmReceiver::class.java)
                    .setAction(ACTION_STOP)
                    .putExtra(EXTRA_NOTIFICATION_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_event), "Stop", stop)
                    .build()
            )
        }

        val notification = builder.build()
        if (ringtone) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(id, notification)
    }

    /** Removes one alert from the notification shade, which also silences a ringing one. */
    fun stop(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    /**
     * Opens the phone's own settings page for the sound of an alert type. Android lets only the user choose
     * the sound of a notification channel, so this is where a different tone or ringtone is picked.
     */
    fun openSoundSettings(activity: Activity, ringtone: Boolean) {
        createChannels(activity.getSystemService(NotificationManager::class.java))
        val channel = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, if (ringtone) CHANNEL_RINGTONE else CHANNEL_NOTIFY)
        try {
            activity.startActivity(channel)
        } catch (e: RuntimeException) {
            openFirst(
                activity,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            )
        }
    }

    /**
     * Opens the phone's battery page for this app, where it can be allowed to run in the background so that
     * alarms are not delayed or dropped (some makers also have a separate auto-start page in their own settings).
     */
    fun openBackgroundSettings(activity: Activity) {
        openFirst(
            activity,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:" + activity.packageName))
        )
    }

    private fun openFirst(activity: Activity, vararg intents: Intent) {
        for (intent in intents) {
            try {
                activity.startActivity(intent)
                return
            } catch (e: RuntimeException) {
                // This phone has no such page; try the next one.
            }
        }
        Toast.makeText(activity, "Could not open the phone's settings. Open them yourself and look for Time Since.", Toast.LENGTH_LONG).show()
    }

    /**
     * A short sentence when the sound of an alert type is switched off in the phone's settings (so a test would
     * be silent), or null when it will make a sound. Silent mode and Do Not Disturb are not detected.
     */
    fun soundProblem(context: Context, ringtone: Boolean): String? {
        val nm = context.getSystemService(NotificationManager::class.java)
        createChannels(nm)
        val channel = nm.getNotificationChannel(if (ringtone) CHANNEL_RINGTONE else CHANNEL_NOTIFY) ?: return null
        return when {
            channel.importance == NotificationManager.IMPORTANCE_NONE ->
                "This alert type is switched off in the phone's settings."
            channel.importance < NotificationManager.IMPORTANCE_DEFAULT || channel.sound == null ->
                "This alert type has no sound in the phone's settings."
            else -> null
        }
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

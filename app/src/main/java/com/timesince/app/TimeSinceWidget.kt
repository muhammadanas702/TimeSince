package com.timesince.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget.
 *
 * A widget cannot redraw itself every second, so the layout is split in two:
 *  - years, months, days and hours are plain text, redrawn once per hour (and on any update);
 *  - minutes and seconds are a Chronometer, which the launcher keeps ticking by itself.
 */
class TimeSinceWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Store.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        cancelRefresh(context)
    }

    companion object {
        const val ACTION_REFRESH = "com.timesince.app.REFRESH"

        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimeSinceWidget::class.java))
            if (ids.isEmpty()) {
                cancelRefresh(context)
                return
            }
            var nextDelay = Long.MAX_VALUE
            for (id in ids) {
                val (views, delay) = build(context, id)
                manager.updateAppWidget(id, views)
                if (delay < nextDelay) nextDelay = delay
            }
            scheduleRefresh(context, nextDelay)
        }

        private fun build(context: Context, id: Int): Pair<RemoteViews, Long> {
            val cfg = Store.forWidget(context, id)
            val p = TimeMath.compute(cfg.start, LocalDateTime.now())
            val views = RemoteViews(context.packageName, R.layout.widget_time_since)

            views.setTextViewText(
                R.id.w_title,
                cfg.label.ifBlank { if (p.future) "Time until" else "Time since" }
            )
            views.setTextViewText(R.id.w_date, describe(cfg.start, p.future))
            views.setTextViewText(R.id.w_years, two(p.years))
            views.setTextViewText(R.id.w_months, two(p.months))
            views.setTextViewText(R.id.w_days, two(p.days))
            views.setTextViewText(R.id.w_hours, two(p.hours))

            // Minutes and seconds: counts up from the last hour tick, or down to the next one.
            val clock = SystemClock.elapsedRealtime()
            val base = if (p.future) clock + p.msInHour else clock - p.msInHour
            views.setChronometerCountDown(R.id.w_clock, p.future)
            views.setChronometer(R.id.w_clock, base, null, true)

            // Tapping the widget opens the screen where its date can be changed.
            val open = Intent(context, MainActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.w_root, pending)

            // The hour count changes when the chronometer wraps, so redraw right after that.
            val untilTick = if (p.future) {
                if (p.msInHour == 0L) 3_600_000L else p.msInHour
            } else {
                3_600_000L - p.msInHour
            }
            return views to (untilTick + 400L)
        }

        private fun describe(start: LocalDateTime, future: Boolean): String {
            val date = DATE_FMT.format(start)
            val time = if (start.toLocalTime() != LocalTime.MIDNIGHT) " · " + TIME_FMT.format(start) else ""
            return (if (future) "Until " else "Since ") + date + time
        }

        private fun two(n: Int): String = n.toString().padStart(2, '0')

        private fun refreshIntent(context: Context): PendingIntent {
            val intent = Intent(context, TimeSinceWidget::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        // A non-waking, inexact alarm is enough: if the phone is asleep nobody is looking at the widget.
        private fun scheduleRefresh(context: Context, delayMs: Long) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.set(
                AlarmManager.RTC,
                System.currentTimeMillis() + maxOf(delayMs, 5_000L),
                refreshIntent(context)
            )
        }

        private fun cancelRefresh(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.cancel(refreshIntent(context))
        }
    }
}

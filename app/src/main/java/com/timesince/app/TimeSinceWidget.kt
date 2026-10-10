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
 *  - years, months, days, hours (and the total-days number) are plain text, redrawn once per hour;
 *  - minutes and seconds are a Chronometer, which the launcher keeps ticking by itself.
 *
 * Each widget has its own Config: date, name, style, colors and transparency.
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
                val cfg = Store.forWidget(context, id)
                val (views, delay) = build(context, id, cfg, false)
                manager.updateAppWidget(id, views)
                if (delay < nextDelay) nextDelay = delay
            }
            scheduleRefresh(context, nextDelay)
        }

        /**
         * Builds the widget picture for one Config. The app screens call this too, with
         * preview = true, so what you see while editing is exactly what the widget shows.
         */
        fun build(context: Context, id: Int, cfg: Config, preview: Boolean): Pair<RemoteViews, Long> {
            val now = LocalDateTime.now()
            val p = TimeMath.compute(cfg.start, now)
            val layout = when (cfg.style) {
                Styles.DAYS -> R.layout.widget_days
                Styles.COMPACT -> R.layout.widget_compact
                else -> R.layout.widget_time_since
            }
            val views = RemoteViews(context.packageName, layout)
            val muted = withAlpha(cfg.fg, 0.7f)

            // Background: a white rounded shape, tinted with the chosen color and faded by the opacity.
            views.setInt(R.id.w_bg, "setColorFilter", cfg.bg or (0xFF shl 24))
            views.setInt(R.id.w_bg, "setImageAlpha", cfg.opacity * 255 / 100)

            views.setTextViewText(
                R.id.w_title,
                cfg.label.ifBlank { if (p.future) "Time until" else "Time since" }
            )
            views.setTextColor(R.id.w_title, cfg.fg)
            views.setTextViewText(R.id.w_date, describe(cfg.start, p.future))
            views.setTextColor(R.id.w_date, muted)

            when (cfg.style) {
                Styles.DAYS -> {
                    views.setTextViewText(R.id.w_total, number(p.totalDays))
                    views.setTextColor(R.id.w_total, cfg.fg)
                    views.setTextViewText(R.id.w_total_unit, unit(p))
                    views.setTextColor(R.id.w_total_unit, cfg.accent)
                    views.setTextViewText(R.id.w_breakdown, TimeMath.breakdown(p))
                    views.setTextColor(R.id.w_breakdown, muted)
                    val m = TimeMath.nextMilestone(cfg.start, now, p)
                    views.setTextViewText(
                        R.id.w_next,
                        if (m == null) "" else "Next: " + m.label + " · in " + TimeMath.daysLabel(m.daysLeft)
                    )
                    views.setTextColor(R.id.w_next, muted)
                }
                Styles.COMPACT -> {
                    views.setTextViewText(R.id.w_total, number(p.totalDays))
                    views.setTextColor(R.id.w_total, cfg.fg)
                    views.setTextViewText(R.id.w_total_unit, unit(p))
                    views.setTextColor(R.id.w_total_unit, cfg.accent)
                }
                else -> {
                    views.setTextViewText(R.id.w_years, two(p.years))
                    views.setTextViewText(R.id.w_months, two(p.months))
                    views.setTextViewText(R.id.w_days, two(p.days))
                    views.setTextViewText(R.id.w_hours, two(p.hours))
                    views.setTextColor(R.id.w_years, cfg.fg)
                    views.setTextColor(R.id.w_months, cfg.fg)
                    views.setTextColor(R.id.w_days, cfg.fg)
                    views.setTextColor(R.id.w_hours, cfg.fg)
                    views.setTextColor(R.id.lbl_years, muted)
                    views.setTextColor(R.id.lbl_months, muted)
                    views.setTextColor(R.id.lbl_days, muted)
                    views.setTextColor(R.id.lbl_hours, muted)
                    views.setTextColor(R.id.lbl_clock, cfg.accent)
                }
            }

            // Minutes and seconds: counts up from the last hour tick, or down to the next one.
            views.setTextColor(R.id.w_clock, cfg.accent)
            val clock = SystemClock.elapsedRealtime()
            val base = if (p.future) clock + p.msInHour else clock - p.msInHour
            views.setChronometerCountDown(R.id.w_clock, p.future)
            views.setChronometer(R.id.w_clock, base, null, true)

            // Tapping the widget opens its settings. Not needed in the in-app preview.
            if (!preview) {
                val open = Intent(context, ConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pending = PendingIntent.getActivity(
                    context, id, open,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.w_root, pending)
            }

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

        private fun unit(p: Parts): String = when {
            p.future && p.totalDays == 1L -> "day to go"
            p.future -> "days to go"
            p.totalDays == 1L -> "day"
            else -> "days"
        }

        private fun number(n: Long): String = String.format(Locale.ENGLISH, "%,d", n)

        private fun two(n: Int): String = n.toString().padStart(2, '0')

        private fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha * 255f).toInt() shl 24)

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

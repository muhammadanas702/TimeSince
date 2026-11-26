package com.timesince.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime

private class ProgressRow(val row: Int, val label: Int, val track: Int, val fill: Int, val pct: Int)

/** One bar: its name, how far along it is (0 to 1) and whether this widget shows it. */
private class ProgressBarInfo(val name: String, val fraction: Double, val shown: Boolean)

private val PROGRESS_ROWS = listOf(
    ProgressRow(R.id.p1_row, R.id.p1_label, R.id.p1_track, R.id.p1_fill, R.id.p1_pct),
    ProgressRow(R.id.p2_row, R.id.p2_label, R.id.p2_track, R.id.p2_fill, R.id.p2_pct),
    ProgressRow(R.id.p3_row, R.id.p3_label, R.id.p3_track, R.id.p3_fill, R.id.p3_pct),
    ProgressRow(R.id.p4_row, R.id.p4_label, R.id.p4_track, R.id.p4_fill, R.id.p4_pct)
)

/**
 * Day Progress: soft bars that show how far through today, this week, this month and this year it is.
 * The numbers move slowly (1% of a day is about 14 minutes), so one quiet repeating alarm that does not wake
 * the phone redraws the widget every 15 minutes, and it is also redrawn at boot and when the clock changes.
 */
class DayProgressWidget : AppWidgetProvider() {

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
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        cancelTick(context)
    }

    companion object {
        const val ACTION_TICK = "com.timesince.app.PROGRESS_TICK"
        private const val EVERY_MS = 15L * 60L * 1000L

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DayProgressWidget::class.java))
            if (ids.isEmpty()) {
                cancelTick(context)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, DayProgressConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readProgress(context, id), false)
                    }
                )
            }
            scheduleTick(context)
        }

        /** The bars of a widget, in order, with the ones the user switched off marked as hidden. At least one stays. */
        private fun bars(cfg: ProgressCfg, now: LocalDateTime): List<ProgressBarInfo> {
            val none = !cfg.showDay && !cfg.showWeek && !cfg.showMonth && !cfg.showYear
            return listOf(
                ProgressBarInfo("Today", ProgressMath.day(now), cfg.showDay || none),
                ProgressBarInfo("Week", ProgressMath.week(now, cfg.mondayFirst), cfg.showWeek),
                ProgressBarInfo("Month", ProgressMath.month(now), cfg.showMonth),
                ProgressBarInfo("Year", ProgressMath.year(now), cfg.showYear)
            )
        }

        /** The text for the app's list: "Today 42% · Week 68%". */
        fun summary(cfg: ProgressCfg): String =
            bars(cfg, LocalDateTime.now()).filter { it.shown }
                .joinToString(" · ") { it.name + " " + ProgressMath.percent(it.fraction) + "%" }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: ProgressCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_day_progress)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val trackColor = look.fg or (0xFF shl 24)
            val shown = bars(cfg, LocalDateTime.now())
            PROGRESS_ROWS.forEachIndexed { i, r ->
                val bar = shown[i]
                views.setViewVisibility(r.row, if (bar.shown) View.VISIBLE else View.GONE)
                views.setTextViewText(r.label, bar.name)
                views.setTextColor(r.label, look.fg)
                views.setTextViewText(r.pct, ProgressMath.percent(bar.fraction).toString() + "%")
                views.setTextColor(r.pct, look.accent)
                // The track is the text color, faded; the fill is the accent, clipped to the right length.
                views.setInt(r.track, "setColorFilter", trackColor)
                views.setInt(r.track, "setImageAlpha", 46)
                views.setInt(r.fill, "setColorFilter", look.accent or (0xFF shl 24))
                views.setInt(r.fill, "setImageLevel", (bar.fraction * 10_000.0).toInt().coerceIn(0, 10_000))
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, DayProgressConfigActivity::class.java)
                )
            }
            return views
        }

        private fun tickIntent(context: Context): PendingIntent {
            val intent = Intent(context, DayProgressWidget::class.java).setAction(ACTION_TICK)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun scheduleTick(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Does not wake the phone: if it sleeps nobody is looking at the widget, and it redraws on wake.
            alarms.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + EVERY_MS,
                EVERY_MS,
                tickIntent(context)
            )
        }

        private fun cancelTick(context: Context) {
            val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarms.cancel(tickIntent(context))
        }
    }
}

package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private class EventRow(val row: Int, val name: Int, val date: Int, val days: Int, val chrono: Int)

private val EVENT_ROWS = listOf(
    EventRow(R.id.e1_row, R.id.e1_name, R.id.e1_date, R.id.e1_days, R.id.e1_chrono),
    EventRow(R.id.e2_row, R.id.e2_name, R.id.e2_date, R.id.e2_days, R.id.e2_chrono),
    EventRow(R.id.e3_row, R.id.e3_name, R.id.e3_date, R.id.e3_days, R.id.e3_chrono),
    EventRow(R.id.e4_row, R.id.e4_name, R.id.e4_date, R.id.e4_days, R.id.e4_chrono)
)

/**
 * Event countdown: up to four named dates (with an optional time) in one widget, each with the time left.
 * Events without a time only change at midnight. Events with a time show whole hours until a day is left,
 * then a live ticking countdown; the alarm of [EventAlerts] redraws the widget when the text must change.
 */
class EventsWidget : AppWidgetProvider() {

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
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
        // The alerts of a removed widget stop with it.
        EventAlerts.process(context)
    }

    override fun onDisabled(context: Context) {
        DailyTick.cancel(context, EventsWidget::class.java, ACTION_TICK)
    }

    companion object {
        const val ACTION_TICK = "com.timesince.app.EVENTS_TICK"

        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val DATE_TIME_FMT = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

        fun updateAll(context: Context) {
            // Sends alerts that are due and keeps the alarm for the next one in place.
            EventAlerts.process(context)
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EventsWidget::class.java))
            if (ids.isEmpty()) {
                DailyTick.cancel(context, EventsWidget::class.java, ACTION_TICK)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(id, build(context, id, ExtraStore.readEvents(context, id), false))
            }
            DailyTick.schedule(context, EventsWidget::class.java, ACTION_TICK)
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: EventsCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_events)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val now = LocalDateTime.now()
            val items = EventMath.order(cfg.events, now).take(EVENT_ROWS.size)

            EVENT_ROWS.forEachIndexed { i, r ->
                views.setViewVisibility(r.chrono, View.GONE)
                views.setViewVisibility(r.days, View.VISIBLE)
                if (items.isEmpty() && i == 0) {
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setTextViewText(r.name, "Tap to add events")
                    views.setTextColor(r.name, muted)
                    views.setTextViewText(r.date, "")
                    views.setTextViewText(r.days, "")
                } else if (i < items.size) {
                    val e = items[i]
                    val past = !e.upcoming
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setTextViewText(r.name, e.item.name)
                    views.setTextColor(r.name, if (past) muted else look.fg)
                    views.setTextViewText(
                        r.date,
                        if (e.timed) {
                            DATE_TIME_FMT.format(e.next.atTime(LocalTime.of(e.item.minutes / 60, e.item.minutes % 60)))
                        } else {
                            DATE_FMT.format(e.next)
                        }
                    )
                    views.setTextColor(r.date, muted)
                    views.setTextViewText(r.days, EventMath.label(e))
                    views.setTextColor(r.days, if (past) muted else look.accent)
                    if (e.live) {
                        // Under a day to go: the launcher ticks this countdown by itself, every second.
                        views.setViewVisibility(r.days, View.GONE)
                        views.setViewVisibility(r.chrono, View.VISIBLE)
                        views.setChronometerCountDown(r.chrono, true)
                        views.setChronometer(r.chrono, SystemClock.elapsedRealtime() + e.remainingMs, null, true)
                        views.setTextColor(r.chrono, look.accent)
                    }
                } else {
                    views.setViewVisibility(r.row, View.GONE)
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, EventsConfigActivity::class.java)
                )
            }
            return views
        }
    }
}

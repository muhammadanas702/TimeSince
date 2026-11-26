package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
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
    EventRow(R.id.e4_row, R.id.e4_name, R.id.e4_date, R.id.e4_days, R.id.e4_chrono),
    EventRow(R.id.e5_row, R.id.e5_name, R.id.e5_date, R.id.e5_days, R.id.e5_chrono),
    EventRow(R.id.e6_row, R.id.e6_name, R.id.e6_date, R.id.e6_days, R.id.e6_chrono)
)

/**
 * Event countdown: any number of named dates (each with an optional time) in one widget, each with the time left,
 * under an optional heading (the name of the widget). The widget shows 3 to 6 events at a time; with more, arrows
 * in the heading flip between the pages. Events without a time only change at midnight. Events with a time show whole hours until a day is left,
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
            ACTION_PREV, ACTION_NEXT -> flip(context, intent)
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    /** The arrows of the heading: the next or the previous page, round and round. */
    private fun flip(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || ExtraStore.isRemoved(context, id)) return
        val cfg = ExtraStore.readEvents(context, id)
        val pages = pageCount(cfg.events.size, cfg.perPage)
        val current = ExtraStore.eventsPage(context, id).coerceIn(0, pages - 1)
        val step = if (intent.action == ACTION_NEXT) 1 else -1
        ExtraStore.setEventsPage(context, id, (current + step + pages) % pages)
        AppWidgetManager.getInstance(context).updateAppWidget(id, build(context, id, cfg, false))
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
        const val ACTION_PREV = "com.timesince.app.EVENTS_PREV"
        const val ACTION_NEXT = "com.timesince.app.EVENTS_NEXT"

        /** The most events a widget can hold. */
        const val MAX_EVENTS = 30
        const val MIN_PER_PAGE = 3
        const val MAX_PER_PAGE = 6

        /** How many pages [total] events make when [perPage] fit on one (always at least one). */
        fun pageCount(total: Int, perPage: Int): Int {
            val size = perPage.coerceAtLeast(1)
            return maxOf(1, (total + size - 1) / size)
        }

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
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, EventsConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readEvents(context, id), false)
                    }
                )
            }
            DailyTick.schedule(context, EventsWidget::class.java, ACTION_TICK)
        }

        /**
         * Also used by the settings screen with preview = true; there [previewPage] is the page the preview
         * shows (the arrows of the preview change it). On the home screen the saved page is shown.
         */
        fun build(
            context: Context,
            id: Int,
            cfg: EventsCfg,
            preview: Boolean,
            previewPage: Int = 0
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_events)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val now = LocalDateTime.now()
            val ordered = EventMath.order(cfg.events, now)
            val perPage = cfg.perPage.coerceIn(MIN_PER_PAGE, MAX_PER_PAGE)
            val pages = pageCount(ordered.size, perPage)
            val page = (if (preview) previewPage else ExtraStore.eventsPage(context, id)).coerceIn(0, pages - 1)
            val items = ordered.drop(page * perPage).take(perPage)
            val paged = pages > 1

            // The heading holds the name of the widget and, with more than one page, the arrows.
            val title = cfg.title.trim()
            views.setViewVisibility(R.id.e_head, if (title.isEmpty() && !paged) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.e_title, if (title.isEmpty()) View.INVISIBLE else View.VISIBLE)
            views.setTextViewText(R.id.e_title, title)
            views.setTextColor(R.id.e_title, look.accent)
            val pagerVisibility = if (paged) View.VISIBLE else View.GONE
            views.setViewVisibility(R.id.e_prev, pagerVisibility)
            views.setViewVisibility(R.id.e_page, pagerVisibility)
            views.setViewVisibility(R.id.e_next, pagerVisibility)
            views.setTextViewText(R.id.e_page, "${page + 1}/$pages")
            views.setTextColor(R.id.e_page, muted)
            views.setTextColor(R.id.e_prev, look.accent)
            views.setTextColor(R.id.e_next, look.accent)

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
                views.setOnClickPendingIntent(R.id.e_prev, nav(context, id, ACTION_PREV, 1))
                views.setOnClickPendingIntent(R.id.e_next, nav(context, id, ACTION_NEXT, 2))
            }
            return views
        }

        /** Each widget and each arrow gets its own request code, so the intents stay separate. */
        private fun nav(context: Context, id: Int, action: String, kind: Int): PendingIntent {
            val intent = Intent(context, EventsWidget::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context, id * 4 + kind, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

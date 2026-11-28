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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private class TtRow(val row: Int, val time: Int, val title: Int)

private val TT_ROWS = listOf(
    TtRow(R.id.t1_row, R.id.t1_time, R.id.t1_title),
    TtRow(R.id.t2_row, R.id.t2_time, R.id.t2_title),
    TtRow(R.id.t3_row, R.id.t3_time, R.id.t3_title),
    TtRow(R.id.t4_row, R.id.t4_time, R.id.t4_title),
    TtRow(R.id.t5_row, R.id.t5_time, R.id.t5_title),
    TtRow(R.id.t6_row, R.id.t6_time, R.id.t6_title),
    TtRow(R.id.t7_row, R.id.t7_time, R.id.t7_title)
)

/** The day a Class Timetable widget shows: its heading, its date, its classes and whether "now" applies to it. */
private class TimetableDay(val title: String, val date: String, val items: List<ClassItem>, val live: Boolean)

/**
 * Class Timetable: the classes of today with their start times. The class that is on now is shown in the accent
 * color and finished ones are faded. When the last class of the day is over, it shows tomorrow's classes. A quiet
 * alarm that does not wake the phone redraws the widget every 15 minutes (and at boot and when the clock changes).
 */
class TimetableWidget : AppWidgetProvider() {

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
        const val ACTION_TICK = "com.timesince.app.TIMETABLE_TICK"
        private const val EVERY_MS = 15L * 60L * 1000L
        private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimetableWidget::class.java))
            if (ids.isEmpty()) {
                cancelTick(context)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, TimetableConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readTimetable(context, id), false)
                    }
                )
            }
            scheduleTick(context)
        }

        private fun itemsOf(cfg: TimetableCfg, date: LocalDate): List<ClassItem> =
            TimetableMath.parseDay(cfg.days.getOrElse(date.dayOfWeek.value - 1) { "" })

        /**
         * Today, or tomorrow once today's classes are over (and tomorrow has some). With [previewWeekday]
         * (1 = Monday ... 7 = Sunday) the settings screen shows the day being edited instead.
         */
        private fun dayToShow(cfg: TimetableCfg, now: LocalDateTime, previewWeekday: Int): TimetableDay {
            val today = now.toLocalDate()
            if (previewWeekday in 1..7) {
                val name = DayOfWeek.of(previewWeekday).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val items = TimetableMath.parseDay(cfg.days.getOrElse(previewWeekday - 1) { "" })
                return TimetableDay(name.uppercase(Locale.ENGLISH), "", items, previewWeekday == today.dayOfWeek.value)
            }
            val nowMinutes = now.hour * 60 + now.minute
            val todays = itemsOf(cfg, today)
            if (todays.all { it.effectiveEnd <= nowMinutes }) {
                val tomorrow = today.plusDays(1)
                val next = itemsOf(cfg, tomorrow)
                if (next.isNotEmpty()) return TimetableDay("TOMORROW", DATE_FMT.format(tomorrow), next, false)
            }
            return TimetableDay("TODAY", DATE_FMT.format(today), todays, true)
        }

        /** The text for the app's list: the class on now or the next one, or how many classes there are. */
        fun summary(cfg: TimetableCfg): String {
            val now = LocalDateTime.now()
            val day = dayToShow(cfg, now, 0)
            if (day.items.isEmpty()) return "No classes added for today. Tap to add."
            val minutes = now.hour * 60 + now.minute
            val current = day.items.firstOrNull { day.live && minutes >= it.start && minutes < it.effectiveEnd }
            val upcoming = day.items.firstOrNull { !day.live || it.start > minutes }
            val pick = current ?: upcoming ?: day.items.last()
            val lead = if (current != null) "Now: " else if (day.title == "TOMORROW") "Tomorrow: " else "Next: "
            return lead + TimetableMath.timeText(pick.start) + " " + pick.title
        }

        /** Also used by the settings screen with preview = true; [previewWeekday] is the day being edited there. */
        fun build(
            context: Context,
            id: Int,
            cfg: TimetableCfg,
            preview: Boolean,
            previewWeekday: Int = 0
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val now = LocalDateTime.now()
            val minutes = now.hour * 60 + now.minute
            val day = dayToShow(cfg, now, previewWeekday)

            views.setTextViewText(R.id.t_title, day.title)
            views.setTextColor(R.id.t_title, look.accent)
            views.setTextViewText(R.id.t_date, day.date)
            views.setTextColor(R.id.t_date, muted)

            TT_ROWS.forEachIndexed { i, r ->
                if (i < day.items.size) {
                    val c = day.items[i]
                    val current = day.live && minutes >= c.start && minutes < c.effectiveEnd
                    val past = day.live && minutes >= c.effectiveEnd
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setTextViewText(r.time, TimetableMath.timeText(c.start))
                    views.setTextColor(r.time, if (current) look.accent else muted)
                    views.setTextViewText(r.title, if (current) "● " + c.title else c.title)
                    views.setTextColor(r.title, if (current) look.accent else if (past) muted else look.fg)
                } else if (day.items.isEmpty() && i == 0) {
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setTextViewText(r.time, "")
                    views.setTextViewText(r.title, "No classes. Tap to add.")
                    views.setTextColor(r.title, muted)
                } else {
                    views.setViewVisibility(r.row, View.GONE)
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, TimetableConfigActivity::class.java)
                )
            }
            return views
        }

        private fun tickIntent(context: Context): PendingIntent {
            val intent = Intent(context, TimetableWidget::class.java).setAction(ACTION_TICK)
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

package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Month calendar. Today is circled in the accent color. The arrows on the widget move to the
 * previous or next month, tapping the month name returns to the current one, and tapping anywhere
 * else opens the settings. The highlight moves on at midnight through one alarm.
 */
class CalendarWidget : AppWidgetProvider() {

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
            ACTION_PREV, ACTION_NEXT, ACTION_RESET -> {
                val id = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val current = ExtraStore.readCalendar(context, id).offset
                    val next = when (intent.action) {
                        ACTION_PREV -> current - 1
                        ACTION_NEXT -> current + 1
                        else -> 0
                    }
                    ExtraStore.setCalendarOffset(context, id, next.coerceIn(-1200, 1200))
                    AppWidgetManager.getInstance(context)
                        .updateAppWidget(id, build(context, id, ExtraStore.readCalendar(context, id), false))
                }
            }
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        DailyTick.cancel(context, CalendarWidget::class.java, ACTION_TICK)
    }

    companion object {
        const val ACTION_TICK = "com.timesince.app.CALENDAR_TICK"
        const val ACTION_PREV = "com.timesince.app.CALENDAR_PREV"
        const val ACTION_NEXT = "com.timesince.app.CALENDAR_NEXT"
        const val ACTION_RESET = "com.timesince.app.CALENDAR_RESET"

        private val TITLE_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CalendarWidget::class.java))
            if (ids.isEmpty()) {
                DailyTick.cancel(context, CalendarWidget::class.java, ACTION_TICK)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(id, build(context, id, ExtraStore.readCalendar(context, id), false))
            }
            DailyTick.schedule(context, CalendarWidget::class.java, ACTION_TICK)
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: CalendarCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_calendar)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val opaque = 0xFF shl 24
            val muted = LookUtil.muted(look.fg)
            val dim = (look.fg and 0x00FFFFFF) or ((0.3f * 255f).toInt() shl 24)

            val today = LocalDate.now()
            val month = YearMonth.from(today).plusMonths(cfg.offset.toLong())
            val cells = CalendarMath.grid(month, today, cfg.mondayFirst)
            val rows = CalendarMath.rowCount(month, cfg.mondayFirst)

            views.setTextViewText(R.id.cal_title, TITLE_FMT.format(month))
            views.setTextColor(R.id.cal_title, look.fg)
            views.setTextColor(R.id.cal_prev, look.accent)
            views.setTextColor(R.id.cal_next, look.accent)

            val names = CalendarMath.weekdayNames(cfg.mondayFirst)
            for (i in 0 until 7) {
                views.setTextViewText(CalendarIds.WEEK[i], names[i])
                views.setTextColor(CalendarIds.WEEK[i], muted)
            }

            for (r in 0 until 6) {
                views.setViewVisibility(CalendarIds.ROWS[r], if (r < rows) View.VISIBLE else View.GONE)
            }
            for (i in 0 until rows * 7) {
                val cell = cells[i]
                views.setTextViewText(CalendarIds.DAY[i], cell.day.toString())
                if (cell.isToday) {
                    // Dark-on-bright or bright-on-dark: the widget's own background color reads well on the accent.
                    views.setTextColor(CalendarIds.DAY[i], look.bg or opaque)
                    views.setInt(CalendarIds.MARK[i], "setColorFilter", look.accent or opaque)
                    views.setViewVisibility(CalendarIds.MARK[i], View.VISIBLE)
                } else {
                    views.setTextColor(CalendarIds.DAY[i], if (cell.inMonth) look.fg else dim)
                    views.setViewVisibility(CalendarIds.MARK[i], View.GONE)
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, CalendarConfigActivity::class.java)
                )
                views.setOnClickPendingIntent(R.id.cal_prev, nav(context, id, ACTION_PREV, 1))
                views.setOnClickPendingIntent(R.id.cal_next, nav(context, id, ACTION_NEXT, 2))
                views.setOnClickPendingIntent(R.id.cal_title, nav(context, id, ACTION_RESET, 3))
            }
            return views
        }

        /** Each widget and each button gets its own request code, so the three intents stay separate. */
        private fun nav(context: Context, id: Int, action: String, kind: Int): PendingIntent {
            val intent = Intent(context, CalendarWidget::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context, id * 4 + kind, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

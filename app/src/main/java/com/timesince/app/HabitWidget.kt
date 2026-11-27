package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private class HabitColumn(val dot: Int, val day: Int)

private val HABIT_COLUMNS = listOf(
    HabitColumn(R.id.h1_dot, R.id.h1_day),
    HabitColumn(R.id.h2_dot, R.id.h2_day),
    HabitColumn(R.id.h3_dot, R.id.h3_day),
    HabitColumn(R.id.h4_dot, R.id.h4_day),
    HabitColumn(R.id.h5_dot, R.id.h5_day),
    HabitColumn(R.id.h6_dot, R.id.h6_day),
    HabitColumn(R.id.h7_dot, R.id.h7_day)
)

/**
 * Habit Streak: one habit, a big circle to tick today (tap again to take it back), the streak, and the last
 * seven days. Ticking redraws only this widget. A quiet alarm at midnight moves the days along.
 */
class HabitWidget : AppWidgetProvider() {

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
            ACTION_TOGGLE -> toggle(context, intent)
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    /** A tap on the big circle: tick today, or take the tick back. */
    private fun toggle(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || ExtraStore.isRemoved(context, id)) return
        ExtraStore.toggleHabitToday(context, id, LocalDate.now().toEpochDay())
        AppWidgetManager.getInstance(context)
            .updateAppWidget(id, build(context, id, ExtraStore.readHabit(context, id), false))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        DailyTick.cancel(context, HabitWidget::class.java, ACTION_TICK)
    }

    companion object {
        const val ACTION_TOGGLE = "com.timesince.app.HABIT_TOGGLE"
        const val ACTION_TICK = "com.timesince.app.HABIT_TICK"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitWidget::class.java))
            if (ids.isEmpty()) {
                DailyTick.cancel(context, HabitWidget::class.java, ACTION_TICK)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, HabitConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readHabit(context, id), false)
                    }
                )
            }
            DailyTick.schedule(context, HabitWidget::class.java, ACTION_TICK)
        }

        /** "5 day streak" or "Not started", for the app's list. */
        fun summary(cfg: HabitCfg): String {
            val today = LocalDate.now().toEpochDay()
            val streak = HabitMath.streak(cfg.days, today)
            val done = if (today in cfg.days) " · done today" else ""
            return (if (streak == 0) "Not started" else "$streak day streak") + done
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: HabitCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_habit)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val accent = look.accent or (0xFF shl 24)
            val ink = look.fg or (0xFF shl 24)
            val today = LocalDate.now().toEpochDay()
            val doneToday = today in cfg.days
            val streak = HabitMath.streak(cfg.days, today)

            views.setTextViewText(R.id.h_name, cfg.name.trim().ifEmpty { "My habit" })
            views.setTextColor(R.id.h_name, look.fg)
            views.setTextViewText(
                R.id.h_streak,
                when {
                    streak == 0 -> "Tap the circle to start"
                    doneToday -> "🔥 $streak day streak"
                    else -> "🔥 $streak day streak · keep it going"
                }
            )
            views.setTextColor(R.id.h_streak, look.accent)

            // The big circle: empty until today is ticked, then filled with a tick.
            views.setImageViewResource(R.id.h_check, if (doneToday) R.drawable.ic_check_on else R.drawable.ic_check_off)
            views.setInt(R.id.h_check, "setColorFilter", accent)
            views.setInt(R.id.h_check, "setImageAlpha", 255)

            // The last seven days, oldest first, today last.
            HABIT_COLUMNS.forEachIndexed { i, c ->
                val day = today - (HABIT_COLUMNS.size - 1 - i)
                val done = day in cfg.days
                val isToday = i == HABIT_COLUMNS.size - 1
                views.setImageViewResource(c.dot, if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
                views.setInt(c.dot, "setColorFilter", if (done || isToday) accent else ink)
                views.setInt(c.dot, "setImageAlpha", if (done || isToday) 255 else 110)
                views.setTextViewText(
                    c.day,
                    LocalDate.ofEpochDay(day).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.ENGLISH)
                )
                views.setTextColor(c.day, if (isToday) look.accent else muted)
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, HabitConfigActivity::class.java)
                )
                views.setOnClickPendingIntent(R.id.h_check, tickIntent(context, id))
            }
            return views
        }

        private fun tickIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, HabitWidget::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context, id * 4 + 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

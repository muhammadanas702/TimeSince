package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

private class AttRow(val row: Int, val name: Int, val hint: Int, val pct: Int, val yes: Int, val no: Int)

private val ATT_ROWS = listOf(
    AttRow(R.id.a1_row, R.id.a1_name, R.id.a1_hint, R.id.a1_pct, R.id.a1_yes, R.id.a1_no),
    AttRow(R.id.a2_row, R.id.a2_name, R.id.a2_hint, R.id.a2_pct, R.id.a2_yes, R.id.a2_no),
    AttRow(R.id.a3_row, R.id.a3_name, R.id.a3_hint, R.id.a3_pct, R.id.a3_yes, R.id.a3_no),
    AttRow(R.id.a4_row, R.id.a4_name, R.id.a4_hint, R.id.a4_pct, R.id.a4_yes, R.id.a4_no),
    AttRow(R.id.a5_row, R.id.a5_name, R.id.a5_hint, R.id.a5_pct, R.id.a5_yes, R.id.a5_no),
    AttRow(R.id.a6_row, R.id.a6_name, R.id.a6_hint, R.id.a6_pct, R.id.a6_yes, R.id.a6_no)
)

/**
 * Attendance Tracker: for each subject, the percent attended, a hint (how many classes can still be missed, or
 * how many in a row must be attended to reach the goal) and two buttons: a tick when a class was attended and a
 * cross when it was missed. Tapping redraws only this widget; it needs no alarms.
 */
class AttendanceWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MARK) mark(context, intent)
    }

    /** One tap on a tick or a cross: a class was held. */
    private fun mark(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || index < 0 || ExtraStore.isRemoved(context, id)) return
        ExtraStore.markAttendance(context, id, index, intent.getBooleanExtra(EXTRA_PRESENT, true))
        AppWidgetManager.getInstance(context)
            .updateAppWidget(id, build(context, id, ExtraStore.readAttendance(context, id), false))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    companion object {
        const val ACTION_MARK = "com.timesince.app.ATTENDANCE_MARK"
        private const val EXTRA_INDEX = "subject_index"
        private const val EXTRA_PRESENT = "present"

        /** Under the goal: a soft red that reads well on light and on dark backgrounds. */
        private val LOW_COLOR = 0xFFE5484D.toInt()

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AttendanceWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, AttendanceConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readAttendance(context, id), false)
                    }
                )
            }
        }

        /** The text for the app's list: the lowest subject, or "no subjects yet". */
        fun summary(cfg: AttendanceCfg): String {
            val counted = cfg.subjects.filter { it.total > 0 }
            if (cfg.subjects.isEmpty()) return "No subjects yet. Tap to add."
            if (counted.isEmpty()) return cfg.subjects.size.toString() + " subjects, no classes yet"
            val lowest = counted.minByOrNull { AttendanceMath.percent(it.present, it.total) }!!
            val pct = AttendanceMath.percent(lowest.present, lowest.total)
            return "Lowest: " + lowest.name + " " + pct + "% (goal " + cfg.target + "%)"
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: AttendanceCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_attendance)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val accent = look.accent or (0xFF shl 24)
            val ink = look.fg or (0xFF shl 24)
            views.setTextColor(R.id.a_title, look.accent)
            views.setTextViewText(R.id.a_goal, "Goal " + cfg.target + "%")
            views.setTextColor(R.id.a_goal, muted)

            ATT_ROWS.forEachIndexed { i, r ->
                if (i < cfg.subjects.size) {
                    val s = cfg.subjects[i]
                    val pct = AttendanceMath.percent(s.present, s.total)
                    val low = pct in 0 until cfg.target
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setViewVisibility(r.yes, View.VISIBLE)
                    views.setViewVisibility(r.no, View.VISIBLE)
                    views.setTextViewText(r.name, s.name)
                    views.setTextColor(r.name, look.fg)
                    views.setTextViewText(r.pct, if (pct < 0) "-" else "$pct%")
                    views.setTextColor(r.pct, if (pct < 0) muted else if (low) LOW_COLOR else look.accent)
                    val counts = s.present.toString() + "/" + s.total
                    views.setTextViewText(
                        r.hint,
                        when {
                            s.total == 0 -> "No classes yet"
                            low -> "Attend next " + AttendanceMath.needed(s.present, s.total, cfg.target) + " · " + counts
                            AttendanceMath.canMiss(s.present, s.total, cfg.target) == 0 -> "Do not miss one · $counts"
                            else -> "Can miss " + AttendanceMath.canMiss(s.present, s.total, cfg.target) + " · " + counts
                        }
                    )
                    views.setTextColor(r.hint, muted)
                    views.setInt(r.yes, "setColorFilter", accent)
                    views.setInt(r.no, "setColorFilter", ink)
                    views.setInt(r.no, "setImageAlpha", 140)
                    if (!preview) {
                        views.setOnClickPendingIntent(r.yes, markIntent(context, id, i, true))
                        views.setOnClickPendingIntent(r.no, markIntent(context, id, i, false))
                    }
                } else if (cfg.subjects.isEmpty() && i == 0) {
                    views.setViewVisibility(r.row, View.VISIBLE)
                    views.setViewVisibility(r.yes, View.GONE)
                    views.setViewVisibility(r.no, View.GONE)
                    views.setTextViewText(r.name, "Tap to add your subjects")
                    views.setTextColor(r.name, muted)
                    views.setTextViewText(r.hint, "")
                    views.setTextViewText(r.pct, "")
                } else {
                    views.setViewVisibility(r.row, View.GONE)
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, AttendanceConfigActivity::class.java)
                )
            }
            return views
        }

        /** Each widget, subject and button gets its own request code, so the twelve intents stay separate. */
        private fun markIntent(context: Context, id: Int, index: Int, present: Boolean): PendingIntent {
            val intent = Intent(context, AttendanceWidget::class.java)
                .setAction(ACTION_MARK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_PRESENT, present)
            return PendingIntent.getBroadcast(
                context, id * 16 + index * 2 + (if (present) 0 else 1), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.TimeZone

private class Row(val row: Int, val name: Int, val day: Int, val time: Int)

private val ROWS = listOf(
    Row(R.id.r1_row, R.id.r1_name, R.id.r1_day, R.id.r1_time),
    Row(R.id.r2_row, R.id.r2_name, R.id.r2_day, R.id.r2_time),
    Row(R.id.r3_row, R.id.r3_name, R.id.r3_day, R.id.r3_time)
)

/**
 * Two or three world clocks in one widget. Each time is a TextClock set to its own time zone,
 * so the launcher keeps every row ticking by itself.
 */
class WorldClockWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // "Local time" rows follow the phone's zone, so redraw when it changes.
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WorldClockWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, build(context, id, ExtraStore.readWorld(context, id), false))
            }
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: WorldCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_world)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val timePattern = if (cfg.h24) "HH:mm" else "h:mm a"
            val phoneZone = TimeZone.getDefault().id

            ROWS.forEachIndexed { i, r ->
                if (i < cfg.zones.size) {
                    val zone = cfg.zones[i]
                    val tz = if (zone.id.isEmpty()) phoneZone else zone.id
                    views.setViewVisibility(r.row, View.VISIBLE)

                    views.setTextViewText(r.name, zone.name)
                    views.setTextColor(r.name, look.fg)

                    views.setString(r.day, "setTimeZone", tz)
                    views.setCharSequence(r.day, "setFormat12Hour", "EEE")
                    views.setCharSequence(r.day, "setFormat24Hour", "EEE")
                    views.setTextColor(r.day, muted)

                    views.setString(r.time, "setTimeZone", tz)
                    views.setCharSequence(r.time, "setFormat12Hour", timePattern)
                    views.setCharSequence(r.time, "setFormat24Hour", timePattern)
                    views.setTextColor(r.time, look.accent)
                } else {
                    views.setViewVisibility(r.row, View.GONE)
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, WorldClockConfigActivity::class.java)
                )
            }
            return views
        }
    }
}

package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/**
 * Big clock with the date. The time is a TextClock, which the launcher keeps ticking by itself,
 * so this widget needs no alarms and no background work.
 */
class ClockWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(id, build(context, id, ExtraStore.readClock(context, id), false))
            }
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: ClockCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            // A TextClock picks 12 or 24 hour from the phone's setting, so both
            // formats get the same pattern and the widget's own choice always wins.
            val pattern = (if (cfg.h24) "HH:mm" else "h:mm") + (if (cfg.seconds) ":ss" else "")
            views.setCharSequence(R.id.c_time, "setFormat12Hour", pattern)
            views.setCharSequence(R.id.c_time, "setFormat24Hour", pattern)
            views.setTextColor(R.id.c_time, look.fg)
            views.setTextViewTextSize(
                R.id.c_time, TypedValue.COMPLEX_UNIT_SP, if (cfg.seconds) 42f else 54f
            )

            views.setCharSequence(R.id.c_ampm, "setFormat12Hour", "a")
            views.setCharSequence(R.id.c_ampm, "setFormat24Hour", "a")
            views.setTextColor(R.id.c_ampm, look.accent)
            views.setViewVisibility(R.id.c_ampm, if (cfg.h24) View.GONE else View.VISIBLE)

            views.setTextColor(R.id.c_date, LookUtil.muted(look.fg))

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, ClockConfigActivity::class.java)
                )
            }
            return views
        }
    }
}

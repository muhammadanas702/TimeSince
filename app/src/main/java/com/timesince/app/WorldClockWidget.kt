package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.util.TimeZone

private class WorldRow(val row: Int, val bg: Int, val name: Int, val time: Int)

private val WORLD_ROWS = listOf(
    WorldRow(R.id.r1_row, R.id.r1_bg, R.id.r1_name, R.id.r1_time),
    WorldRow(R.id.r2_row, R.id.r2_bg, R.id.r2_name, R.id.r2_time),
    WorldRow(R.id.r3_row, R.id.r3_bg, R.id.r3_name, R.id.r3_time),
    WorldRow(R.id.r4_row, R.id.r4_bg, R.id.r4_name, R.id.r4_time),
    WorldRow(R.id.r5_row, R.id.r5_bg, R.id.r5_name, R.id.r5_time),
    WorldRow(R.id.r6_row, R.id.r6_bg, R.id.r6_name, R.id.r6_time)
)

/**
 * World clock in a frosted-glass design: the phone's own time in a colored pill, the date, and one to
 * six city pills. Every time is a TextClock, so the launcher keeps them ticking by itself.
 */
class WorldClockWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    /** Resizing the widget changes how much room each row has, so the text sizes are worked out again. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            build(context, appWidgetId, ExtraStore.readWorld(context, appWidgetId), false)
        )
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

        /** The height the launcher gives this widget right now, in dp. */
        private fun widgetHeightDp(context: Context, id: Int): Float {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val key = if (landscape) {
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            } else {
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
            }
            val h = options.getInt(key, 0)
            return if (h > 0) h.toFloat() else 138f
        }

        /**
         * Also used by the settings screen with preview = true, which passes the height of its
         * preview box as [heightDp]. The widget asks the launcher for its own height.
         */
        fun build(
            context: Context,
            id: Int,
            cfg: WorldCfg,
            preview: Boolean,
            heightDp: Int = 0
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_world)
            val look = cfg.look
            val opaque = 0xFF shl 24

            // Frosted container: the background color, faded by the transparency, plus a thin outline.
            LookUtil.applyBackground(views, look)
            views.setInt(R.id.w_border, "setColorFilter", look.fg or opaque)

            // The text shrinks when many cities share a small widget, and grows back when it is resized.
            val count = cfg.zones.size.coerceIn(1, WorldCfg.MAX_CITIES)
            val height = if (heightDp > 0) heightDp.toFloat() else widgetHeightDp(context, id)
            val rowHeight = (height - 20f - 30f - 4f * count) / (count + 1)
            val rowText = (rowHeight * 0.49f).coerceIn(8f, 13.5f)
            val clockText = (rowHeight * 0.68f).coerceIn(10f, 18f)

            // The phone's own time in the colored pill. A TextClock picks 12 or 24 hour from the
            // phone's setting, so both formats get the same pattern and the widget's choice wins.
            val pill = cfg.pillOpacity.coerceIn(0, 100)
            views.setInt(R.id.p_bg, "setColorFilter", look.accent or opaque)
            views.setInt(R.id.p_bg, "setImageAlpha", 255 * pill / 100)
            val clock = if (cfg.h24) "HH : mm" else "hh : mm"
            views.setCharSequence(R.id.wc_time, "setFormat12Hour", clock)
            views.setCharSequence(R.id.wc_time, "setFormat24Hour", clock)
            views.setTextColor(R.id.wc_time, LookUtil.onColor(look.accent))
            views.setTextViewTextSize(R.id.wc_time, TypedValue.COMPLEX_UNIT_SP, clockText)

            // Date line, written like the sample: Mon, 2026, Oct 26,PM
            val date = if (cfg.h24) "EEE, yyyy, MMM d" else "EEE, yyyy, MMM d,a"
            views.setCharSequence(R.id.wc_date, "setFormat12Hour", date)
            views.setCharSequence(R.id.wc_date, "setFormat24Hour", date)
            views.setTextColor(R.id.wc_date, LookUtil.muted(look.fg))

            // City pills: dark on bright text colors, bright on dark ones.
            val light = LookUtil.isLight(look.fg)
            val pillBase = (if (light) 0x000000 else 0xFFFFFF) or opaque
            val pillAlpha = (if (light) 115 else 140) * pill / 100
            val timePattern = if (cfg.h24) "HH:mm" else "hh:mm a"
            val phoneZone = TimeZone.getDefault().id

            WORLD_ROWS.forEachIndexed { i, r ->
                if (i < cfg.zones.size) {
                    val zone = cfg.zones[i]
                    val tz = if (zone.id.isEmpty()) phoneZone else zone.id
                    views.setViewVisibility(r.row, View.VISIBLE)

                    views.setInt(r.bg, "setColorFilter", pillBase)
                    views.setInt(r.bg, "setImageAlpha", pillAlpha)

                    views.setTextViewText(r.name, if (cfg.shortNames) Cities.shortName(zone) else zone.name)
                    views.setTextColor(r.name, look.fg)
                    views.setTextViewTextSize(r.name, TypedValue.COMPLEX_UNIT_SP, rowText)

                    views.setString(r.time, "setTimeZone", tz)
                    views.setCharSequence(r.time, "setFormat12Hour", timePattern)
                    views.setCharSequence(r.time, "setFormat24Hour", timePattern)
                    views.setTextColor(r.time, look.fg)
                    views.setTextViewTextSize(r.time, TypedValue.COMPLEX_UNIT_SP, rowText)
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

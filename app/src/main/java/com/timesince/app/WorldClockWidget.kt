package com.timesince.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * Text sizes by number of cities (1 to 6), in dp and not in sp: the reference widget does not follow the
 * phone's font-size setting, and a phone set to "small" text made this widget's text about 15% smaller.
 * Two cities match the reference measured on screen: clock 23.5, date 17, city text 13.5.
 * More cities share the same height, so the text gets a little smaller.
 */
private val WORLD_CLOCK_DP = floatArrayOf(23.5f, 23.5f, 22f, 20f, 19f, 18f)
private val WORLD_DATE_DP = floatArrayOf(17f, 17f, 16f, 15f, 14.5f, 14f)
private val WORLD_ROW_DP = floatArrayOf(13.5f, 13.5f, 13f, 12.5f, 12f, 11.5f)

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

        /** Wraps a name in quotes so a time format shows it as plain text (an apostrophe is written twice). */
        private fun literal(name: String): String {
            val text = name.ifBlank { " " }
            return "'" + text.replace("'", "''") + "'"
        }

        /** Also used by the settings screen with preview = true. */
        fun build(
            context: Context,
            id: Int,
            cfg: WorldCfg,
            preview: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_world)
            val look = cfg.look
            val opaque = 0xFF shl 24

            // Frosted container: the background color, faded by the transparency, plus a thin outline.
            LookUtil.applyBackground(views, look)
            views.setInt(R.id.w_border, "setColorFilter", look.fg or opaque)

            // Text sizes come from the number of cities and the user's Text size choice. They do not
            // depend on the size the launcher reports, because launchers report it differently.
            val count = cfg.zones.size.coerceIn(1, WorldCfg.MAX_CITIES)
            val scale = cfg.textScale.coerceIn(70, 130) / 100f
            val rowText = WORLD_ROW_DP[count - 1] * scale
            val clockText = WORLD_CLOCK_DP[count - 1] * scale
            val dateText = WORLD_DATE_DP[count - 1] * scale

            // The phone's own time in the colored pill. A TextClock picks 12 or 24 hour from the
            // phone's setting, so both formats get the same pattern and the widget's choice wins.
            // The colored pill is solid, like the reference; the slider only changes the city pills.
            val pill = cfg.pillOpacity.coerceIn(0, 100)
            views.setInt(R.id.p_bg, "setColorFilter", look.accent or opaque)
            views.setInt(R.id.p_bg, "setImageAlpha", 255)
            val clock = if (cfg.h24) "HH : mm" else "hh : mm"
            views.setCharSequence(R.id.wc_time, "setFormat12Hour", clock)
            views.setCharSequence(R.id.wc_time, "setFormat24Hour", clock)
            views.setTextColor(
                R.id.wc_time,
                if (LookUtil.isLight(look.accent)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            views.setTextViewTextSize(R.id.wc_time, TypedValue.COMPLEX_UNIT_DIP, clockText)

            // Date line, written like the sample: Mon, 2026, Oct 26,PM
            val date = if (cfg.h24) "EEE, yyyy, MMM d" else "EEE, yyyy, MMM d,a"
            views.setCharSequence(R.id.wc_date, "setFormat12Hour", date)
            views.setCharSequence(R.id.wc_date, "setFormat24Hour", date)
            views.setTextColor(R.id.wc_date, (look.fg and 0x00FFFFFF) or (0x9E shl 24))
            views.setTextViewTextSize(R.id.wc_date, TypedValue.COMPLEX_UNIT_DIP, dateText)

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

                    // The name is a TextClock with a fixed-text format, so it is drawn exactly like the time.
                    val label = literal(if (cfg.shortNames) Cities.shortName(zone) else zone.name)
                    views.setCharSequence(r.name, "setFormat12Hour", label)
                    views.setCharSequence(r.name, "setFormat24Hour", label)
                    views.setTextColor(r.name, look.fg)
                    views.setTextViewTextSize(r.name, TypedValue.COMPLEX_UNIT_DIP, rowText)

                    views.setString(r.time, "setTimeZone", tz)
                    views.setCharSequence(r.time, "setFormat12Hour", timePattern)
                    views.setCharSequence(r.time, "setFormat24Hour", timePattern)
                    views.setTextColor(r.time, look.fg)
                    views.setTextViewTextSize(r.time, TypedValue.COMPLEX_UNIT_DIP, rowText)
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

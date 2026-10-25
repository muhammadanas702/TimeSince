package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Colors and transparency shared by the clock-style widgets. Colors are opaque ARGB ints. */
data class Look(
    val theme: Int = 0,
    val bg: Int = Themes.PRESETS[0].bg,
    val fg: Int = Themes.PRESETS[0].fg,
    val accent: Int = Themes.PRESETS[0].accent,
    val opacity: Int = 100
)

object LookUtil {

    /** Tints the white rounded shape (view id w_bg) with the background color and fades it. */
    fun applyBackground(views: RemoteViews, look: Look) {
        views.setInt(R.id.w_bg, "setColorFilter", look.bg or (0xFF shl 24))
        views.setInt(R.id.w_bg, "setImageAlpha", look.opacity * 255 / 100)
    }

    fun muted(color: Int): Int = (color and 0x00FFFFFF) or ((0.7f * 255f).toInt() shl 24)

    /** Tapping a widget opens its settings screen. */
    fun openSettings(context: Context, id: Int, target: Class<*>): PendingIntent {
        val open = Intent(context, target)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, id, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

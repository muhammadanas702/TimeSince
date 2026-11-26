package com.timesince.app

import android.content.Context
import android.widget.RemoteViews

/**
 * What a widget shows after the user pressed Delete in the app.
 *
 * Android does not let an app take a widget off the home screen; only the home screen (launcher) can,
 * when the user presses and holds the widget and taps Remove. So Delete erases the settings and the alerts
 * of the widget, hides it from the app's list, and turns it into this small tile. Tapping the tile opens the
 * settings again, and saving them brings the widget back.
 */
object RemovedWidget {
    fun build(context: Context, id: Int, settings: Class<*>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_removed)
        views.setOnClickPendingIntent(R.id.w_root, LookUtil.openSettings(context, id, settings))
        return views
    }
}

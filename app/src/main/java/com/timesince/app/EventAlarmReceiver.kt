package com.timesince.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wakes up when the alarm of [EventAlerts] goes off. */
class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EventAlerts.ACTION_FIRE) return
        // Sends what is due, sets the next alarm, and redraws the widgets (their text may have changed too).
        // The Events widgets do the sending; Time Since widgets are only redrawn, so a countdown that just
        // ended switches from "until" to "since" at once.
        EventsWidget.updateAll(context)
        TimeSinceWidget.updateAll(context, alerts = false)
    }
}

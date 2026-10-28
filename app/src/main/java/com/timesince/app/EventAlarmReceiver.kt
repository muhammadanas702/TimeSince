package com.timesince.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wakes up when the alarm of [EventAlerts] goes off. */
class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Sends what is due, sets the next alarm, and redraws the widgets (their text may have changed too).
        if (intent.action == EventAlerts.ACTION_FIRE) EventsWidget.updateAll(context)
    }
}

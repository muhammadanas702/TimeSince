package com.timesince.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Wakes up when the alarm of [EventAlerts] goes off. */
class EventAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == EventAlerts.ACTION_FIRE) EventAlerts.process(context)
    }
}

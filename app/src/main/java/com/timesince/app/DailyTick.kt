package com.timesince.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm per widget type that fires just after midnight, so widgets that show dates
 * (days left, today's highlight) move on to the new day. A non-waking, inexact alarm is enough:
 * if the phone is asleep nobody is looking at the widget, and it redraws when the phone wakes.
 */
object DailyTick {

    fun schedule(context: Context, receiver: Class<*>, action: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextMidnight = LocalDate.now()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        alarms.set(AlarmManager.RTC, nextMidnight + 5_000L, pending(context, receiver, action))
    }

    fun cancel(context: Context, receiver: Class<*>, action: String) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarms.cancel(pending(context, receiver, action))
    }

    private fun pending(context: Context, receiver: Class<*>, action: String): PendingIntent {
        val intent = Intent(context, receiver).setAction(action)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

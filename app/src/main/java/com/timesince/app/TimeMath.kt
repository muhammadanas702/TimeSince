package com.timesince.app

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Calendar-accurate difference between two moments.
 * If the start is in the future, the parts describe the time remaining instead.
 */
data class Parts(
    val future: Boolean,
    val years: Int,
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    /** Milliseconds since the last whole-hour tick of the elapsed (or remaining) time. */
    val msInHour: Long
)

object TimeMath {

    fun compute(start: LocalDateTime, now: LocalDateTime): Parts {
        val future = start.isAfter(now)
        val a = if (future) now else start
        val b = if (future) start else now

        // Largest whole number of months that still fits before b.
        var months = ChronoUnit.MONTHS.between(a, b)
        while (a.plusMonths(months).isAfter(b)) months--
        while (!a.plusMonths(months + 1).isAfter(b)) months++

        val rest = Duration.between(a.plusMonths(months), b)
        return Parts(
            future = future,
            years = (months / 12).toInt(),
            months = (months % 12).toInt(),
            days = rest.toDays().toInt(),
            hours = (rest.toHours() % 24).toInt(),
            minutes = (rest.toMinutes() % 60).toInt(),
            seconds = (rest.seconds % 60).toInt(),
            msInHour = rest.toMillis() % 3_600_000L
        )
    }
}

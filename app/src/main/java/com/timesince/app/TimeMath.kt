package com.timesince.app

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    val msInHour: Long,
    /** Whole days in the full span, for example 230. */
    val totalDays: Long,
    val totalWeeks: Long,
    val totalHours: Long
)

data class Milestone(val label: String, val daysLeft: Long)

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
        val whole = Duration.between(a, b)
        return Parts(
            future = future,
            years = (months / 12).toInt(),
            months = (months % 12).toInt(),
            days = rest.toDays().toInt(),
            hours = (rest.toHours() % 24).toInt(),
            minutes = (rest.toMinutes() % 60).toInt(),
            seconds = (rest.seconds % 60).toInt(),
            msInHour = rest.toMillis() % 3_600_000L,
            totalDays = whole.toDays(),
            totalWeeks = whole.toDays() / 7,
            totalHours = whole.toHours()
        )
    }

    /**
     * The next round number to look forward to: the next 100-day mark or the next
     * anniversary, whichever comes first. Not shown for countdowns.
     */
    fun nextMilestone(start: LocalDateTime, now: LocalDateTime, p: Parts): Milestone? {
        if (p.future) return null
        val dayStep = (p.totalDays / 100 + 1) * 100
        val dayTarget = start.plusDays(dayStep)
        val yearsNext = p.years + 1
        val yearTarget = start.plusYears(yearsNext.toLong())
        val useYear = !yearTarget.isAfter(dayTarget)
        val target = if (useYear) yearTarget else dayTarget
        val label = when {
            !useYear -> "$dayStep days"
            yearsNext == 1 -> "1 year"
            else -> "$yearsNext years"
        }
        val secs = Duration.between(now, target).seconds
        return Milestone(label, (secs + 86_399L) / 86_400L)
    }

    fun daysLabel(n: Long): String =
        if (n == 1L) "1 day" else String.format(Locale.ENGLISH, "%,d days", n)

    /** Short text such as "7 mo · 8 d · 10 h". Zero parts are left out. */
    fun breakdown(p: Parts): String {
        val parts = ArrayList<String>()
        if (p.years > 0) parts.add("${p.years} yr")
        if (p.months > 0) parts.add("${p.months} mo")
        if (p.days > 0) parts.add("${p.days} d")
        if (p.hours > 0 || parts.isEmpty()) parts.add("${p.hours} h")
        return parts.joinToString(" · ")
    }
}

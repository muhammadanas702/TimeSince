package com.timesince.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The date and time maths of the Event countdown widget. */
object EventMath {
    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 24L * HOUR_MS

    /**
     * [next] is the date being counted to; [days] is negative when it is already past.
     * An event with a time has [timed] set and [remainingMs] says how long is left (negative once past).
     */
    class Result(
        val item: EventItem,
        val next: LocalDate,
        val days: Long,
        val remainingMs: Long,
        val timed: Boolean
    ) {
        /** Still to come, or happening right now (an event with a time counts as "now" for one hour). */
        val upcoming: Boolean get() = if (timed) remainingMs > -HOUR_MS else days >= 0

        /** Less than a day to go: the widget shows a live ticking countdown instead of fixed text. */
        val live: Boolean get() = timed && remainingMs > 0 && remainingMs <= DAY_MS
    }

    /** Yearly events always count to their next occurrence (29 Feb becomes 28 Feb in other years). */
    fun resolve(item: EventItem, now: LocalDateTime): Result {
        val today = now.toLocalDate()
        val target = if (item.yearly) {
            val md = MonthDay.of(item.date.month, item.date.dayOfMonth)
            val thisYear = md.atYear(today.year)
            if (thisYear.isBefore(today)) md.atYear(today.year + 1) else thisYear
        } else {
            item.date
        }
        val timed = item.minutes >= 0
        val remaining = if (timed) {
            ChronoUnit.MILLIS.between(now, target.atTime(item.minutes / 60, item.minutes % 60))
        } else {
            0L
        }
        return Result(item, target, ChronoUnit.DAYS.between(today, target), remaining, timed)
    }

    private fun sortKey(r: Result): Long = if (r.timed) r.remainingMs else r.days * DAY_MS

    /** Upcoming events first (soonest on top), then past ones (most recent first). */
    fun order(items: List<EventItem>, now: LocalDateTime): List<Result> {
        val all = items.map { resolve(it, now) }
        val upcoming = all.filter { it.upcoming }.sortedBy { sortKey(it) }
        val past = all.filter { !it.upcoming }.sortedByDescending { sortKey(it) }
        return upcoming + past
    }

    /** The text for an event: "Today", "48 days", "2d 5h", "5h 30m", "Now", "3 days ago". */
    fun label(r: Result): String {
        if (!r.timed) return label(r.days)
        val ms = r.remainingMs
        return when {
            ms > DAY_MS -> {
                val hours = ms / HOUR_MS
                val d = hours / 24
                val h = hours % 24
                if (h == 0L) "${d}d" else "${d}d ${h}h"
            }
            ms > 0 -> {
                val minutes = maxOf(1L, ms / 60_000L)
                if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "$minutes min"
            }
            ms > -HOUR_MS -> "Now"
            r.days < 0 -> label(r.days)
            else -> "Earlier today"
        }
    }

    /**
     * When the widget text next changes by itself, in epoch milliseconds, or null if it never does.
     * Far away events show whole hours, so the text changes once an hour; the live countdown starts
     * a day before; at the event it becomes "Now" and an hour later it becomes a past event.
     */
    fun nextRefresh(items: List<EventItem>, now: LocalDateTime, nowMs: Long): Long? {
        var best = Long.MAX_VALUE
        for (item in items) {
            val r = resolve(item, now)
            if (!r.timed) continue
            val ms = r.remainingMs
            val wait = when {
                ms > DAY_MS -> ms % HOUR_MS
                ms > 0 -> ms
                ms > -HOUR_MS -> ms + HOUR_MS
                else -> continue
            }
            val at = nowMs + wait + 1000L
            if (at < best) best = at
        }
        return if (best == Long.MAX_VALUE) null else best
    }

    /** The moment an alert goes off on [date], [minutes] after midnight, in the phone's time zone. */
    private fun triggerMillis(date: LocalDate, minutes: Int): Long =
        date.atTime(minutes / 60, minutes % 60)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** Every alert moment of the event after [from] and up to [to] (both in epoch milliseconds). */
    fun triggersBetween(item: EventItem, minutes: Int, from: Long, to: Long): List<Long> {
        if (to <= from) return emptyList()
        val found = ArrayList<Long>()
        if (!item.yearly) {
            val t = triggerMillis(item.date, minutes)
            if (t > from && t <= to) found.add(t)
        } else {
            val zone = ZoneId.systemDefault()
            val first = Instant.ofEpochMilli(from).atZone(zone).year
            val last = Instant.ofEpochMilli(to).atZone(zone).year
            val md = MonthDay.of(item.date.month, item.date.dayOfMonth)
            for (year in first..last) {
                val t = triggerMillis(md.atYear(year), minutes)
                if (t > from && t <= to) found.add(t)
            }
        }
        return found
    }

    /** The first alert moment of the event after [after], or null when it has none left. */
    fun nextTrigger(item: EventItem, minutes: Int, after: Long): Long? {
        if (!item.yearly) {
            val t = triggerMillis(item.date, minutes)
            return if (t > after) t else null
        }
        val year = Instant.ofEpochMilli(after).atZone(ZoneId.systemDefault()).year
        val md = MonthDay.of(item.date.month, item.date.dayOfMonth)
        for (y in year..year + 1) {
            val t = triggerMillis(md.atYear(y), minutes)
            if (t > after) return t
        }
        return null
    }

    /** The text for an event without a time. */
    fun label(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1L -> String.format(Locale.ENGLISH, "%,d days", days)
        days == -1L -> "Yesterday"
        else -> String.format(Locale.ENGLISH, "%,d days ago", -days)
    }
}

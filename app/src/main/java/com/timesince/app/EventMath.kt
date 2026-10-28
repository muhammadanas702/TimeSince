package com.timesince.app

import java.time.Instant
import java.time.LocalDate
import java.time.MonthDay
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The date maths of the Event countdown widget. */
object EventMath {

    /** [next] is the date being counted to; [days] is negative when it is already past. */
    class Result(val item: EventItem, val next: LocalDate, val days: Long)

    /** Yearly events always count to their next occurrence (29 Feb becomes 28 Feb in other years). */
    fun resolve(item: EventItem, today: LocalDate): Result {
        val target = if (item.yearly) {
            val md = MonthDay.of(item.date.month, item.date.dayOfMonth)
            val thisYear = md.atYear(today.year)
            if (thisYear.isBefore(today)) md.atYear(today.year + 1) else thisYear
        } else {
            item.date
        }
        return Result(item, target, ChronoUnit.DAYS.between(today, target))
    }

    /** Upcoming events first (soonest on top), then past ones (most recent first). */
    fun order(items: List<EventItem>, today: LocalDate): List<Result> {
        val all = items.map { resolve(it, today) }
        val upcoming = all.filter { it.days >= 0 }.sortedBy { it.days }
        val past = all.filter { it.days < 0 }.sortedByDescending { it.days }
        return upcoming + past
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

    fun label(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1L -> String.format(Locale.ENGLISH, "%,d days", days)
        days == -1L -> "Yesterday"
        else -> String.format(Locale.ENGLISH, "%,d days ago", -days)
    }
}

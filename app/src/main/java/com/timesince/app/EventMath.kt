package com.timesince.app

import java.time.LocalDate
import java.time.MonthDay
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

    fun label(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days > 1L -> String.format(Locale.ENGLISH, "%,d days", days)
        days == -1L -> "Yesterday"
        else -> String.format(Locale.ENGLISH, "%,d days ago", -days)
    }
}

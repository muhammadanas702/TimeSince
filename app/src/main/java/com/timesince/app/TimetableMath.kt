package com.timesince.app

import java.util.Locale

/** One class of the timetable: [start] and [end] are minutes after midnight; [end] is -1 when not written. */
class ClassItem(val start: Int, val end: Int, val title: String) {
    /** A class without an end time is taken to last an hour and a half. */
    val effectiveEnd: Int get() = if (end > start) end else start + 90
}

/**
 * Reads the lines a student types for one day, like `9:00 AM - 10:30 AM Database (Room 12)`,
 * `14:00-15:30 Networks lab` or `10 am Physics`. Lines that do not start with a time are ignored.
 */
object TimetableMath {
    private val LINE = Regex(
        "^(\\d{1,2})(?::(\\d{2}))?\\s*([ap]m)?(?:\\s*[-–—]\\s*(\\d{1,2})(?::(\\d{2}))?\\s*([ap]m)?)?\\s+(.+)$",
        RegexOption.IGNORE_CASE
    )

    /** Minutes after midnight, or null if the time is not valid. Without am/pm, 1 to 6 count as the afternoon. */
    private fun minutes(hour: Int, minute: Int, ampm: String): Int? {
        if (minute > 59) return null
        return when (ampm) {
            "am" -> if (hour in 1..12) (hour % 12) * 60 + minute else null
            "pm" -> if (hour in 1..12) (hour % 12 + 12) * 60 + minute else null
            else -> if (hour > 23) null else (if (hour in 1..6) hour + 12 else hour) * 60 + minute
        }
    }

    fun parseLine(line: String): ClassItem? {
        val g = (LINE.find(line.trim()) ?: return null).groupValues
        val hasEnd = g[4].isNotEmpty()
        val ap1 = g[3].lowercase()
        val ap2 = g[6].lowercase()
        // A bare number is part of the title, not a time, unless the end time makes it clear ("9-10 AM Physics").
        if (g[2].isEmpty() && ap1.isEmpty() && !(hasEnd && (g[5].isNotEmpty() || ap2.isNotEmpty()))) return null

        val h1 = g[1].toInt()
        val m1 = if (g[2].isEmpty()) 0 else g[2].toInt()
        val end = if (hasEnd) minutes(g[4].toInt(), if (g[5].isEmpty()) 0 else g[5].toInt(), ap2) else null
        var start = if (ap1.isEmpty() && ap2.isNotEmpty()) minutes(h1, m1, ap2) else minutes(h1, m1, ap1)
        // "11:00 - 12:30 PM": the start has no am/pm, so it takes the end's, unless that would end before it starts.
        if (start != null && end != null && ap1.isEmpty() && ap2.isNotEmpty() && start >= end) {
            start = minutes(h1, m1, if (ap2 == "pm") "am" else "pm")
        }
        if (start == null) return null
        return ClassItem(start, if (end != null && end > start) end else -1, g[7].trim())
    }

    /** The classes of one day, in the order of their start times. */
    fun parseDay(text: String): List<ClassItem> =
        text.lines().mapNotNull { parseLine(it) }.sortedBy { it.start }.take(7)

    /** "9:00 AM", "12:30 PM". */
    fun timeText(minutes: Int): String {
        val hour = minutes / 60
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return String.format(Locale.ENGLISH, "%d:%02d %s", h12, minutes % 60, if (hour < 12) "AM" else "PM")
    }
}

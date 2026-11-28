package com.timesince.app

/** One subject of the Attendance Tracker: [present] classes attended out of [total] held. */
data class Subject(val name: String, val present: Int, val total: Int)

/** The attendance maths. The percent is rounded down, so 74.6% never shows as 75%. */
object AttendanceMath {
    private val WITH_COUNTS = Regex("^(.*?)\\s+(\\d{1,4})\\s*/\\s*(\\d{1,4})$")

    /** The percent attended, or -1 when no class has been held yet. */
    fun percent(present: Int, total: Int): Int = if (total <= 0) -1 else present * 100 / total

    /** How many more classes can be missed and still stay at or above [target] percent. */
    fun canMiss(present: Int, total: Int, target: Int): Int =
        (present * 100 / target - total).coerceAtLeast(0)

    /** How many classes in a row must be attended to get to [target] percent (0 when already there). */
    fun needed(present: Int, total: Int, target: Int): Int {
        val top = target * total - 100 * present
        if (top <= 0) return 0
        val bottom = 100 - target
        return (top + bottom - 1) / bottom
    }

    /** "Database 12/14" is read as a name with its counts; a line with just a name starts at 0/0. */
    fun parseLine(line: String): Subject? {
        val text = line.trim()
        if (text.isEmpty()) return null
        val m = WITH_COUNTS.find(text)
        if (m == null || m.groupValues[1].isBlank()) return Subject(clean(text), 0, 0)
        val total = m.groupValues[3].toInt()
        val present = m.groupValues[2].toInt().coerceAtMost(total)
        return Subject(clean(m.groupValues[1]), present, total)
    }

    fun parse(text: String): List<Subject> = text.lines().mapNotNull { parseLine(it) }

    /** The line shown in the settings: the name, and the counts once a class has been held. */
    fun format(s: Subject): String = if (s.total > 0) s.name + " " + s.present + "/" + s.total else s.name

    /** Names must not hold the separators of the saved form. */
    fun clean(name: String): String = name.replace('|', ' ').replace(';', ' ').replace('\n', ' ').trim()
}

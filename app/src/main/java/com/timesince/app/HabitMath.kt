package com.timesince.app

/** The streak maths of the Habit Streak widget. Days are epoch days (days since 1 January 1970). */
object HabitMath {
    /** How many days of ticks are kept. Older ones are dropped when saving. */
    private const val KEEP_DAYS = 400

    /**
     * The days in a row that are ticked, counting back from today. If today is not ticked yet the streak is
     * still alive (the day is not over), so it counts back from yesterday instead.
     */
    fun streak(days: Set<Long>, today: Long): Int {
        var day = if (today in days) today else today - 1
        var count = 0
        while (day in days) {
            count++
            day--
        }
        return count
    }

    /** The saved form: the newest [KEEP_DAYS] days, separated by commas. */
    fun save(days: Set<Long>): String {
        val newest = days.sortedDescending().take(KEEP_DAYS).sorted()
        return newest.joinToString(",")
    }
}

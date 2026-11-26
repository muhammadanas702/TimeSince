package com.timesince.app

import java.time.LocalDateTime

/** How far through the day, the week, the month and the year it is, as a fraction from 0 to 1. */
object ProgressMath {

    private fun dayFraction(now: LocalDateTime): Double = now.toLocalTime().toSecondOfDay() / 86_400.0

    fun day(now: LocalDateTime): Double = dayFraction(now)

    /** The week starts on Monday or on Sunday. */
    fun week(now: LocalDateTime, mondayFirst: Boolean): Double {
        val weekday = now.dayOfWeek.value // Monday = 1 ... Sunday = 7
        val index = if (mondayFirst) weekday - 1 else weekday % 7
        return (index + dayFraction(now)) / 7.0
    }

    fun month(now: LocalDateTime): Double =
        (now.dayOfMonth - 1 + dayFraction(now)) / now.toLocalDate().lengthOfMonth()

    fun year(now: LocalDateTime): Double =
        (now.dayOfYear - 1 + dayFraction(now)) / now.toLocalDate().lengthOfYear()

    /** Whole percent, rounded down, so "100%" only shows when the period is really over. */
    fun percent(fraction: Double): Int = (fraction * 100.0).toInt().coerceIn(0, 100)
}

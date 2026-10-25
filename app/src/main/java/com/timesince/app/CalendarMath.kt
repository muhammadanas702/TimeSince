package com.timesince.app

import java.time.LocalDate
import java.time.YearMonth

/** The grid maths of the Month calendar widget. */
object CalendarMath {

    /** One square of the grid. [inMonth] is false for the days of the previous or next month. */
    class Cell(val day: Int, val inMonth: Boolean, val isToday: Boolean)

    /** How many empty squares come before the 1st of the month. */
    fun leadingDays(month: YearMonth, mondayFirst: Boolean): Int {
        val dow = month.atDay(1).dayOfWeek.value // Monday = 1 ... Sunday = 7
        return if (mondayFirst) dow - 1 else dow % 7
    }

    /** 4, 5 or 6 week rows, depending on the month. */
    fun rowCount(month: YearMonth, mondayFirst: Boolean): Int {
        val used = leadingDays(month, mondayFirst) + month.lengthOfMonth()
        return (used + 6) / 7
    }

    /** Always 42 cells (6 rows of 7). The widget hides the rows it does not need. */
    fun grid(month: YearMonth, today: LocalDate, mondayFirst: Boolean): List<Cell> {
        val start = month.atDay(1).minusDays(leadingDays(month, mondayFirst).toLong())
        val cells = ArrayList<Cell>(42)
        for (i in 0 until 42) {
            val d = start.plusDays(i.toLong())
            cells.add(Cell(d.dayOfMonth, YearMonth.from(d) == month, d == today))
        }
        return cells
    }

    fun weekdayNames(mondayFirst: Boolean): List<String> =
        if (mondayFirst) listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        else listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
}

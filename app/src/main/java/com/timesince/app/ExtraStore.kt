package com.timesince.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * [alert]: 0 = none, 1 = notification, 2 = notification with the phone's ringtone.
 * [minutes] is the time of the event in minutes after midnight, or -1 for an event without a time.
 * [lead] is how many minutes before the event (or before its all-day alert time) the alert goes off; 0 = at the time.
 */
data class EventItem(
    val name: String,
    val date: LocalDate,
    val yearly: Boolean = false,
    val alert: Int = 0,
    val minutes: Int = -1,
    val lead: Int = 0
) {
    companion object {
        /** The longest lead time: one year, in minutes. */
        const val MAX_LEAD = 365 * 24 * 60
    }
}

/** [notifyMinutes] is the alert time (minutes after midnight, 540 = 9:00 AM) for events that have no time of their own. */
data class EventsCfg(
    val look: Look = Look(),
    val events: List<EventItem> = emptyList(),
    val notifyMinutes: Int = 540,
    /** The name of the widget (for example "Mid exam dates"), shown above the events. Empty = no heading. */
    val title: String = "",
    /** How many events the widget shows at once (3 to 6). With more events than this, arrows flip the pages. */
    val perPage: Int = 4
)

/** The Day Progress widget: which bars it shows (today, this week, month, year) and when the week starts. */
data class ProgressCfg(
    val look: Look = Themes.look("Lavender"),
    val showDay: Boolean = true,
    val showWeek: Boolean = true,
    val showMonth: Boolean = true,
    val showYear: Boolean = true,
    val mondayFirst: Boolean = true
)

/**
 * The Sticky Note widget: an optional [title] and the [text]. In [checklist] mode every line of the text is an
 * item that can be ticked on the home screen; [done] holds one character per item, "1" for ticked.
 * [size] is 0 (small), 1 (medium) or 2 (large).
 */
data class NoteCfg(
    val look: Look = Themes.look("Butter"),
    val title: String = "",
    val text: String = "",
    val checklist: Boolean = false,
    val done: String = "",
    val size: Int = 1
) {
    /** The checklist items: the non-empty lines, at most [MAX_ITEMS]. */
    val items: List<String>
        get() = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_ITEMS)

    fun isDone(index: Int): Boolean = done.getOrNull(index) == '1'

    companion object {
        const val MAX_ITEMS = 8
        const val MAX_TEXT = 600
    }
}

/** The Habit Streak widget: the name of the habit and the days (as epoch days) on which it was ticked. */
data class HabitCfg(
    val look: Look = Themes.look("Mint"),
    val name: String = "",
    val days: Set<Long> = emptySet()
)

/**
 * The Quote of the Day widget. With [own] on and some lines in [text], the quotes are the lines the user wrote;
 * otherwise the built-in ones. [shift] moves to the next quote when the user taps the refresh button.
 * [size] is 0 (small), 1 (medium) or 2 (large).
 */
data class QuoteCfg(
    val look: Look = Themes.look("Sakura"),
    val own: Boolean = false,
    val text: String = "",
    val size: Int = 1,
    val shift: Int = 0
) {
    /** The quotes the user wrote: the non-empty lines, at most [MAX_QUOTES]. */
    val ownQuotes: List<String>
        get() = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_QUOTES)

    companion object {
        const val MAX_QUOTES = 60
        const val MAX_TEXT = 3000
    }
}

/** The Attendance Tracker: the subjects with their counts, and the [target] percent the college asks for. */
data class AttendanceCfg(
    val look: Look = Themes.look("Peach"),
    val target: Int = 75,
    val subjects: List<Subject> = emptyList()
) {
    companion object {
        const val MAX_SUBJECTS = 6
        val TARGETS = intArrayOf(60, 65, 70, 75, 80, 85, 90)
    }
}

/** The Class Timetable: the typed lines of each weekday, [days][0] is Monday and [days][6] is Sunday. */
data class TimetableCfg(
    val look: Look = Themes.look("Sky"),
    val days: List<String> = List(7) { "" }
) {
    companion object {
        const val MAX_TEXT = 800
    }
}

/** [offset] is the month being shown, counted from the current month (0 = this month). */
data class CalendarCfg(
    val look: Look = Look(),
    val mondayFirst: Boolean = true,
    val offset: Int = 0
)

data class ClockCfg(
    val look: Look = Look(),
    val h24: Boolean = false,
    val seconds: Boolean = false
)

/**
 * [shortNames] shows LON and NEW instead of London and New York, as in the sample design.
 * [pillOpacity] (0 to 100) sets how strong the dark city pills are; the colored clock pill is always solid.
 * The look's own opacity fades the container. [textScale] (70 to 130 percent) makes all text smaller or larger.
 * Any number of cities from 1 to [MAX_CITIES].
 */
data class WorldCfg(
    val look: Look = Looks.GLASS,
    val zones: List<Zone> = listOf(
        Cities.byName("London"),
        Cities.byName("New York")
    ),
    val h24: Boolean = false,
    val shortNames: Boolean = true,
    val pillOpacity: Int = 75,
    val textScale: Int = 100
) {
    companion object {
        const val MAX_CITIES = 6
    }
}

/** Saves the settings of the clock-style widgets, one set per widget id. */
object ExtraStore {
    private const val FILE = "time_since_extra"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun readLook(p: SharedPreferences, prefix: String, id: Int, base: Look = Look()): Look {
        return Look(
            theme = p.getInt("${prefix}_theme_$id", base.theme),
            bg = p.getInt("${prefix}_bg_$id", base.bg),
            fg = p.getInt("${prefix}_fg_$id", base.fg),
            accent = p.getInt("${prefix}_accent_$id", base.accent),
            opacity = p.getInt("${prefix}_opacity_$id", base.opacity).coerceIn(0, 100)
        )
    }

    private fun writeLook(e: SharedPreferences.Editor, prefix: String, id: Int, look: Look) {
        e.putInt("${prefix}_theme_$id", look.theme)
        e.putInt("${prefix}_bg_$id", look.bg)
        e.putInt("${prefix}_fg_$id", look.fg)
        e.putInt("${prefix}_accent_$id", look.accent)
        e.putInt("${prefix}_opacity_$id", look.opacity)
    }

    fun readClock(c: Context, id: Int): ClockCfg {
        val p = prefs(c)
        return ClockCfg(
            look = readLook(p, "clock", id),
            h24 = p.getBoolean("clock_h24_$id", false),
            seconds = p.getBoolean("clock_sec_$id", false)
        )
    }

    fun writeClock(c: Context, id: Int, cfg: ClockCfg) {
        val e = prefs(c).edit()
        writeLook(e, "clock", id, cfg.look)
        e.putBoolean("clock_h24_$id", cfg.h24)
        e.putBoolean("clock_sec_$id", cfg.seconds)
        e.remove("removed_$id")
        e.apply()
    }

    fun readProgress(c: Context, id: Int): ProgressCfg {
        val p = prefs(c)
        val base = ProgressCfg()
        return ProgressCfg(
            look = readLook(p, "prog", id, base.look),
            showDay = p.getBoolean("prog_day_$id", true),
            showWeek = p.getBoolean("prog_week_$id", true),
            showMonth = p.getBoolean("prog_month_$id", true),
            showYear = p.getBoolean("prog_year_$id", true),
            mondayFirst = p.getBoolean("prog_monday_$id", true)
        )
    }

    fun writeProgress(c: Context, id: Int, cfg: ProgressCfg) {
        val e = prefs(c).edit()
        writeLook(e, "prog", id, cfg.look)
        e.putBoolean("prog_day_$id", cfg.showDay)
        e.putBoolean("prog_week_$id", cfg.showWeek)
        e.putBoolean("prog_month_$id", cfg.showMonth)
        e.putBoolean("prog_year_$id", cfg.showYear)
        e.putBoolean("prog_monday_$id", cfg.mondayFirst)
        e.remove("removed_$id")
        e.apply()
    }

    fun readNote(c: Context, id: Int): NoteCfg {
        val p = prefs(c)
        val base = NoteCfg()
        return NoteCfg(
            look = readLook(p, "note", id, base.look),
            title = p.getString("note_title_$id", "") ?: "",
            text = p.getString("note_text_$id", "") ?: "",
            checklist = p.getBoolean("note_list_$id", false),
            done = p.getString("note_done_$id", "") ?: "",
            size = p.getInt("note_size_$id", 1).coerceIn(0, 2)
        )
    }

    fun writeNote(c: Context, id: Int, cfg: NoteCfg) {
        val e = prefs(c).edit()
        writeLook(e, "note", id, cfg.look)
        e.putString("note_title_$id", cfg.title.replace('\n', ' ').trim())
        e.putString("note_text_$id", cfg.text.take(NoteCfg.MAX_TEXT))
        e.putBoolean("note_list_$id", cfg.checklist)
        e.putString("note_done_$id", cfg.done)
        e.putInt("note_size_$id", cfg.size)
        e.remove("removed_$id")
        e.apply()
    }

    /** Ticks or unticks one item of a checklist note, from the widget on the home screen. */
    fun toggleNoteDone(c: Context, id: Int, index: Int) {
        val cfg = readNote(c, id)
        val count = cfg.items.size
        if (index !in 0 until count) return
        val flags = CharArray(count) { if (cfg.isDone(it)) '1' else '0' }
        flags[index] = if (flags[index] == '1') '0' else '1'
        prefs(c).edit().putString("note_done_$id", String(flags)).apply()
    }

    fun readHabit(c: Context, id: Int): HabitCfg {
        val p = prefs(c)
        val base = HabitCfg()
        val raw = p.getString("habit_days_$id", "") ?: ""
        return HabitCfg(
            look = readLook(p, "habit", id, base.look),
            name = p.getString("habit_name_$id", "") ?: "",
            days = raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
        )
    }

    fun writeHabit(c: Context, id: Int, cfg: HabitCfg) {
        val e = prefs(c).edit()
        writeLook(e, "habit", id, cfg.look)
        e.putString("habit_name_$id", cfg.name.replace('\n', ' ').trim())
        e.putString("habit_days_$id", HabitMath.save(cfg.days))
        e.remove("removed_$id")
        e.apply()
    }

    /** Ticks today of a habit, or takes the tick back, from the widget on the home screen. */
    fun toggleHabitToday(c: Context, id: Int, today: Long) {
        val days = readHabit(c, id).days.toMutableSet()
        if (!days.remove(today)) days.add(today)
        prefs(c).edit().putString("habit_days_$id", HabitMath.save(days)).apply()
    }

    fun readQuote(c: Context, id: Int): QuoteCfg {
        val p = prefs(c)
        val base = QuoteCfg()
        return QuoteCfg(
            look = readLook(p, "quote", id, base.look),
            own = p.getBoolean("quote_own_$id", false),
            text = p.getString("quote_text_$id", "") ?: "",
            size = p.getInt("quote_size_$id", 1).coerceIn(0, 2),
            shift = p.getInt("quote_shift_$id", 0)
        )
    }

    /** Saving keeps the quote the user is looking at (the shift) as it is. */
    fun writeQuote(c: Context, id: Int, cfg: QuoteCfg) {
        val e = prefs(c).edit()
        writeLook(e, "quote", id, cfg.look)
        e.putBoolean("quote_own_$id", cfg.own)
        e.putString("quote_text_$id", cfg.text.take(QuoteCfg.MAX_TEXT))
        e.putInt("quote_size_$id", cfg.size)
        e.putInt("quote_shift_$id", cfg.shift)
        e.remove("removed_$id")
        e.apply()
    }

    /** The refresh button of the widget: the next quote. */
    fun nextQuote(c: Context, id: Int) {
        val shift = readQuote(c, id).shift
        prefs(c).edit().putInt("quote_shift_$id", (shift + 1) % 100_000).apply()
    }

    fun readAttendance(c: Context, id: Int): AttendanceCfg {
        val p = prefs(c)
        val base = AttendanceCfg()
        val raw = p.getString("att_list_$id", "") ?: ""
        val subjects = raw.split(";").mapNotNull {
            val parts = it.split("|")
            if (parts.size != 3 || parts[0].isBlank()) {
                null
            } else {
                val total = (parts[2].toIntOrNull() ?: 0).coerceIn(0, 9999)
                Subject(parts[0], (parts[1].toIntOrNull() ?: 0).coerceIn(0, total), total)
            }
        }
        return AttendanceCfg(
            look = readLook(p, "att", id, base.look),
            target = p.getInt("att_target_$id", 75).coerceIn(50, 95),
            subjects = subjects.take(AttendanceCfg.MAX_SUBJECTS)
        )
    }

    private fun subjectsText(list: List<Subject>): String =
        list.take(AttendanceCfg.MAX_SUBJECTS).joinToString(";") {
            AttendanceMath.clean(it.name) + "|" + it.present + "|" + it.total
        }

    fun writeAttendance(c: Context, id: Int, cfg: AttendanceCfg) {
        val e = prefs(c).edit()
        writeLook(e, "att", id, cfg.look)
        e.putString("att_list_$id", subjectsText(cfg.subjects))
        e.putInt("att_target_$id", cfg.target)
        e.remove("removed_$id")
        e.apply()
    }

    /** One tap in the widget: a class was held, and the student was there (or not). */
    fun markAttendance(c: Context, id: Int, index: Int, present: Boolean) {
        val cfg = readAttendance(c, id)
        val list = cfg.subjects.toMutableList()
        if (index !in list.indices) return
        val s = list[index]
        list[index] = Subject(s.name, if (present) minOf(s.present + 1, 9999) else s.present, minOf(s.total + 1, 9999))
        prefs(c).edit().putString("att_list_$id", subjectsText(list)).apply()
    }

    fun readTimetable(c: Context, id: Int): TimetableCfg {
        val p = prefs(c)
        return TimetableCfg(
            look = readLook(p, "tt", id, TimetableCfg().look),
            days = List(7) { p.getString("tt_d${it + 1}_$id", "") ?: "" }
        )
    }

    fun writeTimetable(c: Context, id: Int, cfg: TimetableCfg) {
        val e = prefs(c).edit()
        writeLook(e, "tt", id, cfg.look)
        for (i in 0 until 7) e.putString("tt_d${i + 1}_$id", cfg.days.getOrElse(i) { "" }.take(TimetableCfg.MAX_TEXT))
        e.remove("removed_$id")
        e.apply()
    }

    fun readWorld(c: Context, id: Int): WorldCfg {
        val p = prefs(c)
        val base = WorldCfg()
        val raw = p.getString("world_zones_$id", null)
        val saved = if (raw.isNullOrEmpty()) emptyList() else parseZones(raw)
        // The look is stored under "world3": every widget starts again from the reference look (Glass, 15%),
        // while the cities and the 12/24 hour choice of older widgets are kept.
        val look = readLook(p, "world3", id, base.look)
        return WorldCfg(
            look = look,
            zones = saved.ifEmpty { base.zones }.take(WorldCfg.MAX_CITIES),
            h24 = p.getBoolean("world_h24_$id", false),
            shortNames = p.getBoolean("world_short_$id", true),
            pillOpacity = p.getInt("world_pill3_$id", base.pillOpacity).coerceIn(0, 100),
            textScale = p.getInt("world_text3_$id", base.textScale).coerceIn(70, 130)
        )
    }

    fun writeWorld(c: Context, id: Int, cfg: WorldCfg) {
        val e = prefs(c).edit()
        writeLook(e, "world3", id, cfg.look)
        e.putString("world_zones_$id", cfg.zones.joinToString(";") { it.name + "|" + it.id })
        e.putBoolean("world_h24_$id", cfg.h24)
        e.putBoolean("world_short_$id", cfg.shortNames)
        e.putInt("world_pill3_$id", cfg.pillOpacity)
        e.putInt("world_text3_$id", cfg.textScale)
        e.remove("removed_$id")
        e.apply()
    }

    fun readCalendar(c: Context, id: Int): CalendarCfg {
        val p = prefs(c)
        return CalendarCfg(
            look = readLook(p, "cal", id),
            mondayFirst = p.getBoolean("cal_monday_$id", true),
            offset = p.getInt("cal_offset_$id", 0)
        )
    }

    /** Saves the settings. Saving also jumps back to the current month. */
    fun writeCalendar(c: Context, id: Int, cfg: CalendarCfg) {
        val e = prefs(c).edit()
        writeLook(e, "cal", id, cfg.look)
        e.putBoolean("cal_monday_$id", cfg.mondayFirst)
        e.putInt("cal_offset_$id", 0)
        e.remove("removed_$id")
        e.apply()
    }

    /** The arrows on the widget change only this. */
    fun setCalendarOffset(c: Context, id: Int, offset: Int) {
        prefs(c).edit().putInt("cal_offset_$id", offset).apply()
    }

    fun readEvents(c: Context, id: Int): EventsCfg {
        val p = prefs(c)
        val raw = p.getString("events_list_$id", null)
        return EventsCfg(
            look = readLook(p, "events", id),
            events = if (raw.isNullOrEmpty()) emptyList() else parseEvents(raw),
            notifyMinutes = p.getInt("events_notify_$id", 540).coerceIn(0, 1439),
            title = p.getString("events_title_$id", "") ?: "",
            perPage = p.getInt("events_perpage_$id", 4).coerceIn(3, 6)
        )
    }

    /** The page of events a widget shows now (0 = the first). The widget's arrows change it. */
    fun eventsPage(c: Context, id: Int): Int = prefs(c).getInt("events_page_$id", 0).coerceAtLeast(0)

    fun setEventsPage(c: Context, id: Int, page: Int) {
        prefs(c).edit().putInt("events_page_$id", page.coerceAtLeast(0)).apply()
    }

    /**
     * Alerts are sent for every moment after this one. Saving the settings sets it to "now", so
     * dates already past are not announced.
     */
    fun alertCursor(c: Context, id: Int, default: Long): Long {
        val p = prefs(c)
        val key = "events_cursor_$id"
        if (!p.contains(key)) {
            p.edit().putLong(key, default).apply()
            return default
        }
        return p.getLong(key, default)
    }

    fun setAlertCursor(c: Context, id: Int, value: Long) {
        prefs(c).edit().putLong("events_cursor_$id", value).apply()
    }

    fun writeEvents(c: Context, id: Int, cfg: EventsCfg) {
        val e = prefs(c).edit()
        writeLook(e, "events", id, cfg.look)
        // Names must not contain the two separator characters.
        val text = cfg.events.joinToString(";") {
            val clean = it.name.replace('|', ' ').replace(';', ' ').replace('\n', ' ')
            clean + "|" + it.date.toString() + "|" + (if (it.yearly) "1" else "0") + "|" + it.alert + "|" + it.minutes +
                "|" + it.lead.coerceIn(0, EventItem.MAX_LEAD)
        }
        e.putString("events_list_$id", text)
        e.putInt("events_notify_$id", cfg.notifyMinutes)
        e.putString("events_title_$id", cfg.title.replace('\n', ' ').trim())
        e.putInt("events_perpage_$id", cfg.perPage.coerceIn(3, 6))
        // Saving always starts again on the first page.
        e.remove("events_page_$id")
        e.putLong("events_cursor_$id", System.currentTimeMillis())
        e.remove("removed_$id")
        e.apply()
    }

    private fun parseEvents(raw: String): List<EventItem> = raw.split(";").mapNotNull {
        val parts = it.split("|")
        // Three parts: older saves. Four: with the alert choice. Five: with the time of the event.
        // Six: with how long before the event the alert goes off.
        if (parts.size < 3 || parts.size > 6) return@mapNotNull null
        val date = try {
            LocalDate.parse(parts[1])
        } catch (e: Exception) {
            return@mapNotNull null
        }
        val alert = if (parts.size >= 4) (parts[3].toIntOrNull() ?: 0).coerceIn(0, 2) else 0
        val minutes = if (parts.size >= 5) (parts[4].toIntOrNull() ?: -1) else -1
        val lead = if (parts.size == 6) (parts[5].toIntOrNull() ?: 0) else 0
        EventItem(
            parts[0], date, parts[2] == "1", alert,
            if (minutes in 0..1439) minutes else -1,
            lead.coerceIn(0, EventItem.MAX_LEAD)
        )
    }

    private fun parseZones(raw: String): List<Zone> = raw.split(";").mapNotNull {
        val parts = it.split("|")
        // Only the name and the time zone are saved; the country comes back from the city list.
        if (parts.size == 2 && parts[0].isNotEmpty()) {
            val known = Cities.ALL.firstOrNull { z -> z.name == parts[0] && z.id == parts[1] }
            Zone(parts[0], parts[1], known?.country ?: "")
        } else {
            null
        }
    }

    /**
     * A widget the user deleted in the app. Android does not let an app take a widget off the home screen,
     * so such a widget stays there as a small "Deleted" tile (see [RemovedWidget]) until the user removes it.
     * Saving the settings of the widget again clears the mark.
     */
    fun isRemoved(c: Context, id: Int): Boolean = prefs(c).getBoolean("removed_$id", false)

    fun setRemoved(c: Context, id: Int, removed: Boolean) {
        val e = prefs(c).edit()
        if (removed) e.putBoolean("removed_$id", true) else e.remove("removed_$id")
        e.apply()
    }

    /** Forgets every setting of a widget that was removed from the home screen. */
    fun remove(c: Context, id: Int) {
        val p = prefs(c)
        val e = p.edit()
        for (key in p.all.keys) {
            if (key.endsWith("_$id")) e.remove(key)
        }
        e.apply()
    }
}

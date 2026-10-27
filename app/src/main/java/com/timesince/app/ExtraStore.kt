package com.timesince.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

data class EventItem(
    val name: String,
    val date: LocalDate,
    val yearly: Boolean = false
)

data class EventsCfg(
    val look: Look = Look(),
    val events: List<EventItem> = emptyList()
)

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
 * [pillOpacity] (0 to 100) fades the clock pill and the city pills; the look's own opacity fades the container.
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
    val pillOpacity: Int = 75
) {
    companion object {
        const val MAX_CITIES = 6
    }
}

/** Saves the settings of the clock-style widgets, one set per widget id. */
object ExtraStore {
    private const val FILE = "time_since_extra"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The Glass look of version 2.3 (white at 25%). Kept only to recognise and upgrade it. */
    private val OLD_GLASS = Look(
        theme = 0,
        bg = 0xFFFFFFFF.toInt(),
        fg = 0xFFFFFFFF.toInt(),
        accent = 0xFFF5CC00.toInt(),
        opacity = 25
    )

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
        e.apply()
    }

    fun readWorld(c: Context, id: Int): WorldCfg {
        val p = prefs(c)
        val base = WorldCfg()
        val raw = p.getString("world_zones_$id", null)
        val saved = if (raw.isNullOrEmpty()) emptyList() else parseZones(raw)
        // The look is stored under "world2": the redesigned widget starts from the Glass look,
        // while the cities and the 12/24 hour choice of older widgets are kept.
        var look = readLook(p, "world2", id, base.look)
        // Widgets still on the first Glass look (25% white) move to the more transparent default.
        if (look == OLD_GLASS) look = base.look
        return WorldCfg(
            look = look,
            zones = saved.ifEmpty { base.zones }.take(WorldCfg.MAX_CITIES),
            h24 = p.getBoolean("world_h24_$id", false),
            shortNames = p.getBoolean("world_short_$id", true),
            pillOpacity = p.getInt("world_pill_$id", base.pillOpacity).coerceIn(0, 100)
        )
    }

    fun writeWorld(c: Context, id: Int, cfg: WorldCfg) {
        val e = prefs(c).edit()
        writeLook(e, "world2", id, cfg.look)
        e.putString("world_zones_$id", cfg.zones.joinToString(";") { it.name + "|" + it.id })
        e.putBoolean("world_h24_$id", cfg.h24)
        e.putBoolean("world_short_$id", cfg.shortNames)
        e.putInt("world_pill_$id", cfg.pillOpacity)
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
            events = if (raw.isNullOrEmpty()) emptyList() else parseEvents(raw)
        )
    }

    fun writeEvents(c: Context, id: Int, cfg: EventsCfg) {
        val e = prefs(c).edit()
        writeLook(e, "events", id, cfg.look)
        // Names must not contain the two separator characters.
        val text = cfg.events.joinToString(";") {
            val clean = it.name.replace('|', ' ').replace(';', ' ').replace('\n', ' ')
            clean + "|" + it.date.toString() + "|" + (if (it.yearly) "1" else "0")
        }
        e.putString("events_list_$id", text)
        e.apply()
    }

    private fun parseEvents(raw: String): List<EventItem> = raw.split(";").mapNotNull {
        val parts = it.split("|")
        if (parts.size != 3) return@mapNotNull null
        val date = try {
            LocalDate.parse(parts[1])
        } catch (e: Exception) {
            return@mapNotNull null
        }
        EventItem(parts[0], date, parts[2] == "1")
    }

    private fun parseZones(raw: String): List<Zone> = raw.split(";").mapNotNull {
        val parts = it.split("|")
        if (parts.size == 2 && parts[0].isNotEmpty()) Zone(parts[0], parts[1]) else null
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

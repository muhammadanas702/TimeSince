package com.timesince.app

import android.content.Context
import android.content.SharedPreferences

data class ClockCfg(
    val look: Look = Look(),
    val h24: Boolean = false,
    val seconds: Boolean = false
)

data class WorldCfg(
    val look: Look = Look(),
    val zones: List<Zone> = listOf(
        Cities.byName("Lahore"),
        Cities.byName("London"),
        Cities.byName("New York")
    ),
    val h24: Boolean = false
)

/** Saves the settings of the clock-style widgets, one set per widget id. */
object ExtraStore {
    private const val FILE = "time_since_extra"

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun readLook(p: SharedPreferences, prefix: String, id: Int): Look {
        val base = Look()
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
        return WorldCfg(
            look = readLook(p, "world", id),
            zones = saved.ifEmpty { base.zones },
            h24 = p.getBoolean("world_h24_$id", false)
        )
    }

    fun writeWorld(c: Context, id: Int, cfg: WorldCfg) {
        val e = prefs(c).edit()
        writeLook(e, "world", id, cfg.look)
        e.putString("world_zones_$id", cfg.zones.joinToString(";") { it.name + "|" + it.id })
        e.putBoolean("world_h24_$id", cfg.h24)
        e.apply()
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

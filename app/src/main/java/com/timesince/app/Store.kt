package com.timesince.app

import android.content.Context
import java.time.LocalDateTime

data class Config(val label: String, val start: LocalDateTime)

/** Saves one date per widget. Id 0 holds the last date used, as a starting point for new widgets. */
object Store {
    const val PREVIEW_ID = 0
    private const val FILE = "time_since"

    val EXAMPLE = Config("", LocalDateTime.of(2025, 10, 6, 0, 0))

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(c: Context, id: Int): Config? {
        val p = prefs(c)
        val raw = p.getString("start_$id", null) ?: return null
        return try {
            Config(p.getString("label_$id", "") ?: "", LocalDateTime.parse(raw))
        } catch (e: Exception) {
            null
        }
    }

    fun write(c: Context, id: Int, cfg: Config) {
        prefs(c).edit()
            .putString("label_$id", cfg.label)
            .putString("start_$id", cfg.start.toString())
            .apply()
    }

    fun remove(c: Context, id: Int) {
        prefs(c).edit()
            .remove("label_$id")
            .remove("start_$id")
            .apply()
    }

    fun forWidget(c: Context, id: Int): Config =
        read(c, id) ?: read(c, PREVIEW_ID) ?: EXAMPLE
}

package com.timesince.app

import android.content.Context
import java.time.LocalDateTime

/** Everything one widget needs to draw itself. Colors are opaque ARGB ints; opacity is 0 to 100. */
data class Config(
    val label: String,
    val start: LocalDateTime,
    val style: Int = Styles.DETAILED,
    val theme: Int = 0,
    val bg: Int = Themes.PRESETS[0].bg,
    val fg: Int = Themes.PRESETS[0].fg,
    val accent: Int = Themes.PRESETS[0].accent,
    val opacity: Int = 100,
    /** For a date in the future: 0 = no alert, 1 = notification, 2 = notification with the ringtone. */
    val alert: Int = 0
)

/** Saves one Config per widget. Id 0 holds the last look used, as the starting point for new widgets. */
object Store {
    const val PREVIEW_ID = 0
    private const val FILE = "time_since"

    val EXAMPLE = Config("", LocalDateTime.of(2026, 2, 22, 11, 0))

    private fun prefs(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(c: Context, id: Int): Config? {
        val p = prefs(c)
        val raw = p.getString("start_$id", null) ?: return null
        val base = Themes.PRESETS[0]
        return try {
            Config(
                label = p.getString("label_$id", "") ?: "",
                start = LocalDateTime.parse(raw),
                style = p.getInt("style_$id", Styles.DETAILED).coerceIn(0, 2),
                theme = p.getInt("theme_$id", 0),
                bg = p.getInt("bg_$id", base.bg),
                fg = p.getInt("fg_$id", base.fg),
                accent = p.getInt("accent_$id", base.accent),
                opacity = p.getInt("opacity_$id", 100).coerceIn(0, 100),
                alert = p.getInt("alert_$id", 0).coerceIn(0, 2)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun write(c: Context, id: Int, cfg: Config) {
        prefs(c).edit()
            .putString("label_$id", cfg.label)
            .putString("start_$id", cfg.start.toString())
            .putInt("style_$id", cfg.style)
            .putInt("theme_$id", cfg.theme)
            .putInt("bg_$id", cfg.bg)
            .putInt("fg_$id", cfg.fg)
            .putInt("accent_$id", cfg.accent)
            .putInt("opacity_$id", cfg.opacity)
            .putInt("alert_$id", cfg.alert)
            .apply()
    }

    fun remove(c: Context, id: Int) {
        prefs(c).edit()
            .remove("label_$id")
            .remove("start_$id")
            .remove("style_$id")
            .remove("theme_$id")
            .remove("bg_$id")
            .remove("fg_$id")
            .remove("accent_$id")
            .remove("opacity_$id")
            .remove("alert_$id")
            .apply()
    }

    /** A widget that has never been configured starts from the last look used, without its name. */
    fun forWidget(c: Context, id: Int): Config =
        read(c, id) ?: read(c, PREVIEW_ID)?.copy(label = "", alert = 0) ?: EXAMPLE
}

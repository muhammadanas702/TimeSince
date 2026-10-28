package com.timesince.app

import android.content.Context

/** The three ways a widget can show the time. The numbers match the order of the buttons in the editor. */
object Styles {
    const val DETAILED = 0
    const val DAYS = 1
    const val COMPACT = 2

    fun previewHeightDp(style: Int): Int = when (style) {
        DAYS -> 140
        COMPACT -> 72
        else -> 130
    }
}

object Themes {
    const val CUSTOM = -1

    /** [opacity] is optional: a preset that sets it (0 to 100) also moves the transparency slider. */
    class Preset(val name: String, val bg: Int, val fg: Int, val accent: Int, val opacity: Int = -1)

    val PRESETS: List<Preset> = listOf(
        Preset("Midnight", 0xFF0A1120.toInt(), 0xFFE9EEF7.toInt(), 0xFFFF6A2B.toInt()),
        Preset("Amoled", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF3DDC97.toInt()),
        Preset("Paper", 0xFFF5F7FA.toInt(), 0xFF0D1A2F.toInt(), 0xFFC23C08.toInt()),
        Preset("Ocean", 0xFF062B3B.toInt(), 0xFFE6F7FF.toInt(), 0xFF4CC9F0.toInt()),
        Preset("Forest", 0xFF0F2A1D.toInt(), 0xFFE8F5EC.toInt(), 0xFFFFC857.toInt()),
        Preset("Sunset", 0xFF2B1020.toInt(), 0xFFFFEDE6.toInt(), 0xFFFF8A5B.toInt()),
        Preset("Rose", 0xFF3B0F24.toInt(), 0xFFFFE9EE.toInt(), 0xFFFF5C8A.toInt()),
        Preset("Violet", 0xFF1B1033.toInt(), 0xFFEFE9FF.toInt(), 0xFFA78BFA.toInt()),
        Preset("Mint", 0xFFE8F6EF.toInt(), 0xFF0F2A1D.toInt(), 0xFF0E9F6E.toInt())
    )

    /** Frosted glass with a yellow clock pill: the look of the World Clock sample. */
    val GLASS = Preset("Glass", 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFF5CA01.toInt(), 15)

    /** The World Clock offers Glass first, then every ordinary preset. */
    val GLASS_SET: List<Preset> = listOf(GLASS) + PRESETS
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

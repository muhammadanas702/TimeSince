package com.timesince.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

/**
 * The "Color theme" and "Transparency" part of a settings screen, shared by the clock-style widgets.
 * Put layout/view_look.xml into the screen and create one LookPanel for it.
 * Read the current choice from [look]; [onChange] runs after every change.
 */
class LookPanel(
    private val activity: Activity,
    root: View,
    initial: Look,
    private val presets: List<Themes.Preset> = Themes.PRESETS,
    private val onChange: () -> Unit
) {
    var look: Look = initial
        private set

    private val presetsRow: LinearLayout = root.findViewById(R.id.presets_row)
    private val btnBg: Button = root.findViewById(R.id.btn_c_bg)
    private val btnFg: Button = root.findViewById(R.id.btn_c_fg)
    private val btnAccent: Button = root.findViewById(R.id.btn_c_accent)
    private val seek: SeekBar = root.findViewById(R.id.sb_opacity)
    private val tvOpacity: TextView = root.findViewById(R.id.tv_opacity)

    init {
        btnBg.setOnClickListener {
            pickColor("Background color", look.bg) { c ->
                look = look.copy(bg = c, theme = Themes.CUSTOM)
                changed()
            }
        }
        btnFg.setOnClickListener {
            pickColor("Text color", look.fg) { c ->
                look = look.copy(fg = c, theme = Themes.CUSTOM)
                changed()
            }
        }
        btnAccent.setOnClickListener {
            pickColor("Accent color", look.accent) { c ->
                look = look.copy(accent = c, theme = Themes.CUSTOM)
                changed()
            }
        }

        seek.progress = look.opacity
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                look = look.copy(opacity = progress)
                tvOpacity.text = "Background opacity: $progress%"
                onChange()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        refresh()
    }

    private fun changed() {
        refresh()
        onChange()
    }

    /** Replaces the whole look at once, for a "reset" button. */
    fun setLook(newLook: Look) {
        look = newLook
        seek.progress = newLook.opacity
        changed()
    }

    private fun choosePreset(index: Int) {
        val p = presets[index]
        look = look.copy(theme = index, bg = p.bg, fg = p.fg, accent = p.accent)
        // A preset like Glass also brings its own transparency. Moving the slider redraws the preview.
        if (p.opacity >= 0) seek.progress = p.opacity
        changed()
    }

    private fun refresh() {
        setSwatch(btnBg, look.bg)
        setSwatch(btnFg, look.fg)
        setSwatch(btnAccent, look.accent)
        tvOpacity.text = "Background opacity: ${look.opacity}%"
        buildPresets()
    }

    private fun setSwatch(button: Button, color: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color or (0xFF shl 24))
        d.setStroke(activity.dp(1), activity.getColor(R.color.line))
        d.setSize(activity.dp(20), activity.dp(20))
        button.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
        button.compoundDrawablePadding = activity.dp(12)
    }

    private fun buildPresets() {
        presetsRow.removeAllViews()
        presets.forEachIndexed { i, p ->
            val selected = i == look.theme

            val item = LinearLayout(activity)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(activity.dp(4), activity.dp(4), activity.dp(10), activity.dp(4))

            val outer = GradientDrawable()
            outer.shape = GradientDrawable.OVAL
            outer.setColor(p.bg)
            outer.setStroke(
                activity.dp(3),
                activity.getColor(if (selected) R.color.accent else R.color.line)
            )
            val inner = GradientDrawable()
            inner.shape = GradientDrawable.OVAL
            inner.setColor(p.accent)
            val layers = LayerDrawable(arrayOf<Drawable>(outer, inner))
            layers.setLayerInset(1, activity.dp(14), activity.dp(14), activity.dp(14), activity.dp(14))

            val circle = View(activity)
            circle.background = layers
            item.addView(circle, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)))

            val name = TextView(activity)
            name.text = p.name
            name.textSize = 11f
            name.gravity = Gravity.CENTER
            name.setTextColor(activity.getColor(if (selected) R.color.fg else R.color.muted))
            item.addView(
                name,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            item.setOnClickListener { choosePreset(i) }
            presetsRow.addView(item)
        }
    }

    /** A small dialog with three sliders. Simple, and no extra libraries needed. */
    private fun pickColor(title: String, initial: Int, onPicked: (Int) -> Unit) {
        var r = Color.red(initial)
        var g = Color.green(initial)
        var b = Color.blue(initial)

        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(activity.dp(20), activity.dp(16), activity.dp(20), 0)

        val sample = View(activity)
        val hex = TextView(activity)
        hex.textSize = 14f
        hex.setTextColor(activity.getColor(R.color.muted))
        hex.setPadding(0, activity.dp(8), 0, activity.dp(4))

        fun update() {
            sample.setBackgroundColor(Color.rgb(r, g, b))
            hex.text = String.format(Locale.ENGLISH, "#%02X%02X%02X", r, g, b)
        }

        fun addBar(label: String, value: Int, set: (Int) -> Unit) {
            val tv = TextView(activity)
            tv.text = label
            tv.textSize = 13f
            tv.setTextColor(activity.getColor(R.color.muted))
            box.addView(tv)

            val bar = SeekBar(activity)
            bar.max = 255
            bar.progress = value
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    set(progress)
                    update()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            box.addView(bar)
        }

        box.addView(sample, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)))
        box.addView(hex)
        addBar("Red", r) { r = it }
        addBar("Green", g) { g = it }
        addBar("Blue", b) { b = it }
        update()

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Use color") { _, _ -> onPicked(Color.rgb(r, g, b)) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

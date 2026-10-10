package com.timesince.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings for one widget: name, date, style, colors and transparency, with a live preview
 * that uses the very same drawing code as the widget itself.
 * The system opens this screen when a widget is added, and the widget opens it when tapped.
 */
class ConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var date: LocalDate
    private lateinit var time: LocalTime
    private var style = Styles.DETAILED
    private var themeIdx = 0
    private var bg = 0
    private var fg = 0
    private var accent = 0
    private var opacity = 100

    private lateinit var etLabel: EditText
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var btnBg: Button
    private lateinit var btnFg: Button
    private lateinit var btnAccent: Button
    private lateinit var styleButtons: List<Button>
    private lateinit var presetsRow: LinearLayout
    private lateinit var host: FrameLayout
    private lateinit var seek: SeekBar
    private lateinit var tvOpacity: TextView

    private var previewView: View? = null
    private var previewStyle = -1

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            renderPreview()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // If the user backs out while adding a widget, the widget must not be placed.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        setContentView(R.layout.activity_config)

        etLabel = findViewById(R.id.et_label)
        btnDate = findViewById(R.id.btn_date)
        btnTime = findViewById(R.id.btn_time)
        btnBg = findViewById(R.id.btn_c_bg)
        btnFg = findViewById(R.id.btn_c_fg)
        btnAccent = findViewById(R.id.btn_c_accent)
        presetsRow = findViewById(R.id.presets_row)
        host = findViewById(R.id.preview_host)
        seek = findViewById(R.id.sb_opacity)
        tvOpacity = findViewById(R.id.tv_opacity)
        styleButtons = listOf(
            findViewById<Button>(R.id.st_detailed),
            findViewById<Button>(R.id.st_days),
            findViewById<Button>(R.id.st_compact)
        )

        val cfg = Store.forWidget(this, widgetId)
        date = cfg.start.toLocalDate()
        time = cfg.start.toLocalTime()
        style = cfg.style
        themeIdx = cfg.theme
        bg = cfg.bg
        fg = cfg.fg
        accent = cfg.accent
        opacity = cfg.opacity
        etLabel.setText(cfg.label)
        seek.progress = opacity

        etLabel.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date = LocalDate.of(y, m + 1, d)
                    changed()
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    time = LocalTime.of(h, m)
                    changed()
                },
                time.hour, time.minute, DateFormat.is24HourFormat(this)
            ).show()
        }

        styleButtons.forEachIndexed { i, button ->
            button.setOnClickListener {
                style = i
                changed()
            }
        }

        btnBg.setOnClickListener {
            pickColor("Background color", bg) { c ->
                bg = c
                themeIdx = Themes.CUSTOM
                changed()
            }
        }
        btnFg.setOnClickListener {
            pickColor("Text color", fg) { c ->
                fg = c
                themeIdx = Themes.CUSTOM
                changed()
            }
        }
        btnAccent.setOnClickListener {
            pickColor("Accent color", accent) { c ->
                accent = c
                themeIdx = Themes.CUSTOM
                changed()
            }
        }

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                opacity = progress
                tvOpacity.text = "Background opacity: $opacity%"
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        if (!::host.isInitialized) return
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun changed() {
        refreshUi()
        renderPreview()
    }

    private fun chooseTheme(index: Int) {
        val p = Themes.PRESETS[index]
        themeIdx = index
        bg = p.bg
        fg = p.fg
        accent = p.accent
        changed()
    }

    private fun currentConfig() = Config(
        label = etLabel.text.toString().trim().take(40),
        start = LocalDateTime.of(date, time),
        style = style,
        theme = themeIdx,
        bg = bg,
        fg = fg,
        accent = accent,
        opacity = opacity
    )

    private fun save() {
        val cfg = currentConfig()
        Store.write(this, widgetId, cfg)
        Store.write(this, Store.PREVIEW_ID, cfg)
        TimeSinceWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        val cfg = currentConfig()
        val (views, _) = TimeSinceWidget.build(this, widgetId, cfg, true)
        val existing = previewView
        if (existing == null || previewStyle != cfg.style) {
            host.removeAllViews()
            val v = views.apply(this, host)
            host.addView(v)
            previewView = v
            previewStyle = cfg.style
            val lp = host.layoutParams
            lp.height = dp(Styles.previewHeightDp(cfg.style))
            host.layoutParams = lp
        } else {
            views.reapply(this, existing)
        }
    }

    private fun refreshUi() {
        btnDate.text = "Date: " + SHORT_DATE_FMT.format(date)
        btnTime.text = "Time: " + TIME_FMT.format(time)

        styleButtons.forEachIndexed { i, button ->
            val on = i == style
            button.setBackgroundResource(if (on) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            button.setTextColor(getColor(if (on) R.color.on_accent else R.color.fg))
        }

        setSwatch(btnBg, bg)
        setSwatch(btnFg, fg)
        setSwatch(btnAccent, accent)
        tvOpacity.text = "Background opacity: $opacity%"
        buildPresets()
    }

    private fun setSwatch(button: Button, color: Int) {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color or (0xFF shl 24))
        d.setStroke(dp(1), getColor(R.color.line))
        d.setSize(dp(20), dp(20))
        button.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
        button.compoundDrawablePadding = dp(12)
    }

    private fun buildPresets() {
        presetsRow.removeAllViews()
        Themes.PRESETS.forEachIndexed { i, p ->
            val selected = i == themeIdx

            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER_HORIZONTAL
            item.setPadding(dp(4), dp(4), dp(10), dp(4))

            val outer = GradientDrawable()
            outer.shape = GradientDrawable.OVAL
            outer.setColor(p.bg)
            outer.setStroke(dp(3), getColor(if (selected) R.color.accent else R.color.line))
            val inner = GradientDrawable()
            inner.shape = GradientDrawable.OVAL
            inner.setColor(p.accent)
            val layers = LayerDrawable(arrayOf<Drawable>(outer, inner))
            layers.setLayerInset(1, dp(14), dp(14), dp(14), dp(14))

            val circle = View(this)
            circle.background = layers
            item.addView(circle, LinearLayout.LayoutParams(dp(48), dp(48)))

            val name = TextView(this)
            name.text = p.name
            name.textSize = 11f
            name.gravity = Gravity.CENTER
            name.setTextColor(getColor(if (selected) R.color.fg else R.color.muted))
            item.addView(
                name,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            item.setOnClickListener { chooseTheme(i) }
            presetsRow.addView(item)
        }
    }

    /** A small dialog with three sliders. Simple, and no extra libraries needed. */
    private fun pickColor(title: String, initial: Int, onPicked: (Int) -> Unit) {
        var r = Color.red(initial)
        var g = Color.green(initial)
        var b = Color.blue(initial)

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(16), dp(20), 0)

        val sample = View(this)
        val hex = TextView(this)
        hex.textSize = 14f
        hex.setTextColor(getColor(R.color.muted))
        hex.setPadding(0, dp(8), 0, dp(4))

        fun update() {
            sample.setBackgroundColor(Color.rgb(r, g, b))
            hex.text = String.format(Locale.ENGLISH, "#%02X%02X%02X", r, g, b)
        }

        fun addBar(label: String, value: Int, set: (Int) -> Unit) {
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 13f
            tv.setTextColor(getColor(R.color.muted))
            box.addView(tv)

            val bar = SeekBar(this)
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

        box.addView(sample, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        box.addView(hex)
        addBar("Red", r) { r = it }
        addBar("Green", g) { g = it }
        addBar("Blue", b) { b = it }
        update()

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Use color") { _, _ -> onPicked(Color.rgb(r, g, b)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

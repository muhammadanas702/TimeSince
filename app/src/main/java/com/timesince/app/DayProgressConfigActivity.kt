package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** Settings for one Day Progress widget: which bars to show, the first day of the week, colors and transparency. */
class DayProgressConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var showDay = true
    private var showWeek = true
    private var showMonth = true
    private var showYear = true
    private var mondayFirst = true

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnDay: Button
    private lateinit var btnWeek: Button
    private lateinit var btnMonth: Button
    private lateinit var btnYear: Button
    private lateinit var btnStart: Button
    private var previewView: View? = null

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
        setContentView(R.layout.activity_config_progress)

        host = findViewById(R.id.preview_host)
        btnDay = findViewById(R.id.btn_show_day)
        btnWeek = findViewById(R.id.btn_show_week)
        btnMonth = findViewById(R.id.btn_show_month)
        btnYear = findViewById(R.id.btn_show_year)
        btnStart = findViewById(R.id.btn_week_start)

        val cfg = ExtraStore.readProgress(this, widgetId)
        showDay = cfg.showDay
        showWeek = cfg.showWeek
        showMonth = cfg.showMonth
        showYear = cfg.showYear
        mondayFirst = cfg.mondayFirst

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnDay.setOnClickListener { showDay = !showDay; changed() }
        btnWeek.setOnClickListener { showWeek = !showWeek; changed() }
        btnMonth.setOnClickListener { showMonth = !showMonth; changed() }
        btnYear.setOnClickListener { showYear = !showYear; changed() }
        btnStart.setOnClickListener { mondayFirst = !mondayFirst; changed() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshButtons()
        renderPreview()
    }

    /** At least one bar must stay on, so the widget is never empty. */
    private fun changed() {
        if (!showDay && !showWeek && !showMonth && !showYear) showDay = true
        refreshButtons()
        renderPreview()
    }

    private fun currentConfig() = ProgressCfg(
        look = panel.look,
        showDay = showDay,
        showWeek = showWeek,
        showMonth = showMonth,
        showYear = showYear,
        mondayFirst = mondayFirst
    )

    private fun save() {
        ExtraStore.writeProgress(this, widgetId, currentConfig())
        DayProgressWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = DayProgressWidget.build(this, widgetId, currentConfig(), true)
        val existing = previewView
        if (existing == null) {
            host.removeAllViews()
            val v = views.apply(this, host)
            host.addView(v)
            previewView = v
        } else {
            views.reapply(this, existing)
        }
    }

    private fun refreshButtons() {
        toggle(btnDay, "Today", showDay)
        toggle(btnWeek, "This week", showWeek)
        toggle(btnMonth, "This month", showMonth)
        toggle(btnYear, "This year", showYear)
        btnStart.text = "Week starts on: " + (if (mondayFirst) "Monday" else "Sunday")
    }

    private fun toggle(button: Button, label: String, on: Boolean) {
        button.text = label + ": " + (if (on) "Shown" else "Hidden")
        button.setBackgroundResource(if (on) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        button.setTextColor(getColor(if (on) R.color.on_accent else R.color.fg))
    }
}

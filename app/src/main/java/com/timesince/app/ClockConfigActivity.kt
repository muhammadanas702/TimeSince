package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** Settings for one Clock + date widget, with a live preview drawn by the widget's own code. */
class ClockConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var h24 = false
    private var seconds = false

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnH24: Button
    private lateinit var btnSeconds: Button
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
        setContentView(R.layout.activity_config_clock)

        host = findViewById(R.id.preview_host)
        btnH24 = findViewById(R.id.btn_h24)
        btnSeconds = findViewById(R.id.btn_seconds)

        val cfg = ExtraStore.readClock(this, widgetId)
        h24 = cfg.h24
        seconds = cfg.seconds

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnH24.setOnClickListener {
            h24 = !h24
            refreshToggles()
            renderPreview()
        }
        btnSeconds.setOnClickListener {
            seconds = !seconds
            refreshToggles()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshToggles()
        renderPreview()
    }

    private fun currentConfig() = ClockCfg(look = panel.look, h24 = h24, seconds = seconds)

    private fun save() {
        ExtraStore.writeClock(this, widgetId, currentConfig())
        ClockWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        val views = ClockWidget.build(this, widgetId, currentConfig(), true)
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

    private fun refreshToggles() {
        toggle(btnH24, "24-hour clock", h24)
        toggle(btnSeconds, "Show seconds", seconds)
    }

    private fun toggle(button: Button, label: String, on: Boolean) {
        button.text = label + ": " + (if (on) "On" else "Off")
        button.setBackgroundResource(if (on) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        button.setTextColor(getColor(if (on) R.color.on_accent else R.color.fg))
    }
}

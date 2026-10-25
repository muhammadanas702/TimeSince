package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** Settings for one Month calendar widget: first day of the week, colors and transparency. */
class CalendarConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var mondayFirst = true

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnWeek: Button
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
        setContentView(R.layout.activity_config_calendar)

        host = findViewById(R.id.preview_host)
        btnWeek = findViewById(R.id.btn_week)

        val cfg = ExtraStore.readCalendar(this, widgetId)
        mondayFirst = cfg.mondayFirst

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnWeek.setOnClickListener {
            mondayFirst = !mondayFirst
            refreshButton()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshButton()
        renderPreview()
    }

    // The preview always shows the current month, whatever month the widget has been moved to.
    private fun currentConfig() = CalendarCfg(look = panel.look, mondayFirst = mondayFirst, offset = 0)

    private fun save() {
        ExtraStore.writeCalendar(this, widgetId, currentConfig())
        CalendarWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        val views = CalendarWidget.build(this, widgetId, currentConfig(), true)
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

    private fun refreshButton() {
        btnWeek.text = "Week starts on: " + (if (mondayFirst) "Monday" else "Sunday")
    }
}

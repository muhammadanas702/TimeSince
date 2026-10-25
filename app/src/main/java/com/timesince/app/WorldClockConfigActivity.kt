package com.timesince.app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

/** Settings for one World clock widget: up to three cities, 12 or 24 hour, colors, transparency. */
class WorldClockConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var zones: MutableList<Zone> = ArrayList()
    private var h24 = false

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnH24: Button
    private lateinit var cityButtons: List<Button>
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
        setContentView(R.layout.activity_config_world)

        host = findViewById(R.id.preview_host)
        btnH24 = findViewById(R.id.btn_h24)
        cityButtons = listOf(
            findViewById<Button>(R.id.btn_city1),
            findViewById<Button>(R.id.btn_city2),
            findViewById<Button>(R.id.btn_city3)
        )

        val cfg = ExtraStore.readWorld(this, widgetId)
        zones = cfg.zones.toMutableList()
        h24 = cfg.h24

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        cityButtons.forEachIndexed { i, button ->
            button.setOnClickListener { pickCity(i) }
        }
        btnH24.setOnClickListener {
            h24 = !h24
            refreshButtons()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshButtons()
        renderPreview()
    }

    private fun currentConfig() = WorldCfg(look = panel.look, zones = zones.toList(), h24 = h24)

    private fun save() {
        ExtraStore.writeWorld(this, widgetId, currentConfig())
        WorldClockWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        val views = WorldClockWidget.build(this, widgetId, currentConfig(), true)
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

    /** City 1 is always needed. Cities 2 and 3 can be set to None. */
    private fun pickCity(slot: Int) {
        val options: List<Zone> = if (slot > 0) listOf(NONE) + Cities.CHOICES else Cities.CHOICES
        val labels = options.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("City " + (slot + 1))
            .setItems(labels) { _, which ->
                val chosen = options[which]
                if (chosen === NONE) {
                    if (slot < zones.size) zones.removeAt(slot)
                } else if (slot < zones.size) {
                    zones[slot] = chosen
                } else {
                    zones.add(chosen)
                }
                refreshButtons()
                renderPreview()
            }
            .show()
    }

    private fun refreshButtons() {
        cityButtons.forEachIndexed { i, button ->
            val zone = zones.getOrNull(i)
            button.text = "City " + (i + 1) + ": " + (zone?.name ?: "None")
        }
        btnH24.text = "24-hour clock: " + (if (h24) "On" else "Off")
        btnH24.setBackgroundResource(if (h24) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        btnH24.setTextColor(getColor(if (h24) R.color.on_accent else R.color.fg))
    }

    companion object {
        private val NONE = Zone("None", "")
    }
}

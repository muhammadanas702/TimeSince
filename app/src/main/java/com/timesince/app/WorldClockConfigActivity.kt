package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Settings for one World clock widget: any number of cities from 1 to 6 (with search), names,
 * 12/24 hour, colors, and two transparency sliders (container and pills).
 */
class WorldClockConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var zones: MutableList<Zone> = ArrayList()
    private var h24 = false
    private var shortNames = true
    private var pillOpacity = 75

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnH24: Button
    private lateinit var btnNames: Button
    private lateinit var btnAdd: Button
    private lateinit var tvPill: TextView
    private lateinit var cityContainer: LinearLayout
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
        btnNames = findViewById(R.id.btn_names)
        btnAdd = findViewById(R.id.btn_add_city)
        tvPill = findViewById(R.id.tv_pill)
        cityContainer = findViewById(R.id.cities_container)
        val sbPill = findViewById<SeekBar>(R.id.sb_pill)

        val cfg = ExtraStore.readWorld(this, widgetId)
        zones = cfg.zones.toMutableList()
        h24 = cfg.h24
        shortNames = cfg.shortNames
        pillOpacity = cfg.pillOpacity

        panel = LookPanel(
            this,
            findViewById<View>(android.R.id.content),
            cfg.look,
            presets = Themes.GLASS_SET
        ) { renderPreview() }

        sbPill.progress = pillOpacity
        sbPill.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                pillOpacity = progress
                refreshButtons()
                renderPreview()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnAdd.setOnClickListener {
            CityPicker.show(this, "Add a city", Cities.CHOICES) { chosen ->
                if (zones.size < WorldCfg.MAX_CITIES) zones.add(chosen)
                rebuildCities()
                renderPreview()
            }
        }
        btnH24.setOnClickListener {
            h24 = !h24
            refreshButtons()
            renderPreview()
        }
        btnNames.setOnClickListener {
            shortNames = !shortNames
            refreshButtons()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        rebuildCities()
        refreshButtons()
        renderPreview()
    }

    private fun currentConfig() = WorldCfg(
        look = panel.look,
        zones = zones.toList(),
        h24 = h24,
        shortNames = shortNames,
        pillOpacity = pillOpacity
    )

    private fun save() {
        ExtraStore.writeWorld(this, widgetId, currentConfig())
        WorldClockWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    /** The preview box grows with the number of cities, like a widget the user has made taller. */
    private fun previewHeightDp(): Int = 150 + 38 * (zones.size - 2).coerceAtLeast(0)

    private fun renderPreview() {
        val heightDp = previewHeightDp()
        val wanted = dp(heightDp)
        val lp = host.layoutParams
        if (lp.height != wanted) {
            lp.height = wanted
            host.layoutParams = lp
        }

        val views = WorldClockWidget.build(this, widgetId, currentConfig(), true, heightDp)
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

    /** One row per city: a button that opens the searchable list, and a button that removes it. */
    private fun rebuildCities() {
        cityContainer.removeAllViews()
        zones.forEachIndexed { i, zone ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            val pick = outlineButton()
            pick.text = "City " + (i + 1) + ": " + zone.label()
            pick.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            pick.setPadding(dp(16), 0, dp(16), 0)
            pick.setOnClickListener { pickCity(i) }
            row.addView(pick, LinearLayout.LayoutParams(0, dp(48), 1f))

            if (zones.size > 1) {
                val remove = outlineButton()
                remove.text = "×"
                remove.textSize = 22f
                remove.setTextColor(getColor(R.color.muted))
                remove.contentDescription = "Remove this city"
                remove.minWidth = 0
                remove.minimumWidth = 0
                remove.setPadding(0, 0, 0, 0)
                remove.setOnClickListener {
                    if (zones.size > 1 && i < zones.size) zones.removeAt(i)
                    rebuildCities()
                    renderPreview()
                }
                val lp = LinearLayout.LayoutParams(dp(48), dp(48))
                lp.marginStart = dp(8)
                row.addView(remove, lp)
            }

            val rowLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLp.topMargin = dp(8)
            cityContainer.addView(row, rowLp)
        }
        btnAdd.visibility = if (zones.size < WorldCfg.MAX_CITIES) View.VISIBLE else View.GONE
    }

    private fun outlineButton(): Button {
        val b = Button(this)
        b.setBackgroundResource(R.drawable.bg_button_outline)
        b.stateListAnimator = null
        b.setAllCaps(false)
        b.setTextColor(getColor(R.color.fg))
        b.textSize = 15f
        return b
    }

    private fun pickCity(slot: Int) {
        CityPicker.show(this, "City " + (slot + 1), Cities.CHOICES) { chosen ->
            if (slot < zones.size) zones[slot] = chosen
            rebuildCities()
            renderPreview()
        }
    }

    private fun refreshButtons() {
        btnNames.text = "City names: " + (if (shortNames) "Short (LON)" else "Full (London)")
        btnH24.text = "24-hour clock: " + (if (h24) "On" else "Off")
        btnH24.setBackgroundResource(if (h24) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        btnH24.setTextColor(getColor(if (h24) R.color.on_accent else R.color.fg))
        tvPill.text = "Pill opacity: " + pillOpacity + "%"
    }
}

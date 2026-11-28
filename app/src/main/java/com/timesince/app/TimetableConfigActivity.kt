package com.timesince.app

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import java.time.LocalDate

/**
 * Settings for one Class Timetable widget: seven day buttons, and for the chosen day one line per class, like
 * `9:00 AM - 10:30 AM Database (Room 12)`. Colors and transparency as for the other widgets.
 */
class TimetableConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** The typed lines of every weekday, Monday first. Switching day never loses what was typed. */
    private val texts = Array(7) { "" }
    private var selected = 0

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var etText: EditText
    private val dayButtons = ArrayList<Button>()
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
        setContentView(R.layout.activity_config_timetable)

        host = findViewById(R.id.preview_host)
        etText = findViewById(R.id.et_tt_text)

        val cfg = ExtraStore.readTimetable(this, widgetId)
        for (i in 0 until 7) texts[i] = cfg.days.getOrElse(i) { "" }
        selected = LocalDate.now().dayOfWeek.value - 1

        val ids = intArrayOf(
            R.id.btn_tt_d1, R.id.btn_tt_d2, R.id.btn_tt_d3, R.id.btn_tt_d4,
            R.id.btn_tt_d5, R.id.btn_tt_d6, R.id.btn_tt_d7
        )
        ids.forEachIndexed { i, buttonId ->
            val button = findViewById<Button>(buttonId)
            button.setOnClickListener {
                selected = i
                etText.setText(texts[i])
                refreshDays()
                renderPreview()
            }
            dayButtons.add(button)
        }

        etText.setText(texts[selected])
        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                texts[selected] = s?.toString() ?: ""
                renderPreview()
            }
        })

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        findViewById<Button>(R.id.btn_tt_copy).setOnClickListener { confirmCopy() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshDays()
        renderPreview()
    }

    /** Copies the lines of the chosen day to Monday to Friday, after asking, because it replaces those days. */
    private fun confirmCopy() {
        AlertDialog.Builder(this)
            .setTitle("Copy to Monday to Friday?")
            .setMessage("The lines of this day replace the lines of Monday to Friday.")
            .setPositiveButton("Copy") { _, _ ->
                val text = texts[selected]
                for (i in 0 until 5) texts[i] = text
                Toast.makeText(this, "Copied to Monday to Friday.", Toast.LENGTH_SHORT).show()
                renderPreview()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun currentConfig() = TimetableCfg(look = panel.look, days = texts.toList())

    private fun save() {
        ExtraStore.writeTimetable(this, widgetId, currentConfig())
        TimetableWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = TimetableWidget.build(this, widgetId, currentConfig(), true, selected + 1)
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

    /** The chosen day is filled; a day that already has lines gets a dot in its name. */
    private fun refreshDays() {
        val names = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        dayButtons.forEachIndexed { i, button ->
            val on = i == selected
            button.text = names[i] + (if (texts[i].isNotBlank() && !on) "•" else "")
            button.setBackgroundResource(if (on) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
            button.setTextColor(getColor(if (on) R.color.on_accent else R.color.fg))
        }
    }
}

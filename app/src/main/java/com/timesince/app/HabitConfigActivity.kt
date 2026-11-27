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

/** Settings for one Habit Streak widget: the name of the habit, colors and transparency, and clearing the ticks. */
class HabitConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** The ticks of the habit. Settings never lose them; only "Clear all ticks" empties this. */
    private var days: Set<Long> = emptySet()

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var etName: EditText
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
        setContentView(R.layout.activity_config_habit)

        host = findViewById(R.id.preview_host)
        etName = findViewById(R.id.et_habit_name)

        val cfg = ExtraStore.readHabit(this, widgetId)
        days = cfg.days
        etName.setText(cfg.name)
        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        findViewById<Button>(R.id.btn_habit_clear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all ticks?")
                .setMessage("The streak starts again from zero. This is applied when you save the widget.")
                .setPositiveButton("Clear") { _, _ ->
                    days = emptySet()
                    renderPreview()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        renderPreview()
    }

    private fun currentConfig() = HabitCfg(look = panel.look, name = etName.text.toString().trim(), days = days)

    private fun save() {
        ExtraStore.writeHabit(this, widgetId, currentConfig())
        HabitWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = HabitWidget.build(this, widgetId, currentConfig(), true)
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
}

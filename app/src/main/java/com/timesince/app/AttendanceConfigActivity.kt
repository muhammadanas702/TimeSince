package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout

/**
 * Settings for one Attendance Tracker widget: the subjects (one per line, with their counts, for example
 * `Database 12/14`), the goal percent, colors and transparency.
 */
class AttendanceConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var target = 75

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var etSubjects: EditText
    private lateinit var btnTarget: Button
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
        setContentView(R.layout.activity_config_attendance)

        host = findViewById(R.id.preview_host)
        etSubjects = findViewById(R.id.et_subjects)
        btnTarget = findViewById(R.id.btn_target)

        val cfg = ExtraStore.readAttendance(this, widgetId)
        target = cfg.target
        // The counts are part of the text, so correcting a count is just editing the line.
        etSubjects.setText(cfg.subjects.joinToString("\n") { AttendanceMath.format(it) })
        etSubjects.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnTarget.setOnClickListener {
            // 60, 65 ... 90 and round again.
            val targets = AttendanceCfg.TARGETS
            val at = targets.indexOf(target)
            target = targets[(at + 1) % targets.size]
            refreshTarget()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshTarget()
        renderPreview()
    }

    private fun currentConfig() = AttendanceCfg(
        look = panel.look,
        target = target,
        subjects = AttendanceMath.parse(etSubjects.text.toString()).take(AttendanceCfg.MAX_SUBJECTS)
    )

    private fun save() {
        ExtraStore.writeAttendance(this, widgetId, currentConfig())
        AttendanceWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = AttendanceWidget.build(this, widgetId, currentConfig(), true)
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

    private fun refreshTarget() {
        btnTarget.text = "Attendance goal: $target%"
    }
}

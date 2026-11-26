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
import android.widget.TextView

/**
 * Settings for one Sticky Note widget: a title, the text (or the checklist, one item per line), the text size,
 * colors and transparency.
 */
class NoteConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var checklist = false
    private var size = 1

    /** The note as it was saved, so ticks stay on the items that did not change. */
    private var saved = NoteCfg()

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var etTitle: EditText
    private lateinit var etText: EditText
    private lateinit var btnMode: Button
    private lateinit var btnSize: Button
    private lateinit var tvHelp: TextView
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
        setContentView(R.layout.activity_config_note)

        host = findViewById(R.id.preview_host)
        etTitle = findViewById(R.id.et_note_title)
        etText = findViewById(R.id.et_note_text)
        btnMode = findViewById(R.id.btn_note_mode)
        btnSize = findViewById(R.id.btn_note_size)
        tvHelp = findViewById(R.id.tv_note_help)

        saved = ExtraStore.readNote(this, widgetId)
        checklist = saved.checklist
        size = saved.size
        etTitle.setText(saved.title)
        etText.setText(saved.text)

        panel = LookPanel(this, findViewById<View>(android.R.id.content), saved.look) { renderPreview() }

        watch(etTitle)
        watch(etText)
        btnMode.setOnClickListener {
            checklist = !checklist
            refreshButtons()
            renderPreview()
        }
        btnSize.setOnClickListener {
            size = (size + 1) % 3
            refreshButtons()
            renderPreview()
        }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshButtons()
        renderPreview()
    }

    private fun watch(edit: EditText) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })
    }

    private fun currentConfig(): NoteCfg {
        val draft = NoteCfg(
            look = panel.look,
            title = etTitle.text.toString().trim(),
            text = etText.text.toString(),
            checklist = checklist,
            size = size
        )
        // An item keeps its tick only if it is still the same text on the same line.
        val before = saved.items
        val flags = StringBuilder()
        draft.items.forEachIndexed { i, item ->
            flags.append(if (before.getOrNull(i) == item && saved.isDone(i)) '1' else '0')
        }
        return draft.copy(done = flags.toString())
    }

    private fun save() {
        ExtraStore.writeNote(this, widgetId, currentConfig())
        NoteWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = NoteWidget.build(this, widgetId, currentConfig(), true)
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
        btnMode.text = "Checklist: " + (if (checklist) "On" else "Off")
        btnMode.setBackgroundResource(if (checklist) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        btnMode.setTextColor(getColor(if (checklist) R.color.on_accent else R.color.fg))
        btnSize.text = "Text size: " + arrayOf("Small", "Medium", "Large")[size]
        tvHelp.text = if (checklist) {
            "One item per line, up to " + NoteCfg.MAX_ITEMS + ". Tap an item on the home screen to tick it."
        } else {
            "Write anything. A long note is cut at the end of the widget, so make the widget bigger to see more."
        }
    }
}

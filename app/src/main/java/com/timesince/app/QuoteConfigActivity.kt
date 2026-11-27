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

/** Settings for one Quote of the Day widget: built-in quotes or your own, the text size, colors and transparency. */
class QuoteConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var own = false
    private var size = 1
    private var shift = 0

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
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
        setContentView(R.layout.activity_config_quote)

        host = findViewById(R.id.preview_host)
        etText = findViewById(R.id.et_quote_text)
        btnMode = findViewById(R.id.btn_quote_mode)
        btnSize = findViewById(R.id.btn_quote_size)
        tvHelp = findViewById(R.id.tv_quote_help)

        val cfg = ExtraStore.readQuote(this, widgetId)
        own = cfg.own
        size = cfg.size
        shift = cfg.shift
        etText.setText(cfg.text)
        etText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnMode.setOnClickListener {
            own = !own
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

    private fun currentConfig() = QuoteCfg(
        look = panel.look,
        own = own,
        text = etText.text.toString(),
        size = size,
        shift = shift
    )

    private fun save() {
        ExtraStore.writeQuote(this, widgetId, currentConfig())
        QuoteWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = QuoteWidget.build(this, widgetId, currentConfig(), true)
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
        btnMode.text = "Quotes: " + (if (own) "My own" else "Built-in")
        btnMode.setBackgroundResource(if (own) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        btnMode.setTextColor(getColor(if (own) R.color.on_accent else R.color.fg))
        btnSize.text = "Text size: " + arrayOf("Small", "Medium", "Large")[size]
        etText.visibility = if (own) View.VISIBLE else View.GONE
        tvHelp.text = if (own) {
            "One quote per line, up to " + QuoteCfg.MAX_QUOTES + ". Add the author after a dash, for example: Be kind. - Anas. " +
                "You get a new line each day; with no lines, the built-in quotes are used."
        } else {
            "A new short quote every day. The arrows in the corner of the widget show the next one at once."
        }
    }
}

package com.timesince.app

import android.app.Activity
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings for one Event countdown widget: up to four events, colors and transparency. */
class EventsConfigActivity : Activity() {

    private class Slot(
        val name: EditText,
        val dateButton: Button,
        val yearly: CheckBox
    ) {
        var value: LocalDate? = null
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val slots = ArrayList<Slot>()

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
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
        setContentView(R.layout.activity_config_events)

        host = findViewById(R.id.preview_host)
        val container = findViewById<LinearLayout>(R.id.events_container)
        val cfg = ExtraStore.readEvents(this, widgetId)

        for (i in 0 until MAX_EVENTS) {
            val row = layoutInflater.inflate(R.layout.item_event_slot, container, false)
            container.addView(row)

            row.findViewById<TextView>(R.id.ev_caption).text = "Event " + (i + 1)
            val slot = Slot(
                row.findViewById<EditText>(R.id.ev_name),
                row.findViewById<Button>(R.id.ev_date),
                row.findViewById<CheckBox>(R.id.ev_yearly)
            )
            val clear = row.findViewById<Button>(R.id.ev_clear)

            // Fill in the saved values first, then start listening for changes.
            val saved = cfg.events.getOrNull(i)
            if (saved != null) {
                slot.name.setText(saved.name)
                slot.value = saved.date
                slot.yearly.isChecked = saved.yearly
            }
            refreshSlot(slot)

            slot.name.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    renderPreview()
                }
            })
            slot.dateButton.setOnClickListener { pickDate(slot) }
            slot.yearly.setOnCheckedChangeListener { _, _ -> renderPreview() }
            clear.setOnClickListener {
                slot.name.setText("")
                slot.value = null
                slot.yearly.isChecked = false
                refreshSlot(slot)
                renderPreview()
            }
            slots.add(slot)
        }

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        renderPreview()
    }

    /** Only rows that have a date count. A date without a name is called "Event". */
    private fun currentConfig(): EventsCfg {
        val events = ArrayList<EventItem>()
        for (slot in slots) {
            val date = slot.value ?: continue
            val name = slot.name.text.toString().trim().ifEmpty { "Event" }
            events.add(EventItem(name, date, slot.yearly.isChecked))
        }
        return EventsCfg(look = panel.look, events = events)
    }

    private fun save() {
        ExtraStore.writeEvents(this, widgetId, currentConfig())
        EventsWidget.updateAll(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val views = EventsWidget.build(this, widgetId, currentConfig(), true)
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

    private fun pickDate(slot: Slot) {
        val start = slot.value ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                slot.value = LocalDate.of(y, m + 1, d)
                refreshSlot(slot)
                renderPreview()
            },
            start.year, start.monthValue - 1, start.dayOfMonth
        ).show()
    }

    private fun refreshSlot(slot: Slot) {
        val date = slot.value
        slot.dateButton.text = if (date == null) "Pick date" else DATE_FMT.format(date)
    }

    companion object {
        private const val MAX_EVENTS = 4
        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}

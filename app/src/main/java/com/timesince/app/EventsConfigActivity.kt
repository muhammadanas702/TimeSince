package com.timesince.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.DialogInterface
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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings for one Event countdown widget: a name for the widget, any number of events (up to [EventsWidget.MAX_EVENTS]),
 * each with a date, an optional time and an optional alert (notification or ringtone), how many events the widget
 * shows at once, the alert time for events without a time, colors and transparency.
 */
class EventsConfigActivity : Activity() {

    private class Slot(
        val row: View,
        val caption: TextView,
        val name: EditText,
        val dateButton: Button,
        val yearly: CheckBox,
        val timeButton: Button,
        val alertButton: Button
    ) {
        var value: LocalDate? = null
        var minutes = -1
        var alert = EventAlerts.MODE_OFF
    }

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val slots = ArrayList<Slot>()
    private var notifyMinutes = 540
    private var perPage = 4
    private var previewPage = 0

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnTime: Button
    private lateinit var etTitle: EditText
    private lateinit var container: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var btnAdd: Button
    private lateinit var btnPerPage: Button
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
        btnTime = findViewById(R.id.btn_notify_time)
        etTitle = findViewById(R.id.et_widget_title)
        container = findViewById(R.id.events_container)
        scroll = findViewById(R.id.config_scroll)
        btnAdd = findViewById(R.id.btn_add_event)
        btnPerPage = findViewById(R.id.btn_per_page)
        val cfg = ExtraStore.readEvents(this, widgetId)
        notifyMinutes = cfg.notifyMinutes
        perPage = cfg.perPage

        // Four empty rows to start with (as before), or one row for every saved event.
        val rows = maxOf(START_ROWS, cfg.events.size)
        for (i in 0 until rows) addSlot(cfg.events.getOrNull(i))

        btnAdd.setOnClickListener { addNewEvent() }
        btnPerPage.setOnClickListener {
            perPage = if (perPage >= EventsWidget.MAX_PER_PAGE) EventsWidget.MIN_PER_PAGE else perPage + 1
            previewPage = 0
            refreshPerPage()
            renderPreview()
        }

        etTitle.setText(cfg.title)
        etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnTime.setOnClickListener { pickAlertTime() }
        findViewById<Button>(R.id.btn_test_alert).setOnClickListener { sendTestAlert() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshTimeButton()
        refreshPerPage()
        renderPreview()
    }

    /** Adds one row to the list of events, filled in with [saved] when there is one. */
    private fun addSlot(saved: EventItem?): Slot {
        val row = layoutInflater.inflate(R.layout.item_event_slot, container, false)
        container.addView(row)

        val slot = Slot(
            row,
            row.findViewById<TextView>(R.id.ev_caption),
            row.findViewById<EditText>(R.id.ev_name),
            row.findViewById<Button>(R.id.ev_date),
            row.findViewById<CheckBox>(R.id.ev_yearly),
            row.findViewById<Button>(R.id.ev_time),
            row.findViewById<Button>(R.id.ev_alert)
        )
        val remove = row.findViewById<Button>(R.id.ev_clear)

        // Fill in the saved values first, then start listening for changes.
        if (saved != null) {
            slot.name.setText(saved.name)
            slot.value = saved.date
            slot.yearly.isChecked = saved.yearly
            slot.alert = saved.alert
            slot.minutes = saved.minutes
        }
        slots.add(slot)
        refreshSlot(slot)
        renumber()
        refreshAddButton()

        slot.name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderPreview()
            }
        })
        slot.dateButton.setOnClickListener { pickDate(slot) }
        slot.yearly.setOnCheckedChangeListener { _, _ -> renderPreview() }
        slot.timeButton.setOnClickListener { pickEventTime(slot) }
        slot.alertButton.setOnClickListener {
            // Off, then Notification, then Ringtone, then Off again.
            slot.alert = (slot.alert + 1) % 3
            if (slot.alert != EventAlerts.MODE_OFF) askNotificationPermission()
            refreshSlot(slot)
        }
        remove.setOnClickListener {
            if (slots.size <= 1) {
                // The last row stays, but empty, so there is always somewhere to type.
                slot.name.setText("")
                slot.value = null
                slot.yearly.isChecked = false
                slot.minutes = -1
                slot.alert = EventAlerts.MODE_OFF
                refreshSlot(slot)
            } else {
                container.removeView(slot.row)
                slots.remove(slot)
                renumber()
                refreshAddButton()
            }
            renderPreview()
        }
        return slot
    }

    /** The button below the list: one more row, scrolled into view. */
    private fun addNewEvent() {
        if (slots.size >= EventsWidget.MAX_EVENTS) {
            Toast.makeText(
                this,
                "A widget can hold up to " + EventsWidget.MAX_EVENTS + " events. Use another Event Countdown widget for more.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val slot = addSlot(null)
        scroll.post { scroll.smoothScrollTo(0, container.top + slot.row.top) }
        slot.name.requestFocus()
    }

    private fun renumber() {
        slots.forEachIndexed { i, slot -> slot.caption.text = "Event " + (i + 1) }
    }

    private fun refreshAddButton() {
        btnAdd.text = "+ Add another event  (" + slots.size + " of " + EventsWidget.MAX_EVENTS + ")"
    }

    private fun refreshPerPage() {
        btnPerPage.text = "Shown at once: " + perPage + " events"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Only rows that have a date count. A date without a name is called "Event". */
    private fun currentConfig(): EventsCfg {
        val events = ArrayList<EventItem>()
        for (slot in slots) {
            val date = slot.value ?: continue
            val name = slot.name.text.toString().trim().ifEmpty { "Event" }
            events.add(EventItem(name, date, slot.yearly.isChecked, slot.alert, slot.minutes))
        }
        return EventsCfg(
            look = panel.look,
            events = events,
            notifyMinutes = notifyMinutes,
            title = etTitle.text.toString().trim(),
            perPage = perPage
        )
    }

    private fun save() {
        val cfg = currentConfig()
        ExtraStore.writeEvents(this, widgetId, cfg)
        EventsWidget.updateAll(this)
        if (cfg.events.any { it.alert != EventAlerts.MODE_OFF }) EventAlerts.warnIfBlocked(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }

    private fun renderPreview() {
        if (!::panel.isInitialized) return
        val cfg = currentConfig()
        // Room for the rows that are shown at once, plus the heading.
        val wanted = dp(28 * cfg.perPage + 52)
        val params = host.layoutParams
        if (params.height != wanted) {
            params.height = wanted
            host.layoutParams = params
        }
        val pages = EventsWidget.pageCount(cfg.events.size, cfg.perPage)
        previewPage = previewPage.coerceIn(0, pages - 1)

        val views = EventsWidget.build(this, widgetId, cfg, true, previewPage)
        val existing = previewView
        if (existing == null) {
            host.removeAllViews()
            val v = views.apply(this, host)
            host.addView(v)
            previewView = v
            // The arrows of the preview flip its pages, so every page can be checked before saving.
            v.findViewById<View>(R.id.e_prev).setOnClickListener { flipPreview(-1) }
            v.findViewById<View>(R.id.e_next).setOnClickListener { flipPreview(1) }
        } else {
            views.reapply(this, existing)
        }
    }

    private fun flipPreview(step: Int) {
        val cfg = currentConfig()
        val pages = EventsWidget.pageCount(cfg.events.size, cfg.perPage)
        previewPage = (previewPage.coerceIn(0, pages - 1) + step + pages) % pages
        renderPreview()
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

    /** The time of one event. "No time" in the dialog goes back to counting days only. */
    private fun pickEventTime(slot: Slot) {
        val start = if (slot.minutes >= 0) slot.minutes else 9 * 60
        val dialog = TimePickerDialog(
            this,
            { _, h, m ->
                slot.minutes = h * 60 + m
                refreshSlot(slot)
                renderPreview()
            },
            start / 60, start % 60, false
        )
        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, "No time") { _, _ ->
            slot.minutes = -1
            refreshSlot(slot)
            renderPreview()
        }
        dialog.show()
    }

    private fun pickAlertTime() {
        TimePickerDialog(
            this,
            { _, h, m ->
                notifyMinutes = h * 60 + m
                refreshTimeButton()
            },
            notifyMinutes / 60, notifyMinutes % 60, false
        ).show()
    }

    private fun refreshSlot(slot: Slot) {
        val date = slot.value
        slot.dateButton.text = if (date == null) "Pick date" else DATE_FMT.format(date)

        val timed = slot.minutes >= 0
        slot.timeButton.text = "Time: " +
            (if (timed) TIME_FMT.format(LocalTime.of(slot.minutes / 60, slot.minutes % 60)) else "none")
        slot.timeButton.setBackgroundResource(if (timed) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        slot.timeButton.setTextColor(getColor(if (timed) R.color.on_accent else R.color.fg))

        val on = slot.alert != EventAlerts.MODE_OFF
        slot.alertButton.text = "Alert: " + EventAlerts.modeLabel(slot.alert)
        slot.alertButton.setBackgroundResource(if (on) R.drawable.bg_button_primary else R.drawable.bg_button_outline)
        slot.alertButton.setTextColor(getColor(if (on) R.color.on_accent else R.color.fg))
    }

    private fun refreshTimeButton() {
        btnTime.text = "All-day alert time: " + TIME_FMT.format(LocalTime.of(notifyMinutes / 60, notifyMinutes % 60))
    }

    /** Shows one alert now, in the strongest style chosen on any event, so the sound can be checked. */
    private fun sendTestAlert() {
        if (!notificationsAllowed()) {
            askNotificationPermission()
            Toast.makeText(
                this,
                "Allow notifications for Time Since (in the box that appears, or in the phone's app settings), then tap the test again.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val strongest = slots.maxOfOrNull { it.alert } ?: EventAlerts.MODE_OFF
        val mode = if (strongest == EventAlerts.MODE_OFF) EventAlerts.MODE_NOTIFY else strongest
        EventAlerts.post(this, "Test alert", mode, "This is how an alert from your Event Countdown looks and sounds.")
    }

    private fun notificationsAllowed(): Boolean = EventAlerts.notificationsAllowed(this)

    /** Android 13 and newer ask before an app may show notifications. */
    private fun askNotificationPermission() = EventAlerts.askNotificationPermission(this)

    companion object {
        private const val START_ROWS = 4
        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

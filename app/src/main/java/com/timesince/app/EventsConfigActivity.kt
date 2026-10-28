package com.timesince.app

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings for one Event countdown widget: up to four events, each with a date, an optional time and an
 * optional alert (notification or ringtone), the alert time for events without a time, colors and transparency.
 */
class EventsConfigActivity : Activity() {

    private class Slot(
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

    private lateinit var panel: LookPanel
    private lateinit var host: FrameLayout
    private lateinit var btnTime: Button
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
        val container = findViewById<LinearLayout>(R.id.events_container)
        val cfg = ExtraStore.readEvents(this, widgetId)
        notifyMinutes = cfg.notifyMinutes

        for (i in 0 until MAX_EVENTS) {
            val row = layoutInflater.inflate(R.layout.item_event_slot, container, false)
            container.addView(row)

            row.findViewById<TextView>(R.id.ev_caption).text = "Event " + (i + 1)
            val slot = Slot(
                row.findViewById<EditText>(R.id.ev_name),
                row.findViewById<Button>(R.id.ev_date),
                row.findViewById<CheckBox>(R.id.ev_yearly),
                row.findViewById<Button>(R.id.ev_time),
                row.findViewById<Button>(R.id.ev_alert)
            )
            val clear = row.findViewById<Button>(R.id.ev_clear)

            // Fill in the saved values first, then start listening for changes.
            val saved = cfg.events.getOrNull(i)
            if (saved != null) {
                slot.name.setText(saved.name)
                slot.value = saved.date
                slot.yearly.isChecked = saved.yearly
                slot.alert = saved.alert
                slot.minutes = saved.minutes
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
            slot.timeButton.setOnClickListener { pickEventTime(slot) }
            slot.alertButton.setOnClickListener {
                // Off, then Notification, then Ringtone, then Off again.
                slot.alert = (slot.alert + 1) % 3
                if (slot.alert != EventAlerts.MODE_OFF) askNotificationPermission()
                refreshSlot(slot)
            }
            clear.setOnClickListener {
                slot.name.setText("")
                slot.value = null
                slot.yearly.isChecked = false
                slot.minutes = -1
                slot.alert = EventAlerts.MODE_OFF
                refreshSlot(slot)
                renderPreview()
            }
            slots.add(slot)
        }

        panel = LookPanel(this, findViewById<View>(android.R.id.content), cfg.look) { renderPreview() }

        btnTime.setOnClickListener { pickAlertTime() }
        findViewById<Button>(R.id.btn_test_alert).setOnClickListener { sendTestAlert() }
        findViewById<Button>(R.id.btn_save).setOnClickListener { save() }

        refreshTimeButton()
        renderPreview()
    }

    /** Only rows that have a date count. A date without a name is called "Event". */
    private fun currentConfig(): EventsCfg {
        val events = ArrayList<EventItem>()
        for (slot in slots) {
            val date = slot.value ?: continue
            val name = slot.name.text.toString().trim().ifEmpty { "Event" }
            events.add(EventItem(name, date, slot.yearly.isChecked, slot.alert, slot.minutes))
        }
        return EventsCfg(look = panel.look, events = events, notifyMinutes = notifyMinutes)
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
            Toast.makeText(this, "Allow notifications, then tap the test again.", Toast.LENGTH_LONG).show()
            return
        }
        val strongest = slots.maxOfOrNull { it.alert } ?: EventAlerts.MODE_OFF
        val mode = if (strongest == EventAlerts.MODE_OFF) EventAlerts.MODE_NOTIFY else strongest
        EventAlerts.post(this, "Test alert", mode, "This is how an alert from your Event Countdown looks and sounds.")
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Android 13 and newer ask before an app may show notifications. */
    private fun askNotificationPermission() {
        if (!notificationsAllowed()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    companion object {
        private const val MAX_EVENTS = 4
        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

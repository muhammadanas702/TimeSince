package com.timesince.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Two jobs in one screen:
 *  - opened from the launcher: a live preview, plus help for adding the widget;
 *  - opened for a widget (when it is added, or when it is tapped): choose that widget's date.
 */
class MainActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var date: LocalDate
    private lateinit var time: LocalTime

    private lateinit var etLabel: EditText
    private lateinit var btnDate: Button
    private lateinit var btnTime: Button
    private lateinit var tvTitle: TextView
    private lateinit var tvSub: TextView
    private lateinit var cells: List<TextView>

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L - (System.currentTimeMillis() % 1000L) + 5L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val configuring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID

        // If the user backs out while adding a widget, the widget must not be placed.
        if (configuring) {
            setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        etLabel = findViewById(R.id.et_label)
        btnDate = findViewById(R.id.btn_date)
        btnTime = findViewById(R.id.btn_time)
        tvTitle = findViewById(R.id.tv_title)
        tvSub = findViewById(R.id.tv_sub)
        cells = listOf(
            findViewById(R.id.v_years),
            findViewById(R.id.v_months),
            findViewById(R.id.v_days),
            findViewById(R.id.v_hours),
            findViewById(R.id.v_minutes),
            findViewById(R.id.v_seconds)
        )

        val cfg = if (configuring) {
            Store.forWidget(this, widgetId)
        } else {
            Store.read(this, Store.PREVIEW_ID) ?: Store.EXAMPLE
        }
        date = cfg.start.toLocalDate()
        time = cfg.start.toLocalTime()
        etLabel.setText(cfg.label)

        btnDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date = LocalDate.of(y, m + 1, d)
                    render()
                },
                date.year, date.monthValue - 1, date.dayOfMonth
            ).show()
        }
        btnTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, h, m ->
                    time = LocalTime.of(h, m)
                    render()
                },
                time.hour, time.minute, DateFormat.is24HourFormat(this)
            ).show()
        }

        findViewById<Button>(R.id.btn_save).apply {
            text = if (configuring) "Save widget" else "Save as default"
            setOnClickListener { save(configuring) }
        }

        val help = findViewById<View>(R.id.card_help)
        val pin = findViewById<Button>(R.id.btn_pin)
        if (configuring) {
            help.visibility = View.GONE
        } else {
            val manager = AppWidgetManager.getInstance(this)
            if (manager.isRequestPinAppWidgetSupported) {
                pin.setOnClickListener {
                    manager.requestPinAppWidget(
                        ComponentName(this, TimeSinceWidget::class.java), null, null
                    )
                }
            } else {
                pin.visibility = View.GONE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun save(configuring: Boolean) {
        val cfg = Config(etLabel.text.toString().trim().take(40), LocalDateTime.of(date, time))
        Store.write(this, Store.PREVIEW_ID, cfg)
        if (configuring) {
            Store.write(this, widgetId, cfg)
            TimeSinceWidget.updateAll(this)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        } else {
            Toast.makeText(this, "Saved. New widgets will start with this date.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun render() {
        val start = LocalDateTime.of(date, time)
        val p = TimeMath.compute(start, LocalDateTime.now())

        val values = listOf(p.years, p.months, p.days, p.hours, p.minutes, p.seconds)
        for (i in values.indices) {
            val text = values[i].toString().padStart(2, '0')
            if (cells[i].text.toString() != text) cells[i].text = text
        }

        val label = etLabel.text.toString().trim()
        tvTitle.text = label.ifEmpty { if (p.future) "Time until" else "Time since" }

        val clock = if (time != LocalTime.MIDNIGHT) " · " + TIME_FMT.format(time) else ""
        tvSub.text = (if (p.future) "Counting down to " else "Counting up from ") +
            LONG_DATE_FMT.format(date) + clock

        btnDate.text = "Date: " + SHORT_DATE_FMT.format(date)
        btnTime.text = "Time: " + TIME_FMT.format(time)
    }

    companion object {
        private val LONG_DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
        private val SHORT_DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
        private val TIME_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

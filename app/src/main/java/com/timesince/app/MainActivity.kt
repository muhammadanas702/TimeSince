package com.timesince.app

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home screen of the app: a live preview, an Add button for every kind of widget, and the list of
 * widgets already on the home screen (tap one to change its settings).
 */
class MainActivity : Activity() {

    /** One kind of widget: how to add it, which screen edits it, and how to describe a placed one. */
    private class WidgetKind(
        val title: String,
        val blurb: String,
        val receiver: Class<*>,
        val config: Class<*>,
        val describe: (Int) -> ListInfo
    )

    private class ListInfo(val title: String, val sub: String, val color: Int)

    private val kinds: List<WidgetKind> = listOf(
        WidgetKind(
            "Time Since",
            "Years, months, days and a live clock since or until a date.",
            TimeSinceWidget::class.java, ConfigActivity::class.java
        ) { id -> describeSince(id) },
        WidgetKind(
            "Clock and Date",
            "A big live clock with today's date.",
            ClockWidget::class.java, ClockConfigActivity::class.java
        ) { id -> describeClock(id) },
        WidgetKind(
            "World Clock",
            "Your time in a bright pill, plus two or three cities. Search any city or country.",
            WorldClockWidget::class.java, WorldClockConfigActivity::class.java
        ) { id -> describeWorld(id) },
        WidgetKind(
            "Event Countdown",
            "Up to four events with the days left. Birthdays can repeat every year.",
            EventsWidget::class.java, EventsConfigActivity::class.java
        ) { id -> describeEvents(id) },
        WidgetKind(
            "Month Calendar",
            "A month at a glance with today circled and arrows for other months.",
            CalendarWidget::class.java, CalendarConfigActivity::class.java
        ) { id -> describeCalendar(id) }
    )

    private lateinit var previewHost: FrameLayout
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    private var previewView: View? = null
    private var previewStyle = -1

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewHost = findViewById(R.id.preview_host)
        list = findViewById(R.id.list_widgets)
        empty = findViewById(R.id.tv_empty)

        buildAddButtons()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    /** One row with an Add button per kind of widget. Not every launcher can pin widgets. */
    private fun buildAddButtons() {
        val pinList = findViewById<LinearLayout>(R.id.pin_list)
        val hint = findViewById<TextView>(R.id.tv_pin_hint)
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            pinList.visibility = View.GONE
            hint.visibility = View.VISIBLE
            return
        }
        val inflater = LayoutInflater.from(this)
        for (kind in kinds) {
            val row = inflater.inflate(R.layout.item_add_widget, pinList, false)
            row.findViewById<TextView>(R.id.ad_title).text = kind.title
            row.findViewById<TextView>(R.id.ad_blurb).text = kind.blurb
            row.findViewById<Button>(R.id.ad_button).setOnClickListener {
                manager.requestPinAppWidget(ComponentName(this, kind.receiver), null, null)
            }
            pinList.addView(row)
        }
    }

    private fun refresh() {
        renderPreview()
        renderList()
    }

    /** Shows the most recently saved Time Since look, or the example date before anything has been saved. */
    private fun renderPreview() {
        val cfg = Store.read(this, Store.PREVIEW_ID) ?: Store.EXAMPLE
        val (views, _) = TimeSinceWidget.build(this, Store.PREVIEW_ID, cfg, true)
        val existing = previewView
        if (existing == null || previewStyle != cfg.style) {
            previewHost.removeAllViews()
            val v = views.apply(this, previewHost)
            previewHost.addView(v)
            previewView = v
            previewStyle = cfg.style
            val lp = previewHost.layoutParams
            lp.height = dp(Styles.previewHeightDp(cfg.style))
            previewHost.layoutParams = lp
        } else {
            views.reapply(this, existing)
        }
    }

    /** Every widget on the home screen, of every kind. Tapping a row opens that widget's settings. */
    private fun renderList() {
        val manager = AppWidgetManager.getInstance(this)
        list.removeAllViews()

        val inflater = LayoutInflater.from(this)
        var count = 0
        for (kind in kinds) {
            val ids = manager.getAppWidgetIds(ComponentName(this, kind.receiver))
            for (id in ids) {
                count++
                val info = kind.describe(id)
                val row = inflater.inflate(R.layout.item_widget, list, false)

                row.findViewById<TextView>(R.id.it_title).text = info.title
                row.findViewById<TextView>(R.id.it_sub).text = info.sub

                val dot = GradientDrawable()
                dot.shape = GradientDrawable.OVAL
                dot.setColor(info.color)
                row.findViewById<View>(R.id.it_dot).background = dot

                row.setOnClickListener {
                    startActivity(
                        Intent(this, kind.config)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    )
                }
                list.addView(row)
            }
        }
        empty.visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    private fun describeSince(id: Int): ListInfo {
        val cfg = Store.forWidget(this, id)
        val p = TimeMath.compute(cfg.start, LocalDateTime.now())
        return ListInfo(
            cfg.label.ifBlank { if (p.future) "Time until" else "Time since" },
            TimeMath.daysLabel(p.totalDays) + (if (p.future) " to go · " else " · ") +
                DATE_FMT.format(cfg.start),
            cfg.accent
        )
    }

    private fun describeClock(id: Int): ListInfo {
        val cfg = ExtraStore.readClock(this, id)
        val sub = (if (cfg.h24) "24-hour" else "12-hour") + (if (cfg.seconds) " · with seconds" else "")
        return ListInfo("Clock and Date", sub, cfg.look.accent)
    }

    private fun describeWorld(id: Int): ListInfo {
        val cfg = ExtraStore.readWorld(this, id)
        val names = cfg.zones.joinToString(" · ") {
            if (cfg.shortNames) Cities.shortName(it) else it.name
        }
        return ListInfo("World Clock", names, cfg.look.accent)
    }

    private fun describeEvents(id: Int): ListInfo {
        val cfg = ExtraStore.readEvents(this, id)
        val next = EventMath.order(cfg.events, LocalDate.now()).firstOrNull()
        val sub = if (next == null) {
            "No events yet. Tap to add some."
        } else {
            val n = cfg.events.size
            n.toString() + (if (n == 1) " event" else " events") + " · " +
                next.item.name + ": " + EventMath.label(next.days)
        }
        return ListInfo("Event Countdown", sub, cfg.look.accent)
    }

    private fun describeCalendar(id: Int): ListInfo {
        val cfg = ExtraStore.readCalendar(this, id)
        val sub = "Week starts on " + (if (cfg.mondayFirst) "Monday" else "Sunday")
        return ListInfo("Month Calendar", sub, cfg.look.accent)
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}

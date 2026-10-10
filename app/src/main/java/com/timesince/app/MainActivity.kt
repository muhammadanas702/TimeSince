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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home screen of the app: a live preview, the list of widgets already on the home screen
 * (tap one to change its date, style or colors), and a button to add another widget.
 */
class MainActivity : Activity() {

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

        val pin = findViewById<Button>(R.id.btn_pin)
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

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun refresh() {
        renderPreview()
        renderList()
    }

    /** Shows the most recently saved look, or the example date before anything has been saved. */
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

    private fun renderList() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, TimeSinceWidget::class.java))
        list.removeAllViews()
        empty.visibility = if (ids.isEmpty()) View.VISIBLE else View.GONE

        val inflater = LayoutInflater.from(this)
        val now = LocalDateTime.now()
        for (id in ids) {
            val cfg = Store.forWidget(this, id)
            val p = TimeMath.compute(cfg.start, now)
            val row = inflater.inflate(R.layout.item_widget, list, false)

            row.findViewById<TextView>(R.id.it_title).text =
                cfg.label.ifBlank { if (p.future) "Time until" else "Time since" }
            row.findViewById<TextView>(R.id.it_sub).text =
                TimeMath.daysLabel(p.totalDays) + (if (p.future) " to go · " else " · ") +
                    DATE_FMT.format(cfg.start)

            val dot = GradientDrawable()
            dot.shape = GradientDrawable.OVAL
            dot.setColor(cfg.accent)
            row.findViewById<View>(R.id.it_dot).background = dot

            row.setOnClickListener {
                startActivity(
                    Intent(this, ConfigActivity::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                )
            }
            list.addView(row)
        }
    }

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    }
}

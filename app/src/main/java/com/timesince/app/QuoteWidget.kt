package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

/**
 * Quote of the Day: a short quote in a soft serif italic. It is a new one every day (a quiet alarm at midnight
 * redraws the widget), and the small refresh icon in the corner shows the next quote at once. The quotes are
 * the built-in ones or lines the user wrote; "text - author" shows the author under the quote.
 */
class QuoteWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_NEXT -> next(context, intent)
            ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    /** The refresh icon: the next quote, in this widget only. */
    private fun next(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || ExtraStore.isRemoved(context, id)) return
        ExtraStore.nextQuote(context, id)
        AppWidgetManager.getInstance(context)
            .updateAppWidget(id, build(context, id, ExtraStore.readQuote(context, id), false))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    override fun onDisabled(context: Context) {
        DailyTick.cancel(context, QuoteWidget::class.java, ACTION_TICK)
    }

    companion object {
        const val ACTION_NEXT = "com.timesince.app.QUOTE_NEXT"
        const val ACTION_TICK = "com.timesince.app.QUOTE_TICK"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuoteWidget::class.java))
            if (ids.isEmpty()) {
                DailyTick.cancel(context, QuoteWidget::class.java, ACTION_TICK)
                return
            }
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, QuoteConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readQuote(context, id), false)
                    }
                )
            }
            DailyTick.schedule(context, QuoteWidget::class.java, ACTION_TICK)
        }

        /** The quote of today, for the app's list. */
        fun summary(id: Int, cfg: QuoteCfg): String =
            QuoteBank.pick(cfg, id, LocalDate.now().toEpochDay()).first

        private fun sizeSp(size: Int): Float = when (size) {
            0 -> 13f
            2 -> 20f
            else -> 16f
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: QuoteCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_quote)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val (text, author) = QuoteBank.pick(cfg, id, LocalDate.now().toEpochDay())
            views.setTextViewText(R.id.q_text, "“" + text + "”")
            views.setTextColor(R.id.q_text, look.fg)
            views.setTextViewTextSize(R.id.q_text, TypedValue.COMPLEX_UNIT_SP, sizeSp(cfg.size))

            views.setViewVisibility(R.id.q_author, if (author.isEmpty()) View.GONE else View.VISIBLE)
            views.setTextViewText(R.id.q_author, "— " + author)
            views.setTextColor(R.id.q_author, LookUtil.muted(look.fg))

            views.setInt(R.id.q_next, "setColorFilter", look.fg or (0xFF shl 24))
            views.setInt(R.id.q_next, "setImageAlpha", 150)

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, QuoteConfigActivity::class.java)
                )
                views.setOnClickPendingIntent(R.id.q_next, nextIntent(context, id))
            }
            return views
        }

        private fun nextIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, QuoteWidget::class.java)
                .setAction(ACTION_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context, id * 4 + 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

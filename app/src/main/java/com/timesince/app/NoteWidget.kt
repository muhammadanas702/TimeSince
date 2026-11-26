package com.timesince.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

private class NoteRow(val row: Int, val box: Int, val text: Int)

private val NOTE_ROWS = listOf(
    NoteRow(R.id.n1_row, R.id.n1_box, R.id.n1_text),
    NoteRow(R.id.n2_row, R.id.n2_box, R.id.n2_text),
    NoteRow(R.id.n3_row, R.id.n3_box, R.id.n3_text),
    NoteRow(R.id.n4_row, R.id.n4_box, R.id.n4_text),
    NoteRow(R.id.n5_row, R.id.n5_box, R.id.n5_text),
    NoteRow(R.id.n6_row, R.id.n6_box, R.id.n6_text),
    NoteRow(R.id.n7_row, R.id.n7_box, R.id.n7_text),
    NoteRow(R.id.n8_row, R.id.n8_box, R.id.n8_text)
)

/**
 * Sticky Note: a small pastel note on the home screen. It shows plain text, or a checklist whose items are ticked
 * by tapping them on the home screen (the widget redraws itself, no app needed). Tapping anywhere else opens
 * the settings, where the note is written. It needs no alarms: it only changes when the user changes it.
 */
class NoteWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) toggle(context, intent)
    }

    /** A tap on one checklist item: tick it, or untick it, and redraw just that widget. */
    private fun toggle(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || index < 0 || ExtraStore.isRemoved(context, id)) return
        ExtraStore.toggleNoteDone(context, id, index)
        AppWidgetManager.getInstance(context)
            .updateAppWidget(id, build(context, id, ExtraStore.readNote(context, id), false))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { ExtraStore.remove(context, it) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.timesince.app.NOTE_TOGGLE"
        private const val EXTRA_INDEX = "item_index"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NoteWidget::class.java))
            for (id in ids) {
                manager.updateAppWidget(
                    id,
                    if (ExtraStore.isRemoved(context, id)) {
                        RemovedWidget.build(context, id, NoteConfigActivity::class.java)
                    } else {
                        build(context, id, ExtraStore.readNote(context, id), false)
                    }
                )
            }
        }

        /** The text for the app's list: how many items are done, or the first line of the note. */
        fun summary(cfg: NoteCfg): String {
            if (cfg.checklist) {
                val items = cfg.items
                if (items.isEmpty()) return "Checklist is empty. Tap to add items."
                val done = items.indices.count { cfg.isDone(it) }
                return done.toString() + " of " + items.size + " done"
            }
            val first = cfg.text.lines().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            return first ?: "Empty note. Tap to write."
        }

        private fun sizeSp(size: Int): Float = when (size) {
            0 -> 12f
            2 -> 19f
            else -> 15f
        }

        /** Also used by the settings screen with preview = true. */
        fun build(context: Context, id: Int, cfg: NoteCfg, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_note)
            val look = cfg.look
            LookUtil.applyBackground(views, look)

            val muted = LookUtil.muted(look.fg)
            val items = cfg.items
            val list = cfg.checklist
            val title = cfg.title.trim()
            val sp = sizeSp(cfg.size)

            // The heading holds the title and, for a checklist, how many items are done.
            views.setViewVisibility(R.id.n_head, if (title.isEmpty() && !list) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.n_title, if (title.isEmpty()) View.INVISIBLE else View.VISIBLE)
            views.setTextViewText(R.id.n_title, title)
            views.setTextColor(R.id.n_title, look.accent)
            val showCount = list && items.isNotEmpty()
            views.setViewVisibility(R.id.n_count, if (showCount) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.n_count, items.indices.count { cfg.isDone(it) }.toString() + "/" + items.size)
            views.setTextColor(R.id.n_count, muted)

            views.setViewVisibility(R.id.n_body, if (list) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.n_list, if (list) View.VISIBLE else View.GONE)

            if (!list) {
                val empty = cfg.text.isBlank()
                views.setTextViewText(R.id.n_body, if (empty) "Tap to write a note" else cfg.text.trim())
                views.setTextColor(R.id.n_body, if (empty) muted else look.fg)
                views.setTextViewTextSize(R.id.n_body, TypedValue.COMPLEX_UNIT_SP, sp)
            } else {
                NOTE_ROWS.forEachIndexed { i, r ->
                    views.setTextViewTextSize(r.text, TypedValue.COMPLEX_UNIT_SP, sp)
                    if (items.isEmpty() && i == 0) {
                        views.setViewVisibility(r.row, View.VISIBLE)
                        views.setViewVisibility(r.box, View.GONE)
                        views.setTextViewText(r.text, "Tap to add items")
                        views.setTextColor(r.text, muted)
                    } else if (i < items.size) {
                        val done = cfg.isDone(i)
                        views.setViewVisibility(r.row, View.VISIBLE)
                        views.setViewVisibility(r.box, View.VISIBLE)
                        views.setImageViewResource(r.box, if (done) R.drawable.ic_check_on else R.drawable.ic_check_off)
                        views.setInt(r.box, "setColorFilter", (if (done) look.accent else look.fg) or (0xFF shl 24))
                        views.setInt(r.box, "setImageAlpha", if (done) 255 else 150)
                        if (done) {
                            // A ticked item is struck through and faded.
                            val struck = SpannableString(items[i])
                            struck.setSpan(StrikethroughSpan(), 0, struck.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            views.setTextViewText(r.text, struck)
                            views.setTextColor(r.text, muted)
                        } else {
                            views.setTextViewText(r.text, items[i])
                            views.setTextColor(r.text, look.fg)
                        }
                        if (!preview) views.setOnClickPendingIntent(r.row, tickIntent(context, id, i))
                    } else {
                        views.setViewVisibility(r.row, View.GONE)
                    }
                }
            }

            if (!preview) {
                views.setOnClickPendingIntent(
                    R.id.w_root,
                    LookUtil.openSettings(context, id, NoteConfigActivity::class.java)
                )
            }
            return views
        }

        /** Each widget and each item gets its own request code, so the eight intents stay separate. */
        private fun tickIntent(context: Context, id: Int, index: Int): PendingIntent {
            val intent = Intent(context, NoteWidget::class.java)
                .setAction(ACTION_TOGGLE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_INDEX, index)
            return PendingIntent.getBroadcast(
                context, id * 16 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

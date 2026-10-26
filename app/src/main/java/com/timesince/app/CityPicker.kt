package com.timesince.app

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView

/** A list of cities with a search box on top. Typing filters by city, country or time zone. */
object CityPicker {

    fun show(activity: Activity, title: String, options: List<Zone>, onPick: (Zone) -> Unit) {
        val shown = ArrayList<Zone>()
        val adapter = ArrayAdapter<String>(
            activity, android.R.layout.simple_list_item_1, ArrayList<String>()
        )

        fun fill(query: String) {
            shown.clear()
            for (zone in options) {
                if (zone.matches(query)) shown.add(zone)
            }
            adapter.clear()
            adapter.addAll(shown.map { it.label() })
            adapter.notifyDataSetChanged()
        }

        val search = EditText(activity)
        search.hint = "Search city or country"
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true

        val list = ListView(activity)
        list.adapter = adapter

        val box = LinearLayout(activity)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0)
        box.addView(
            search,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        box.addView(
            list,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                activity.dp(300)
            )
        )

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()

        list.setOnItemClickListener { _, _, position, _ ->
            val zone = shown.getOrNull(position)
            if (zone != null) {
                dialog.dismiss()
                onPick(zone)
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                fill(s?.toString() ?: "")
            }
        })

        fill("")
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        dialog.show()
        search.requestFocus()
    }
}

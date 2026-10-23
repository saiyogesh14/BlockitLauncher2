package com.blockit.launcher

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("blockit_prefs", Context.MODE_PRIVATE)

    // Dock: ordered list of 4 package names (empty string = empty slot)
    var dockPackages: List<String>
        get() = listOf(
            prefs.getString("dock_0", "") ?: "",
            prefs.getString("dock_1", "") ?: "",
            prefs.getString("dock_2", "") ?: "",
            prefs.getString("dock_3", "") ?: ""
        )
        set(value) {
            prefs.edit().apply {
                value.forEachIndexed { i, pkg -> putString("dock_$i", pkg) }
            }.apply()
        }

    fun setDockSlot(slot: Int, packageName: String) {
        prefs.edit().putString("dock_$slot", packageName).apply()
    }

    fun getDockSlot(slot: Int): String = prefs.getString("dock_$slot", "") ?: ""

    // Widget app IDs stored as comma-separated list
    var widgetIds: List<Int>
        get() {
            val s = prefs.getString("widget_ids", "") ?: ""
            return if (s.isBlank()) emptyList()
            else s.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        set(value) {
            prefs.edit().putString("widget_ids", value.joinToString(",")).apply()
        }

    fun addWidgetId(id: Int) {
        val current = widgetIds.toMutableList()
        current.add(id)
        widgetIds = current
    }

    fun removeWidgetId(id: Int) {
        widgetIds = widgetIds.filter { it != id }
    }

    // Status bar visibility
    var showStatusBar: Boolean
        get() = prefs.getBoolean("show_status_bar", true)
        set(value) = prefs.edit().putBoolean("show_status_bar", value).apply()
}

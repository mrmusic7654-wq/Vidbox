package com.vidbox.domain.util

import java.util.Locale

object DisplayFormat {
    fun bytes(value: Long?): String {
        if (value == null || value < 0) return "Size unknown"
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var amount = value.toDouble() / 1024
        var unit = 0
        while (amount >= 1024 && unit < units.lastIndex) { amount /= 1024; unit++ }
        return String.format(Locale.getDefault(), if (amount >= 100) "%.0f %s" else "%.1f %s", amount, units[unit])
    }
    fun duration(seconds: Long?): String {
        if (seconds == null || seconds < 0) return "—"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}

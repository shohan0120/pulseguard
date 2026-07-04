package com.pulseguard.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object TimeFormat {

    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val clockWithSeconds = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun clock(epochMs: Long): String = clock.format(Date(epochMs))

    fun clockSeconds(epochMs: Long): String = clockWithSeconds.format(Date(epochMs))

    /** "just now", "5 min ago", "2 h ago", or a clock time for older events. */
    fun relative(epochMs: Long, now: Long = System.currentTimeMillis()): String {
        if (epochMs <= 0L) return "never"
        val deltaMs = now - epochMs
        val past = deltaMs >= 0
        val minutes = abs(deltaMs) / 60_000
        val suffix = if (past) "ago" else "from now"
        return when {
            abs(deltaMs) < 60_000 -> if (past) "just now" else "in <1 min"
            minutes < 60 -> "$minutes min $suffix"
            minutes < 24 * 60 -> "${minutes / 60} h $suffix"
            else -> clock(epochMs)
        }
    }
}

package io.github.meko123456.dayblocks.core.designsystem.time

import androidx.compose.runtime.Composable

/**
 * A plan-day minute as a clock reading. Minutes past midnight wrap: minute 1500 of Monday's plan is
 * "01:00", because that is what the clock on the wall will say.
 *
 * Pure and platform-free so it can be tested exhaustively; which of the two styles to use is the
 * device's decision, read by [rememberIs24HourFormat].
 */
fun formatClock(planMinute: Int, is24Hour: Boolean): String {
    val m = planMinute.mod(24 * 60)
    val hour = m / 60
    val minute = m % 60
    return if (is24Hour) {
        "${hour.twoDigits()}:${minute.twoDigits()}"
    } else {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val suffix = if (hour < 12) "AM" else "PM"
        "$h12:${minute.twoDigits()} $suffix"
    }
}

/** "2h", "45m", "1h 20m" — the way the buddy says it. */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

private fun Int.twoDigits(): String = toString().padStart(2, '0')

/**
 * Whether the device is set to a 24-hour clock. Read from the platform rather than guessed from
 * the locale, because the user can override their locale's default in system settings and the app
 * must follow what they chose.
 */
@Composable
expect fun rememberIs24HourFormat(): Boolean

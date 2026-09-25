package io.github.meko123456.dayblocks.core.common

/**
 * A plan-day minute as a clock reading. Minutes past midnight wrap: minute 1500 of Monday's plan is
 * "01:00", because that is what the clock on the wall will say.
 *
 * Here rather than in the design system because not all of the app's text is drawn by Compose.
 * A notification is written by the planner, often in a broadcast receiver or a background refresh
 * with no screen at all, and it has to read the clock the same way Today does.
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

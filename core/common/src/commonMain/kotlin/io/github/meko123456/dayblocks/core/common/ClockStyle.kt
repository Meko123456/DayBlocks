package io.github.meko123456.dayblocks.core.common

/**
 * Whether the device is set to a 24-hour clock, readable without a screen.
 *
 * Compose code asks `rememberIs24HourFormat()`. Notification text is written where there is no
 * composition to ask — a broadcast receiver after a reboot, an iOS background refresh — and must
 * still say "13:00" or "1:00 PM" the way the user chose.
 */
fun interface ClockStyle {
    fun is24Hour(): Boolean
}

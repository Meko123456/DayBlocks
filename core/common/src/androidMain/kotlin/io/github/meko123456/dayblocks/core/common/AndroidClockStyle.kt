package io.github.meko123456.dayblocks.core.common

import android.content.Context
import android.text.format.DateFormat

/** The system setting, which follows the user's override rather than their locale's default. */
class AndroidClockStyle(private val context: Context) : ClockStyle {
    override fun is24Hour(): Boolean = DateFormat.is24HourFormat(context)
}

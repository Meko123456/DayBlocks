package io.github.meko123456.dayblocks.core.common

import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * iOS has no is-24-hour flag; it has a format template. The "j" skeleton asks for the hour in the
 * user's preferred style, and the answer contains an "a" (the AM/PM marker) exactly when that
 * style is 12-hour — including when the 24-Hour Time switch overrides the region's default.
 */
class IosClockStyle : ClockStyle {
    override fun is24Hour(): Boolean {
        val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale) ?: "HH"
        return !pattern.contains('a')
    }
}

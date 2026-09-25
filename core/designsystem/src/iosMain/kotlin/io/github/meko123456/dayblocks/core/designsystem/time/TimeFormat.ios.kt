package io.github.meko123456.dayblocks.core.designsystem.time

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale

/**
 * iOS has no is-24-hour flag; it has a format template. The "j" skeleton asks for the hour in the
 * user's preferred style, and the answer contains an "a" (the AM/PM marker) exactly when that
 * style is 12-hour — including when the user has flipped the 24-Hour Time switch against their
 * region's default.
 */
@Composable
actual fun rememberIs24HourFormat(): Boolean = remember {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, NSLocale.currentLocale) ?: "HH"
    !pattern.contains('a')
}

package io.github.meko123456.dayblocks.core.designsystem.time

import androidx.compose.runtime.Composable

/**
 * Whether the device is set to a 24-hour clock. Read from the platform rather than guessed from
 * the locale, because the user can override their locale's default in system settings and the app
 * must follow what they chose.
 *
 * The formatting itself is `formatClock` in :core:common, where notification text can reach it too.
 */
@Composable
expect fun rememberIs24HourFormat(): Boolean

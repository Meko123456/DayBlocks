package io.github.meko123456.dayblocks.composeapp.reminders

import androidx.compose.runtime.Composable

/** Whether reminders can reach the user, and the two ways to fix it when they cannot. */
interface ReminderAccess {
    /** Null until the platform has answered. */
    val notificationsAllowed: Boolean?

    /** Whether a reminder can fire on the minute. Only Android 12 and later can say no. */
    val exactTimingAllowed: Boolean

    fun requestNotifications()

    fun openExactTimingSettings()
}

/** Re-read whenever the app comes back to the foreground: the fix happens in system settings. */
@Composable
expect fun rememberReminderAccess(onGranted: () -> Unit): ReminderAccess

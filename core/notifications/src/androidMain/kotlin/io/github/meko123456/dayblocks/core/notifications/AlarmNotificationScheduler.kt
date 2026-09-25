package io.github.meko123456.dayblocks.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [NotificationScheduler] on AlarmManager: one alarm per notification, delivered to [deliverTo],
 * which posts it. [refreshTo] is woken every [REFRESH_INTERVAL_MS] to roll the window forward even
 * on a day when nothing else would start the app.
 *
 * Exact alarms when the user allows them, a ten-minute window otherwise — a reminder a few minutes
 * late is better than none. Alarms survive neither a reboot nor a force-stop; the app reschedules after
 * both.
 */
class AlarmNotificationScheduler(
    private val context: Context,
    private val deliverTo: Class<out BroadcastReceiver>,
    private val refreshTo: Class<out BroadcastReceiver>,
) : NotificationScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    // AlarmManager cannot list what is pending, so the ids scheduled last time are kept here: the
    // only way to cancel an alarm is to rebuild its PendingIntent.
    private val ledger = context.getSharedPreferences(LEDGER_FILE, Context.MODE_PRIVATE)

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) = withContext(Dispatchers.IO) {
        val ids = notifications.mapTo(mutableSetOf()) { it.id }
        for (stale in ledger.getStringSet(KEY_IDS, emptySet()).orEmpty() - ids) {
            PendingIntent.getBroadcast(
                context, 0, ReminderIntents.delivery(context, deliverTo, stale),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
        }
        notifications.forEach(::schedule)
        ledger.edit().putStringSet(KEY_IDS, ids).apply()
        scheduleRefresh()
    }

    private fun schedule(notification: ScheduledNotification) {
        val operation = PendingIntent.getBroadcast(
            context, 0, ReminderIntents.delivery(context, deliverTo, notification),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = notification.at.toEpochMilliseconds()
        try {
            if (ExactAlarms.allowed(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, operation)
            }
        } catch (revoked: SecurityException) {
            // Allowed a moment ago and revoked since: late beats never.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MS, operation)
        }
    }

    private fun scheduleRefresh() {
        val refresh = PendingIntent.getBroadcast(
            context, 0, Intent(context, refreshTo).setAction(ReminderIntents.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Not a wake-up alarm: it only has to run before the window empties, and the next time the
        // phone is awake is soon enough.
        alarms.set(AlarmManager.RTC, System.currentTimeMillis() + REFRESH_INTERVAL_MS, refresh)
    }

    private companion object {
        const val LEDGER_FILE = "scheduled_reminders"
        const val KEY_IDS = "ids"
        // Android 14 widens any shorter window to ten minutes, so ask for what will be given.
        const val INEXACT_WINDOW_MS = 10 * 60 * 1000L
        const val REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L
    }
}

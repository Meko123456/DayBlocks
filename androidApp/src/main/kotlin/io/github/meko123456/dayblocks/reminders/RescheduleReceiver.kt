package io.github.meko123456.dayblocks.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.meko123456.dayblocks.DayBlocksApplication
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderRescheduler
import io.github.meko123456.dayblocks.core.notifications.ReminderIntents
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Rebuilds the schedule after everything that invalidates it without touching the plan: a reboot
 * (alarms do not survive one), an update, the clock or time zone changing — Android reports a
 * switch between 12- and 24-hour time as a time change too, which matters because the text says
 * "13:00" or "1:00 PM" — the language changing, exact alarms being allowed, and the scheduler's
 * own refresh that keeps the window rolling.
 *
 * Not exported: these are system broadcasts, which reach a receiver that is not exported, and no
 * other app has any business asking for a reschedule.
 */
class RescheduleReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        (context.applicationContext as DayBlocksApplication).scope.launch {
            try {
                get<ReminderRescheduler>().rescheduleNow()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, spelled out because
            // the constant only exists from Android 12 and this receiver runs on 8.
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            ReminderIntents.ACTION_REFRESH,
        )
    }
}

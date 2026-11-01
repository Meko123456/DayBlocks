package io.github.meko123456.dayblocks.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Intent
import android.os.ParcelFileDescriptor
import io.github.meko123456.dayblocks.core.notifications.ExactAlarms
import io.github.meko123456.dayblocks.core.notifications.ReminderIntents

/**
 * Auto Backup does the backing up and restoring (fullBackupOnly, with the rules in res/xml). This
 * agent is here for the moment a restore finishes, on a new phone or after a reinstall: the plan
 * comes back, but its reminders don't, since they're alarms, and nothing would start the app to set
 * them again until it happened to be opened.
 *
 * The agent runs in a restricted process, without [io.github.meko123456.dayblocks.DayBlocksApplication]
 * or Koin, so it doesn't reschedule anything itself. It sets the scheduler's own refresh alarm a few
 * seconds out. When that fires, the app starts as usual and rebuilds every reminder, and the next
 * refresh it schedules replaces this one: the PendingIntent is the same.
 */
class RestoreAgent : BackupAgent() {

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        val alarms = getSystemService(AlarmManager::class.java) ?: return
        val refresh = PendingIntent.getBroadcast(
            this, 0, Intent(this, RescheduleReceiver::class.java).setAction(ReminderIntents.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + RESTART_DELAY_MS
        try {
            if (ExactAlarms.allowed(this)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, refresh)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, refresh)
            }
        } catch (revoked: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, refresh)
        }
    }

    private companion object {
        /** Long enough for the restricted process to be gone, so the alarm starts a normal one. */
        const val RESTART_DELAY_MS = 10_000L
    }
}

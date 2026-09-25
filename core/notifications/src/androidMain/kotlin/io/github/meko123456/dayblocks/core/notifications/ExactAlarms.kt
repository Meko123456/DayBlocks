package io.github.meko123456.dayblocks.core.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

/**
 * Android 12 put exact alarms behind a permission the user grants in system settings, and Android
 * 14 stopped granting it by default. Without it a reminder still comes, within ten minutes of its
 * time; with it, "It's 13:00" arrives at 13:00.
 */
object ExactAlarms {
    fun allowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** The system page where the user allows "Alarms & reminders" for this app. */
    @RequiresApi(Build.VERSION_CODES.S)
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", context.packageName, null))
}

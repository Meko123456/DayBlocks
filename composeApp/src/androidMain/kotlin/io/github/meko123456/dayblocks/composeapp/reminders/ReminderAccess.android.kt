package io.github.meko123456.dayblocks.composeapp.reminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.meko123456.dayblocks.core.notifications.ExactAlarms

@Composable
actual fun rememberReminderAccess(onGranted: () -> Unit): ReminderAccess {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val access = remember(context) { AndroidReminderAccess(context) }
    access.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (access.refresh()) granted()
    }
    LifecycleResumeEffect(access) {
        if (access.refresh()) granted()
        onPauseOrDispose { }
    }
    return access
}

@Stable
private class AndroidReminderAccess(private val context: Context) : ReminderAccess {
    override var notificationsAllowed by mutableStateOf<Boolean?>(null)
        private set
    override var exactTimingAllowed by mutableStateOf(true)
        private set
    var launcher: ManagedActivityResultLauncher<String, Boolean>? = null
    private var asked = false

    /** Re-reads both. True when something that was off is now on. */
    fun refresh(): Boolean {
        val before = notificationsAllowed to exactTimingAllowed
        notificationsAllowed = notificationsEnabled()
        exactTimingAllowed = ExactAlarms.allowed(context)
        return (before.first == false && notificationsAllowed == true) || (!before.second && exactTimingAllowed)
    }

    override fun requestNotifications() {
        val launcher = launcher
        // The system dialog once per launch; after a refusal it would return at once without
        // showing anything, so the next tap goes where the user can still say yes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked && launcher != null) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    override fun openExactTimingSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(ExactAlarms.settingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun notificationsEnabled(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

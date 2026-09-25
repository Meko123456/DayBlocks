package io.github.meko123456.dayblocks.composeapp.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun rememberReminderAccess(onGranted: () -> Unit): ReminderAccess {
    val scope = rememberCoroutineScope()
    val granted by rememberUpdatedState(onGranted)
    val access = remember { IosReminderAccess(scope) { granted() } }
    LifecycleResumeEffect(access) {
        access.refresh()
        onPauseOrDispose { }
    }
    return access
}

@Stable
private class IosReminderAccess(
    private val scope: CoroutineScope,
    private val onGranted: () -> Unit,
) : ReminderAccess {
    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private var status by mutableStateOf<UNAuthorizationStatus?>(null)

    override val notificationsAllowed: Boolean?
        get() = status?.let {
            it == UNAuthorizationStatusAuthorized || it == UNAuthorizationStatusProvisional || it == UNAuthorizationStatusEphemeral
        }

    /** iOS delivers on the minute; there is nothing to allow. */
    override val exactTimingAllowed: Boolean = true

    fun refresh() {
        scope.launch { status = currentStatus() }
    }

    override fun requestNotifications() {
        // iOS asks once, ever. After a refusal only the Settings app can change the answer.
        if (status == UNAuthorizationStatusDenied) {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                UIApplication.sharedApplication.openURL(it, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
            return
        }
        scope.launch {
            val allowed = suspendCancellableCoroutine { done ->
                center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { ok, _ ->
                    done.resume(ok)
                }
            }
            status = currentStatus()
            if (allowed) onGranted()
        }
    }

    override fun openExactTimingSettings() = Unit

    private suspend fun currentStatus(): UNAuthorizationStatus = suspendCancellableCoroutine { done ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            done.resume(settings?.authorizationStatus ?: UNAuthorizationStatusDenied)
        }
    }
}

package io.github.meko123456.dayblocks.core.notifications

import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLog
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * [NotificationScheduler] on UNUserNotificationCenter.
 *
 * iOS keeps at most 64 pending requests and silently drops the rest, which is why the planner hands
 * over a window rather than the whole future, and why the app reschedules on every open, every plan
 * change and every background refresh.
 *
 * Triggers are calendar dates in the device's zone, not intervals. A plan is wall-clock time, so
 * someone who lands in another zone before the app next runs still hears about their 13:00 block at
 * 13:00 where they are — the same thing the app itself would do on its next reschedule.
 */
class UserNotificationScheduler(
    private val center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : NotificationScheduler {

    override suspend fun replaceAll(notifications: List<ScheduledNotification>) {
        val keep = notifications.take(PENDING_LIMIT)
        val stale = pendingIds() - keep.mapTo(mutableSetOf()) { it.id }
        if (stale.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(stale.toList())
        // Adding a request whose id is already pending replaces it rather than adding a second.
        keep.forEach { add(it) }
        log("DayBlocks reminders: ${keep.size} pending, next at ${keep.firstOrNull()?.at ?: "none"}")
    }

    private suspend fun pendingIds(): Set<String> = suspendCancellableCoroutine { done ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            done.resume(requests.orEmpty().mapNotNullTo(mutableSetOf()) { (it as? UNNotificationRequest)?.identifier })
        }
    }

    private suspend fun add(notification: ScheduledNotification) = suspendCancellableCoroutine { done ->
        val content = UNMutableNotificationContent().apply {
            setTitle(notification.title)
            setBody(notification.body)
            setSound(UNNotificationSound.defaultSound)
            setThreadIdentifier(notification.kind.name)
            if (notification.actions.isNotEmpty()) setCategoryIdentifier(CHECK_IN_CATEGORY)
            setUserInfo(
                buildMap<Any?, Any?> {
                    put(USER_INFO_KIND, notification.kind.name)
                    notification.blockId?.let { put(USER_INFO_BLOCK, it.value) }
                },
            )
        }
        val local = notification.at.toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents().apply {
            year = local.year.toLong()
            month = local.month.number.toLong()
            day = local.day.toLong()
            hour = local.hour.toLong()
            minute = local.minute.toLong()
            second = local.second.toLong()
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(notification.id, content, trigger)) { _ ->
            done.resume(Unit)
        }
    }
}

/**
 * The check-in's three buttons, one per [CheckInAnswer], each identified by the answer's name.
 * Registered once at launch, before any notification can arrive: one whose category is unknown
 * shows no buttons at all.
 */
fun registerNotificationCategories(center: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter()) {
    val answers = listOf(
        UNNotificationAction.actionWithIdentifier(CheckInAnswer.OnIt.name, "On it ✅", UNNotificationActionOptionNone),
        UNNotificationAction.actionWithIdentifier(CheckInAnswer.GotDistracted.name, "Got distracted 😅", UNNotificationActionOptionNone),
        UNNotificationAction.actionWithIdentifier(CheckInAnswer.SkipBlock.name, "Skip this block", UNNotificationActionOptionDestructive),
    )
    val checkIn = UNNotificationCategory.categoryWithIdentifier(
        CHECK_IN_CATEGORY, answers, intentIdentifiers = emptyList<String>(), options = UNNotificationCategoryOptionNone,
    )
    center.setNotificationCategories(setOf(checkIn))
}

/** A check-in's category: the one that carries the answer buttons. */
const val CHECK_IN_CATEGORY: String = "CHECK_IN"

/** userInfo keys. The Swift notification delegate reads the block from [USER_INFO_BLOCK]. */
const val USER_INFO_BLOCK: String = "blockId"
const val USER_INFO_KIND: String = "kind"

private const val PENDING_LIMIT = 64

/**
 * NSLog with no format arguments. A Kotlin String passed through NSLog's C varargs is not bridged
 * to an NSString, and Foundation crashes dereferencing it; the message itself is the format, with
 * any % escaped.
 */
internal fun log(message: String) = NSLog(message.replace("%", "%%"))

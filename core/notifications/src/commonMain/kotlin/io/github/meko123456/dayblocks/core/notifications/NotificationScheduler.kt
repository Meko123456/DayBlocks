package io.github.meko123456.dayblocks.core.notifications

import kotlin.time.Instant

/**
 * The port the rest of the app schedules through. Deliberately narrow: the domain decides *what*
 * the buddy says and *when*, and this only carries it to the platform.
 *
 * [cancelAll] then re-scheduling is the only supported way to change the plan. iOS caps pending
 * notifications at 64, so the implementation there schedules a rolling window rather than the
 * whole future, and a partial update would leave the two platforms with different ideas of the
 * day. One entry point makes that impossible to get wrong from the caller's side.
 */
interface NotificationScheduler {
    suspend fun schedule(requests: List<ScheduledNotification>)
    suspend fun cancelAll()
    suspend fun hasPermission(): Boolean
    suspend fun requestPermission(): Boolean
}

/**
 * One notification the platform should deliver. [id] is stable across reschedules for the same
 * logical notification so replacing the plan replaces rather than duplicates.
 */
data class ScheduledNotification(
    val id: String,
    val at: Instant,
    val title: String,
    val body: String,
    val kind: NotificationKind,
    val actions: List<NotificationAction> = emptyList(),
)

enum class NotificationKind { BlockStart, MidBlockCheckIn, Nudge, PlanningReminder, Streak, Comeback, EndOfDay }

/** A button on the notification. The answer is written to the database by the platform handler. */
enum class NotificationAction { OnIt, GotDistracted, SkipBlock, OpenPlanner, ReviewDay }

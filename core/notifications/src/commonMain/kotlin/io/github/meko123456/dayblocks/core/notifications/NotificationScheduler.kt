package io.github.meko123456.dayblocks.core.notifications

import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification

/**
 * The port the app schedules through. Deliberately narrow: :core:buddy decides *what* is said and
 * *when*, and this only carries it to the platform.
 *
 * Replacing everything is the only way to change what is pending. iOS holds at most 64 pending
 * notifications, so the plan is always a rolling window rather than the whole future, and an
 * incremental update would let the two platforms drift into different ideas of the day. One
 * entry point makes that impossible to get wrong from the caller's side.
 */
interface NotificationScheduler {
    /**
     * Makes [notifications] the whole of what is pending: anything scheduled earlier and absent
     * from the list is cancelled, and one with the same id is replaced rather than duplicated.
     */
    suspend fun replaceAll(notifications: List<ScheduledNotification>)
}

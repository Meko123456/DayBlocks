package io.github.meko123456.dayblocks.core.domain.model

import kotlin.time.Instant

/**
 * One notification waiting to be delivered: what the buddy will say, and when.
 *
 * [id] names the *logical* notification — "the start of block X", "the review of Monday" — so it
 * stays the same across reschedules, and replacing the plan replaces a notification instead of
 * adding a second copy of it.
 *
 * [actions] are the buttons, and every button is an answer: tapping "On it ✅" records
 * [CheckInAnswer.OnIt] for [blockId] without opening the app.
 *
 * In the domain rather than in either module that handles it, because two do: :core:buddy decides
 * what is said and when, :core:notifications hands it to the platform, and neither depends on the
 * other.
 */
data class ScheduledNotification(
    val id: String,
    val at: Instant,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val blockId: BlockId? = null,
    val actions: List<CheckInAnswer> = emptyList(),
) {
    init {
        require(actions.isEmpty() || blockId != null) { "an answer needs a block to answer for" }
    }
}

/** What a notification is for. On Android each kind is its own channel, silenced on its own. */
enum class NotificationKind { BlockStart, MidBlockCheckIn, Nudge, PlanningReminder, Streak, Comeback, EndOfDay }

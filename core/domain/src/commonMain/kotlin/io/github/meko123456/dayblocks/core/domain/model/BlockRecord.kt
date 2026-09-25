package io.github.meko123456.dayblocks.core.domain.model

import kotlin.time.Instant

/**
 * Everything recorded about how one block went: the answer given from a notification during the
 * day, and the outcome confirmed at the end-of-day check-in. Either or both may be missing.
 */
data class BlockRecord(
    val blockId: BlockId,
    val answer: CheckInAnswer? = null,
    val outcome: BlockOutcome? = null,
    /** When [answer] was given. The follow-up after "Got distracted" is timed from it. */
    val answeredAt: Instant? = null,
) {
    /**
     * The outcome to score with: the check-in wins, and until there is one the notification answer
     * stands in for it. This is what lets the buddy's mood move during the day, long before anyone
     * opens the check-in.
     */
    val effectiveOutcome: BlockOutcome? get() = outcome ?: answer?.suggestedOutcome
}

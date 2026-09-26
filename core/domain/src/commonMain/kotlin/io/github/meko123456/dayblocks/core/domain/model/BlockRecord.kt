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

/**
 * What each block is scored as: the check-in's outcome where there is one, the notification
 * answer's suggestion where there is not, and nothing at all for a block nobody said a word about.
 */
fun Map<BlockId, BlockRecord>.effectiveOutcomes(): Map<BlockId, BlockOutcome> =
    mapNotNull { (id, record) -> record.effectiveOutcome?.let { id to it } }.toMap()

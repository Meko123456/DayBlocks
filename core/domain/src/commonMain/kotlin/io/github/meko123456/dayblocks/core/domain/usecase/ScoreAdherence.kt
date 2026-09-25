package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlin.math.roundToInt

/**
 * How closely a day followed its plan, from 0 to 100.
 *
 * Weighted by planned duration, because that is what "following the plan" means: skipping a
 * three-hour work block is not the same as skipping a fifteen-minute break, and a per-block
 * average would score them identically.
 *
 * Only blocks with an outcome are scored. A block that has not happened yet, or that nobody
 * answered for, is neither a success nor a failure, so it is left out rather than counted as zero
 * — otherwise the score at lunchtime would look like a disaster purely because the afternoon has
 * not arrived. That is also what lets the buddy's mood track "how is it going *so far*".
 */
class ScoreAdherence {

    /** Null when nothing has been scored yet — distinct from a real score of zero. */
    operator fun invoke(blocks: List<TimeBlock>, outcomes: Map<BlockId, BlockOutcome>): Int? {
        val scored = blocks.mapNotNull { block -> outcomes[block.id]?.let { block to it } }
        val plannedMinutes = scored.sumOf { (block, _) -> block.span.durationMinutes }
        if (plannedMinutes == 0) return null
        val followedMinutes = scored.sumOf { (block, outcome) -> block.span.durationMinutes * outcome.weight }
        return (followedMinutes / plannedMinutes * 100).roundToInt()
    }
}

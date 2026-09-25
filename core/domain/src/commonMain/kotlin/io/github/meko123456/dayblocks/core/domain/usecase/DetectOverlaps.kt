package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.endOnLineOf
import io.github.meko123456.dayblocks.core.domain.time.startOnLineOf
import kotlinx.datetime.LocalDate

/**
 * Which existing blocks a candidate would clash with — the editor's overlap warning.
 *
 * Compared on one wall-clock line rather than per plan date, because a clash can straddle two
 * plans: Monday's "00:00 Sleep" (minute 1440–1920 of Monday) collides with a Tuesday block at
 * 00:30 even though they belong to different days. Callers pass the neighbouring plans' blocks
 * too, and this finds the collision either way round.
 *
 * Intervals are half-open, so back-to-back blocks — Work until 12:00, Rest from 12:00 — are fine.
 * It warns rather than refuses: sometimes two things really do happen at once, and the plan is
 * the user's.
 */
class DetectOverlaps {

    /**
     * Blocks in [existing] that overlap [candidate] on [date]. [editing] is excluded so moving a
     * block does not report it colliding with its own previous position.
     */
    operator fun invoke(
        date: LocalDate,
        candidate: DaySpan,
        existing: List<TimeBlock>,
        editing: BlockId? = null,
    ): List<TimeBlock> = existing
        .filter { it.id != editing }
        .filter { other ->
            val otherStart = other.startOnLineOf(date)
            val otherEnd = other.endOnLineOf(date)
            candidate.startMinutes < otherEnd && otherStart < candidate.endMinutes
        }
        .sortedBy { it.startOnLineOf(date) }

    /** Every clashing pair within [blocks], each pair once, for validating a whole day. */
    fun pairs(blocks: List<TimeBlock>): List<Pair<TimeBlock, TimeBlock>> {
        if (blocks.isEmpty()) return emptyList()
        val reference = blocks.minOf { it.date }
        val sorted = blocks.sortedBy { it.startOnLineOf(reference) }
        val clashes = mutableListOf<Pair<TimeBlock, TimeBlock>>()
        for (i in sorted.indices) {
            val a = sorted[i]
            val aEnd = a.endOnLineOf(reference)
            for (j in i + 1 until sorted.size) {
                val b = sorted[j]
                // Sorted by start: once b starts at or after a ends, nothing later can clash with a.
                if (b.startOnLineOf(reference) >= aEnd) break
                clashes += a to b
            }
        }
        return clashes
    }
}

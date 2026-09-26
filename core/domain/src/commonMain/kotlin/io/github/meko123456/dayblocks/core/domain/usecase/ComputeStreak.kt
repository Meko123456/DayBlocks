package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.model.effectiveOutcomes
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * How many days in a row, counting back from the day before [today], followed their plan: a day
 * with blocks that scored at least [THRESHOLD].
 *
 * Today never counts — it is still being lived, and "don't break it today" is about the days
 * behind it. A day with nothing planned ends the streak, and so does a day with no word on how it
 * went: there was no plan to follow, or nothing says it was followed. Notification answers count
 * as well as check-in outcomes, so a streak can grow for someone who answers "On it" and never
 * opens the check-in.
 */
class ComputeStreak(private val score: ScoreAdherence) {

    /**
     * Reads a day at a time, counting back, and stops at the first day that breaks the streak — so
     * a long streak costs a few reads, not a year of them, every time the schedule is rebuilt.
     */
    suspend operator fun invoke(
        today: LocalDate,
        blocksOn: suspend (LocalDate) -> List<TimeBlock>,
        recordsOn: suspend (LocalDate) -> Map<BlockId, BlockRecord>,
    ): Int {
        var streak = 0
        var day = today.minus(1, DateTimeUnit.DAY)
        while (true) {
            val blocks = blocksOn(day)
            if (blocks.isEmpty()) return streak
            val adherence = score(blocks, recordsOn(day).effectiveOutcomes()) ?: return streak
            if (adherence < THRESHOLD) return streak
            streak++
            day = day.minus(1, DateTimeUnit.DAY)
        }
    }

    companion object {
        /** The adherence a day needs to count as following its plan. */
        const val THRESHOLD: Int = 70
    }
}

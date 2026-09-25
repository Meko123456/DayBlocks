package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome.Done
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome.Partly
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome.Skipped
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreAdherenceTest {

    private val score = ScoreAdherence()
    private val work = block("work", MONDAY, at(9), at(12)) // 3h
    private val rest = block("rest", MONDAY, at(12), at(13)) // 1h
    private val read = block("read", MONDAY, at(13), at(15)) // 2h
    private val day = listOf(work, rest, read)

    private fun outcomes(vararg pairs: Pair<String, BlockOutcome>) =
        pairs.associate { (id, o) -> BlockId(id) to o }

    @Test
    fun aFullyFollowedDayScoresAHundred() {
        assertEquals(100, score(day, outcomes("work" to Done, "rest" to Done, "read" to Done)))
    }

    @Test
    fun aFullySkippedDayScoresZero() {
        assertEquals(0, score(day, outcomes("work" to Skipped, "rest" to Skipped, "read" to Skipped)))
    }

    @Test
    fun nothingScoredIsNullNotZero() {
        // Zero means "you skipped everything". Before anything happens there is no score at all.
        assertNull(score(day, emptyMap()))
        assertNull(score(emptyList(), emptyMap()))
    }

    @Test
    fun theScoreIsWeightedByPlannedTime() {
        // Three hours of work done, one hour of rest skipped: 75% of the planned time, not 50%.
        assertEquals(75, score(listOf(work, rest), outcomes("work" to Done, "rest" to Skipped)))
    }

    @Test
    fun partlyCountsAsHalf() {
        assertEquals(50, score(listOf(read), outcomes("read" to Partly)))
    }

    @Test
    fun blocksWithNoOutcomeAreLeftOutRatherThanCountedAsFailures() {
        // At lunchtime only the morning has happened. The unanswered afternoon must not drag
        // the score down — that is what lets the buddy judge the day "so far".
        assertEquals(100, score(day, outcomes("work" to Done)))
    }

    @Test
    fun outcomesForBlocksNotInTheDayAreIgnored() {
        assertEquals(100, score(listOf(work), outcomes("work" to Done, "someone-else" to Skipped)))
    }

    @Test
    fun aMixedDayRoundsToTheNearestPercent() {
        // Done 3h, Partly 1h, Skipped 2h: (180 + 30 + 0) / 360 = 58.33%.
        assertEquals(58, score(day, outcomes("work" to Done, "rest" to Partly, "read" to Skipped)))
    }
}

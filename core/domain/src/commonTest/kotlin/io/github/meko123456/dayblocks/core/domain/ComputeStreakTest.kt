package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.usecase.ComputeStreak
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

class ComputeStreakTest {
    private val streak = ComputeStreak(ScoreAdherence())
    private val today = LocalDate(2026, 9, 25)
    private fun daysAgo(n: Int) = today.minus(DatePeriod(days = n))

    /** A one-block day that went [outcome], the way the check-in would record it. */
    private fun day(n: Int, outcome: BlockOutcome?): Pair<LocalDate, Pair<List<TimeBlock>, Map<BlockId, BlockRecord>>> {
        val b = block("b$n", daysAgo(n), at(9), at(10))
        val records = outcome?.let { mapOf(b.id to BlockRecord(b.id, outcome = it)) }.orEmpty()
        return daysAgo(n) to (listOf(b) to records)
    }

    private fun count(vararg days: Pair<LocalDate, Pair<List<TimeBlock>, Map<BlockId, BlockRecord>>>): Int = runBlockingTest {
        val blocks = days.associate { it.first to it.second.first }
        val records = days.associate { it.first to it.second.second }
        streak(today, { blocks[it].orEmpty() }, { records[it].orEmpty() })
    }

    private fun <T> runBlockingTest(block: suspend () -> T): T {
        var result: Result<T>? = null
        runTest { result = runCatching { block() } }
        return result!!.getOrThrow()
    }

    @Test
    fun aStreakCountsFollowedDaysBackFromYesterday() {
        assertEquals(3, count(day(1, BlockOutcome.Done), day(2, BlockOutcome.Done), day(3, BlockOutcome.Done)))
    }

    @Test
    fun todayNeverCountsAndABadDayEndsTheStreak() {
        assertEquals(1, count(day(0, BlockOutcome.Done), day(1, BlockOutcome.Done), day(2, BlockOutcome.Skipped), day(3, BlockOutcome.Done)))
    }

    @Test
    fun aDayWithNoPlanOrNoWordOnItEndsTheStreak() {
        assertEquals(0, count(day(2, BlockOutcome.Done)), "yesterday had nothing planned")
        assertEquals(0, count(day(1, null), day(2, BlockOutcome.Done)), "yesterday was never answered for")
    }

    @Test
    fun notificationAnswersCountAsMuchAsTheCheckIn() {
        val b = block("b", daysAgo(1), at(9), at(10))
        val answered = mapOf(b.id to BlockRecord(b.id, answer = CheckInAnswer.OnIt))
        assertEquals(1, runBlockingTest { streak(today, { if (it == daysAgo(1)) listOf(b) else emptyList() }, { if (it == daysAgo(1)) answered else emptyMap() }) })
        // "Partly" is half: below the threshold on its own.
        assertEquals(0, count(day(1, BlockOutcome.Partly)))
    }
}

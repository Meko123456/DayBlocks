package io.github.meko123456.dayblocks.feature.stats

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.ComputeStreak
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeOutcomeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

class StatsViewModelTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val blocks = FakeBlockRepository()
    private val outcomes = FakeOutcomeRepository(blocks)
    private val earlier = Instant.parse("2026-09-21T06:00:00Z")

    private fun TestScope.stats(): StatsViewModel {
        val clock = OffsetTimeProvider(LocalDateTime(2026, 9, 21, 12, 0).toInstant(zone), zone) { testScheduler.currentTime }
        return StatsViewModel(blocks, outcomes, clock, PlanningDayRule(), ScoreAdherence(), ComputeStreak(ScoreAdherence()), backgroundScope)
    }

    /** A one-hour block, [daysAgo] days back, rated [outcome]. */
    private suspend fun dayRated(daysAgo: Int, outcome: BlockOutcome) {
        val date = monday.minus(daysAgo, DateTimeUnit.DAY)
        val block = TimeBlock(BlockId("b$daysAgo"), date, "Block", Category.Work, DaySpan(9 * 60, 10 * 60))
        blocks.upsert(block)
        outcomes.recordOutcome(block.id, outcome, earlier)
    }

    private suspend fun ReceiveTurbine<StatsState>.until(done: (StatsState) -> Boolean): StatsState {
        var state = awaitItem()
        while (!done(state)) state = awaitItem()
        return state
    }

    @Test
    fun theWeekHasABarPerDayAndADayWithoutAScoreIsNotAZero() = runTest {
        dayRated(0, BlockOutcome.Done)
        dayRated(1, BlockOutcome.Partly)
        dayRated(3, BlockOutcome.Skipped)
        stats().state.test {
            val state = until { !it.loading }
            assertEquals((6 downTo 0).map { monday.minus(it, DateTimeUnit.DAY) }, state.week.map { it.date })
            assertEquals(listOf(null, null, null, 0, null, 50, 100), state.week.map { it.score })
            assertTrue(state.week.last().isToday)
            assertEquals(50, state.average, "the days with a score only: 0, 50 and 100")
        }
    }

    @Test
    fun theStreakCountsTheFollowedDaysBeforeToday() = runTest {
        dayRated(1, BlockOutcome.Done)
        dayRated(2, BlockOutcome.Done)
        dayRated(3, BlockOutcome.Partly)
        stats().state.test {
            assertEquals(2, until { !it.loading }.streak)
        }
    }

    @Test
    fun aNewOutcomeRedrawsTheWeek() = runTest {
        val today = TimeBlock(BlockId("today"), monday, "Block", Category.Work, DaySpan(9 * 60, 10 * 60))
        blocks.upsert(today)
        stats().state.test {
            assertNull(until { !it.loading }.week.last().score)
            outcomes.recordOutcome(today.id, BlockOutcome.Done, earlier)
            assertEquals(100, until { it.week.last().score != null }.week.last().score)
        }
    }

    @Test
    fun backCloses() = runTest {
        val vm = stats()
        vm.effects.test {
            vm.onIntent(StatsIntent.BackTapped)
            assertEquals(StatsEffect.Close, awaitItem())
        }
    }
}

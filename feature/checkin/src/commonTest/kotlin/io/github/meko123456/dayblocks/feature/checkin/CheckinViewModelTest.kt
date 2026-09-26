package io.github.meko123456.dayblocks.feature.checkin

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.buddy.BuddyVoice
import io.github.meko123456.dayblocks.core.buddy.CheckInBuddy
import io.github.meko123456.dayblocks.core.buddy.MessagePools
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeOutcomeRepository
import io.github.meko123456.dayblocks.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class CheckinViewModelTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private fun at(h: Int) = h * 60
    private fun block(id: String, date: LocalDate, start: Int, end: Int, category: Category = Category.Work) =
        TimeBlock(BlockId(id), date, id, category, DaySpan(start, end))

    private val work = block("work", monday, at(9), at(12))
    private val rest = block("rest", monday, at(12), at(13), Category.Rest)
    private val read = block("read", monday, at(13), at(15), Category.Reading)
    private val blocks = FakeBlockRepository(listOf(work, rest, read))
    private val outcomes = FakeOutcomeRepository(blocks)
    private val settings = FakeSettingsRepository()
    private val earlier = Instant.parse("2026-09-21T10:00:00Z")

    private fun TestScope.checkin(args: CheckinArgs = CheckinArgs(), now: LocalDateTime = LocalDateTime(2026, 9, 21, 21, 0)): CheckinViewModel {
        val clock = OffsetTimeProvider(now.toInstant(zone), zone) { testScheduler.currentTime }
        return CheckinViewModel(
            args, blocks, outcomes, settings, clock, PlanningDayRule(), ScoreAdherence(),
            CheckInBuddy(BuddyVoice(MessagePools.Default.firstLinesOnly())), backgroundScope,
        )
    }

    private suspend fun ReceiveTurbine<CheckinState>.until(done: (CheckinState) -> Boolean): CheckinState {
        var state = awaitItem()
        while (!done(state)) state = awaitItem()
        return state
    }

    private suspend fun ReceiveTurbine<CheckinState>.loaded() = until { !it.loading }

    @Test
    fun rowsArePrefilledFromTheDaysAnswers() = runTest {
        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, earlier)
        outcomes.recordOutcome(read.id, BlockOutcome.Skipped, earlier)
        checkin().state.test {
            val state = loaded()
            assertEquals(monday, state.date)
            val rows = state.rows.associateBy { it.block.id }
            assertNull(rows.getValue(work.id).outcome, "a suggestion is not an outcome until confirmed")
            assertEquals(BlockOutcome.Done, rows.getValue(work.id).shown)
            assertNull(rows.getValue(rest.id).shown)
            assertEquals(BlockOutcome.Skipped, rows.getValue(read.id).shown)
            assertEquals(60, state.score, "three hours done of five scored")
            assertTrue(state.hasSuggestions)
            assertEquals(BuddyMood.Encouraging, state.buddy.mood)
            assertEquals("One tap per block: done, partly or skipped?", state.buddy.line)
        }
    }

    @Test
    fun oneTapRecordsTheOutcomeAndTheBuddyReacts() = runTest {
        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, earlier)
        outcomes.recordOutcome(read.id, BlockOutcome.Done, earlier)
        val vm = checkin()
        vm.state.test {
            loaded()
            vm.onIntent(CheckinIntent.OutcomePicked(rest.id, BlockOutcome.Done))
            val state = until { it.rows.single { row -> row.block.id == rest.id }.outcome == BlockOutcome.Done }
            assertEquals(100, state.score)
            assertEquals(BuddyMood.Proud, state.buddy.mood)
            assertEquals("100%! That's a day to be proud of 🌟", state.buddy.line)
        }
    }

    @Test
    fun looksRightConfirmsEverySuggestionAtOnce() = runTest {
        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, earlier)
        outcomes.recordAnswer(rest.id, CheckInAnswer.GotDistracted, earlier)
        val vm = checkin()
        vm.state.test {
            loaded()
            vm.onIntent(CheckinIntent.ConfirmSuggestions)
            val state = until { !it.hasSuggestions }
            val rows = state.rows.associateBy { it.block.id }
            assertEquals(BlockOutcome.Done, rows.getValue(work.id).outcome)
            assertEquals(BlockOutcome.Partly, rows.getValue(rest.id).outcome)
            assertNull(rows.getValue(read.id).outcome, "nobody answered for it, so there is nothing to confirm")
        }
    }

    @Test
    fun anEmptyDaySaysSoAndLooksAhead() = runTest {
        blocks.all.value = emptyList()
        checkin().state.test {
            val state = loaded()
            assertTrue(state.rows.isEmpty())
            assertNull(state.score)
            assertEquals("Nothing was planned today. Want to plan tomorrow?", state.buddy.line)
        }
    }

    @Test
    fun itReviewsTheDayItWasOpenedFor() = runTest {
        val sunday = LocalDate(2026, 9, 20)
        blocks.upsert(block("sunday-run", sunday, at(10), at(11), Category.Exercise))
        checkin(CheckinArgs(sunday)).state.test {
            val state = loaded()
            assertEquals(sunday, state.date)
            assertEquals(listOf("sunday-run"), state.rows.map { it.block.id.value })
            assertEquals(false, state.isToday, "so the screen asks how Sunday went, not today")
        }
    }

    @Test
    fun afterMidnightTheDayUnderReviewIsTheOneStillBeingLived() = runTest {
        checkin(now = LocalDateTime(2026, 9, 22, 0, 30)).state.test {
            assertEquals(monday, loaded().date)
        }
    }

    @Test
    fun backCloses() = runTest {
        val vm = checkin()
        vm.effects.test {
            vm.onIntent(CheckinIntent.BackTapped)
            assertEquals(CheckinEffect.Close, awaitItem())
        }
    }
}

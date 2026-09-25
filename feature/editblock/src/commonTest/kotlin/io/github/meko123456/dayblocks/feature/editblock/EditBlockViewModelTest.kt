package io.github.meko123456.dayblocks.feature.editblock

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.DetectOverlaps
import io.github.meko123456.dayblocks.core.domain.usecase.IdGenerator
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class EditBlockViewModelTest {

    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val tuesday = LocalDate(2026, 9, 22)
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun block(id: String, date: LocalDate, start: Int, end: Int, title: String = id, category: Category = Category.Work) =
        TimeBlock(BlockId(id), date, title, category, DaySpan(start, end))

    private val work = block("work", monday, at(9), at(12), title = "Deep work")
    private val sleep = block("sleep", monday, at(24), at(32), title = "Sleep", category = Category.Sleep)
    private val gym = block("gym", monday, at(18), at(20), title = "Gym", category = Category.Exercise)
    private val repo = FakeBlockRepository(listOf(work, sleep, gym))
    private val ids = IdGenerator { "new-id" }

    private fun TestScope.editor(args: EditBlockArgs, now: LocalDateTime = LocalDateTime(2026, 9, 21, 15, 40)): EditBlockViewModel {
        val clock = OffsetTimeProvider(now.toInstant(zone), zone) { testScheduler.currentTime }
        return EditBlockViewModel(args, repo, DetectOverlaps(), ids, clock, PlanningDayRule(), backgroundScope)
    }

    private suspend fun ReceiveTurbine<EditBlockState>.awaitLoaded(): EditBlockState {
        var s = awaitItem()
        while (s.loading) s = awaitItem()
        return s
    }

    // --- starting spans -------------------------------------------------------------------------

    @Test
    fun fillingAFreeTimeGapStartsThereAndLastsAnHour() = runTest {
        editor(EditBlockArgs(monday, prefill = DaySpan(at(15), at(18)))).state.test {
            assertEquals(DaySpan(at(15), at(16)), awaitLoaded().span)
        }
    }

    @Test
    fun aGapShorterThanAnHourIsFilledExactly() = runTest {
        editor(EditBlockArgs(monday, prefill = DaySpan(at(12), at(12, 30)))).state.test {
            assertEquals(DaySpan(at(12), at(12, 30)), awaitLoaded().span)
        }
    }

    @Test
    fun aNewBlockForTodayStartsAtTheNextQuarterHour() = runTest {
        // At 15:40, planning "now" means 15:45 — not 09:00.
        editor(EditBlockArgs(monday), now = LocalDateTime(2026, 9, 21, 15, 40)).state.test {
            assertEquals(DaySpan(at(15, 45), at(16, 45)), awaitLoaded().span)
        }
    }

    @Test
    fun aNewBlockForAnotherDayStartsAtNine() = runTest {
        editor(EditBlockArgs(tuesday), now = LocalDateTime(2026, 9, 21, 15, 40)).state.test {
            assertEquals(DaySpan(at(9), at(10)), awaitLoaded().span)
        }
    }

    @Test
    fun editingLoadsTheBlocksFields() = runTest {
        editor(EditBlockArgs(monday, blockId = work.id)).state.test {
            val s = awaitLoaded()
            assertFalse(s.isNew)
            assertEquals("Deep work", s.title)
            assertEquals(Category.Work, s.category)
            assertEquals(work.span, s.span)
        }
    }

    @Test
    fun editingABlockThatNoLongerExistsCloses() = runTest {
        val vm = editor(EditBlockArgs(monday, blockId = BlockId("gone")))
        vm.effects.test { assertEquals(EditBlockEffect.Close, awaitItem()) }
    }

    // --- suggestions ----------------------------------------------------------------------------

    @Test
    fun typingFiltersRecentTitlesAndHidesAnExactMatch() = runTest {
        val vm = editor(EditBlockArgs(monday))
        vm.state.test {
            awaitLoaded()
            vm.onIntent(EditBlockIntent.TitleChanged("s"))
            runCurrent()
            assertEquals(setOf("Sleep"), expectMostRecentItem().suggestions.toSet() - setOf("Deep work", "Gym"))
            vm.onIntent(EditBlockIntent.TitleChanged("sleep")) // exact, in another case
            runCurrent()
            assertTrue(expectMostRecentItem().suggestions.none { it.equals("sleep", ignoreCase = true) })
        }
    }

    @Test
    fun pickingASuggestionFillsTheTitleAndClearsTheList() = runTest {
        val vm = editor(EditBlockArgs(monday))
        vm.state.test {
            awaitLoaded()
            vm.onIntent(EditBlockIntent.SuggestionPicked("Gym"))
            runCurrent()
            val s = expectMostRecentItem()
            assertEquals("Gym", s.title)
            assertTrue(s.suggestions.isEmpty())
        }
    }

    @Test
    fun titlesAreCappedSoTheTimelineCanStillShowThem() = runTest {
        val vm = editor(EditBlockArgs(monday))
        vm.state.test {
            awaitLoaded()
            vm.onIntent(EditBlockIntent.TitleChanged("x".repeat(200)))
            runCurrent()
            assertEquals(EditBlockViewModel.MAX_TITLE, expectMostRecentItem().title.length)
        }
    }

    // --- quarter-hour stepping ------------------------------------------------------------------

    @Test
    fun steppingLandsOnQuarterHoursEvenFromAnOddMinute() {
        assertEquals(at(9, 15), stepToQuarter(at(9, 7), 1))
        assertEquals(at(9), stepToQuarter(at(9, 7), -1))
        assertEquals(at(9, 30), stepToQuarter(at(9), 2))
        assertEquals(at(8, 45), stepToQuarter(at(9), -1))
        assertEquals(at(9, 7), stepToQuarter(at(9, 7), 0))
    }

    @Test
    fun movingTheStartPastTheEndPushesTheEndAlong() {
        val span = DaySpan(at(9), at(9, 30))
        assertEquals(DaySpan(at(9, 30), at(9, 45)), span.withStartStepped(2))
    }

    @Test
    fun theEndNeverComesBeforeTheStartPlusAQuarter() {
        val span = DaySpan(at(9), at(10))
        assertEquals(DaySpan(at(9), at(9, 15)), span.withEndStepped(-10))
    }

    @Test
    fun aBlockCannotBeSteppedBeyondADay() {
        val span = DaySpan(at(0), at(23, 45))
        assertEquals(DaySpan(at(0), at(24)), span.withEndStepped(8))
    }

    @Test
    fun theStartCannotBeSteppedBeforeMidnight() {
        assertEquals(DaySpan(0, at(1)), DaySpan(0, at(1)).withStartStepped(-4))
    }

    // --- overlap warning ------------------------------------------------------------------------

    @Test
    fun overlappingAnotherBlockWarnsWithoutBlockingTheSave() = runTest {
        val vm = editor(EditBlockArgs(monday, prefill = DaySpan(at(11), at(13))))
        vm.state.test {
            val s = awaitLoaded()
            assertEquals(listOf(work), s.overlaps)
            vm.onIntent(EditBlockIntent.TitleChanged("Call"))
            runCurrent()
            assertTrue(expectMostRecentItem().canSave)
        }
    }

    @Test
    fun aTuesdayBlockAtHalfPastMidnightWarnsAboutMondaysSleep() = runTest {
        // Different plans, same half hour — the neighbouring day is loaded for exactly this.
        editor(EditBlockArgs(tuesday, prefill = DaySpan(at(0, 30), at(1)))).state.test {
            assertEquals(listOf(sleep), awaitLoaded().overlaps)
        }
    }

    @Test
    fun steppingOutOfAClashClearsTheWarning() = runTest {
        val vm = editor(EditBlockArgs(monday, prefill = DaySpan(at(11, 30), at(12, 30))))
        vm.state.test {
            assertEquals(listOf(work), awaitLoaded().overlaps)
            vm.onIntent(EditBlockIntent.StartStepped(2)) // 12:00–12:30
            runCurrent()
            assertTrue(expectMostRecentItem().overlaps.isEmpty())
        }
    }

    @Test
    fun aBlockBeingEditedDoesNotWarnAboutItself() = runTest {
        editor(EditBlockArgs(monday, blockId = work.id)).state.test {
            assertTrue(awaitLoaded().overlaps.isEmpty())
        }
    }

    // --- saving, deleting, cancelling -----------------------------------------------------------

    @Test
    fun savingANewBlockStoresItWithAFreshIdAndCloses() = runTest {
        val vm = editor(EditBlockArgs(monday, prefill = DaySpan(at(15), at(18))))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(EditBlockIntent.TitleChanged("  Reading  "))
            vm.onIntent(EditBlockIntent.CategoryPicked(Category.Reading))
            vm.onIntent(EditBlockIntent.NoteChanged("   "))
            vm.onIntent(EditBlockIntent.SaveTapped)
            assertEquals(EditBlockEffect.Close, awaitItem())
        }
        val saved = repo.all.value.single { it.id == BlockId("new-id") }
        assertEquals("Reading", saved.title) // trimmed
        assertEquals(Category.Reading, saved.category)
        assertEquals(DaySpan(at(15), at(16)), saved.span)
        assertNull(saved.note) // a blank note is no note
    }

    @Test
    fun savingAnEditKeepsTheBlocksIdentity() = runTest {
        val vm = editor(EditBlockArgs(monday, blockId = gym.id))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(EditBlockIntent.EndStepped(2))
            vm.onIntent(EditBlockIntent.SaveTapped)
            assertEquals(EditBlockEffect.Close, awaitItem())
        }
        val gyms = repo.all.value.filter { it.id == gym.id }
        assertEquals(1, gyms.size)
        assertEquals(DaySpan(at(18), at(20, 30)), gyms.single().span)
    }

    @Test
    fun aBlankTitleCannotBeSaved() = runTest {
        val before = repo.all.value
        val vm = editor(EditBlockArgs(monday))
        vm.state.test { assertFalse(awaitLoaded().canSave) }
        vm.effects.test {
            vm.onIntent(EditBlockIntent.TitleChanged("   "))
            vm.onIntent(EditBlockIntent.SaveTapped)
            expectNoEvents()
        }
        assertEquals(before, repo.all.value)
    }

    @Test
    fun deletingRemovesTheBlockAndCloses() = runTest {
        val vm = editor(EditBlockArgs(monday, blockId = gym.id))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(EditBlockIntent.DeleteTapped)
            assertEquals(EditBlockEffect.Close, awaitItem())
        }
        assertTrue(repo.all.value.none { it.id == gym.id })
    }

    @Test
    fun cancellingClosesWithoutChangingAnything() = runTest {
        val before = repo.all.value
        val vm = editor(EditBlockArgs(monday, blockId = gym.id))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(EditBlockIntent.TitleChanged("Something else"))
            vm.onIntent(EditBlockIntent.CancelTapped)
            assertEquals(EditBlockEffect.Close, awaitItem())
        }
        assertEquals(before, repo.all.value)
    }
}

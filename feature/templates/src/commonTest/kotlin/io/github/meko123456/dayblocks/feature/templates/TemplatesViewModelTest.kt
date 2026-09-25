package io.github.meko123456.dayblocks.feature.templates

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.CopyDay
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.core.domain.usecase.SaveDayAsTemplate
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeTemplateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class TemplatesViewModelTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val sunday = LocalDate(2026, 9, 20)
    private fun at(h: Int) = h * 60
    private fun block(id: String, date: LocalDate, title: String, start: Int, end: Int) =
        TimeBlock(BlockId(id), date, title, Category.Work, DaySpan(start, end))

    private val blocks = FakeBlockRepository()
    private val templates = FakeTemplateRepository()
    private var n = 0
    private val ids = io.github.meko123456.dayblocks.core.domain.usecase.IdGenerator { "id-${++n}" }
    private val weekday = Template(TemplateId("weekday"), "Weekday", listOf(TemplateBlock("Deep work", Category.Work, DaySpan(at(9), at(12)))))

    private fun TestScope.screen(): TemplatesViewModel {
        val clock = OffsetTimeProvider(LocalDateTime(2026, 9, 21, 10, 0).toInstant(zone), zone) { testScheduler.currentTime }
        return TemplatesViewModel(templates, blocks, SaveDayAsTemplate(ids), CopyDay(ids), GenerateDayFromTemplate(ids), clock, PlanningDayRule(), backgroundScope)
    }

    private suspend fun ReceiveTurbine<TemplatesState>.loaded(): TemplatesState {
        var s = awaitItem(); while (s.loading) s = awaitItem(); return s
    }

    @Test
    fun savingAnEmptyTodaySaysToPlanFirst() = runTest {
        val vm = screen(); vm.state.test { loaded() }
        vm.effects.test {
            vm.onIntent(TemplatesIntent.SaveTodayTapped)
            assertTrue((awaitItem() as TemplatesEffect.Message).text.contains("Plan something"))
        }
    }

    @Test
    fun savingTodayStoresItsShapeUnderTheName() = runTest {
        blocks.upsert(block("w", monday, "Deep work", at(9), at(12)))
        val vm = screen()
        vm.state.test {
            loaded()
            vm.onIntent(TemplatesIntent.SaveTodayTapped); runCurrent()
            assertTrue(expectMostRecentItem().naming)
            vm.onIntent(TemplatesIntent.DraftNameChanged("  Weekday "))
            vm.onIntent(TemplatesIntent.NameConfirmed); runCurrent()
            cancelAndIgnoreRemainingEvents()
        }
        val saved = templates.templates.value.single()
        assertEquals("Weekday", saved.name)
        assertEquals(listOf("Deep work"), saved.blocks.map { it.title })
    }

    @Test
    fun applyingToAnEmptyTodayFillsItWithoutAsking() = runTest {
        templates.upsert(weekday)
        val vm = screen(); vm.state.test { loaded() }
        vm.onIntent(TemplatesIntent.ApplyTapped(weekday.id)); runCurrent()
        assertEquals(listOf("Deep work"), blocks.all.value.filter { it.date == monday }.map { it.title })
    }

    @Test
    fun applyingOverAPlannedTodayAsksAndOnlyReplacesWhenConfirmed() = runTest {
        templates.upsert(weekday)
        blocks.upsert(block("gym", monday, "Gym", at(18), at(19)))
        val vm = screen()
        vm.state.test {
            loaded()
            vm.onIntent(TemplatesIntent.ApplyTapped(weekday.id)); runCurrent()
            assertEquals(PendingReplace.ApplyTemplate(weekday.id, "Weekday"), expectMostRecentItem().pending)
            vm.onIntent(TemplatesIntent.ReplaceDismissed); runCurrent()
            assertNull(expectMostRecentItem().pending)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Gym"), blocks.all.value.filter { it.date == monday }.map { it.title }) // kept
        vm.onIntent(TemplatesIntent.ApplyTapped(weekday.id)); runCurrent()
        vm.onIntent(TemplatesIntent.ReplaceConfirmed); runCurrent()
        assertEquals(listOf("Deep work"), blocks.all.value.filter { it.date == monday }.map { it.title }) // replaced
    }

    @Test
    fun copyingYesterdayGivesFreshIdsAndTodaysDate() = runTest {
        val sundayRun = block("run", sunday, "Run", at(7), at(8))
        blocks.upsert(sundayRun)
        val vm = screen(); vm.state.test { loaded() }
        vm.onIntent(TemplatesIntent.CopyYesterdayTapped); runCurrent()
        val copied = blocks.all.value.single { it.date == monday }
        assertEquals("Run", copied.title)
        assertTrue(copied.id != sundayRun.id, "a copy must not reuse yesterday's id")
        assertTrue(blocks.all.value.any { it.id == sundayRun.id && it.date == sunday }, "yesterday must be untouched")
    }

    @Test
    fun copyingAnEmptyYesterdaySaysSo() = runTest {
        val vm = screen(); vm.state.test { loaded() }
        vm.effects.test {
            vm.onIntent(TemplatesIntent.CopyYesterdayTapped)
            assertTrue((awaitItem() as TemplatesEffect.Message).text.contains("no plan"))
        }
    }

    @Test
    fun weekdaysCanBeAssignedAndCleared() = runTest {
        templates.upsert(weekday)
        val vm = screen()
        vm.state.test {
            loaded()
            vm.onIntent(TemplatesIntent.AssignPicked(DayOfWeek.MONDAY, weekday.id)); runCurrent()
            assertEquals(mapOf(DayOfWeek.MONDAY to weekday.id), expectMostRecentItem().assignments)
            vm.onIntent(TemplatesIntent.AssignPicked(DayOfWeek.MONDAY, null)); runCurrent()
            assertTrue(expectMostRecentItem().assignments.isEmpty())
        }
    }

    @Test
    fun deletingATemplateClearsTheWeekdaysItFilled() = runTest {
        templates.upsert(weekday)
        templates.assign(DayOfWeek.FRIDAY, weekday.id)
        val vm = screen()
        vm.state.test {
            loaded()
            vm.onIntent(TemplatesIntent.DeleteTapped(weekday.id)); runCurrent()
            val s = expectMostRecentItem()
            assertTrue(s.templates.isEmpty() && s.assignments.isEmpty())
        }
    }
}

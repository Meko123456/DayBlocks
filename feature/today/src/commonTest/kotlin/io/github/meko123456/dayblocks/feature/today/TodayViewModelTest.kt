package io.github.meko123456.dayblocks.feature.today

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.FindFreeTime
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeOutcomeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class TodayViewModelTest {

    private val zone = TimeZone.of("Asia/Tbilisi") // UTC+4, no DST
    private val monday = LocalDate(2026, 9, 21)
    private val tuesday = LocalDate(2026, 9, 22)
    private fun at(h: Int, m: Int = 0) = h * 60 + m
    private fun block(id: String, date: LocalDate, start: Int, end: Int, category: Category = Category.Work) =
        TimeBlock(BlockId(id), date, id, category, DaySpan(start, end))

    private val work = block("work", monday, at(9), at(12))
    private val rest = block("rest", monday, at(12), at(13), Category.Rest)
    private val read = block("read", monday, at(13), at(15), Category.Reading)
    private val sleep = block("sleep", monday, at(24), at(32), Category.Sleep)
    private val repo = FakeBlockRepository(listOf(work, rest, read, sleep))
    private val outcomes = FakeOutcomeRepository(repo)

    /** A ViewModel whose clock starts at [start] and moves only when the test advances virtual time. */
    private fun TestScope.todayAt(start: LocalDateTime): TodayViewModel {
        val clock = OffsetTimeProvider(start.toInstant(zone), zone) { testScheduler.currentTime }
        return TodayViewModel(repo, outcomes, clock, PlanningDayRule(), ResolveNow(), FindFreeTime(), backgroundScope)
    }

    private suspend fun ReceiveTurbine<TodayState>.awaitLoaded(): TodayState {
        var state = awaitItem()
        while (state.loading) state = awaitItem()
        return state
    }

    @Test
    fun midMorningTheNowCardShowsTheBlockItsCountdownAndWhatIsNext() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 10, 30)).state.test {
            val state = awaitLoaded()
            assertEquals(monday, state.planDate)
            assertEquals(NowCard(current = work, minutesLeft = 90, next = rest, minutesUntilNext = 90), state.now)
        }
    }

    @Test
    fun theCountdownMovesWithTheClockAndTheBlockChangesAtTheBoundary() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 10, 30)).state.test {
            awaitLoaded()
            advanceTimeBy(60.seconds); runCurrent()
            assertEquals(89, awaitItem().now.minutesLeft)

            advanceTimeBy(89.minutes); runCurrent() // 12:00 exactly
            val atNoon = expectMostRecentItem()
            assertEquals(rest, atNoon.now.current)
            assertEquals(60, atNoon.now.minutesLeft)
        }
    }

    @Test
    fun blocksAreMarkedPastCurrentOrUpcoming() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 12, 30)).state.test {
            val statuses = awaitLoaded().timeline.filterIsInstance<TimelineItem.Block>().associate { it.block.id.value to it.status }
            assertEquals(
                mapOf("work" to BlockStatus.Past, "rest" to BlockStatus.Current, "read" to BlockStatus.Upcoming, "sleep" to BlockStatus.Upcoming),
                statuses,
            )
        }
    }

    @Test
    fun freeTimeSitsInTheTimelineInOrderBetweenTheBlocks() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 10, 0)).state.test {
            val timeline = awaitLoaded().timeline
            val shape = timeline.map { item ->
                when (item) {
                    is TimelineItem.Block -> item.block.id.value
                    is TimelineItem.FreeTime -> "free ${item.span.startMinutes / 60}-${item.span.endMinutes / 60}"
                }
            }
            // At 10:00 the 06:00–09:00 gap has already gone by, so it is not offered; past blocks
            // still are, so the day keeps its shape. The only usable gap is 15:00–24:00.
            assertEquals(listOf("work", "rest", "read", "free 15-24", "sleep"), shape)
        }
    }

    @Test
    fun theCurrentTimeLineSitsAtThePresentMinute() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 10, 30)).state.test {
            assertEquals(at(10, 30), awaitLoaded().nowMinute)
        }
    }

    @Test
    fun aPlanChangedElsewhereRedrawsWithoutAnyIntent() = runTest {
        todayAt(LocalDateTime(2026, 9, 21, 10, 30)).state.test {
            awaitLoaded()
            repo.upsert(block("gym", monday, at(18), at(20), Category.Exercise))
            runCurrent()
            val ids = expectMostRecentItem().timeline.filterIsInstance<TimelineItem.Block>().map { it.block.id.value }
            assertTrue("gym" in ids, "timeline was $ids")
        }
    }

    @Test
    fun atHalfPastMidnightMondayIsStillOnScreenAndSleepIsCurrent() = runTest {
        todayAt(LocalDateTime(2026, 9, 22, 0, 30)).state.test {
            val state = awaitLoaded()
            assertEquals(monday, state.planDate) // before the 04:00 rollover
            assertEquals(sleep, state.now.current)
            assertEquals(450, state.now.minutesLeft) // until 08:00
            assertEquals(at(24, 30), state.nowMinute) // 1470, not 30
            // One lap of the clock: 06:00 to 06:00 the next morning. Sleep starts inside it and is
            // drawn clipped at the end rather than stretching the timeline to 26 hours.
            assertEquals(DaySpan(at(6), at(30)), state.window)
            assertTrue(sleep.span.startMinutes in state.window)
        }
    }

    @Test
    fun atTheRolloverTheScreenMovesToTheNewDay() = runTest {
        repo.upsert(block("tue-work", tuesday, at(9), at(12)))
        todayAt(LocalDateTime(2026, 9, 22, 3, 59)).state.test {
            assertEquals(monday, awaitLoaded().planDate)
            advanceTimeBy(60.seconds); runCurrent()
            val rolled = expectMostRecentItem()
            assertEquals(tuesday, rolled.planDate)
            // Monday's Sleep is still running at 04:00 Tuesday — it belongs to yesterday's plan,
            // which the Now card still consults even though the timeline has moved on.
            assertEquals(sleep, rolled.now.current)
            assertEquals(listOf("tue-work"), rolled.timeline.filterIsInstance<TimelineItem.Block>().map { it.block.id.value })
        }
    }

    @Test
    fun anEmptyDayIsFreeFromNowUntilMidnight() = runTest {
        repo.all.value = emptyList()
        todayAt(LocalDateTime(2026, 9, 21, 10, 0)).state.test {
            val state = awaitLoaded()
            assertEquals(listOf(TimelineItem.FreeTime(DaySpan(at(10), at(24)))), state.timeline)
            assertNull(state.now.current)
            assertNull(state.now.next)
        }
    }

    @Test
    fun freeTimeStartsAtTheNextQuarterHourNotAtTheTopOfTheDay() = runTest {
        // Found by running the app: at 12:20 an empty day used to be one 06:00–24:00 dashed box
        // whose label was scrolled out of sight. The usable part of the day starts at 12:30.
        repo.all.value = emptyList()
        todayAt(LocalDateTime(2026, 9, 21, 12, 20)).state.test {
            assertEquals(listOf(TimelineItem.FreeTime(DaySpan(at(12, 30), at(24)))), awaitLoaded().timeline)
        }
    }

    @Test
    fun quarterHourRoundingLeavesAlignedMinutesAlone() {
        assertEquals(at(12, 30), ceilToQuarter(at(12, 20)))
        assertEquals(at(12, 15), ceilToQuarter(at(12, 15)))
        assertEquals(at(12, 15), ceilToQuarter(at(12, 1)))
        assertEquals(0, ceilToQuarter(0))
    }

    @Test
    fun recordsAttachToTheBlocksTheyDescribe() = runTest {
        outcomes.recordAnswer(work.id, CheckInAnswer.GotDistracted, Instant.parse("2026-09-21T07:00:00Z"))
        todayAt(LocalDateTime(2026, 9, 21, 12, 30)).state.test {
            val workItem = awaitLoaded().timeline.filterIsInstance<TimelineItem.Block>().single { it.block == work }
            assertEquals(BlockRecord(work.id, answer = CheckInAnswer.GotDistracted), workItem.record)
            assertEquals(BlockOutcome.Partly, workItem.record?.effectiveOutcome)
        }
    }

    @Test
    fun tappingFreeTimeOpensTheEditorPrefilledWithThatGap() = runTest {
        val vm = todayAt(LocalDateTime(2026, 9, 21, 10, 0))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(TodayIntent.FreeTimeTapped(DaySpan(at(15), at(18))))
            assertEquals(TodayEffect.OpenEditor(monday, prefill = DaySpan(at(15), at(18))), awaitItem())
        }
    }

    @Test
    fun tappingABlockOpensTheEditorForThatBlock() = runTest {
        val vm = todayAt(LocalDateTime(2026, 9, 21, 10, 0))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(TodayIntent.BlockTapped(work.id))
            assertEquals(TodayEffect.OpenEditor(monday, blockId = work.id), awaitItem())
        }
    }

    @Test
    fun theOtherButtonsEachNavigateOnce() = runTest {
        val vm = todayAt(LocalDateTime(2026, 9, 21, 10, 0))
        vm.state.test { awaitLoaded() }
        vm.effects.test {
            vm.onIntent(TodayIntent.AddBlockTapped)
            vm.onIntent(TodayIntent.CheckInTapped)
            vm.onIntent(TodayIntent.TemplatesTapped)
            vm.onIntent(TodayIntent.StatsTapped)
            vm.onIntent(TodayIntent.SettingsTapped)
            assertEquals(TodayEffect.OpenEditor(monday), awaitItem())
            assertEquals(TodayEffect.OpenCheckIn, awaitItem())
            assertEquals(TodayEffect.OpenTemplates, awaitItem())
            assertEquals(TodayEffect.OpenStats, awaitItem())
            assertEquals(TodayEffect.OpenSettings, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun countdownsRoundUpSoARunningBlockNeverReadsZero() {
        assertEquals(1, 30.seconds.ceilMinutes())
        assertEquals(90, 90.minutes.ceilMinutes())
        assertEquals(90, (89.minutes + 1.seconds).ceilMinutes())
        assertEquals(0, 0.seconds.ceilMinutes())
    }
}

package io.github.meko123456.dayblocks.composeapp.widgets

import io.github.meko123456.dayblocks.core.buddy.BuddyVoice
import io.github.meko123456.dayblocks.core.buddy.TodayBuddy
import io.github.meko123456.dayblocks.core.buddy.WidgetTimeline
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeOutcomeRepository
import io.github.meko123456.dayblocks.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class WidgetUpdaterTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val blocks = FakeBlockRepository()
    private val outcomes = FakeOutcomeRepository(blocks)
    private val settings = FakeSettingsRepository()
    private val published = mutableListOf<WidgetState>()
    private val publisher = object : WidgetPublisher {
        override suspend fun publish(state: WidgetState) {
            published += state
        }
    }

    @Test
    fun theWidgetsAreRepublishedWheneverThePlanOrTheBuddyChanges() = runTest {
        val clock = OffsetTimeProvider(LocalDateTime(2026, 9, 21, 10, 0).toInstant(zone), zone) { testScheduler.currentTime }
        val updater = WidgetUpdater(
            blocks, outcomes, settings, clock, { true }, PlanningDayRule(),
            WidgetTimeline(ResolveNow(), TodayBuddy(BuddyVoice()), ScoreAdherence()), publisher,
        )
        updater.start(backgroundScope)
        runCurrent()
        advanceTimeBy(WidgetUpdater.SETTLE + 1.milliseconds)
        assertEquals(1, published.size)
        assertEquals(null, published.last().entries.first().current)
        assertEquals(LocalDateTime(2026, 9, 22, 4, 0).toInstant(zone), published.last().refreshAt, "the rollover")

        blocks.upsert(TimeBlock(BlockId("work"), monday, "Deep work", Category.Work, DaySpan(9 * 60, 12 * 60)))
        runCurrent()
        advanceTimeBy(WidgetUpdater.SETTLE + 1.milliseconds)
        assertEquals("Deep work", published.last().entries.first().current?.title)

        settings.buddy.value = settings.buddy.value.copy(name = "Bloop")
        runCurrent()
        advanceTimeBy(WidgetUpdater.SETTLE + 1.milliseconds)
        assertEquals("Bloop", published.last().buddyName)
    }
}

package io.github.meko123456.dayblocks.composeapp.reminders

import io.github.meko123456.dayblocks.core.buddy.NotificationPlanner
import io.github.meko123456.dayblocks.core.buddy.PlainVoice
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.AutoFillDay
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.core.notifications.NotificationScheduler
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeOutcomeRepository
import io.github.meko123456.dayblocks.core.testing.FakeTemplateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class ReminderReschedulerTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val tuesday = LocalDate(2026, 9, 22)
    private val blocks = FakeBlockRepository()
    private val outcomes = FakeOutcomeRepository(blocks)
    private val templates = FakeTemplateRepository()
    private val scheduler = RecordingScheduler()
    private var generated = 0

    private fun block(id: String, date: LocalDate, start: Int, end: Int) =
        TimeBlock(BlockId(id), date, id, Category.Work, DaySpan(start, end))

    private fun TestScope.clockAt(start: LocalDateTime): TimeProvider =
        OffsetTimeProvider(start.toInstant(zone), zone) { testScheduler.currentTime }

    private fun rescheduler(clock: TimeProvider) = ReminderRescheduler(
        blocks, outcomes, templates,
        AutoFillDay(blocks, templates, GenerateDayFromTemplate { "generated-${++generated}" }, clock),
        NotificationPlanner(PlainVoice()), scheduler, clock, { true }, PlanningDayRule(),
    )

    @Test
    fun aChangedPlanReachesThePlatformOnceTheEditSettles() = runTest {
        rescheduler(clockAt(LocalDateTime(2026, 9, 21, 9, 0))).start(backgroundScope)
        runCurrent()
        advanceTimeBy(ReminderRescheduler.SETTLE + 1.milliseconds)
        assertEquals(1, scheduler.passes.size, "one pass as soon as it starts")

        blocks.upsert(block("read", monday, 13 * 60, 15 * 60))
        runCurrent()
        advanceTimeBy(ReminderRescheduler.SETTLE / 2)
        assertEquals(1, scheduler.passes.size, "not before the edit settles")
        advanceTimeBy(ReminderRescheduler.SETTLE)
        assertEquals(2, scheduler.passes.size)
        assertTrue(scheduler.last.any { it.id == "start:read" }, "${scheduler.last}")
    }

    @Test
    fun aNewPlanningDayIsRescheduledWithoutAnyEdit() = runTest {
        rescheduler(clockAt(LocalDateTime(2026, 9, 21, 3, 58))).start(backgroundScope) // still Sunday's plan
        runCurrent()
        advanceTimeBy(ReminderRescheduler.SETTLE + 1.milliseconds)
        val before = scheduler.passes.size
        advanceTimeBy(3.minutes) // 04:00 rolls the plan over to Monday
        assertEquals(before + 1, scheduler.passes.size)
    }

    @Test
    fun tomorrowsTemplateIsFilledTonightSoItsMorningIsScheduled() = runTest {
        val weekday = Template(TemplateId("weekday"), "Weekday", listOf(TemplateBlock("Run", Category.Exercise, DaySpan(7 * 60, 8 * 60))))
        templates.upsert(weekday)
        templates.assign(DayOfWeek.TUESDAY, weekday.id)

        rescheduler(clockAt(LocalDateTime(2026, 9, 21, 21, 0))).rescheduleNow() // Monday evening

        val run = blocks.all.value.single { it.date == tuesday }
        assertEquals("Run", run.title)
        val start = scheduler.last.single { it.id == "start:${run.id.value}" }
        assertEquals(LocalDateTime(2026, 9, 22, 7, 0).toInstant(zone), start.at)
        assertTrue(scheduler.last.none { it.id.startsWith("plan:2026-09-22") }, "Tuesday is planned: ${scheduler.last}")
    }

    @Test
    fun answeringGotDistractedRecordsItAndSchedulesTheFollowUp() = runTest {
        val read = block("read", monday, 13 * 60, 15 * 60)
        blocks.upsert(read)
        val clock = clockAt(LocalDateTime(2026, 9, 21, 14, 2))

        ReminderResponder(outcomes, clock, rescheduler(clock)).answer(read.id, CheckInAnswer.GotDistracted)

        assertEquals(CheckInAnswer.GotDistracted, outcomes.observeDay(monday).first()[read.id]?.answer)
        val nudge = scheduler.last.single { it.kind == NotificationKind.Nudge }
        assertEquals(LocalDateTime(2026, 9, 21, 14, 12).toInstant(zone), nudge.at)
        assertTrue(scheduler.last.none { it.id == "checkin:read" }, "an answered block is not asked again")
    }

    @Test
    fun anAnswerForABlockDeletedSinceIsDroppedQuietly() = runTest {
        val clock = clockAt(LocalDateTime(2026, 9, 21, 14, 2))
        ReminderResponder(outcomes, clock, rescheduler(clock)).answer(BlockId("deleted-since"), CheckInAnswer.OnIt)
        assertTrue(outcomes.observeDay(monday).first().isEmpty())
        assertEquals(1, scheduler.passes.size, "and the schedule is still rebuilt")
    }

    private class RecordingScheduler : NotificationScheduler {
        val passes = mutableListOf<List<ScheduledNotification>>()
        val last: List<ScheduledNotification> get() = passes.last()

        override suspend fun replaceAll(notifications: List<ScheduledNotification>) {
            passes += notifications
        }
    }
}

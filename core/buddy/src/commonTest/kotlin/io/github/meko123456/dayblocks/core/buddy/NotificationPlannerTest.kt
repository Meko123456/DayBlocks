package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.BlockStart
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.EndOfDay
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.MidBlockCheckIn
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.Nudge
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.PlanningReminder
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

class NotificationPlannerTest {
    private val tbilisi = TimeZone.of("Asia/Tbilisi")
    private val berlin = TimeZone.of("Europe/Berlin")
    private val monday = LocalDate(2026, 9, 21)
    private val tuesday = monday + DatePeriod(days = 1)
    private val planner = NotificationPlanner(PlainVoice())

    private fun at(date: LocalDate, hour: Int, minute: Int = 0, zone: TimeZone = tbilisi): Instant =
        LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)

    private fun block(id: String, date: LocalDate, start: Int, end: Int, title: String = id, category: Category = Category.Work) =
        TimeBlock(BlockId(id), date, title, category, DaySpan(start, end))

    private fun day(date: LocalDate, vararg blocks: TimeBlock, records: Map<BlockId, BlockRecord> = emptyMap()) =
        PlannedDay(date, blocks.toList(), records)

    private fun plan(
        now: Instant,
        vararg days: PlannedDay,
        is24Hour: Boolean = true,
        zone: TimeZone = tbilisi,
        templated: Set<DayOfWeek> = emptySet(),
    ): List<ScheduledNotification> = planner.plan(PlanInput(now, zone, is24Hour, days.toList(), templated))

    private val reading = block("read", monday, 13 * 60, 15 * 60, "Reading", Category.Reading)

    @Test
    fun aBlockIsAnnouncedAtItsStartInTheWordsAndClockOfTheDevice() {
        val start = plan(at(monday, 9), day(monday, reading)).single { it.kind == BlockStart }
        assertEquals(at(monday, 13), start.at)
        assertEquals("start:read", start.id)
        assertEquals(reading.id, start.blockId)
        assertEquals("Kubi", start.title)
        assertEquals("Hey! It's 13:00. Time for “Reading” 📖 You've got 2 hours.", start.body)
        assertTrue(start.actions.isEmpty(), "a start notification has nothing to answer")

        val twelveHour = plan(at(monday, 9), day(monday, reading), is24Hour = false).single { it.kind == BlockStart }
        assertTrue(twelveHour.body.startsWith("Hey! It's 1:00 PM."), twelveHour.body)
    }

    @Test
    fun nothingIsScheduledForAMomentAlreadyPast() {
        val result = plan(at(monday, 14, 30), day(monday, reading))
        assertTrue(result.none { it.kind == BlockStart || it.kind == MidBlockCheckIn }, "$result")
    }

    @Test
    fun aLongBlockIsCheckedOnHalfwayWithTheThreeAnswers() {
        val checkIn = plan(at(monday, 9), day(monday, reading)).single { it.kind == MidBlockCheckIn }
        assertEquals(at(monday, 14), checkIn.at)
        assertEquals("checkin:read", checkIn.id)
        assertEquals(listOf(CheckInAnswer.OnIt, CheckInAnswer.GotDistracted, CheckInAnswer.SkipBlock), checkIn.actions)
        assertEquals("Still on “Reading”? Or did the phone win again? 👀", checkIn.body)

        // An hour is long enough to be asked about.
        val walk = block("walk", monday, 10 * 60, 11 * 60)
        assertEquals(at(monday, 10, 30), plan(at(monday, 9), day(monday, walk)).single { it.kind == MidBlockCheckIn }.at)
    }

    @Test
    fun shortBlocksAndSleepAreAnnouncedButNeverCheckedOn() {
        val coffee = block("coffee", monday, 10 * 60, 10 * 60 + 45)
        val sleep = block("sleep", monday, 23 * 60, 31 * 60, "Sleep", Category.Sleep)
        val result = plan(at(monday, 9), day(monday, coffee, sleep))
        assertTrue(result.none { it.kind == MidBlockCheckIn }, "$result")
        assertEquals(setOf("start:coffee", "start:sleep"), result.filter { it.kind == BlockStart }.map { it.id }.toSet())
    }

    @Test
    fun aBlockAlreadyAnsweredIsNotAskedAgain() {
        val records = mapOf(reading.id to BlockRecord(reading.id, answer = CheckInAnswer.OnIt, answeredAt = at(monday, 13, 5)))
        assertTrue(plan(at(monday, 13, 10), day(monday, reading, records = records)).none { it.kind == MidBlockCheckIn })
    }

    @Test
    fun gotDistractedBringsAFollowUpTenMinutesLaterWithTheTimeLeft() {
        val records = mapOf(reading.id to BlockRecord(reading.id, answer = CheckInAnswer.GotDistracted, answeredAt = at(monday, 14, 2)))
        val nudge = plan(at(monday, 14, 2), day(monday, reading, records = records)).single { it.kind == Nudge }
        assertEquals(at(monday, 14, 12), nudge.at)
        assertEquals("nudge:read", nudge.id)
        assertEquals("No stress, 48m left. Let's go back to it.", nudge.body)
        assertTrue(nudge.actions.isEmpty())
    }

    @Test
    fun noFollowUpWhenTheBlockIsAlmostOverOrTheMomentHasPassed() {
        fun distractedAt(hour: Int, minute: Int) =
            mapOf(reading.id to BlockRecord(reading.id, answer = CheckInAnswer.GotDistracted, answeredAt = at(monday, hour, minute)))
        // Answered at 14:47: the follow-up would come at 14:57 with three minutes left.
        assertTrue(plan(at(monday, 14, 47), day(monday, reading, records = distractedAt(14, 47))).none { it.kind == Nudge })
        // Answered at 14:02 and rescheduled at 14:30: 14:12 is gone, and is not sent late.
        assertTrue(plan(at(monday, 14, 30), day(monday, reading, records = distractedAt(14, 2))).none { it.kind == Nudge })
    }

    @Test
    fun theWindowReachesThirtySixHoursAhead() {
        val now = at(monday, 9) // the window closes Tuesday 21:00
        val inside = block("inside", tuesday, 20 * 60, 20 * 60 + 30)
        val outside = block("outside", tuesday, 21 * 60 + 30, 22 * 60)
        val starts = plan(now, day(monday), day(tuesday, inside, outside)).filter { it.kind == BlockStart }
        assertEquals(listOf("start:inside"), starts.map { it.id })
    }

    @Test
    fun noMoreThanSixtyFourArePendingAndTheEarliestAreKept() {
        // Forty half-hour blocks a day, 04:00 to midnight: far more than iOS would hold.
        fun busy(date: LocalDate) = day(date, *(0 until 40).map { i ->
            block("$date-$i", date, 4 * 60 + i * 30, 4 * 60 + i * 30 + 30)
        }.toTypedArray())
        val now = at(monday, 9)
        val result = plan(now, busy(monday), busy(tuesday), busy(tuesday + DatePeriod(days = 1)))
        assertEquals(NotificationPlanner.MAX_PENDING, result.size)
        assertEquals(result.sortedBy { it.at }, result)
        assertEquals(at(monday, 9, 30), result.first().at, "09:00 is now, so the first is 09:30")
        assertTrue(result.all { it.at > now && it.at < now + 36.hours })
    }

    @Test
    fun aBlockAcrossMidnightFiresOnTheRightCalendarDay() {
        val film = block("film", monday, 23 * 60 + 30, 24 * 60 + 30, "Late film") // Monday 23:30 to Tuesday 00:30
        val sleep = block("sleep", monday, 24 * 60 + 30, 32 * 60, "Sleep", Category.Sleep) // "00:30 Sleep" ends Monday's plan
        val result = plan(at(monday, 22), day(monday, film, sleep), day(tuesday, block("work", tuesday, 9 * 60, 10 * 60)))
        assertEquals(at(monday, 23, 30), result.single { it.id == "start:film" }.at)
        assertEquals(at(tuesday, 0, 0), result.single { it.id == "checkin:film" }.at)
        val bedtime = result.single { it.id == "start:sleep" }
        assertEquals(at(tuesday, 0, 30), bedtime.at)
        assertTrue(bedtime.body.startsWith("Hey! It's 00:30."), bedtime.body)
    }

    @Test
    fun yesterdaysBlockStillRunningAfterMidnightIsCheckedOn() {
        val sunday = LocalDate(2026, 9, 20)
        val shift = block("shift", sunday, 22 * 60, 28 * 60, "Night shift") // Sunday 22:00 to Monday 04:00
        val checkIn = plan(at(monday, 0, 30), day(sunday, shift), day(monday, reading)).single { it.id == "checkin:shift" }
        assertEquals(at(monday, 1), checkIn.at)
    }

    @Test
    fun onASpringForwardNightABlockInTheMissingHourStartsAtTheFirstRealMoment() {
        val dstDay = LocalDate(2026, 3, 29) // Berlin: 02:00 CET becomes 03:00 CEST
        val gap = block("gap", dstDay, 2 * 60 + 30, 4 * 60, "Early run", Category.Exercise)
        val now = LocalDateTime(2026, 3, 28, 22, 0).toInstant(berlin)
        val result = plan(now, day(dstDay, gap), zone = berlin)
        val start = result.single { it.id == "start:gap" }
        assertEquals(LocalDateTime(2026, 3, 29, 3, 30).toInstant(berlin), start.at)
        assertTrue(start.body.startsWith("Hey! It's 03:30."), start.body)
        assertTrue(start.body.contains("30 minutes"), "the block really lasts half an hour: ${start.body}")
        assertTrue(result.none { it.id == "checkin:gap" }, "half an hour is too short to be checked on")
    }

    @Test
    fun onAFallBackNightABlockLastsItsRealLengthAndIsCheckedOnAtItsRealMiddle() {
        val fallDay = LocalDate(2026, 10, 25) // Berlin: 03:00 CEST becomes 02:00 CET
        val film = block("film", fallDay, 1 * 60, 4 * 60, "Film")
        val now = LocalDateTime(2026, 10, 24, 22, 0).toInstant(berlin)
        val result = plan(now, day(fallDay, film), zone = berlin)
        assertTrue(result.single { it.id == "start:film" }.body.contains("4 hours"))
        // 01:00 CEST is 23:00Z and 04:00 CET is 03:00Z: the middle is 01:00Z, whatever the wall says.
        assertEquals(Instant.parse("2026-10-25T01:00:00Z"), result.single { it.id == "checkin:film" }.at)
    }

    @Test
    fun theDayIsReviewedShortlyAfterItsLastWakingBlock() {
        val work = block("work", monday, 9 * 60, 12 * 60)
        val read = block("read", monday, 20 * 60, 21 * 60, "Reading", Category.Reading)
        val sleep = block("sleep", monday, 23 * 60, 31 * 60, "Sleep", Category.Sleep)
        val review = plan(at(monday, 8), day(monday, work, read, sleep)).single { it.kind == EndOfDay }
        assertEquals(at(monday, 21, 10), review.at)
        assertEquals("review:2026-09-21", review.id)
        assertEquals("How did today go? Tap to review.", review.body)

        val reviewed = listOf(work, read, sleep).associate { it.id to BlockRecord(it.id, outcome = BlockOutcome.Done) }
        assertTrue(plan(at(monday, 8), day(monday, work, read, sleep, records = reviewed)).none { it.kind == EndOfDay })
    }

    @Test
    fun anEmptyTomorrowIsRemindedInTheEveningAndAgainInTheMorning() {
        val today = day(monday, block("work", monday, 9 * 60, 17 * 60))
        val reminders = plan(at(monday, 12), today, day(tuesday)).filter { it.kind == PlanningReminder }
        assertEquals(listOf(at(monday, 20), at(tuesday, 8, 30)), reminders.map { it.at })
        assertEquals("Tomorrow's still empty. Want to plan it in 2 minutes?", reminders[0].body)
        assertEquals(listOf("plan:2026-09-22:evening", "plan:2026-09-22:morning"), reminders.map { it.id })

        val planned = day(tuesday, block("gym", tuesday, 7 * 60, 8 * 60))
        assertTrue(plan(at(monday, 12), today, planned).none { it.kind == PlanningReminder })
        val fillsItself = plan(at(monday, 12), today, day(tuesday), templated = setOf(DayOfWeek.TUESDAY))
        assertTrue(fillsItself.none { it.kind == PlanningReminder }, "an assigned template will fill Tuesday")
    }

    @Test
    fun rebuildingTheScheduleKeepsEveryId() {
        val first = plan(at(monday, 9), day(monday, reading), day(tuesday)).map { it.id }
        val later = plan(at(monday, 10), day(monday, reading), day(tuesday)).map { it.id }
        assertEquals(first, later)
    }

    @Test
    fun lengthsAreSaidTheWayAPersonWouldSayThem() {
        assertEquals("an hour", spokenLength(60))
        assertEquals("2 hours", spokenLength(120))
        assertEquals("45 minutes", spokenLength(45))
        assertEquals("1h 20m", spokenLength(80))
        assertEquals("a minute", spokenLength(1))
    }
}

package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.BlockStart
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.Comeback
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.EndOfDay
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.MidBlockCheckIn
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.Nudge
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.PlanningReminder
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind.Streak
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    /** One line per situation — the spec's own — so tests can assert exact words. */
    private val planner = NotificationPlanner(BuddyVoice(MessagePools.Default.firstLinesOnly()))

    /** Timing tests run without quiet hours or a cap; each of those has tests of its own. */
    private val unlimited = BuddySettings(quietHours = QuietHours(LocalTime(0, 0), LocalTime(0, 0)), dailyCap = BuddySettings.MAX_DAILY_CAP)

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
        settings: BuddySettings = unlimited,
        streak: Int = 0,
        lastOpened: Instant? = null,
        voice: NotificationPlanner = planner,
    ): List<ScheduledNotification> =
        voice.plan(PlanInput(now, zone, is24Hour, days.toList(), templated, settings, streak, lastOpened))

    private val reading = block("read", monday, 13 * 60, 15 * 60, "Reading", Category.Reading)

    // ── When ─────────────────────────────────────────────────────────────────────────────────

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
    fun theBuddySpeaksUnderTheNameItWasGiven() {
        val renamed = plan(at(monday, 9), day(monday, reading), settings = unlimited.copy(name = "Bloop"))
        assertTrue(renamed.all { it.title == "Bloop" })
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
    fun aBlockAlreadyRatedAtTheCheckInIsNotAskedAboutEither() {
        val rated = mapOf(reading.id to BlockRecord(reading.id, outcome = BlockOutcome.Done))
        assertTrue(plan(at(monday, 13, 10), day(monday, reading, records = rated)).none { it.kind == MidBlockCheckIn })
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
        assertTrue(plan(at(monday, 14, 47), day(monday, reading, records = distractedAt(14, 47))).none { it.kind == Nudge })
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
        // A block every quarter hour from 04:00: more than iOS would hold, even after the daily cap.
        fun busy(date: LocalDate) = day(date, *(0 until 80).map { i ->
            block("$date-$i", date, 4 * 60 + i * 15, 4 * 60 + i * 15 + 15)
        }.toTypedArray())
        val now = at(monday, 3)
        val result = plan(now, busy(monday), busy(tuesday))
        assertEquals(NotificationPlanner.MAX_PENDING, result.size)
        assertEquals(result.sortedBy { it.at }, result)
        assertEquals(at(monday, 4), result.first().at)
        assertEquals(at(tuesday, 9, 45), result.last().at, "Monday's forty, the daily cap, then Tuesday's first 24")
        assertTrue(result.all { it.at > now && it.at < now + 36.hours })
    }

    @Test
    fun aBlockAcrossMidnightFiresOnTheRightCalendarDay() {
        val film = block("film", monday, 23 * 60 + 30, 24 * 60 + 30, "Late film")
        val sleep = block("sleep", monday, 24 * 60 + 30, 32 * 60, "Sleep", Category.Sleep)
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
        val shift = block("shift", sunday, 22 * 60, 28 * 60, "Night shift")
        val checkIn = plan(at(monday, 0, 30), day(sunday, shift), day(monday, reading)).single { it.id == "checkin:shift" }
        assertEquals(at(monday, 1), checkIn.at)
    }

    @Test
    fun onASpringForwardNightABlockInTheMissingHourStartsAtTheFirstRealMoment() {
        val dstDay = LocalDate(2026, 3, 29)
        val gap = block("gap", dstDay, 2 * 60 + 30, 4 * 60, "Early run", Category.Exercise)
        val result = plan(LocalDateTime(2026, 3, 28, 22, 0).toInstant(berlin), day(dstDay, gap), zone = berlin)
        val start = result.single { it.id == "start:gap" }
        assertEquals(LocalDateTime(2026, 3, 29, 3, 30).toInstant(berlin), start.at)
        assertTrue(start.body.startsWith("Hey! It's 03:30."), start.body)
        assertTrue(start.body.contains("30 minutes"), start.body)
        assertTrue(result.none { it.id == "checkin:gap" })
    }

    @Test
    fun onAFallBackNightABlockLastsItsRealLengthAndIsCheckedOnAtItsRealMiddle() {
        val fallDay = LocalDate(2026, 10, 25)
        val film = block("film", fallDay, 1 * 60, 4 * 60, "Film")
        val result = plan(LocalDateTime(2026, 10, 24, 22, 0).toInstant(berlin), day(fallDay, film), zone = berlin)
        assertTrue(result.single { it.id == "start:film" }.body.contains("4 hours"))
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
        assertEquals(monday, review.date, "tapping it opens that day's check-in")
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

        assertTrue(plan(at(monday, 12), today, day(tuesday, block("gym", tuesday, 7 * 60, 8 * 60))).none { it.kind == PlanningReminder })
        assertTrue(plan(at(monday, 12), today, day(tuesday), templated = setOf(DayOfWeek.TUESDAY)).none { it.kind == PlanningReminder })
    }

    @Test
    fun rebuildingTheScheduleKeepsEveryIdAndEveryWord() {
        val default = NotificationPlanner(BuddyVoice())
        val first = plan(at(monday, 9), day(monday, reading), day(tuesday), voice = default)
        val later = plan(at(monday, 10), day(monday, reading), day(tuesday), voice = default)
        assertEquals(first.map { it.id to it.body }, later.map { it.id to it.body })
    }

    // ── Tone ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun aGentleBuddyOnlyAsksAboutLongBlocksAndGivesMoreTimeBeforeFollowingUp() {
        val gentle = unlimited.copy(tone = BuddyTone.Gentle)
        val hour = block("hour", monday, 10 * 60, 11 * 60)
        val long = block("long", monday, 13 * 60, 14 * 60 + 30)
        val checkIns = plan(at(monday, 9), day(monday, hour, long), settings = gentle).filter { it.kind == MidBlockCheckIn }
        assertEquals(listOf("checkin:long"), checkIns.map { it.id })

        val distracted = mapOf(long.id to BlockRecord(long.id, answer = CheckInAnswer.GotDistracted, answeredAt = at(monday, 13, 45)))
        val nudge = plan(at(monday, 13, 45), day(monday, long, records = distracted), settings = gentle).single { it.kind == Nudge }
        assertEquals(at(monday, 14), nudge.at, "fifteen minutes, not ten")

        val reminders = plan(at(monday, 12), day(monday, hour), day(tuesday), settings = gentle).filter { it.kind == PlanningReminder }
        assertEquals(listOf("plan:2026-09-22:evening"), reminders.map { it.id }, "no second reminder in the morning")
    }

    @Test
    fun aPushyBuddyAsksSoonerTwiceAboutLongBlocksAndFollowsUpTwice() {
        val pushy = unlimited.copy(tone = BuddyTone.Pushy)
        val short = block("short", monday, 10 * 60, 10 * 60 + 45)
        val long = block("long", monday, 13 * 60, 15 * 60)
        val checkIns = plan(at(monday, 9), day(monday, short, long), settings = pushy).filter { it.kind == MidBlockCheckIn }
        assertEquals(
            listOf("checkin:short" to at(monday, 10, 22), "checkin:long" to at(monday, 13, 40), "checkin2:long" to at(monday, 14, 20)),
            checkIns.map { it.id to it.at },
        )

        val distracted = mapOf(long.id to BlockRecord(long.id, answer = CheckInAnswer.GotDistracted, answeredAt = at(monday, 13, 40)))
        val nudges = plan(at(monday, 13, 40), day(monday, long, records = distracted), settings = pushy).filter { it.kind == Nudge }
        assertEquals(listOf(at(monday, 13, 50), at(monday, 14, 5)), nudges.map { it.at })
    }

    @Test
    fun theToneChangesTheWordsNotOnlyTheRhythm() {
        fun startLine(tone: BuddyTone) = plan(at(monday, 9), day(monday, reading), settings = unlimited.copy(tone = tone)).single { it.kind == BlockStart }.body
        assertEquals(3, BuddyTone.entries.map(::startLine).toSet().size)
    }

    // ── Quiet hours ──────────────────────────────────────────────────────────────────────────

    private val quietNights = unlimited.copy(quietHours = QuietHours(LocalTime(23, 0), LocalTime(8, 0)))

    @Test
    fun insideQuietHoursOnlyWhatTheUserPlannedIsStillSaid() {
        val film = block("film", monday, 23 * 60 + 30, 24 * 60 + 30, "Late film")
        val result = plan(at(monday, 21), day(monday, film), day(tuesday, block("work", tuesday, 9 * 60, 10 * 60)), settings = quietNights)
        assertTrue(result.any { it.id == "start:film" }, "planning a late film is asking to hear about it: $result")
        assertTrue(result.none { it.id == "checkin:film" }, "but nobody is asked how it is going at midnight")

        val shift = block("shift", monday, 22 * 60, 30 * 60, "Night shift")
        val distracted = mapOf(shift.id to BlockRecord(shift.id, answer = CheckInAnswer.GotDistracted, answeredAt = at(monday, 23, 40)))
        assertTrue(plan(at(monday, 23, 40), day(monday, shift, records = distracted), settings = quietNights).none { it.kind == Nudge })
    }

    @Test
    fun insideQuietHoursWhatCanWaitWaitsForThemToEnd() {
        // Opened at 01:30 on Monday: a day later is 01:30 on Tuesday, inside quiet hours.
        val result = plan(at(monday, 12), day(monday), day(tuesday), settings = quietNights, lastOpened = at(monday, 1, 30))
        assertEquals(at(tuesday, 8), result.single { it.kind == Comeback }.at)

        // A morning planning reminder at 08:30 moves to 10:00 when quiet hours run to 10:00...
        val lateMornings = unlimited.copy(quietHours = QuietHours(LocalTime(23, 0), LocalTime(10, 0)))
        val today = day(monday, block("work", monday, 12 * 60, 13 * 60))
        assertEquals(at(tuesday, 10), plan(at(monday, 12), today, day(tuesday), settings = lateMornings).single { it.id == "plan:2026-09-22:morning" }.at)
        // ...but "tomorrow's still empty" is only true the evening before, so at 20:00 inside quiet hours it is dropped.
        val earlyNights = unlimited.copy(quietHours = QuietHours(LocalTime(19, 0), LocalTime(8, 0)))
        assertTrue(plan(at(monday, 12), today, day(tuesday), settings = earlyNights).none { it.id == "plan:2026-09-22:evening" })
    }

    // ── The daily cap ────────────────────────────────────────────────────────────────────────

    @Test
    fun overTheCapBlockStartsWinAndTheBudgetIsTheWholeDay() {
        val blocks = listOf(
            block("a", monday, 9 * 60, 10 * 60),
            block("b", monday, 11 * 60, 12 * 60),
            block("c", monday, 15 * 60, 16 * 60),
        )
        val capped = unlimited.copy(dailyCap = 4)
        // Seven candidates — three starts, three check-ins, the review — for a budget of four:
        // the starts, then the earliest check-in.
        val morning = plan(at(monday, 8), day(monday, *blocks.toTypedArray()), settings = capped)
        assertEquals(listOf("start:a", "checkin:a", "start:b", "start:c"), morning.map { it.id })
        // At 14:00 the first three are spent. The budget is not handed out again.
        val afternoon = plan(at(monday, 14), day(monday, *blocks.toTypedArray()), settings = capped)
        assertEquals(listOf("start:c"), afternoon.map { it.id })
    }

    // ── Streak and comeback ──────────────────────────────────────────────────────────────────

    @Test
    fun aStreakWorthProtectingIsMentionedInTheMorningOfAPlannedDay() {
        val today = day(monday, reading)
        val streak = plan(at(monday, 7), today, streak = 5).single { it.kind == Streak }
        assertEquals(at(monday, 9), streak.at)
        assertEquals("You've followed your plan 5 days in a row 🔥 Don't break it today!", streak.body)

        assertTrue(plan(at(monday, 7), today, streak = 1).none { it.kind == Streak }, "one day is not a streak yet")
        assertTrue(plan(at(monday, 7), day(monday), streak = 5).none { it.kind == Streak }, "no plan today, nothing to protect")
    }

    @Test
    fun aComebackComesADayAfterTheAppWasLastOpened() {
        val comeback = plan(at(monday, 12), day(monday), day(tuesday), lastOpened = at(monday, 10)).single { it.kind == Comeback }
        assertEquals(at(tuesday, 10), comeback.at)
        assertEquals("I miss you. Just plan one block today?", comeback.body)
        // Opening the app again moves it along.
        val later = plan(at(monday, 18), day(monday), day(tuesday), lastOpened = at(monday, 18)).single { it.kind == Comeback }
        assertEquals(at(tuesday, 18), later.at)
    }

    // ── Rotation ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun backToBackNotificationsOfTheSameKindNeverShareALine() {
        val default = NotificationPlanner(BuddyVoice())
        val blocks = (0 until 6).map { i -> block("b$i", monday, (9 + i) * 60, (9 + i) * 60 + 30) }
        val starts = plan(at(monday, 8), day(monday, *blocks.toTypedArray()), voice = default).filter { it.kind == BlockStart }
        starts.zipWithNext().forEach { (a, b) ->
            val template = { n: ScheduledNotification -> n.body.replace(Regex("\\d{2}:\\d{2}|“[^”]*”"), "") }
            assertNotEquals(template(a), template(b), "${a.body} / ${b.body}")
        }
    }
}

package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

class WidgetTimelineTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val sunday = monday.minus(DatePeriod(days = 1))
    private val timeline = WidgetTimeline(ResolveNow(), TodayBuddy(BuddyVoice()), ScoreAdherence())
    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant = LocalDateTime(date, LocalTime(h, m)).toInstant(zone)
    private fun block(id: String, date: LocalDate, start: Int, end: Int, category: Category = Category.Work) =
        TimeBlock(BlockId(id), date, id, category, DaySpan(start, end))

    private val work = block("work", monday, 9 * 60, 12 * 60)
    private val rest = block("rest", monday, 12 * 60, 13 * 60, Category.Rest)
    private val read = block("read", monday, 13 * 60, 15 * 60, Category.Reading)

    private fun build(now: Instant, today: List<TimeBlock>, yesterday: List<TimeBlock> = emptyList(), records: Map<BlockId, BlockRecord> = emptyMap(), is24Hour: Boolean = true) =
        timeline.build(now, zone, is24Hour, PlannedDay(sunday, yesterday), PlannedDay(monday, today, records), BuddySettings())

    @Test
    fun anEntryNowAndOneAtEveryMomentThePictureChanges() {
        val entries = build(at(monday, 10, 30), listOf(work, rest, read))
        // now, the three boundaries still ahead, and quiet hours at 23:00; 08:00 is past the rollover.
        assertEquals(listOf(at(monday, 10, 30), at(monday, 12), at(monday, 13), at(monday, 15), at(monday, 23)), entries.map { it.at })
        assertEquals(listOf("work", "rest", "read", null, null), entries.map { it.current?.title })
        assertEquals(listOf("rest", "read", null, null, null), entries.map { it.next?.title })
        assertEquals(listOf(0, 1, 2, 3, 3), entries.map { it.done })
        assertEquals(3, entries.first().planned)
    }

    @Test
    fun theBuddyGetsSleepyWhenQuietHoursBegin() {
        val entries = build(at(monday, 10, 30), listOf(work))
        assertEquals(BuddyMood.Happy, entries.first().mood)
        assertEquals(BuddyMood.Sleepy, entries.last().mood)
    }

    @Test
    fun theMoodFollowsTheOutcomesSoFar() {
        val records = mapOf(work.id to BlockRecord(work.id, outcome = BlockOutcome.Skipped))
        assertEquals(BuddyMood.Disappointed, build(at(monday, 12, 30), listOf(work, rest), records = records).first().mood)
    }

    @Test
    fun clocksAreWrittenTheWayTheDeviceReadsThem() {
        val entry = build(at(monday, 10, 30), listOf(read), is24Hour = false).first()
        assertEquals("1:00 PM", entry.next?.startClock)
        assertEquals("3:00 PM", entry.next?.endClock)
        assertEquals(at(monday, 13), entry.next?.starts)
    }

    @Test
    fun lastNightsBlockIsStillOnAfterMidnight() {
        val film = block("film", sunday, 23 * 60, 25 * 60) // Sunday 23:00 to Monday 01:00
        val entry = build(at(monday, 0, 30), today = emptyList(), yesterday = listOf(film)).first()
        assertEquals("film", entry.current?.title)
    }

    @Test
    fun anEmptyDayStillHasAPicture() {
        val entry = build(at(monday, 10, 30), emptyList()).first()
        assertNull(entry.current)
        assertNull(entry.next)
        assertEquals(0f, entry.progress)
        assertEquals(BuddyMood.Encouraging, entry.mood)
    }

    @Test
    fun theTimelineStopsAtTheRollover() {
        val late = build(at(monday, 23, 30), listOf(block("night", monday, 24 * 60 + 30, 26 * 60)))
        val rollover = at(monday.plus(DatePeriod(days = 1)), 4)
        assertEquals(true, late.all { it.at < rollover })
    }
}

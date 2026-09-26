package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TodayBuddyTest {
    private val buddy = TodayBuddy(BuddyVoice(MessagePools.Default.firstLinesOnly()))
    private val settings = BuddySettings()
    private val monday = LocalDate(2026, 9, 21)
    private val reading = TimeBlock(BlockId("read"), monday, "Reading", Category.Reading, DaySpan(13 * 60, 15 * 60))
    private val sleep = TimeBlock(BlockId("sleep"), monday, "Sleep", Category.Sleep, DaySpan(23 * 60, 31 * 60))

    private fun snapshot(time: LocalTime = LocalTime(14, 0), planned: Int = 3, adherence: Int? = null, current: TimeBlock? = null, next: TimeBlock? = null) =
        TodaySnapshot(monday, time, planned, adherence, current, left = current?.let { "1h" }, next = next, nextClock = next?.let { "18:00" })

    private fun mood(s: TodaySnapshot) = buddy.outlook(s, settings).mood

    @Test
    fun theFaceFollowsHowTheDayIsGoing() {
        assertEquals(BuddyMood.Happy, mood(snapshot(adherence = null)), "nothing scored yet is a fresh start")
        assertEquals(BuddyMood.Proud, mood(snapshot(adherence = 90)))
        assertEquals(BuddyMood.Happy, mood(snapshot(adherence = 70)))
        assertEquals(BuddyMood.Encouraging, mood(snapshot(adherence = 50)))
        assertEquals(BuddyMood.Worried, mood(snapshot(adherence = 20)))
        assertEquals(BuddyMood.Disappointed, mood(snapshot(adherence = 5)))
        assertEquals(BuddyMood.Encouraging, mood(snapshot(planned = 0)), "an empty day is an invitation")
    }

    @Test
    fun itIsSleepyAtNightAndThroughASleepBlock() {
        assertEquals(BuddyMood.Sleepy, mood(snapshot(time = LocalTime(23, 30), adherence = 90)))
        assertEquals(BuddyMood.Sleepy, mood(snapshot(time = LocalTime(22, 0), current = sleep)))
        // but a block running inside quiet hours keeps its face
        assertEquals(BuddyMood.Proud, mood(snapshot(time = LocalTime(23, 30), adherence = 90, current = reading)))
    }

    @Test
    fun theLineIsAboutWhatIsHappeningNow() {
        assertEquals("“Reading” now, 1h to go. You're on a roll!", buddy.outlook(snapshot(adherence = 90, current = reading), settings).line)
        assertEquals("Free until 18:00. Want to fill it?", buddy.outlook(snapshot(next = reading), settings).line)
        assertEquals("Nothing planned yet. One block is enough to start.", buddy.outlook(snapshot(planned = 0), settings).line)
        assertEquals("Zzz… see you in the morning.", buddy.outlook(snapshot(time = LocalTime(0, 30)), settings).line)
        assertTrue(buddy.outlook(snapshot(adherence = 10), settings).line.startsWith("Tough day?"))
    }

    @Test
    fun theLineHoldsStillWhileTheSameBlockIsOn() {
        val real = TodayBuddy(BuddyVoice())
        val at14 = real.outlook(snapshot(time = LocalTime(14, 0), adherence = 90, current = reading), settings).line
        val at1430 = real.outlook(snapshot(time = LocalTime(14, 30), adherence = 90, current = reading), settings).line
        assertEquals(at14, at1430)
    }
}

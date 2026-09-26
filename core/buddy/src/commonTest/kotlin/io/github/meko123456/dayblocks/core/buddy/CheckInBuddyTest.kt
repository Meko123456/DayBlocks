package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class CheckInBuddyTest {
    private val buddy = CheckInBuddy(BuddyVoice(MessagePools.Default.firstLinesOnly()))
    private val monday = LocalDate(2026, 9, 21)
    private fun react(score: Int?, unrated: Int = 0, planned: Int = 3) = buddy.reaction(monday, planned, score, unrated, BuddySettings())

    @Test
    fun theBuddyTakesTheScoreWarmlyAtEveryLevel() {
        assertEquals(BuddyOutlook(BuddyMood.Proud, "90%! That's a day to be proud of 🌟"), react(90))
        assertEquals(BuddyOutlook(BuddyMood.Happy, "70% on plan. A solid day!"), react(70))
        assertEquals(BuddyMood.Encouraging, react(40).mood)
        assertEquals(BuddyOutlook(BuddyMood.Worried, "20%, a rough one. Rest up, we'll go again tomorrow 💛"), react(20))
        assertEquals(BuddyMood.Disappointed, react(5).mood)
    }

    @Test
    fun whileBlocksAreUnratedItWaitsAndOnAnEmptyDayItLooksAhead() {
        assertEquals(BuddyOutlook(BuddyMood.Encouraging, "One tap per block: done, partly or skipped?"), react(score = 100, unrated = 1))
        assertEquals(BuddyMood.Encouraging, react(score = null).mood)
        assertEquals("Nothing was planned today. Want to plan tomorrow?", react(score = null, planned = 0).line)
    }
}

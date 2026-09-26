package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuddyVoiceTest {
    private val everySlot = Slots(
        title = "Reading", emoji = "📖", time = "13:00", length = "2 hours", left = "1h 20m",
        streak = 6, next = "Gym", nextTime = "18:00", score = 82,
    )

    @Test
    fun everyLineInEveryPoolFillsCompletely() {
        val pools = MessagePools.Default
        val unfilled = Situation.entries.flatMap { s -> BuddyTone.entries.flatMap { t -> pools.lines(s, t) } }
            .plus(Bubble.entries.flatMap { pools.lines(it) })
            .map { it.fill(everySlot, "Kubi") }
            .filter { '{' in it || '}' in it }
        assertTrue(unfilled.isEmpty(), "placeholders left over: $unfilled")
    }

    @Test
    fun theSituationsThatMatterMostHaveARealPool() {
        val pools = MessagePools.Default
        for (tone in BuddyTone.entries) {
            for (situation in listOf(Situation.BlockStart, Situation.CheckIn, Situation.BackOnTrack)) {
                assertTrue(pools.lines(situation, tone).size >= 3, "$situation in $tone has ${pools.lines(situation, tone).size}")
            }
        }
    }

    @Test
    fun placeholdersAreFilledTheWayTheSpecSaysThem() {
        val voice = BuddyVoice(MessagePools.Default.firstLinesOnly())
        assertEquals(
            Line("Kubi", "Hey! It's 13:00. Time for “Reading” 📖 You've got 2 hours."),
            voice.line(Situation.BlockStart, BuddyTone.Normal, "Kubi", rotation = 0, everySlot),
        )
        assertEquals(
            "You've followed your plan 6 days in a row 🔥 Don't break it today!",
            voice.line(Situation.Streak, BuddyTone.Normal, "Kubi", 0, everySlot).body,
        )
    }

    @Test
    fun consecutiveRotationsGiveDifferentLinesAndTheSameRotationTheSameOne() {
        val voice = BuddyVoice()
        val a = voice.line(Situation.CheckIn, BuddyTone.Normal, "Kubi", 41, everySlot)
        val b = voice.line(Situation.CheckIn, BuddyTone.Normal, "Kubi", 42, everySlot)
        assertTrue(a != b)
        assertEquals(a, voice.line(Situation.CheckIn, BuddyTone.Normal, "Kubi", 41, everySlot))
    }

    @Test
    fun theHashIsTheSameEverywhere() {
        // Pinned values: the rotation must pick the same line on Android and iOS.
        assertEquals(stableHash("BlockStart:2026-09-21"), stableHash("BlockStart:2026-09-21"))
        assertEquals(1335831723, stableHash("hello"))
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

package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DaySpanTest {

    @Test
    fun durationIsEndMinusStart() {
        assertEquals(180, DaySpan.of(9, 0, 12, 0).durationMinutes)
    }

    @Test
    fun aSpanMayRunPastMidnightButNotBeyondADay() {
        val sleep = DaySpan(at(24), at(32)) // "00:00 Sleep" at the end of a plan
        assertEquals(480, sleep.durationMinutes)
        assertTrue(sleep.crossesMidnight)
        assertFalse(DaySpan.of(9, 0, 12, 0).crossesMidnight)
        assertFailsWith<IllegalArgumentException> { DaySpan(at(9), at(9) + 24 * 60 + 1) }
    }

    @Test
    fun endingAtExactlyMidnightDoesNotCrossIt() {
        assertFalse(DaySpan(at(22), at(24)).crossesMidnight)
    }

    @Test
    fun invalidSpansAreRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { DaySpan(at(12), at(12)) }
        assertFailsWith<IllegalArgumentException> { DaySpan(at(12), at(11)) }
        assertFailsWith<IllegalArgumentException> { DaySpan(-15, at(1)) }
        assertFailsWith<IllegalArgumentException> { DaySpan(DaySpan.MAX_START, DaySpan.MAX_START + 15) }
    }

    @Test
    fun backToBackSpansTouchWithoutOverlapping() {
        val work = DaySpan.of(9, 0, 12, 0)
        val rest = DaySpan.of(12, 0, 13, 0)
        assertFalse(work.overlaps(rest))
        assertFalse(rest.overlaps(work))
    }

    @Test
    fun overlapIsSymmetricAndIncludesContainment() {
        val outer = DaySpan.of(9, 0, 17, 0)
        val inner = DaySpan.of(12, 0, 13, 0)
        val straddling = DaySpan.of(16, 30, 18, 0)
        assertTrue(outer.overlaps(inner) && inner.overlaps(outer))
        assertTrue(outer.overlaps(straddling) && straddling.overlaps(outer))
    }

    @Test
    fun containsIsHalfOpen() {
        val work = DaySpan.of(9, 0, 12, 0)
        assertTrue(at(9) in work)
        assertTrue(at(11, 59) in work)
        assertFalse(at(12) in work)
        assertFalse(at(8, 59) in work)
    }
}

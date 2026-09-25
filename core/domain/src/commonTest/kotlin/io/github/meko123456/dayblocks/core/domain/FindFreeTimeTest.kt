package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.usecase.FindFreeTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindFreeTimeTest {

    private val free = FindFreeTime()
    private val waking = DaySpan(at(8), at(22)) // the timeline window

    @Test
    fun anEmptyDayIsOneGapCoveringTheWindow() {
        assertEquals(listOf(waking), free(emptyList(), waking))
    }

    @Test
    fun gapsSitBetweenAndAroundTheBlocks() {
        val blocks = listOf(block("work", MONDAY, at(9), at(12)), block("read", MONDAY, at(13), at(15)))
        assertEquals(
            listOf(DaySpan(at(8), at(9)), DaySpan(at(12), at(13)), DaySpan(at(15), at(22))),
            free(blocks, waking),
        )
    }

    @Test
    fun overlappingBlocksAreMergedSoNoFalseGapAppears() {
        val blocks = listOf(
            block("work", MONDAY, at(9), at(12)),
            block("call", MONDAY, at(11), at(13)),
            block("lunch", MONDAY, at(12, 30), at(13, 30)),
        )
        assertEquals(listOf(DaySpan(at(8), at(9)), DaySpan(at(13, 30), at(22))), free(blocks, waking))
    }

    @Test
    fun aBlockInsideAnotherDoesNotOpenAGapInTheMiddleOfIt() {
        // A 10:00 call inside a 09:00–17:00 work block ends before the work does. Sorted by start,
        // it comes second — and if the cursor simply took *its* end, the timeline would offer
        // "Free time" from 11:00 in the middle of work. Found by mutation testing: the overlap
        // test above only had blocks that each ended later than the last, so it could not tell.
        val blocks = listOf(block("work", MONDAY, at(9), at(17)), block("call", MONDAY, at(10), at(11)))
        assertEquals(listOf(DaySpan(at(8), at(9)), DaySpan(at(17), at(22))), free(blocks, waking))
    }

    @Test
    fun blocksOutsideTheWindowAreIgnoredAndStraddlersAreClipped() {
        val blocks = listOf(
            block("early", MONDAY, at(6), at(8, 30)), // straddles the window's start
            block("late", MONDAY, at(21), at(23)), // straddles its end
            block("night", MONDAY, at(23), at(24)), // wholly outside
        )
        assertEquals(listOf(DaySpan(at(8, 30), at(21))), free(blocks, waking))
    }

    @Test
    fun gapsShorterThanFifteenMinutesAreNotOffered() {
        val blocks = listOf(block("a", MONDAY, at(8), at(12)), block("b", MONDAY, at(12, 10), at(22)))
        assertTrue(free(blocks, waking).isEmpty())
    }

    @Test
    fun aFullyPlannedWindowHasNoGaps() {
        assertTrue(free(listOf(block("all", MONDAY, at(7), at(23))), waking).isEmpty())
    }

    @Test
    fun aWindowRunningPastMidnightStopsGapsAtTheSleepBlock() {
        val lateWindow = DaySpan(at(8), at(26)) // timeline to 02:00
        val blocks = listOf(block("evening", MONDAY, at(18), at(22)), block("sleep", MONDAY, at(24), at(32)))
        assertEquals(
            listOf(DaySpan(at(8), at(18)), DaySpan(at(22), at(24))),
            free(blocks, lateWindow),
        )
    }
}

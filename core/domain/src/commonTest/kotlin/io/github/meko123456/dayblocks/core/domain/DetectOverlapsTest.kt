package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.usecase.DetectOverlaps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

class DetectOverlapsTest {

    private val detect = DetectOverlaps()
    private val day = listOf(
        block("work", MONDAY, at(9), at(12)),
        block("rest", MONDAY, at(12), at(13)),
        block("read", MONDAY, at(13), at(15)),
    )

    @Test
    fun aCandidateInsideABlockClashesWithIt() {
        assertEquals(listOf("work"), detect(MONDAY, DaySpan(at(10), at(11)), day).map { it.id.value })
    }

    @Test
    fun aCandidateSpanningSeveralBlocksClashesWithAllOfThemInOrder() {
        val clashes = detect(MONDAY, DaySpan(at(11), at(14)), day)
        assertEquals(listOf("work", "rest", "read"), clashes.map { it.id.value })
    }

    @Test
    fun backToBackIsNotAClash() {
        assertTrue(detect(MONDAY, DaySpan(at(15), at(16)), day).isEmpty())
        assertTrue(detect(MONDAY, DaySpan(at(8), at(9)), day).isEmpty())
    }

    @Test
    fun theBlockBeingEditedDoesNotClashWithItself() {
        val moved = DaySpan(at(9, 30), at(11, 30))
        assertTrue(detect(MONDAY, moved, day, editing = BlockId("work")).isEmpty())
    }

    @Test
    fun mondaysSleepClashesWithATuesdayBlockAtHalfPastMidnight() {
        // Different plans, same wall-clock half hour. Checked from Tuesday's editor.
        val mondaySleep = block("sleep", MONDAY, at(24), at(32), title = "Sleep")
        val clashes = detect(TUESDAY, DaySpan(at(0, 30), at(1)), listOf(mondaySleep))
        assertEquals(listOf("sleep"), clashes.map { it.id.value })
    }

    @Test
    fun aLateMondayBlockClashesWithAnEarlyTuesdayOne() {
        // The other direction: checked from Monday's editor, a 23:00–01:00 candidate.
        val tuesdayEarly = block("early", TUESDAY, at(0, 30), at(1, 30))
        val clashes = detect(MONDAY, DaySpan(at(23), at(25)), listOf(tuesdayEarly))
        assertEquals(listOf("early"), clashes.map { it.id.value })
    }

    @Test
    fun blocksTwoDaysApartNeverClash() {
        val wednesday = block("wed", LocalDate(2026, 9, 23), at(0), at(1))
        assertTrue(detect(MONDAY, DaySpan(at(23), at(24)), listOf(wednesday)).isEmpty())
    }

    @Test
    fun pairsReportsEachClashOnce() {
        val blocks = day + block("call", MONDAY, at(11), at(12, 30))
        val pairs = detect.pairs(blocks).map { (a, b) -> a.id.value to b.id.value }
        assertEquals(listOf("work" to "call", "call" to "rest"), pairs)
    }

    @Test
    fun pairsOfACleanDayIsEmpty() {
        assertTrue(detect.pairs(day).isEmpty())
        assertTrue(detect.pairs(emptyList()).isEmpty())
    }

    @Test
    fun identicalSpansClash() {
        val twice = listOf(block("a", MONDAY, at(9), at(10)), block("b", MONDAY, at(9), at(10)))
        assertEquals(1, detect.pairs(twice).size)
    }
}

package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.usecase.CopyDay
import io.github.meko123456.dayblocks.core.domain.usecase.SaveDayAsTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplateUseCasesTest {

    private val day = listOf(
        block("sleep", MONDAY, at(24), at(32), Category.Sleep, "Sleep"),
        block("work", MONDAY, at(9), at(12), Category.Work, "Work"),
    )

    @Test
    fun savingADayKeepsItsShapeInStartOrderAndDropsDatesAndIds() {
        val template = SaveDayAsTemplate(CountingIds())("  Weekday ", day)
        assertEquals("Weekday", template.name)
        assertEquals(listOf("Work", "Sleep"), template.blocks.map { it.title })
        assertEquals(DaySpan(at(24), at(32)), template.blocks.last().span) // past midnight, kept
    }

    @Test
    fun copyingADayGivesEveryBlockAFreshIdAndTheNewDate() {
        val copied = CopyDay(CountingIds())(day, TUESDAY)
        assertEquals(listOf("id-1", "id-2"), copied.map { it.id.value })
        assertTrue(copied.all { it.date == TUESDAY })
        assertEquals(listOf(DaySpan(at(9), at(12)), DaySpan(at(24), at(32))), copied.map { it.span })
        assertTrue(copied.none { c -> day.any { it.id == c.id } }, "a copy must never reuse a source id")
    }
}

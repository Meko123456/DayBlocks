package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateDayFromTemplateTest {

    /** The example day from the brief, deliberately listed out of order. */
    private val weekday = Template(
        id = TemplateId("weekday"),
        name = "Weekday",
        blocks = listOf(
            TemplateBlock("Gym", Category.Exercise, DaySpan(at(18), at(20))),
            TemplateBlock("Work", Category.Work, DaySpan(at(9), at(12))),
            TemplateBlock("Sleep", Category.Sleep, DaySpan(at(24), at(32))),
            TemplateBlock("Rest", Category.Rest, DaySpan(at(12), at(13))),
            TemplateBlock("Reading", Category.Reading, DaySpan(at(13), at(15)), note = "Chapter 4"),
            TemplateBlock("Meal prep for tomorrow", Category.Cooking, DaySpan(at(20), at(22))),
        ),
    )

    @Test
    fun everyTemplateBlockBecomesABlockOnTheDateInStartOrder() {
        val day = GenerateDayFromTemplate(CountingIds())(weekday, MONDAY)
        assertEquals(
            listOf("Work", "Rest", "Reading", "Gym", "Meal prep for tomorrow", "Sleep"),
            day.map { it.title },
        )
        assertTrue(day.all { it.date == MONDAY })
    }

    @Test
    fun theSleepBlockStaysAtTheEndOfItsOwnPlan() {
        // Past 24:00 is kept as it is, not wrapped onto the morning of the same date.
        val sleep = GenerateDayFromTemplate(CountingIds())(weekday, MONDAY).last()
        assertEquals(DaySpan(at(24), at(32)), sleep.span)
        assertEquals(MONDAY, sleep.date)
    }

    @Test
    fun eachBlockGetsAFreshId() {
        val day = GenerateDayFromTemplate(CountingIds())(weekday, MONDAY)
        assertEquals((1..6).map { "id-$it" }, day.map { it.id.value })
    }

    @Test
    fun applyingTheSameTemplateTwiceNeverReusesAnId() {
        val ids = CountingIds()
        val generate = GenerateDayFromTemplate(ids)
        val monday = generate(weekday, MONDAY).map { it.id }
        val tuesday = generate(weekday, TUESDAY).map { it.id }
        assertTrue(monday.intersect(tuesday.toSet()).isEmpty())
    }

    @Test
    fun categoriesAndNotesCarryOver() {
        val reading = GenerateDayFromTemplate(CountingIds())(weekday, MONDAY).single { it.title == "Reading" }
        assertEquals(Category.Reading, reading.category)
        assertEquals("Chapter 4", reading.note)
    }

    @Test
    fun anEmptyTemplateMakesAnEmptyDay() {
        val empty = Template(TemplateId("empty"), "Empty", emptyList())
        assertTrue(GenerateDayFromTemplate(CountingIds())(empty, MONDAY).isEmpty())
    }
}

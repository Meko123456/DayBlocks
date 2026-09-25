package io.github.meko123456.dayblocks.core.data.repository

import io.github.meko123456.dayblocks.core.common.FixedTimeProvider
import io.github.meko123456.dayblocks.core.data.block
import io.github.meko123456.dayblocks.core.data.createTestDriver
import io.github.meko123456.dayblocks.core.data.monday
import io.github.meko123456.dayblocks.core.data.testDispatchers
import io.github.meko123456.dayblocks.core.data.tuesday
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.usecase.AutoFillDay
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek

/** Weekday auto-fill against the real repositories: the once-only claim is the whole contract. */
class AutoFillTest {
    private val driver = createTestDriver()
    private val database = DayBlocksDatabase(driver)
    private val clock = FixedTimeProvider(Instant.parse("2026-09-21T06:00:00Z"))
    private val weekday = Template(
        TemplateId("weekday"),
        "Weekday",
        listOf(
            TemplateBlock("Work", Category.Work, DaySpan.of(9, 0, 12, 0)),
            TemplateBlock("Sleep", Category.Sleep, DaySpan(1440, 1920)),
        ),
    )

    @AfterTest
    fun closeDriver() = driver.close()

    private fun TestScope.setup(): Triple<SqlBlockRepository, SqlTemplateRepository, AutoFillDay> {
        val blocks = SqlBlockRepository(database, testDispatchers(), clock)
        val templates = SqlTemplateRepository(database, testDispatchers())
        var n = 0
        val fill = AutoFillDay(blocks, templates, GenerateDayFromTemplate { "fill-${++n}" }, clock)
        return Triple(blocks, templates, fill)
    }

    @Test
    fun aDayIsClaimedOnlyOnce() = runTest {
        val (_, templates, _) = setup()
        assertTrue(templates.claimAutoFill(monday, clock.now()))
        assertFalse(templates.claimAutoFill(monday, clock.now()))
        assertTrue(templates.claimAutoFill(tuesday, clock.now()))
    }

    @Test
    fun anEmptyMondayIsFilledFromTheMondayTemplate() = runTest {
        val (blocks, templates, fill) = setup()
        templates.upsert(weekday)
        templates.assign(DayOfWeek.MONDAY, weekday.id)
        assertTrue(fill(monday))
        assertEquals(listOf("Work", "Sleep"), blocks.observeDay(monday).first().map { it.title })
    }

    @Test
    fun withoutAnAssignmentNothingIsFilledOrClaimed() = runTest {
        val (blocks, templates, fill) = setup()
        templates.upsert(weekday)
        assertFalse(fill(monday))
        assertTrue(blocks.observeDay(monday).first().isEmpty())
        // Not claimed: assigning a template later the same day still fills it.
        templates.assign(DayOfWeek.MONDAY, weekday.id)
        assertTrue(fill(monday))
    }

    @Test
    fun clearingAFilledDayIsNeverUndone() = runTest {
        val (blocks, templates, fill) = setup()
        templates.upsert(weekday)
        templates.assign(DayOfWeek.MONDAY, weekday.id)
        fill(monday)
        blocks.replaceDay(monday, emptyList()) // the user clears the day on purpose
        assertFalse(fill(monday))
        assertTrue(blocks.observeDay(monday).first().isEmpty(), "the template came back after the day was cleared")
    }

    @Test
    fun aDayPlannedByHandIsLeftAloneAndNeverFilledLater() = runTest {
        val (blocks, templates, fill) = setup()
        templates.upsert(weekday)
        templates.assign(DayOfWeek.MONDAY, weekday.id)
        val gym = block("gym", "Gym", DaySpan.of(18, 0, 19, 0), category = Category.Exercise)
        blocks.upsert(gym)
        assertFalse(fill(monday))
        blocks.delete(gym.id) // emptied later — still not the template's to fill
        assertFalse(fill(monday))
        assertTrue(blocks.observeDay(monday).first().isEmpty())
    }
}

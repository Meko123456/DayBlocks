package io.github.meko123456.dayblocks.core.data.repository

import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.data.count
import io.github.meko123456.dayblocks.core.data.createTestDriver
import io.github.meko123456.dayblocks.core.data.testDispatchers
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek

class TemplateRepositoryTest {
    private val driver = createTestDriver()
    private val database = DayBlocksDatabase(driver)

    @AfterTest
    fun closeDriver() = driver.close()

    private fun TestScope.repository() = SqlTemplateRepository(database, testDispatchers())

    private val weekday = Template(
        id = TemplateId("weekday"),
        name = "Weekday",
        blocks = listOf(
            TemplateBlock("Deep work", Category.Work, DaySpan.of(9, 0, 12, 0)),
            // Listed after a later block on purpose: a template keeps the order it was saved in.
            TemplateBlock("Run", Category.Exercise, DaySpan.of(7, 0, 7, 45), note = "easy pace"),
            TemplateBlock("Sleep", Category.Sleep, DaySpan(1440, 1920)),
        ),
    )
    private val weekend = Template(
        id = TemplateId("weekend"),
        name = "weekend",
        blocks = listOf(TemplateBlock("Long run", Category.Exercise, DaySpan.of(8, 0, 10, 0))),
    )

    @Test
    fun aTemplateReadsBackExactlyAsSaved() = runTest {
        val repository = repository()
        val blank = Template(TemplateId("blank"), "blank", emptyList())
        listOf(weekend, blank, weekday).forEach { repository.upsert(it) }

        assertEquals(weekday, repository.template(weekday.id))
        assertEquals(blank, repository.template(blank.id))
        assertNull(repository.template(TemplateId("never-saved")))
        // Listed by name ignoring case, whatever order they were saved in: a byte-order sort would
        // put "Weekday" before "blank".
        assertEquals(listOf(blank, weekday, weekend), repository.observeTemplates().first())
    }

    @Test
    fun upsertReplacesTheBlocksRatherThanMergingThem() = runTest {
        val repository = repository()
        repository.upsert(weekday)
        val trimmed = weekday.copy(
            name = "Workday",
            blocks = listOf(TemplateBlock("Meetings", Category.Work, DaySpan.of(10, 0, 11, 0))),
        )

        repository.observeTemplates().test {
            assertEquals(listOf(weekday), awaitItem())
            repository.upsert(trimmed)
            assertEquals(listOf(trimmed), awaitItem())
        }
        assertEquals(1, driver.count("SELECT count(*) FROM template_block"))
    }

    @Test
    fun savingATemplateAgainKeepsItsWeekdays() = runTest {
        val repository = repository()
        repository.upsert(weekday)
        repository.assign(DayOfWeek.MONDAY, weekday.id)

        repository.upsert(weekday.copy(name = "Workday"))

        assertEquals(mapOf(DayOfWeek.MONDAY to weekday.id), repository.observeWeekdayAssignments().first())
    }

    @Test
    fun deletingATemplateCascadesToItsBlocksAndClearsItsWeekdays() = runTest {
        val repository = repository()
        repository.upsert(weekday)
        repository.upsert(weekend)
        repository.assign(DayOfWeek.MONDAY, weekday.id)
        repository.assign(DayOfWeek.TUESDAY, weekday.id)
        repository.assign(DayOfWeek.SATURDAY, weekend.id)

        repository.observeWeekdayAssignments().test {
            assertEquals(
                mapOf(DayOfWeek.MONDAY to weekday.id, DayOfWeek.TUESDAY to weekday.id, DayOfWeek.SATURDAY to weekend.id),
                awaitItem(),
            )
            repository.delete(weekday.id)
            assertEquals(mapOf(DayOfWeek.SATURDAY to weekend.id), awaitItem())
        }
        assertNull(repository.template(weekday.id))
        // Gone from the table, not merely unreachable through the join.
        assertEquals(0, driver.count("SELECT count(*) FROM template_block WHERE template_id = ?", weekday.id.value))
        assertEquals(weekend, repository.template(weekend.id))
    }

    @Test
    fun assignSetsReplacesAndClearsADay() = runTest {
        val repository = repository()
        repository.upsert(weekday)
        repository.upsert(weekend)

        repository.observeWeekdayAssignments().test {
            assertEquals(emptyMap(), awaitItem())
            repository.assign(DayOfWeek.FRIDAY, weekday.id)
            assertEquals(mapOf(DayOfWeek.FRIDAY to weekday.id), awaitItem())
            repository.assign(DayOfWeek.FRIDAY, weekend.id)
            assertEquals(mapOf(DayOfWeek.FRIDAY to weekend.id), awaitItem())
            repository.assign(DayOfWeek.FRIDAY, null)
            assertEquals(emptyMap(), awaitItem())
        }
    }

    @Test
    fun assigningATemplateThatDoesNotExistIsRefused() = runTest {
        val repository = repository()

        assertFails { repository.assign(DayOfWeek.MONDAY, TemplateId("never-saved")) }
        assertEquals(emptyMap(), repository.observeWeekdayAssignments().first())
    }
}

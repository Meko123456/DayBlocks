package io.github.meko123456.dayblocks.core.data.repository

import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.FixedTimeProvider
import io.github.meko123456.dayblocks.core.data.block
import io.github.meko123456.dayblocks.core.data.count
import io.github.meko123456.dayblocks.core.data.createTestDriver
import io.github.meko123456.dayblocks.core.data.everyNotifiedState
import io.github.meko123456.dayblocks.core.data.mapper.toColumn
import io.github.meko123456.dayblocks.core.data.monday
import io.github.meko123456.dayblocks.core.data.testDispatchers
import io.github.meko123456.dayblocks.core.data.tuesday
import io.github.meko123456.dayblocks.core.data.wednesday
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class BlockRepositoryTest {
    private val driver = createTestDriver()
    private val database = DayBlocksDatabase(driver)
    private val clock = FixedTimeProvider(Instant.parse("2026-09-21T06:00:00Z"))

    @AfterTest
    fun closeDriver() = driver.close()

    private fun TestScope.repository() = SqlBlockRepository(database, testDispatchers(), clock)

    private val planBefore = listOf(
        block("old-work", "Deep work", DaySpan.of(9, 0, 12, 0)),
        block("old-gym", "Gym", DaySpan.of(18, 0, 19, 0), category = Category.Exercise),
    )
    private val planAfter = listOf(
        block("new-run", "Run", DaySpan.of(7, 0, 7, 45), category = Category.Exercise),
        block("new-work", "Deep work", DaySpan.of(9, 0, 13, 0)),
        block("new-read", "Reading", DaySpan.of(21, 0, 22, 0), category = Category.Reading),
        block("new-sleep", "Sleep", DaySpan(1440, 1920), category = Category.Sleep),
    )

    @Test
    fun replaceDayWithNothingClearsTheDayAndOnlyThatDay() = runTest {
        // Clearing a day, or applying an empty template, is replaceDay with an empty list — and
        // that becomes SQL "IN ()" in two statements. SQLite accepts an empty list only as an
        // extension to standard SQL, so this proves the driver and SQLDelight's expansion of an
        // empty collection agree, on both platforms, before step 5 depends on it.
        val repository = repository()
        repository.replaceDay(monday, planBefore)
        val tuesdayWork = block("tuesday-work", "Deep work", DaySpan.of(9, 0, 12, 0), date = tuesday)
        repository.upsert(tuesdayWork)

        repository.replaceDay(monday, emptyList())

        assertEquals(emptyList(), repository.observeDay(monday).first())
        assertEquals(listOf(tuesdayWork), repository.observeDay(tuesday).first())
    }

    @Test
    fun observeDayListsOnlyThatDatesBlocksInStartOrder() = runTest {
        val repository = repository()
        val run = block("run", "Run", DaySpan.of(7, 0, 7, 45), category = Category.Exercise)
        val work = block("work", "Deep work", DaySpan.of(9, 0, 12, 0))
        val lunch = block("lunch", "Lunch", DaySpan.of(12, 0, 13, 0), category = Category.Cooking)
        val tuesdayWork = block("tuesday-work", "Deep work", DaySpan.of(9, 0, 12, 0), date = tuesday)
        // Written out of start order, and interleaved with another day's block.
        listOf(lunch, tuesdayWork, run, work).forEach { repository.upsert(it) }

        assertEquals(listOf(run, work, lunch), repository.observeDay(monday).first())
        assertEquals(listOf(tuesdayWork), repository.observeDay(tuesday).first())
        assertEquals(emptyList(), repository.observeDay(wednesday).first())
    }

    @Test
    fun aBlockPastMidnightKeepsItsSpanAndItsPlanDay() = runTest {
        val repository = repository()
        val film = block("film", "Film", DaySpan.of(23, 0, 25, 0), category = Category.Rest)
        val sleep = block("sleep", "Sleep", DaySpan(1440, 1920), category = Category.Sleep)
        repository.upsert(sleep)
        repository.upsert(film)

        val day = repository.observeDay(monday).first()
        assertEquals(listOf(film, sleep), day)
        assertEquals(listOf(DaySpan(1380, 1500), DaySpan(1440, 1920)), day.map { it.span })
        // Both run into Tuesday's date, and neither is Tuesday's: they belong to Monday's plan.
        assertEquals(emptyList(), repository.observeDay(tuesday).first())
    }

    @Test
    fun everyCategoryAndNoteSurvivesStorage() = runTest {
        val repository = repository()
        val blocks = Category.entries.mapIndexed { i, category ->
            block(
                id = "block-$i",
                title = "Block $i",
                span = DaySpan(i * 60, i * 60 + 30),
                category = category,
                note = if (i % 2 == 0) "note $i" else null,
            )
        }
        blocks.forEach { repository.upsert(it) }

        assertEquals(blocks, repository.observeDay(monday).first())
    }

    @Test
    fun upsertRewritesABlockInPlaceAndCanMoveItToAnotherDay() = runTest {
        val repository = repository()
        val work = block("work", "Deep work", DaySpan.of(9, 0, 12, 0))
        repository.upsert(work)

        val edited = work.copy(title = "Writing", category = Category.Personal, span = DaySpan.of(10, 0, 12, 30), note = "chapter 3")
        repository.upsert(edited)
        assertEquals(listOf(edited), repository.observeDay(monday).first())
        assertEquals(1, driver.count("SELECT count(*) FROM block"))

        val moved = edited.copy(date = tuesday)
        repository.upsert(moved)
        assertEquals(emptyList(), repository.observeDay(monday).first())
        assertEquals(listOf(moved), repository.observeDay(tuesday).first())
    }

    @Test
    fun deleteRemovesOnlyThatBlock() = runTest {
        val repository = repository()
        repository.replaceDay(monday, planBefore)

        repository.delete(planBefore.first().id)
        assertEquals(planBefore.drop(1), repository.observeDay(monday).first())

        repository.delete(BlockId("never-existed"))
        assertEquals(planBefore.drop(1), repository.observeDay(monday).first())
    }

    @Test
    fun observeRangeOrdersByDateThenStart() = runTest {
        val repository = repository()
        val mondayFilm = block("monday-film", "Film", DaySpan.of(23, 0, 25, 0), category = Category.Rest)
        val mondayRun = block("monday-run", "Run", DaySpan.of(7, 0, 8, 0), category = Category.Exercise)
        val tuesdayRun = block("tuesday-run", "Run", DaySpan.of(6, 0, 7, 0), date = tuesday, category = Category.Exercise)
        val wednesdayWork = block("wednesday-work", "Deep work", DaySpan.of(9, 0, 12, 0), date = wednesday)
        listOf(wednesdayWork, tuesdayRun, mondayFilm, mondayRun).forEach { repository.upsert(it) }

        // Monday's film ends at Tuesday 01:00 and still sorts with Monday, ahead of Tuesday's 06:00.
        assertEquals(listOf(mondayRun, mondayFilm, tuesdayRun), repository.observeRange(monday, tuesday).first())
    }

    @Test
    fun replaceDayReachesAnObserverAsOneChange() = runTest {
        val repository = repository()
        repository.replaceDay(monday, planBefore)

        repository.observeDay(monday).test {
            assertEquals(planBefore, awaitItem())
            repository.replaceDay(monday, planAfter)
            // Straight from the old day to the whole new one: never empty, never a prefix of it.
            assertEquals(planAfter, awaitItem())
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun replaceDayNotifiesOnceWithTheWholeNewDay() = runTest {
        val repository = repository()
        repository.replaceDay(monday, planBefore)

        // Reads the day inside each notification, so an intermediate state cannot be conflated away
        // the way it can between asFlow's re-reads: a delete-then-insert outside one transaction
        // would show up here as an empty day, then a growing one.
        database.blockQueries.selectByDate(monday.toColumn()).everyNotifiedState().test {
            repository.replaceDay(monday, planAfter)
            assertEquals(planAfter.map { it.id.value }, awaitItem().map { it.id })
            expectNoEvents()
        }
    }

    @Test
    fun replaceDayRefusesABlockDatedForAnotherDay() = runTest {
        val repository = repository()
        repository.replaceDay(monday, planBefore)
        val stray = block("stray", "Run", DaySpan.of(7, 0, 8, 0), date = tuesday)

        assertFailsWith<IllegalArgumentException> { repository.replaceDay(monday, planAfter + stray) }
        assertEquals(planBefore, repository.observeDay(monday).first())
        assertEquals(emptyList(), repository.observeDay(tuesday).first())
    }

    @Test
    fun replaceDayRefusesToMoveABlockFromAnotherDay() = runTest {
        val repository = repository()
        val tuesdayRun = block("tuesday-run", "Run", DaySpan.of(7, 0, 8, 0), date = tuesday)
        repository.upsert(tuesdayRun)
        repository.replaceDay(monday, planBefore)

        // A "copy Tuesday" that re-dated the block but kept its id would move it rather than copy it.
        assertFailsWith<IllegalArgumentException> {
            repository.replaceDay(monday, planBefore + tuesdayRun.copy(date = monday))
        }
        assertEquals(planBefore, repository.observeDay(monday).first())
        assertEquals(listOf(tuesdayRun), repository.observeDay(tuesday).first())
    }

    @Test
    fun recentTitlesAreDistinctMostRecentlyPlannedFirstAndLimited() = runTest {
        val repository = repository()
        suspend fun plan(id: String, title: String, date: LocalDate) {
            repository.upsert(block(id, title, DaySpan.of(9, 0, 10, 0), date = date))
            clock.instant += 1.minutes
        }
        plan("1", "Gym", monday)
        plan("2", "Reading", wednesday) // for a later day, but planned earlier: planning time decides
        plan("3", "Deep work", monday)
        plan("4", "Gym", tuesday) // planned again, so it moves to the front, once

        assertEquals(listOf("Gym", "Deep work", "Reading"), repository.recentTitles(limit = 10))
        assertEquals(listOf("Gym", "Deep work"), repository.recentTitles(limit = 2))
        assertEquals(emptyList(), repository.recentTitles(limit = 0))
        assertFailsWith<IllegalArgumentException> { repository.recentTitles(limit = -1) }
    }
}

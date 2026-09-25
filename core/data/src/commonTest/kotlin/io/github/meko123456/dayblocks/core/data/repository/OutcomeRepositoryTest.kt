package io.github.meko123456.dayblocks.core.data.repository

import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.FixedTimeProvider
import io.github.meko123456.dayblocks.core.data.block
import io.github.meko123456.dayblocks.core.data.count
import io.github.meko123456.dayblocks.core.data.createTestDriver
import io.github.meko123456.dayblocks.core.data.monday
import io.github.meko123456.dayblocks.core.data.testDispatchers
import io.github.meko123456.dayblocks.core.data.tuesday
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class OutcomeRepositoryTest {
    private val driver = createTestDriver()
    private val database = DayBlocksDatabase(driver)
    private val clock = FixedTimeProvider(Instant.parse("2026-09-21T06:00:00Z"))

    @AfterTest
    fun closeDriver() = driver.close()

    private fun TestScope.repositories(): Pair<BlockRepository, OutcomeRepository> {
        val dispatchers = testDispatchers()
        return SqlBlockRepository(database, dispatchers, clock) to SqlOutcomeRepository(database, dispatchers)
    }

    private val work = block("work", "Deep work", DaySpan.of(9, 0, 12, 0))
    private val gym = block("gym", "Gym", DaySpan.of(18, 0, 19, 0), category = Category.Exercise)
    private val morning = Instant.parse("2026-09-21T07:30:00Z")

    @Test
    fun aLaterAnswerReplacesAnEarlierOne() = runTest {
        val (blocks, outcomes) = repositories()
        blocks.upsert(work)

        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, morning)
        outcomes.recordAnswer(work.id, CheckInAnswer.GotDistracted, morning + 30.minutes)

        assertEquals(
            mapOf(work.id to BlockRecord(work.id, answer = CheckInAnswer.GotDistracted)),
            outcomes.observeDay(monday).first(),
        )
    }

    @Test
    fun recordingTheOutcomeKeepsTheAnswer() = runTest {
        val (blocks, outcomes) = repositories()
        blocks.upsert(work)
        blocks.upsert(gym)

        outcomes.recordAnswer(work.id, CheckInAnswer.GotDistracted, morning)
        outcomes.recordOutcome(work.id, BlockOutcome.Done, morning + 12.hours)
        // The other way round too: an answer arriving after the check-in leaves the outcome alone.
        outcomes.recordOutcome(gym.id, BlockOutcome.Skipped, morning + 12.hours)
        outcomes.recordAnswer(gym.id, CheckInAnswer.OnIt, morning + 13.hours)

        assertEquals(
            mapOf(
                work.id to BlockRecord(work.id, CheckInAnswer.GotDistracted, BlockOutcome.Done),
                gym.id to BlockRecord(gym.id, CheckInAnswer.OnIt, BlockOutcome.Skipped),
            ),
            outcomes.observeDay(monday).first(),
        )
    }

    @Test
    fun effectiveOutcomeFollowsTheAnswerUntilTheCheckInDecides() = runTest {
        val (blocks, outcomes) = repositories()
        blocks.upsert(work)

        outcomes.observeDay(monday).test {
            assertEquals(emptyMap(), awaitItem())

            outcomes.recordAnswer(work.id, CheckInAnswer.GotDistracted, morning)
            assertEquals(BlockOutcome.Partly, awaitItem().getValue(work.id).effectiveOutcome)

            outcomes.recordOutcome(work.id, BlockOutcome.Done, morning + 12.hours)
            assertEquals(BlockOutcome.Done, awaitItem().getValue(work.id).effectiveOutcome)

            outcomes.recordAnswer(work.id, CheckInAnswer.SkipBlock, morning + 13.hours)
            val record = awaitItem().getValue(work.id)
            assertEquals(CheckInAnswer.SkipBlock, record.answer)
            assertEquals(BlockOutcome.Done, record.effectiveOutcome)
        }
    }

    @Test
    fun deletingABlockCascadesToItsRecord() = runTest {
        val (blocks, outcomes) = repositories()
        blocks.upsert(work)
        blocks.upsert(gym)
        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, morning)
        outcomes.recordAnswer(gym.id, CheckInAnswer.OnIt, morning)

        outcomes.observeDay(monday).test {
            assertEquals(setOf(work.id, gym.id), awaitItem().keys)
            blocks.delete(work.id)
            assertEquals(setOf(gym.id), awaitItem().keys)
        }
        // Gone from the table, not merely hidden by the join to block.
        assertEquals(0, driver.count("SELECT count(*) FROM block_record WHERE block_id = ?", work.id.value))
        // So a block that comes back under the same id starts with nothing recorded.
        blocks.upsert(work)
        assertEquals(setOf(gym.id), outcomes.observeDay(monday).first().keys)
    }

    @Test
    fun replaceDayKeepsTheRecordsOfTheBlocksItKeeps() = runTest {
        val (blocks, outcomes) = repositories()
        blocks.replaceDay(monday, listOf(work, gym))
        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, morning)
        outcomes.recordAnswer(gym.id, CheckInAnswer.SkipBlock, morning)

        val longerWork = work.copy(span = DaySpan.of(9, 0, 13, 0))
        val reading = block("reading", "Reading", DaySpan.of(21, 0, 22, 0), category = Category.Reading)
        blocks.replaceDay(monday, listOf(longerWork, reading))

        assertEquals(
            mapOf(work.id to BlockRecord(work.id, answer = CheckInAnswer.OnIt)),
            outcomes.observeDay(monday).first(),
        )
        assertEquals(1, driver.count("SELECT count(*) FROM block_record"))
    }

    @Test
    fun observeDayOnlyReturnsRecordsForThatDatesBlocks() = runTest {
        val (blocks, outcomes) = repositories()
        val tuesdayWork = block("tuesday-work", "Deep work", DaySpan.of(9, 0, 12, 0), date = tuesday)
        listOf(work, gym, tuesdayWork).forEach { blocks.upsert(it) }

        outcomes.recordAnswer(work.id, CheckInAnswer.OnIt, morning)
        outcomes.recordOutcome(tuesdayWork.id, BlockOutcome.Partly, morning + 1.days)

        // Gym has nothing recorded, so it is absent rather than present and empty.
        assertEquals(
            mapOf(work.id to BlockRecord(work.id, answer = CheckInAnswer.OnIt)),
            outcomes.observeDay(monday).first(),
        )
        assertEquals(
            mapOf(tuesdayWork.id to BlockRecord(tuesdayWork.id, outcome = BlockOutcome.Partly)),
            outcomes.observeDay(tuesday).first(),
        )
    }

    @Test
    fun anAnswerForABlockThatNoLongerExistsIsDropped() = runTest {
        val (_, outcomes) = repositories()

        outcomes.recordAnswer(BlockId("deleted-since"), CheckInAnswer.OnIt, morning)
        outcomes.recordOutcome(BlockId("deleted-since"), BlockOutcome.Done, morning)

        assertEquals(0, driver.count("SELECT count(*) FROM block_record"))
    }
}

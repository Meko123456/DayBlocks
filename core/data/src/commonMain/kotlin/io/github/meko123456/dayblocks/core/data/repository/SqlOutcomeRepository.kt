package io.github.meko123456.dayblocks.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.meko123456.dayblocks.core.common.AppDispatchers
import io.github.meko123456.dayblocks.core.data.mapper.toBlockRecord
import io.github.meko123456.dayblocks.core.data.mapper.toColumn
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * [OutcomeRepository] on SQLDelight. The answer and the outcome live in separate columns of one
 * row per block, each with its own timestamp, so recording one never touches the other.
 *
 * The latest call wins, whatever its [Instant]: the time is stored for the record rather than used
 * to order writes. A record is only ever written for a block that exists — an answer for a block
 * deleted since its notification was scheduled is dropped rather than thrown from a receiver.
 */
internal class SqlOutcomeRepository(
    private val database: DayBlocksDatabase,
    private val dispatchers: AppDispatchers,
) : OutcomeRepository {

    private val queries = database.blockRecordQueries

    override fun observeDay(date: LocalDate): Flow<Map<BlockId, BlockRecord>> =
        queries.selectByDate(date.toColumn())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toBlockRecord() }.associateBy { it.blockId } }

    override suspend fun recordAnswer(block: BlockId, answer: CheckInAnswer, at: Instant) {
        withContext(dispatchers.io) {
            database.transaction {
                queries.insertIfMissing(block.value)
                queries.setAnswer(answer = answer.name, answeredAt = at.toEpochMilliseconds(), blockId = block.value)
            }
        }
    }

    override suspend fun recordOutcome(block: BlockId, outcome: BlockOutcome, at: Instant) {
        withContext(dispatchers.io) {
            database.transaction {
                queries.insertIfMissing(block.value)
                queries.setOutcome(outcome = outcome.name, outcomeAt = at.toEpochMilliseconds(), blockId = block.value)
            }
        }
    }
}

package io.github.meko123456.dayblocks.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.meko123456.dayblocks.core.common.AppDispatchers
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.data.mapper.toColumn
import io.github.meko123456.dayblocks.core.data.mapper.toTimeBlock
import io.github.meko123456.dayblocks.core.data.mapper.upsert
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * [BlockRepository] on SQLDelight. Every write runs in one transaction, which is what makes each
 * write a single change to observers: SQLDelight holds back query notifications until the
 * outermost transaction commits, then re-runs each affected query once.
 */
internal class SqlBlockRepository(
    private val database: DayBlocksDatabase,
    private val dispatchers: AppDispatchers,
    private val time: TimeProvider,
) : BlockRepository {

    private val queries = database.blockQueries

    override fun observeDay(date: LocalDate): Flow<List<TimeBlock>> =
        queries.selectByDate(date.toColumn())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toTimeBlock() } }

    override fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<TimeBlock>> =
        queries.selectInRange(from.toColumn(), toInclusive.toColumn())
            .asFlow()
            .mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toTimeBlock() } }

    override suspend fun upsert(block: TimeBlock) {
        withContext(dispatchers.io) {
            val now = time.now().toEpochMilliseconds()
            database.transaction { queries.upsert(block, now) }
        }
    }

    /**
     * Deletes the date's blocks that are not in [blocks], then upserts [blocks], in one
     * transaction. Blocks that are kept are updated in place rather than deleted and re-inserted,
     * so what was recorded about them survives; the records of blocks that are dropped go with
     * them.
     *
     * Refuses, before writing anything, a block dated for another day, an id given twice, and an
     * id that already belongs to another date. The last is the easy mistake: a "copy yesterday"
     * that re-dated yesterday's blocks without giving them fresh ids would move yesterday's plan
     * here instead of copying it.
     */
    override suspend fun replaceDay(date: LocalDate, blocks: List<TimeBlock>) {
        val misdated = blocks.filter { it.date != date }.map { it.id.value }
        require(misdated.isEmpty()) { "replaceDay($date) was given blocks dated for other days: $misdated" }
        val ids = blocks.map { it.id.value }
        require(ids.size == ids.toSet().size) { "replaceDay($date) was given the same block id more than once" }

        withContext(dispatchers.io) {
            val now = time.now().toEpochMilliseconds()
            database.transaction {
                val elsewhere = queries.idsOnOtherDates(ids, date.toColumn()).executeAsList()
                require(elsewhere.isEmpty()) {
                    "replaceDay($date) was given blocks that belong to other days: $elsewhere; copies need fresh ids"
                }
                queries.deleteFromDateExcept(planDate = date.toColumn(), keep = ids)
                blocks.forEach { queries.upsert(it, now) }
            }
        }
    }

    override suspend fun delete(id: BlockId) {
        // The block's check-in record goes with it: block_record cascades from block.
        withContext(dispatchers.io) { queries.deleteById(id.value) }
    }

    override suspend fun recentTitles(limit: Int): List<String> {
        // SQLite reads a negative LIMIT as "no limit", which is never what a caller meant.
        require(limit >= 0) { "limit must not be negative, was $limit" }
        return withContext(dispatchers.io) { queries.recentTitles(limit.toLong()).executeAsList() }
    }
}

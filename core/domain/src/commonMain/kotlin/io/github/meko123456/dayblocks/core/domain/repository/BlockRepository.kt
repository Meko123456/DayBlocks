package io.github.meko123456.dayblocks.core.domain.repository

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * The planned blocks. Implemented in :core:data; screens only ever see this interface.
 *
 * Reads are Flows so a screen redraws when the plan changes from anywhere — the editor, a template
 * applied on another screen, an import — without being told to.
 */
interface BlockRepository {
    /** Blocks planned for [date], in start order. */
    fun observeDay(date: LocalDate): Flow<List<TimeBlock>>

    /** Blocks planned from [from] to [toInclusive], in date then start order. */
    fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<TimeBlock>>

    suspend fun upsert(block: TimeBlock)

    /**
     * Replaces the whole of [date]'s plan with [blocks] in one transaction — how a template is
     * applied or yesterday copied. One step rather than delete-then-insert from the caller, so
     * an observer never sees the half-empty day in between.
     */
    suspend fun replaceDay(date: LocalDate, blocks: List<TimeBlock>)

    suspend fun delete(id: BlockId)

    /** Distinct recent titles, most recently planned first — the editor's quick suggestions. */
    suspend fun recentTitles(limit: Int): List<String>
}

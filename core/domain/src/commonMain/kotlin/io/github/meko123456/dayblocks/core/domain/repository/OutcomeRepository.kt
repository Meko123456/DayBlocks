package io.github.meko123456.dayblocks.core.domain.repository

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * How blocks went. Notification answers arrive during the day — often while the app is not
 * running, written by a broadcast receiver or a notification action handler — and the check-in
 * confirms them at the end of it.
 */
interface OutcomeRepository {
    /** Records for the blocks of [date], keyed by block. Blocks with nothing recorded are absent. */
    fun observeDay(date: LocalDate): Flow<Map<BlockId, BlockRecord>>

    /** Records a notification answer. A later answer for the same block replaces an earlier one. */
    suspend fun recordAnswer(block: BlockId, answer: CheckInAnswer, at: Instant)

    /** Records the check-in outcome. Leaves the notification answer in place for the record. */
    suspend fun recordOutcome(block: BlockId, outcome: BlockOutcome, at: Instant)
}

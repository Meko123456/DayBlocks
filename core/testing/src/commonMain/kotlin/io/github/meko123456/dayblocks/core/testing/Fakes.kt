package io.github.meko123456.dayblocks.core.testing

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/** In-memory stand-ins honouring the interfaces' contracts, so the ViewModel is tested alone. */
class FakeBlockRepository(initial: List<TimeBlock> = emptyList()) : BlockRepository {
    val all = MutableStateFlow(initial)

    override fun observeDay(date: LocalDate): Flow<List<TimeBlock>> =
        all.map { blocks -> blocks.filter { it.date == date }.sortedBy { it.span.startMinutes } }

    override fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<TimeBlock>> =
        all.map { blocks -> blocks.filter { it.date in from..toInclusive }.sortedWith(compareBy({ it.date }, { it.span.startMinutes })) }

    override suspend fun upsert(block: TimeBlock) = all.update { blocks -> blocks.filterNot { it.id == block.id } + block }

    override suspend fun replaceDay(date: LocalDate, blocks: List<TimeBlock>) =
        all.update { existing -> existing.filterNot { it.date == date } + blocks }

    override suspend fun delete(id: BlockId) = all.update { blocks -> blocks.filterNot { it.id == id } }

    override suspend fun recentTitles(limit: Int): List<String> = all.value.map { it.title }.distinct().take(limit)
}

class FakeOutcomeRepository(private val blocks: FakeBlockRepository) : OutcomeRepository {
    val records = MutableStateFlow<Map<BlockId, BlockRecord>>(emptyMap())

    override fun observeDay(date: LocalDate): Flow<Map<BlockId, BlockRecord>> = records.map { all ->
        val ids = blocks.all.value.filter { it.date == date }.map { it.id }.toSet()
        all.filterKeys { it in ids }
    }

    override suspend fun recordAnswer(block: BlockId, answer: CheckInAnswer, at: Instant) = records.update {
        it + (block to (it[block] ?: BlockRecord(block)).copy(answer = answer))
    }

    override suspend fun recordOutcome(block: BlockId, outcome: BlockOutcome, at: Instant) = records.update {
        it + (block to (it[block] ?: BlockRecord(block)).copy(outcome = outcome))
    }
}

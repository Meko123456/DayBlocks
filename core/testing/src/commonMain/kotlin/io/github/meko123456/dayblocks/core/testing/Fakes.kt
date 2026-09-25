package io.github.meko123456.dayblocks.core.testing

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * In-memory stand-ins for the domain's repositories, so a ViewModel is tested alone.
 *
 * They must honour the same contract as the SQLDelight implementations — a fake that is more
 * permissive than the real thing lets a screen's tests pass against behaviour the app will never
 * see. Each rule below mirrors one the real repositories enforce and their own tests pin.
 */
class FakeBlockRepository(initial: List<TimeBlock> = emptyList()) : BlockRepository {
    val all = MutableStateFlow(initial)

    /** Titles in write order, newest last — what "most recently planned" means for the real one. */
    private val writes = initial.map { it.title }.toMutableList()

    override fun observeDay(date: LocalDate): Flow<List<TimeBlock>> =
        all.map { blocks -> blocks.filter { it.date == date }.sortedBy { it.span.startMinutes } }

    override fun observeRange(from: LocalDate, toInclusive: LocalDate): Flow<List<TimeBlock>> =
        all.map { blocks ->
            blocks.filter { it.date in from..toInclusive }.sortedWith(compareBy({ it.date }, { it.span.startMinutes }))
        }

    override suspend fun upsert(block: TimeBlock) {
        writes += block.title
        all.update { blocks -> blocks.filterNot { it.id == block.id } + block }
    }

    /** Refuses exactly what the real one refuses, before changing anything. */
    override suspend fun replaceDay(date: LocalDate, blocks: List<TimeBlock>) {
        require(blocks.all { it.date == date }) { "replaceDay($date) was given blocks dated for other days" }
        val ids = blocks.map { it.id }
        require(ids.size == ids.toSet().size) { "replaceDay($date) was given the same block id more than once" }
        val elsewhere = all.value.filter { it.id in ids && it.date != date }
        require(elsewhere.isEmpty()) { "replaceDay($date) was given blocks that belong to other days; copies need fresh ids" }
        writes += blocks.map { it.title }
        all.update { existing -> existing.filterNot { it.date == date } + blocks }
    }

    override suspend fun delete(id: BlockId) = all.update { blocks -> blocks.filterNot { it.id == id } }

    /** Distinct, most recently written first — the real repository's order, not insertion order. */
    override suspend fun recentTitles(limit: Int): List<String> {
        require(limit >= 0) { "limit must not be negative, was $limit" }
        return writes.asReversed().distinct().take(limit)
    }
}

/**
 * Records live only as long as their block, as they do behind the real repository's foreign key:
 * an answer for a block that does not exist is dropped, and deleting a block makes its record
 * disappear from every observer — which is why [observeDay] watches the blocks as well as the
 * records, rather than reading the blocks once.
 */
class FakeOutcomeRepository(private val blocks: FakeBlockRepository) : OutcomeRepository {
    val records = MutableStateFlow<Map<BlockId, BlockRecord>>(emptyMap())

    override fun observeDay(date: LocalDate): Flow<Map<BlockId, BlockRecord>> =
        combine(blocks.all, records) { allBlocks, all ->
            val ids = allBlocks.filter { it.date == date }.map { it.id }.toSet()
            all.filterKeys { it in ids }
        }

    override suspend fun recordAnswer(block: BlockId, answer: CheckInAnswer, at: Instant) {
        if (blocks.all.value.none { it.id == block }) return
        records.update { it + (block to (it[block] ?: BlockRecord(block)).copy(answer = answer)) }
    }

    override suspend fun recordOutcome(block: BlockId, outcome: BlockOutcome, at: Instant) {
        if (blocks.all.value.none { it.id == block }) return
        records.update { it + (block to (it[block] ?: BlockRecord(block)).copy(outcome = outcome)) }
    }
}

/** Templates, weekday assignments and auto-fill claims, with the real repository's rules. */
class FakeTemplateRepository(initial: List<Template> = emptyList()) : TemplateRepository {
    val templates = MutableStateFlow(initial)
    val assignments = MutableStateFlow<Map<DayOfWeek, TemplateId>>(emptyMap())
    val claimed = mutableSetOf<LocalDate>()

    override fun observeTemplates(): Flow<List<Template>> = templates.map { all -> all.sortedBy { it.name.lowercase() } }
    override suspend fun template(id: TemplateId): Template? = templates.value.firstOrNull { it.id == id }
    override suspend fun upsert(template: Template) = templates.update { all -> all.filterNot { it.id == template.id } + template }

    /** Deleting clears the template's weekdays, as the real foreign key does. */
    override suspend fun delete(id: TemplateId) {
        templates.update { all -> all.filterNot { it.id == id } }
        assignments.update { a -> a.filterValues { it != id } }
    }

    override fun observeWeekdayAssignments(): Flow<Map<DayOfWeek, TemplateId>> = assignments

    override suspend fun assign(day: DayOfWeek, template: TemplateId?) {
        require(template == null || templates.value.any { it.id == template }) { "no such template: $template" }
        assignments.update { if (template == null) it - day else it + (day to template) }
    }

    override suspend fun claimAutoFill(date: LocalDate, at: Instant): Boolean = claimed.add(date)
}

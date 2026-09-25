package io.github.meko123456.dayblocks.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import io.github.meko123456.dayblocks.core.common.AppDispatchers
import io.github.meko123456.dayblocks.core.data.mapper.insertBlockAt
import io.github.meko123456.dayblocks.core.data.mapper.toAssignments
import io.github.meko123456.dayblocks.core.data.mapper.toTemplates
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import io.github.meko123456.dayblocks.core.data.mapper.toColumn
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek

/**
 * [TemplateRepository] on SQLDelight. Templates are listed by name, case-insensitively, and each
 * template's blocks keep the order they were saved in.
 */
internal class SqlTemplateRepository(
    private val database: DayBlocksDatabase,
    private val dispatchers: AppDispatchers,
) : TemplateRepository {

    private val queries = database.templateQueries

    override fun observeTemplates(): Flow<List<Template>> =
        queries.selectAllWithBlocks()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.toTemplates() }

    override suspend fun template(id: TemplateId): Template? = withContext(dispatchers.io) {
        queries.selectWithBlocks(id.value).executeAsList().toTemplates().singleOrNull()
    }

    override suspend fun upsert(template: Template) {
        withContext(dispatchers.io) {
            database.transaction {
                // Update-then-insert keeps the template row, and so its weekday assignments.
                if (queries.rename(name = template.name, id = template.id.value).value == 0L) {
                    queries.insert(id = template.id.value, name = template.name)
                }
                queries.deleteBlocks(template.id.value)
                template.blocks.forEachIndexed { position, block ->
                    queries.insertBlockAt(template.id, position, block)
                }
            }
        }
    }

    override suspend fun delete(id: TemplateId) {
        // template_block and weekday_template both cascade from template: see Template.sq.
        withContext(dispatchers.io) { queries.deleteById(id.value) }
    }

    override fun observeWeekdayAssignments(): Flow<Map<DayOfWeek, TemplateId>> =
        queries.selectWeekdays()
            .asFlow()
            .mapToList(dispatchers.io)
            .map { it.toAssignments() }

    /** Assigning a template that does not exist fails on the foreign key rather than being stored. */
    override suspend fun assign(day: DayOfWeek, template: TemplateId?) {
        withContext(dispatchers.io) {
            if (template == null) {
                queries.clearWeekday(day.name)
            } else {
                queries.assignWeekday(weekday = day.name, templateId = template.value)
            }
        }
    }

    /** Check and insert in one transaction, so two callers can never both claim the same day. */
    override suspend fun claimAutoFill(date: LocalDate, at: Instant): Boolean = withContext(dispatchers.io) {
        database.transactionWithResult {
            val column = date.toColumn()
            if (queries.selectAutoFill(column).executeAsOneOrNull() != null) {
                false
            } else {
                queries.insertAutoFill(planDate = column, claimedAt = at.toEpochMilliseconds())
                true
            }
        }
    }
}

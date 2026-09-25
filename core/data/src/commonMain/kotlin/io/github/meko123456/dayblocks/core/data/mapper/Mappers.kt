package io.github.meko123456.dayblocks.core.data.mapper

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.database.Block
import io.github.meko123456.dayblocks.database.BlockQueries
import io.github.meko123456.dayblocks.database.Block_record
import io.github.meko123456.dayblocks.database.TemplateQueries
import io.github.meko123456.dayblocks.database.Template_with_blocks
import io.github.meko123456.dayblocks.database.Weekday_template
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

// Every conversion between a stored value and a domain type is in this file, so the storage format
// can be read in one place:
//
//  - dates are ISO yyyy-MM-dd text, whose string order is date order;
//  - Category, CheckInAnswer, BlockOutcome and DayOfWeek are stored by constant name;
//  - a span is its two plan-minute integers, copied verbatim, so 1440..1920 stays 1440..1920;
//  - instants are epoch milliseconds.
//
// Reading is strict. A stored name that no longer matches a constant throws rather than quietly
// becoming Category.Other: a crash in a test is noticed, a block that silently changed category is
// not. Renaming a constant is therefore a migration, not a refactor.

internal fun LocalDate.toColumn(): String = format(LocalDate.Formats.ISO)

private fun String.toPlanDate(): LocalDate = LocalDate.parse(this, LocalDate.Formats.ISO)

internal fun Block.toTimeBlock(): TimeBlock = TimeBlock(
    id = BlockId(id),
    date = plan_date.toPlanDate(),
    title = title,
    category = Category.valueOf(category),
    span = DaySpan(start_minutes.toInt(), end_minutes.toInt()),
    note = note,
)

/**
 * Writes [block] over the row with its id, or inserts it when there is none. The update comes
 * first and keeps the row — and with it the block's check-in record, which a delete-and-insert
 * would cascade away. Run it inside a transaction, so the pair is one change to observers.
 */
internal fun BlockQueries.upsert(block: TimeBlock, updatedAt: Long) {
    val updated = update(
        planDate = block.date.toColumn(),
        title = block.title,
        category = block.category.name,
        startMinutes = block.span.startMinutes.toLong(),
        endMinutes = block.span.endMinutes.toLong(),
        note = block.note,
        updatedAt = updatedAt,
        id = block.id.value,
    ).value
    if (updated == 0L) {
        insert(
            id = block.id.value,
            planDate = block.date.toColumn(),
            title = block.title,
            category = block.category.name,
            startMinutes = block.span.startMinutes.toLong(),
            endMinutes = block.span.endMinutes.toLong(),
            note = block.note,
            updatedAt = updatedAt,
        )
    }
}

/**
 * Folds template_with_blocks rows — one per template block, ordered by template and then by
 * position — back into templates, keeping both orders.
 */
internal fun List<Template_with_blocks>.toTemplates(): List<Template> =
    groupBy { it.id }.map { (id, rows) ->
        Template(
            id = TemplateId(id),
            name = rows.first().name,
            blocks = rows.mapNotNull { it.toTemplateBlockOrNull() },
        )
    }

private fun Template_with_blocks.toTemplateBlockOrNull(): TemplateBlock? {
    // A template without blocks still has its one LEFT JOIN row, with every block column NULL.
    val title = title ?: return null
    return TemplateBlock(
        title = title,
        category = Category.valueOf(category!!),
        span = DaySpan(start_minutes!!.toInt(), end_minutes!!.toInt()),
        note = note,
    )
}

internal fun TemplateQueries.insertBlockAt(templateId: TemplateId, position: Int, block: TemplateBlock) {
    insertBlock(
        templateId = templateId.value,
        position = position.toLong(),
        title = block.title,
        category = block.category.name,
        startMinutes = block.span.startMinutes.toLong(),
        endMinutes = block.span.endMinutes.toLong(),
        note = block.note,
    )
}

/** In week order, Monday first, so iterating the map reads like a week. */
internal fun List<Weekday_template>.toAssignments(): Map<DayOfWeek, TemplateId> =
    map { DayOfWeek.valueOf(it.weekday) to TemplateId(it.template_id) }
        .sortedBy { (day, _) -> day }
        .toMap()

internal fun Block_record.toBlockRecord(): BlockRecord = BlockRecord(
    blockId = BlockId(block_id),
    answer = answer?.let { CheckInAnswer.valueOf(it) },
    outcome = outcome?.let { BlockOutcome.valueOf(it) },
)

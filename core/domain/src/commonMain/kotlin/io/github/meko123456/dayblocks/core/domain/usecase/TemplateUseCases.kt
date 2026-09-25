package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** A day's blocks as a reusable template: the same shape, no date, no ids. */
class SaveDayAsTemplate(private val ids: IdGenerator) {
    operator fun invoke(name: String, day: List<TimeBlock>): Template = Template(
        id = TemplateId(ids.next()),
        name = name.trim(),
        blocks = day.sortedBy { it.span.startMinutes }.map { TemplateBlock(it.title, it.category, it.span, it.note) },
    )
}

/**
 * Copies a day onto another date — "Copy yesterday". Every copy gets a fresh id: reusing the
 * source ids would *move* yesterday's plan, which the block repository refuses for that reason.
 */
class CopyDay(private val ids: IdGenerator) {
    operator fun invoke(source: List<TimeBlock>, to: LocalDate): List<TimeBlock> =
        source.sortedBy { it.span.startMinutes }.map { it.copy(id = BlockId(ids.next()), date = to) }
}

/**
 * Fills a day from the template assigned to its weekday — once.
 *
 * The rules, in order: nothing happens without an assigned template; a day that already has blocks
 * is claimed and left alone, so it is never filled later either; and only the first caller to
 * claim an empty day fills it. Filling *adds* blocks and never replaces, so even a block planned in
 * the instant between the check and the claim survives.
 */
class AutoFillDay(
    private val blocks: BlockRepository,
    private val templates: TemplateRepository,
    private val generate: GenerateDayFromTemplate,
    private val clock: TimeProvider,
) {
    /** True when this call filled the day. */
    suspend operator fun invoke(date: LocalDate): Boolean {
        val assigned = templates.observeWeekdayAssignments().first()[date.dayOfWeek] ?: return false
        val template = templates.template(assigned) ?: return false
        if (blocks.observeDay(date).first().isNotEmpty()) {
            templates.claimAutoFill(date, clock.now())
            return false
        }
        if (!templates.claimAutoFill(date, clock.now())) return false
        generate(template, date).forEach { blocks.upsert(it) }
        return true
    }
}

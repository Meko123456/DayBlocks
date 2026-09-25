package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate

/**
 * A concrete day from a template: every template block becomes a [TimeBlock] on [date], with a
 * fresh id, in start order.
 *
 * The spans are copied verbatim, including ones past 24:00 — a template's "00:00 Sleep" becomes
 * that date's 00:00 Sleep, belonging to that plan, rather than being wrapped onto the following
 * morning of the same date.
 */
class GenerateDayFromTemplate(private val ids: IdGenerator) {
    operator fun invoke(template: Template, date: LocalDate): List<TimeBlock> =
        template.blocks
            .sortedBy { it.span.startMinutes }
            .map { block ->
                TimeBlock(
                    id = BlockId(ids.next()),
                    date = date,
                    title = block.title,
                    category = block.category,
                    span = block.span,
                    note = block.note,
                )
            }
}

package io.github.meko123456.dayblocks.core.domain.model

import kotlinx.datetime.LocalDate
import kotlin.jvm.JvmInline

@JvmInline
value class BlockId(val value: String)

/**
 * One block of a planned day.
 *
 * [date] is the planning day the block belongs to, not necessarily the calendar date it starts
 * on — see [DaySpan] for why "00:00 Sleep" at the end of Monday's plan has Monday's date.
 */
data class TimeBlock(
    val id: BlockId,
    val date: LocalDate,
    val title: String,
    val category: Category,
    val span: DaySpan,
    val note: String? = null,
) {
    init {
        require(title.isNotBlank()) { "a block needs a title" }
    }
}

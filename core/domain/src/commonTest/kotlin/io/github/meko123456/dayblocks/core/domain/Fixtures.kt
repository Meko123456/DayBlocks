package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.usecase.IdGenerator
import kotlinx.datetime.LocalDate

/** Minutes for "hh:mm", so a test reads like the plan it describes. */
fun at(hh: Int, mm: Int = 0): Int = hh * 60 + mm

fun block(
    id: String,
    date: LocalDate,
    start: Int,
    end: Int,
    category: Category = Category.Work,
    title: String = id,
): TimeBlock = TimeBlock(BlockId(id), date, title, category, DaySpan(start, end))

/** Ids "id-1", "id-2", ... so generated days can be asserted on exactly. */
class CountingIds : IdGenerator {
    private var n = 0
    override fun next(): String = "id-${++n}"
}

val MONDAY: LocalDate = LocalDate(2026, 9, 21)
val TUESDAY: LocalDate = LocalDate(2026, 9, 22)

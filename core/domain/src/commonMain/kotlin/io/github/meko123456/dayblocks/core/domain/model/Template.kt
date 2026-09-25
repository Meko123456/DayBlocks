package io.github.meko123456.dayblocks.core.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class TemplateId(val value: String)

/**
 * A reusable shape for a day — "Weekday", "Weekend" — with no date attached. Turning one into a
 * concrete day is [io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate].
 */
data class Template(
    val id: TemplateId,
    val name: String,
    val blocks: List<TemplateBlock>,
) {
    init {
        require(name.isNotBlank()) { "a template needs a name" }
    }
}

/** A block in a template: everything a [TimeBlock] has except an id and a date. */
data class TemplateBlock(
    val title: String,
    val category: Category,
    val span: DaySpan,
    val note: String? = null,
)

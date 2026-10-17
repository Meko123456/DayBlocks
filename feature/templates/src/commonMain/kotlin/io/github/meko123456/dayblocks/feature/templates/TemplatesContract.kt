package io.github.meko123456.dayblocks.feature.templates

import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

data class TemplatesState(
    val loading: Boolean = true,
    val today: LocalDate? = null,
    val templates: List<Template> = emptyList(),
    val assignments: Map<DayOfWeek, TemplateId> = emptyMap(),
    val todayBlocks: Int = 0,
    val yesterdayBlocks: Int = 0,
    /** The "save today as…" dialog is open. */
    val naming: Boolean = false,
    val draftName: String = "",
    /** A replace waiting for confirmation, because today already has a plan. */
    val pending: PendingReplace? = null,
    /** A template delete waiting for confirmation. */
    val deleting: PendingDelete? = null,
)

/** Replacing a non-empty day always asks first. */
sealed interface PendingReplace {
    data object CopyYesterday : PendingReplace
    data class ApplyTemplate(val id: TemplateId, val name: String) : PendingReplace
}

/**
 * Deleting a template asks first as well. It went at one tap of Delete, for good, and the
 * weekdays that filled from it stopped without a word; [days] are those weekdays, so the question
 * can name them.
 */
data class PendingDelete(val id: TemplateId, val name: String, val blocks: Int, val days: List<DayOfWeek>) {
    /** What goes with it, said under the question. */
    val consequences: String
        get() = buildString {
            append(
                when (blocks) {
                    0 -> "It has no blocks."
                    1 -> "Its 1 block goes with it."
                    else -> "Its $blocks blocks go with it."
                },
            )
            if (days.isNotEmpty()) {
                append(" ").append(days.spoken())
                append(if (days.size == 1) " stops filling itself from it." else " stop filling themselves from it.")
            }
        }
}

/** "Monday", "Monday and Friday", "Monday, Wednesday and Friday". */
private fun List<DayOfWeek>.spoken(): String {
    val names = map { day -> day.name.lowercase().replaceFirstChar { it.uppercase() } }
    return if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " and " + names.last()
}

sealed interface TemplatesIntent {
    data object SaveTodayTapped : TemplatesIntent
    data class DraftNameChanged(val name: String) : TemplatesIntent
    data object NameConfirmed : TemplatesIntent
    data object NameDismissed : TemplatesIntent
    data class AssignPicked(val day: DayOfWeek, val template: TemplateId?) : TemplatesIntent
    data class ApplyTapped(val id: TemplateId) : TemplatesIntent
    data object CopyYesterdayTapped : TemplatesIntent
    data object ReplaceConfirmed : TemplatesIntent
    data object ReplaceDismissed : TemplatesIntent
    data class DeleteTapped(val id: TemplateId) : TemplatesIntent
    data object DeleteConfirmed : TemplatesIntent
    data object DeleteDismissed : TemplatesIntent
    data object BackTapped : TemplatesIntent
}

sealed interface TemplatesEffect {
    data class Message(val text: String) : TemplatesEffect
    data object Close : TemplatesEffect
}

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
)

/** Replacing a non-empty day always asks first: it is the one destructive thing on this screen. */
sealed interface PendingReplace {
    data object CopyYesterday : PendingReplace
    data class ApplyTemplate(val id: TemplateId, val name: String) : PendingReplace
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
    data object BackTapped : TemplatesIntent
}

sealed interface TemplatesEffect {
    data class Message(val text: String) : TemplatesEffect
    data object Close : TemplatesEffect
}

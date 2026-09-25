package io.github.meko123456.dayblocks.feature.editblock

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate

/** What the editor was opened for: an existing block, or a new one, optionally in a chosen gap. */
data class EditBlockArgs(
    val date: LocalDate,
    val blockId: BlockId? = null,
    val prefill: DaySpan? = null,
)

data class EditBlockState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val date: LocalDate,
    val title: String = "",
    val category: Category = Category.Work,
    val span: DaySpan = DaySpan(9 * 60, 10 * 60),
    val note: String = "",
    /** Recent titles matching what has been typed — tap one instead of typing it again. */
    val suggestions: List<String> = emptyList(),
    /**
     * Blocks this one would overlap, including ones in the neighbouring days' plans. A warning,
     * not a refusal: sometimes two things really do happen at once, and the plan is the user's.
     */
    val overlaps: List<TimeBlock> = emptyList(),
) {
    val canSave: Boolean get() = !loading && title.isNotBlank()
}

sealed interface EditBlockIntent {
    data class TitleChanged(val title: String) : EditBlockIntent
    data class SuggestionPicked(val title: String) : EditBlockIntent
    data class CategoryPicked(val category: Category) : EditBlockIntent
    /** Moves the start by [steps] quarter hours; negative moves it earlier. */
    data class StartStepped(val steps: Int) : EditBlockIntent
    /** Moves the end by [steps] quarter hours; negative moves it earlier. */
    data class EndStepped(val steps: Int) : EditBlockIntent
    data class NoteChanged(val note: String) : EditBlockIntent
    data object SaveTapped : EditBlockIntent
    data object DeleteTapped : EditBlockIntent
    data object CancelTapped : EditBlockIntent
}

sealed interface EditBlockEffect {
    data object Close : EditBlockEffect
}

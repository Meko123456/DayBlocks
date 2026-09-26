package io.github.meko123456.dayblocks.feature.checkin

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate

/** Which day to review. Null is the current planning day; the review notification names its own. */
data class CheckinArgs(val date: LocalDate? = null)

data class CheckinState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val rows: List<CheckinRow> = emptyList(),
    /** Adherence from every outcome and suggestion, 0 to 100; null before there is either. */
    val score: Int? = null,
    val buddy: CheckinBuddy = CheckinBuddy(),
    /** Whether [date] is the day being lived now, or an earlier one opened from its notification. */
    val isToday: Boolean = true,
) {
    /** Suggestions from notification answers that are not yet confirmed. */
    val hasSuggestions: Boolean get() = rows.any { it.outcome == null && it.suggested != null }
}

/**
 * One block to rate. [suggested] comes from the answer given in a notification during the day:
 * "On it" suggests Done, "Got distracted" Partly, "Skip this block" Skipped.
 */
data class CheckinRow(val block: TimeBlock, val outcome: BlockOutcome?, val suggested: BlockOutcome?) {
    /** What the row shows as chosen: the confirmed outcome, or the suggestion until there is one. */
    val shown: BlockOutcome? get() = outcome ?: suggested
}

data class CheckinBuddy(
    val name: String = BuddySettings.DEFAULT_NAME,
    val mood: BuddyMood = BuddyMood.Encouraging,
    val line: String = "",
)

sealed interface CheckinIntent {
    data class OutcomePicked(val block: BlockId, val outcome: BlockOutcome) : CheckinIntent
    /** "Looks right": every suggestion becomes the outcome, in one tap. */
    data object ConfirmSuggestions : CheckinIntent
    data object BackTapped : CheckinIntent
}

sealed interface CheckinEffect {
    data object Close : CheckinEffect
}

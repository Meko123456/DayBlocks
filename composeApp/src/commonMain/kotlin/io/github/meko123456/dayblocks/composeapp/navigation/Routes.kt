package io.github.meko123456.dayblocks.composeapp.navigation

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.feature.checkin.CheckinArgs
import io.github.meko123456.dayblocks.feature.editblock.EditBlockArgs
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Every destination in the app, in one place, as a type.
 *
 * This is also why feature modules never depend on each other: a screen raises a navigation
 * effect, and only this graph decides what it means. Today sends you to the editor without
 * knowing the editor exists.
 */
@Serializable data object OnboardingRoute
@Serializable data object TodayRoute
@Serializable data object TemplatesRoute
@Serializable data object StatsRoute
@Serializable data object SettingsRoute

/**
 * The editor's arguments as primitives, because a route is serialised into the back stack.
 * A new block has no [blockId]; filling a free-time gap passes its bounds.
 */
@Serializable
data class EditBlockRoute(
    val date: String,
    val blockId: String? = null,
    val prefillStart: Int? = null,
    val prefillEnd: Int? = null,
) {
    fun toArgs(): EditBlockArgs = EditBlockArgs(
        date = LocalDate.parse(date),
        blockId = blockId?.let(::BlockId),
        prefill = if (prefillStart != null && prefillEnd != null) DaySpan(prefillStart, prefillEnd) else null,
    )

    companion object {
        fun of(date: LocalDate, blockId: BlockId?, prefill: DaySpan?) = EditBlockRoute(
            date = date.toString(),
            blockId = blockId?.value,
            prefillStart = prefill?.startMinutes,
            prefillEnd = prefill?.endMinutes,
        )
    }
}

/** The check-in, for [date] or — without one — for the current planning day. */
@Serializable
data class CheckInRoute(val date: String? = null) {
    fun toArgs(): CheckinArgs = CheckinArgs(date = date?.let(LocalDate::parse))
}

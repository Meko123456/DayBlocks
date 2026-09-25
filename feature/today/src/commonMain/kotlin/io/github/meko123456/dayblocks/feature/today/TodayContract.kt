package io.github.meko123456.dayblocks.feature.today

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate

/** Everything the Today screen draws. Immutable; the ViewModel replaces it, never mutates it. */
data class TodayState(
    val loading: Boolean = true,
    /** The plan on screen. Before the 04:00 rollover this is still yesterday's. */
    val planDate: LocalDate? = null,
    /** The hours the timeline covers, in plan-day minutes. Always contains every block. */
    val window: DaySpan = DEFAULT_WINDOW,
    /**
     * Where the current-time line sits, in plan-day minutes, or null when it falls outside the
     * window. At 00:30 on Tuesday with Monday's plan on screen this is 1470, not 30.
     */
    val nowMinute: Int? = null,
    /** Blocks and free-time gaps together, in start order — exactly what the timeline lays out. */
    val timeline: List<TimelineItem> = emptyList(),
    val now: NowCard = NowCard(),
) {
    companion object {
        /** 06:00 to midnight, widened as needed so no block and not the current time falls off. */
        val DEFAULT_WINDOW: DaySpan = DaySpan(6 * 60, 24 * 60)
    }
}

sealed interface TimelineItem {
    val span: DaySpan

    data class Block(val block: TimeBlock, val status: BlockStatus, val record: BlockRecord?) : TimelineItem {
        override val span: DaySpan get() = block.span
    }

    /** An unplanned gap, tappable to fill. */
    data class FreeTime(override val span: DaySpan) : TimelineItem
}

enum class BlockStatus { Past, Current, Upcoming }

/**
 * The "Now" card. Minutes are rounded *up*: with 30 seconds to go the card says "1m left", not
 * "0m left", because a countdown that reads zero while the block is still running is wrong.
 */
data class NowCard(
    val current: TimeBlock? = null,
    val minutesLeft: Int? = null,
    val next: TimeBlock? = null,
    val minutesUntilNext: Int? = null,
)

sealed interface TodayIntent {
    data class BlockTapped(val id: BlockId) : TodayIntent
    data class FreeTimeTapped(val span: DaySpan) : TodayIntent
    data object AddBlockTapped : TodayIntent
    data object CheckInTapped : TodayIntent
    data object TemplatesTapped : TodayIntent
    data object StatsTapped : TodayIntent
    data object SettingsTapped : TodayIntent
}

/**
 * One-off events. Navigation is an effect and not state, so it fires exactly once — a route kept
 * in state would navigate again after rotation.
 */
sealed interface TodayEffect {
    /** Open the editor: an existing block when [blockId] is set, otherwise a new one, prefilled with [prefill]. */
    data class OpenEditor(val date: LocalDate, val blockId: BlockId? = null, val prefill: DaySpan? = null) : TodayEffect
    data object OpenCheckIn : TodayEffect
    data object OpenTemplates : TodayEffect
    data object OpenStats : TodayEffect
    data object OpenSettings : TodayEffect
}

package io.github.meko123456.dayblocks.feature.stats

import kotlinx.datetime.LocalDate

data class StatsState(
    val loading: Boolean = true,
    /** Days in a row, before today, that followed their plan. */
    val streak: Int = 0,
    /** The last seven planning days, oldest first, today last. */
    val week: List<DayBar> = emptyList(),
    /** The week's average over the days that have a score; null when none has. */
    val average: Int? = null,
)

/** One bar: a day's adherence, or null for a day with nothing planned or nothing rated. */
data class DayBar(val date: LocalDate, val score: Int?, val isToday: Boolean)

sealed interface StatsIntent {
    data object BackTapped : StatsIntent
}

sealed interface StatsEffect {
    data object Close : StatsEffect
}

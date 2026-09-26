package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.model.effectiveOutcomes
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.time.startInstant
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** A block as a widget shows it: already in the user's clock, so the widget formats nothing. */
data class WidgetBlock(
    val title: String,
    val category: Category,
    val starts: Instant,
    val ends: Instant,
    val startClock: String,
    val endClock: String,
)

/** What the widgets show from [at] until the next entry. */
data class WidgetEntry(
    val at: Instant,
    val current: WidgetBlock?,
    val next: WidgetBlock?,
    /** Blocks of the day's plan that are over, of [planned]. */
    val done: Int,
    val planned: Int,
    val mood: BuddyMood,
) {
    val progress: Float get() = if (planned == 0) 0f else done.toFloat() / planned
}

/**
 * What the widgets show through the rest of the planning day: an entry now, and one at every
 * moment the picture changes — a block starting or ending, quiet hours beginning or ending — up to
 * the rollover, when another day's plan takes over and the app refreshes them.
 *
 * iOS takes the list as a WidgetKit timeline, one entry per boundary. Android shows the entry for
 * now and comes back at the next one. The mood uses the day's outcomes as they stand: a widget
 * cannot know how a block that has not happened yet will go.
 */
class WidgetTimeline(
    private val resolveNow: ResolveNow,
    private val todayBuddy: TodayBuddy,
    private val score: ScoreAdherence,
) {
    fun build(
        now: Instant,
        zone: TimeZone,
        is24Hour: Boolean,
        yesterday: PlannedDay,
        today: PlannedDay,
        settings: BuddySettings,
        rule: PlanningDayRule = PlanningDayRule(),
    ): List<WidgetEntry> {
        val blocks = yesterday.blocks + today.blocks
        val nextDay = today.date.plus(1, DateTimeUnit.DAY)
        val rollover = LocalDateTime(nextDay, LocalTime(rule.rolloverMinutes / 60, rule.rolloverMinutes % 60)).toInstant(zone)
        val quietEdges = listOf(settings.quietHours.start, settings.quietHours.end).flatMap { time ->
            listOf(today.date, nextDay).map { date -> LocalDateTime(date, time).toInstant(zone) }
        }
        val moments = (listOf(now) + blocks.flatMap { listOf(it.startInstant(zone), it.endInstant(zone)) } + quietEdges)
            .filter { it >= now && it < rollover }
            .distinct()
            .sorted()
        val adherence = score(today.blocks, today.records.effectiveOutcomes())

        return moments.map { at ->
            val resolved = resolveNow(blocks, at, zone)
            val mood = todayBuddy.moodFor(
                TodaySnapshot(today.date, at.toLocalDateTime(zone).time, today.blocks.size, adherence, resolved.current),
                settings,
            )
            WidgetEntry(
                at = at,
                current = resolved.current?.forWidget(zone, is24Hour),
                next = resolved.next?.forWidget(zone, is24Hour),
                done = today.blocks.count { it.endInstant(zone) <= at },
                planned = today.blocks.size,
                mood = mood,
            )
        }
    }
}

private fun TimeBlock.forWidget(zone: TimeZone, is24Hour: Boolean): WidgetBlock {
    val starts = startInstant(zone)
    val ends = endInstant(zone)
    return WidgetBlock(title, category, starts, ends, starts.clock(zone, is24Hour), ends.clock(zone, is24Hour))
}

private fun Instant.clock(zone: TimeZone, is24Hour: Boolean): String {
    val local = toLocalDateTime(zone)
    return formatClock(local.hour * 60 + local.minute, is24Hour)
}

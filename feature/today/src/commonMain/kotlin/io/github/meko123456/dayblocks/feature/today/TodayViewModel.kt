package io.github.meko123456.dayblocks.feature.today

import androidx.lifecycle.viewModelScope
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.common.minuteTicks
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.MINUTES_PER_DAY
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.usecase.FindFreeTime
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * Today: what is happening now, what is next, and the shape of the whole day.
 *
 * Two clocks drive it at different rates, deliberately. The repositories are subscribed once per
 * *planning day* — re-subscribed only when the 04:00 rollover changes which plan is on screen —
 * while the minute ticker recomputes the Now card, the free time and the current-time line.
 * Re-querying the database every minute to redraw a countdown would be work for nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val blocks: BlockRepository,
    private val outcomes: OutcomeRepository,
    private val clock: TimeProvider,
    private val planningDay: PlanningDayRule,
    private val resolveNow: ResolveNow,
    private val findFreeTime: FindFreeTime,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<TodayState, TodayIntent, TodayEffect>(TodayState(), scope) {

    init {
        val ticks = clock.minuteTicks().shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)
        val days = ticks
            .map { planningDay.planDateAt(it.toLocalDateTime(clock.zone())) }
            .distinctUntilChanged()
            .flatMapLatest { date ->
                // Yesterday's plan too: at 00:30 the block in progress is usually yesterday's.
                combine(
                    blocks.observeDay(date.minus(1, DateTimeUnit.DAY)),
                    blocks.observeDay(date),
                    outcomes.observeDay(date),
                ) { yesterday, today, records -> Day(date, yesterday, today, records) }
            }
        launchInScope {
            combine(ticks, days) { now, day -> render(now, day) }.collect { next -> reduce { next } }
        }
    }

    override suspend fun handle(intent: TodayIntent) {
        val date = state.value.planDate ?: return
        when (intent) {
            is TodayIntent.BlockTapped -> emit(TodayEffect.OpenEditor(date, blockId = intent.id))
            is TodayIntent.FreeTimeTapped -> emit(TodayEffect.OpenEditor(date, prefill = intent.span))
            TodayIntent.AddBlockTapped -> emit(TodayEffect.OpenEditor(date))
            TodayIntent.CheckInTapped -> emit(TodayEffect.OpenCheckIn)
            TodayIntent.TemplatesTapped -> emit(TodayEffect.OpenTemplates)
            TodayIntent.StatsTapped -> emit(TodayEffect.OpenStats)
            TodayIntent.SettingsTapped -> emit(TodayEffect.OpenSettings)
        }
    }

    private fun render(now: Instant, day: Day): TodayState {
        val zone = clock.zone()
        val resolved = resolveNow(day.yesterday + day.today, now, zone)
        val nowMinute = minuteOfPlanDay(now, day.date)
        val window = windowFor(day.today, nowMinute)

        val blockItems = day.today.map { block ->
            val status = when {
                block == resolved.current -> BlockStatus.Current
                block.endInstant(zone) <= now -> BlockStatus.Past
                else -> BlockStatus.Upcoming
            }
            TimelineItem.Block(block, status, day.records[block.id])
        }
        // Free time from now on, not from the top of the window. A gap that has already gone by
        // is not something anyone can use, and offering it made an empty day one enormous dashed
        // box whose label sat at 06:00, scrolled out of sight. Rounded up to the quarter hour so a
        // slot never starts at 12:20.
        val freeFrom = maxOf(window.startMinutes, ceilToQuarter(nowMinute))
        val gaps = if (freeFrom < window.endMinutes) {
            findFreeTime(day.today, DaySpan(freeFrom, window.endMinutes)).map { TimelineItem.FreeTime(it) }
        } else {
            emptyList()
        }

        return TodayState(
            loading = false,
            planDate = day.date,
            window = window,
            nowMinute = nowMinute.takeIf { it in window },
            timeline = (blockItems + gaps).sortedBy { it.span.startMinutes },
            now = NowCard(
                current = resolved.current,
                minutesLeft = resolved.timeLeft?.ceilMinutes(),
                next = resolved.next,
                minutesUntilNext = resolved.untilNext?.ceilMinutes(),
            ),
        )
    }

    /** Wall-clock minutes since [planDate]'s midnight: 00:30 the next day is 1470. */
    private fun minuteOfPlanDay(now: Instant, planDate: LocalDate): Int {
        val local = now.toLocalDateTime(clock.zone())
        return planDate.daysUntil(local.date) * MINUTES_PER_DAY + local.hour * 60 + local.minute
    }

    /**
     * The default window widened to hold every block and the current time, snapped to whole hours
     * so the hour labels line up. A plan that runs to 02:00 gets a timeline that does too.
     *
     * Never more than a day, though: a timeline is one lap of the clock. With the example plan —
     * 06:00 start, "00:00 Sleep" until 08:00 — showing all of Sleep would need 26 hours and would
     * draw 06:00–08:00 twice. So the window stops a day after it starts, and a block running past
     * the end is drawn clipped, which is how every calendar shows an overnight event.
     */
    private fun windowFor(today: List<TimeBlock>, nowMinute: Int): DaySpan {
        val default = TodayState.DEFAULT_WINDOW
        val earliest = listOfNotNull(default.startMinutes, today.minOfOrNull { it.span.startMinutes }, nowMinute.takeIf { it >= 0 }).min()
        val latest = listOfNotNull(default.endMinutes, today.maxOfOrNull { it.span.endMinutes }, nowMinute + 1).max()
        val start = (earliest / 60) * 60
        val end = ((latest + 59) / 60) * 60
        return DaySpan(start.coerceAtLeast(0), end.coerceAtMost(start + MINUTES_PER_DAY))
    }

    private class Day(
        val date: LocalDate,
        val yesterday: List<TimeBlock>,
        val today: List<TimeBlock>,
        val records: Map<BlockId, BlockRecord>,
    )
}

/** Up to the next quarter hour; already-aligned values are unchanged. */
internal fun ceilToQuarter(minute: Int): Int = (minute + 14).floorDiv(15) * 15

/** Whole minutes, rounded up: a countdown must not read zero while the block is still running. */
internal fun Duration.ceilMinutes(): Int {
    val whole = inWholeMinutes
    return (if (this > whole.minutes) whole + 1 else whole).toInt()
}

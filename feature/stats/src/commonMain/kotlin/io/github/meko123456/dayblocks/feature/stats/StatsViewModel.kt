package io.github.meko123456.dayblocks.feature.stats

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.effectiveOutcomes
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.ComputeStreak
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * The streak and the last seven days of adherence. Minimal on purpose: the number that matters
 * and the shape of the week, not a dashboard.
 */
class StatsViewModel(
    private val blocks: BlockRepository,
    private val outcomes: OutcomeRepository,
    clock: TimeProvider,
    planningDay: PlanningDayRule,
    private val score: ScoreAdherence,
    private val computeStreak: ComputeStreak,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<StatsState, StatsIntent, StatsEffect>(StatsState(), scope) {

    init {
        val today = planningDay.planDateAt(clock.nowLocal())
        val days = (6 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
        launchInScope {
            combine(
                blocks.observeRange(days.first(), today),
                combine(days.map { outcomes.observeDay(it) }) { it.toList() },
            ) { plan, records -> plan to records }.collect { (plan, records) ->
                val byDay = plan.groupBy { it.date }
                val week = days.mapIndexed { i, date ->
                    DayBar(date, score(byDay[date].orEmpty(), records[i].effectiveOutcomes()), isToday = date == today)
                }
                val streak = computeStreak(today, { blocks.observeDay(it).first() }, { outcomes.observeDay(it).first() })
                val scored = week.mapNotNull { it.score }
                reduce {
                    StatsState(
                        loading = false,
                        streak = streak,
                        week = week,
                        average = scored.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                    )
                }
            }
        }
    }

    override suspend fun handle(intent: StatsIntent) {
        when (intent) {
            StatsIntent.BackTapped -> emit(StatsEffect.Close)
        }
    }
}

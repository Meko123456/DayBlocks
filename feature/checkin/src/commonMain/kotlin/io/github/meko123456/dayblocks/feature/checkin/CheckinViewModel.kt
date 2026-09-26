package io.github.meko123456.dayblocks.feature.checkin

import io.github.meko123456.dayblocks.core.buddy.CheckInBuddy
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.model.effectiveOutcomes
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine

/**
 * The end-of-day check-in: one tap per block, prefilled from the day's notification answers, and
 * the buddy's reaction to the score.
 *
 * Every tap is recorded at once — there is no Save button to forget. Recording is also what
 * clears the review notification: the planner stops asking once every block has an outcome.
 */
class CheckinViewModel(
    args: CheckinArgs,
    blocks: BlockRepository,
    private val outcomes: OutcomeRepository,
    settings: SettingsRepository,
    private val clock: TimeProvider,
    planningDay: PlanningDayRule,
    private val score: ScoreAdherence,
    private val buddy: CheckInBuddy,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<CheckinState, CheckinIntent, CheckinEffect>(CheckinState(), scope) {

    private val today = planningDay.planDateAt(clock.nowLocal())
    private val date = args.date ?: today

    init {
        launchInScope {
            combine(blocks.observeDay(date), outcomes.observeDay(date), settings.observeBuddy()) { day, records, buddySettings ->
                render(day, records, buddySettings)
            }.collect { next -> reduce { next } }
        }
    }

    override suspend fun handle(intent: CheckinIntent) {
        when (intent) {
            is CheckinIntent.OutcomePicked -> outcomes.recordOutcome(intent.block, intent.outcome, clock.now())
            CheckinIntent.ConfirmSuggestions -> state.value.rows.forEach { row ->
                val suggested = row.suggested
                if (row.outcome == null && suggested != null) outcomes.recordOutcome(row.block.id, suggested, clock.now())
            }
            CheckinIntent.BackTapped -> emit(CheckinEffect.Close)
        }
    }

    private fun render(day: List<TimeBlock>, records: Map<BlockId, BlockRecord>, settings: BuddySettings): CheckinState {
        val rows = day.map { block ->
            val record = records[block.id]
            CheckinRow(block, outcome = record?.outcome, suggested = record?.answer?.suggestedOutcome)
        }
        val dayScore = score(day, records.effectiveOutcomes())
        val reaction = buddy.reaction(date, day.size, dayScore, unrated = rows.count { it.shown == null }, settings)
        return CheckinState(
            loading = false,
            date = date,
            rows = rows,
            score = dayScore,
            buddy = CheckinBuddy(settings.name, reaction.mood, reaction.line),
            isToday = date == today,
        )
    }
}

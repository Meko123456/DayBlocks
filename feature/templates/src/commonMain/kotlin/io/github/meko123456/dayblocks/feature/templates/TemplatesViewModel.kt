package io.github.meko123456.dayblocks.feature.templates

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.CopyDay
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.core.domain.usecase.SaveDayAsTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.minus

/**
 * Save a day as a template, give weekdays a template to auto-fill from, apply one now, or copy
 * yesterday. "Today" is the planning day, so at 00:40 it is still the day being lived.
 */
class TemplatesViewModel(
    private val templates: TemplateRepository,
    private val blocks: BlockRepository,
    private val saveDayAsTemplate: SaveDayAsTemplate,
    private val copyDay: CopyDay,
    private val generate: GenerateDayFromTemplate,
    clock: TimeProvider,
    planningDay: PlanningDayRule,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<TemplatesState, TemplatesIntent, TemplatesEffect>(TemplatesState(), scope) {

    private val today = planningDay.planDateAt(clock.nowLocal())
    private var todayPlan: List<TimeBlock> = emptyList()
    private var yesterdayPlan: List<TimeBlock> = emptyList()

    init {
        launchInScope {
            combine(
                templates.observeTemplates(),
                templates.observeWeekdayAssignments(),
                blocks.observeDay(today),
                blocks.observeDay(today.minus(1, DateTimeUnit.DAY)),
            ) { all, assigned, todays, yesterdays ->
                todayPlan = todays
                yesterdayPlan = yesterdays
                Snapshot(all, assigned, todays.size, yesterdays.size)
            }.collect { s ->
                reduce {
                    copy(
                        loading = false,
                        today = this@TemplatesViewModel.today,
                        templates = s.all,
                        assignments = s.assigned,
                        todayBlocks = s.todayCount,
                        yesterdayBlocks = s.yesterdayCount,
                    )
                }
            }
        }
    }

    override suspend fun handle(intent: TemplatesIntent) {
        when (intent) {
            TemplatesIntent.SaveTodayTapped ->
                if (todayPlan.isEmpty()) emit(TemplatesEffect.Message("Plan something today first, then save it."))
                else reduce { copy(naming = true, draftName = "") }
            is TemplatesIntent.DraftNameChanged -> reduce { copy(draftName = intent.name.take(MAX_NAME)) }
            TemplatesIntent.NameConfirmed -> {
                val name = state.value.draftName.trim()
                if (name.isEmpty()) return
                templates.upsert(saveDayAsTemplate(name, todayPlan))
                reduce { copy(naming = false, draftName = "") }
                emit(TemplatesEffect.Message("Saved “$name”"))
            }
            TemplatesIntent.NameDismissed -> reduce { copy(naming = false, draftName = "") }
            is TemplatesIntent.AssignPicked -> templates.assign(intent.day, intent.template)
            is TemplatesIntent.ApplyTapped -> {
                val template = templates.template(intent.id) ?: return
                if (todayPlan.isEmpty()) apply(intent.id) else reduce { copy(pending = PendingReplace.ApplyTemplate(template.id, template.name)) }
            }
            TemplatesIntent.CopyYesterdayTapped -> when {
                yesterdayPlan.isEmpty() -> emit(TemplatesEffect.Message("Yesterday had no plan to copy."))
                todayPlan.isEmpty() -> copyYesterday()
                else -> reduce { copy(pending = PendingReplace.CopyYesterday) }
            }
            TemplatesIntent.ReplaceConfirmed -> {
                val pending = state.value.pending ?: return
                reduce { copy(pending = null) }
                when (pending) {
                    PendingReplace.CopyYesterday -> copyYesterday()
                    is PendingReplace.ApplyTemplate -> apply(pending.id)
                }
            }
            TemplatesIntent.ReplaceDismissed -> reduce { copy(pending = null) }
            is TemplatesIntent.DeleteTapped -> templates.delete(intent.id)
            TemplatesIntent.BackTapped -> emit(TemplatesEffect.Close)
        }
    }

    private suspend fun apply(id: TemplateId) {
        val template = templates.template(id) ?: return
        blocks.replaceDay(today, generate(template, today))
        emit(TemplatesEffect.Message("Today now follows “${template.name}”"))
    }

    private suspend fun copyYesterday() {
        blocks.replaceDay(today, copyDay(yesterdayPlan, today))
        emit(TemplatesEffect.Message("Copied yesterday's plan"))
    }

    private class Snapshot(
        val all: List<Template>,
        val assigned: Map<DayOfWeek, TemplateId>,
        val todayCount: Int,
        val yesterdayCount: Int,
    )

    companion object {
        const val MAX_NAME: Int = 40
    }
}

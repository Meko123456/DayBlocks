package io.github.meko123456.dayblocks.composeapp.reminders

import io.github.meko123456.dayblocks.core.buddy.NotificationPlanner
import io.github.meko123456.dayblocks.core.buddy.PlanInput
import io.github.meko123456.dayblocks.core.buddy.PlannedDay
import io.github.meko123456.dayblocks.core.common.ClockStyle
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.common.minuteTicks
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.AutoFillDay
import io.github.meko123456.dayblocks.core.notifications.NotificationScheduler
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Keeps what the platform has pending in step with the plan.
 *
 * Everything that could change a notification ends in one [rescheduleNow], which rebuilds the whole
 * window: a block edited, a template applied or assigned, an answer given, a new planning day, the
 * app opened, the device rebooted or moved to another time zone. Rebuilding rather than patching is
 * what makes it trustworthy — what is pending is always exactly what the planner says about the plan
 * as it is now, whatever happened in between.
 */
class ReminderRescheduler(
    private val blocks: BlockRepository,
    private val outcomes: OutcomeRepository,
    private val templates: TemplateRepository,
    private val autoFill: AutoFillDay,
    private val planner: NotificationPlanner,
    private val scheduler: NotificationScheduler,
    private val clock: TimeProvider,
    private val clockStyle: ClockStyle,
    private val rule: PlanningDayRule,
) {
    private val passes = Mutex()

    /** One full pass. Safe to call from anywhere, any number of times: passes never overlap. */
    suspend fun rescheduleNow() {
        passes.withLock {
            val now = clock.now()
            val zone = clock.zone()
            val first = rule.planDateAt(now.toLocalDateTime(zone))
            val last = rule.planDateAt((now + NotificationPlanner.WINDOW).toLocalDateTime(zone))
            // A day the window reaches gets its template now, not when it is first opened: tomorrow's
            // 07:00 run has to be scheduled tonight, whether or not the app is opened before it.
            first.through(last).forEach { autoFill(it) }

            val from = first.minus(1, DateTimeUnit.DAY) // yesterday's last block can still be running
            val to = last.plus(1, DateTimeUnit.DAY) // so "tomorrow's still empty" is known on the last evening
            val planned = blocks.observeRange(from, to).first().groupBy { it.date }
            val days = from.through(to).map { date ->
                PlannedDay(date, planned[date].orEmpty(), outcomes.observeDay(date).first())
            }
            val templated = templates.observeWeekdayAssignments().first().keys
            scheduler.replaceAll(planner.plan(PlanInput(now, zone, clockStyle.is24Hour(), days, templated, rule)))
        }
    }

    /**
     * Reschedules whenever anything within reach changes, for as long as [scope] lives — the process
     * on Android, the running app on iOS — beginning with one pass straight away.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        clock.minuteTicks()
            .map { rule.planDateAt(it.toLocalDateTime(clock.zone())) }
            .distinctUntilChanged()
            .flatMapLatest { today -> changesAround(today) }
            .debounce(SETTLE)
            .collect { rescheduleNow() }
    }

    private fun changesAround(today: LocalDate): Flow<List<Any>> {
        val from = today.minus(1, DateTimeUnit.DAY)
        val to = today.plus(2, DateTimeUnit.DAY)
        val sources: List<Flow<Any>> = listOf(blocks.observeRange(from, to), templates.observeWeekdayAssignments()) +
            from.through(to).map { outcomes.observeDay(it) }
        return combine(sources) { it.toList() }
    }

    companion object {
        /** Long enough for an edit that writes several rows — a template applied — to land as one change. */
        val SETTLE = 500.milliseconds
    }
}

private fun LocalDate.through(last: LocalDate): List<LocalDate> =
    generateSequence(this) { it.plus(1, DateTimeUnit.DAY) }.takeWhile { it <= last }.toList()

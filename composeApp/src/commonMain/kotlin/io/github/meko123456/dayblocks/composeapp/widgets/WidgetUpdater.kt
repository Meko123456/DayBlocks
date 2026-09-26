package io.github.meko123456.dayblocks.composeapp.widgets

import io.github.meko123456.dayblocks.core.buddy.PlannedDay
import io.github.meko123456.dayblocks.core.buddy.WidgetEntry
import io.github.meko123456.dayblocks.core.buddy.WidgetTimeline
import io.github.meko123456.dayblocks.core.common.ClockStyle
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.common.minuteTicks
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Everything the widgets show until [refreshAt]: the entries through the rest of the planning day,
 * and the buddy's name. [refreshAt] is the rollover, when another day's plan takes over.
 */
data class WidgetState(val entries: List<WidgetEntry>, val buddyName: String, val refreshAt: Instant)

/** Where the widgets' timeline goes: Glance on Android, the App Group on iOS. */
interface WidgetPublisher {
    suspend fun publish(state: WidgetState)
}

/**
 * Keeps the home-screen widgets in step with the plan, the way the rescheduler keeps the
 * notifications: rebuilt whole on every change — a block edited, an outcome recorded, the buddy
 * renamed, a new planning day. Between changes the timeline itself carries the widget from one
 * block boundary to the next.
 */
class WidgetUpdater(
    private val blocks: BlockRepository,
    private val outcomes: OutcomeRepository,
    private val settings: SettingsRepository,
    private val clock: TimeProvider,
    private val clockStyle: ClockStyle,
    private val rule: PlanningDayRule,
    private val timeline: WidgetTimeline,
    private val publisher: WidgetPublisher,
) {
    private val passes = Mutex()

    suspend fun refreshNow() {
        passes.withLock { publisher.publish(build()) }
    }

    /** The widgets' state now. Android's widget reads it when it draws; both platforms publish it. */
    suspend fun build(): WidgetState {
        val now = clock.now()
        val zone = clock.zone()
        val today = rule.planDateAt(now.toLocalDateTime(zone))
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val buddy = settings.observeBuddy().first()
        val entries = timeline.build(
            now = now,
            zone = zone,
            is24Hour = clockStyle.is24Hour(),
            yesterday = PlannedDay(yesterday, blocks.observeDay(yesterday).first()),
            today = PlannedDay(today, blocks.observeDay(today).first(), outcomes.observeDay(today).first()),
            settings = buddy,
            rule = rule,
        )
        val rollover = LocalDateTime(today.plus(1, DateTimeUnit.DAY), LocalTime(rule.rolloverMinutes / 60, rule.rolloverMinutes % 60)).toInstant(zone)
        return WidgetState(entries, buddy.name, refreshAt = rollover)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        clock.minuteTicks()
            .map { rule.planDateAt(it.toLocalDateTime(clock.zone())) }
            .distinctUntilChanged()
            .flatMapLatest { today ->
                combine(
                    blocks.observeRange(today.minus(1, DateTimeUnit.DAY), today),
                    outcomes.observeDay(today),
                    settings.observeBuddy(),
                ) { plan, records, buddy -> Triple(plan, records, buddy) }
            }
            .debounce(SETTLE)
            .collect { refreshNow() }
    }

    companion object {
        val SETTLE = 500.milliseconds
    }
}

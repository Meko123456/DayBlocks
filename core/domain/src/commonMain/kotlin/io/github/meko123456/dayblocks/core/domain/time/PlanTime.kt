package io.github.meko123456.dayblocks.core.domain.time

import io.github.meko123456.dayblocks.core.domain.model.MINUTES_PER_DAY
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * The wall-clock moment [minutes] after this planning day's midnight. Minute 1500 of Monday is
 * Tuesday 01:00.
 */
fun LocalDate.atPlanMinute(minutes: Int): LocalDateTime {
    require(minutes >= 0) { "plan minutes cannot be negative, was $minutes" }
    val day = plus(minutes / MINUTES_PER_DAY, DateTimeUnit.DAY)
    val m = minutes % MINUTES_PER_DAY
    return LocalDateTime(day, LocalTime(m / 60, m % 60))
}

/**
 * When the block really begins, in [zone].
 *
 * This is where DST is allowed to matter, and only here. On a spring-forward night 02:30 does not
 * exist; kotlinx-datetime resolves a time in the gap forward by the gap's length, so a block
 * planned for 02:30 starts at 03:30 — the first moment the wall clock reads at or after its plan.
 * On a fall-back night the ambiguous hour resolves to its earlier occurrence. Either way the
 * *plan* is untouched: tomorrow's 09:00 is still 09:00.
 */
fun TimeBlock.startInstant(zone: TimeZone): Instant = date.atPlanMinute(span.startMinutes).toInstant(zone)

/** When the block really ends, in [zone]. See [startInstant]. */
fun TimeBlock.endInstant(zone: TimeZone): Instant = date.atPlanMinute(span.endMinutes).toInstant(zone)

/**
 * This block's start as minutes past [reference]'s midnight, so blocks from neighbouring plans
 * can be compared on one wall-clock line. Monday's 00:00 Sleep (minute 1440 of Monday) and
 * Tuesday's 00:00 (minute 0 of Tuesday) both land on 1440 against a Monday reference, which is
 * how a cross-plan clash becomes visible.
 */
internal fun TimeBlock.startOnLineOf(reference: LocalDate): Int =
    reference.daysUntil(date) * MINUTES_PER_DAY + span.startMinutes

internal fun TimeBlock.endOnLineOf(reference: LocalDate): Int =
    reference.daysUntil(date) * MINUTES_PER_DAY + span.endMinutes

/**
 * Which planning day a wall-clock moment belongs to.
 *
 * Before [rolloverMinutes] past midnight it is still the previous day's plan: at 00:40 someone who
 * has not gone to bed is living the end of Monday, not the start of Tuesday, and the Today screen
 * should show Monday's blocks — including the "00:00 Sleep" they are about to be late for.
 *
 * This only decides which plan is *shown*. Which block is *current* is decided on real instants
 * across neighbouring plans by [io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow],
 * so a Tuesday 03:00 block is still found at 03:15 even though the display has not rolled over.
 */
data class PlanningDayRule(val rolloverMinutes: Int = DEFAULT_ROLLOVER_MINUTES) {
    init {
        require(rolloverMinutes in 0 until MINUTES_PER_DAY) { "rollover must be within a day" }
    }

    fun planDateAt(local: LocalDateTime): LocalDate {
        val minuteOfDay = local.hour * 60 + local.minute
        return if (minuteOfDay < rolloverMinutes) local.date.plus(-1, DateTimeUnit.DAY) else local.date
    }

    companion object {
        const val DEFAULT_ROLLOVER_MINUTES: Int = 4 * 60
    }
}

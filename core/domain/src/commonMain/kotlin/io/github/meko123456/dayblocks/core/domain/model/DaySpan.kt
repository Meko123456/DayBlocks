package io.github.meko123456.dayblocks.core.domain.model

/** Minutes in a calendar day. */
const val MINUTES_PER_DAY: Int = 24 * 60

/**
 * A wall-clock span inside a planning day, in minutes from that day's midnight.
 *
 * The end may run past 24:00, and that is the point. A plan is not a calendar day: "00:00 Sleep"
 * at the end of Monday's plan starts on Tuesday's date but belongs to Monday, and a 23:00–01:00
 * block is one block, not two halves on two days. Measuring from the plan's own midnight makes
 * both of those a plain `1440..1920` or `1380..1500`, and every question about them — does it
 * overlap, is it current, how long is it — becomes integer arithmetic with no date edge case.
 *
 * Wall-clock minutes rather than an instant because a plan is wall-clock: 09:00 work means 09:00
 * wherever the user is and whatever the DST rules did overnight. Converting to an instant happens
 * at the edge, through the time zone, and only when something has to fire at a real moment.
 *
 * Intervals are half-open, `[start, end)`: a block ending at 12:00 and one starting at 12:00
 * touch without overlapping, which is the ordinary way to plan a day.
 */
data class DaySpan(val startMinutes: Int, val endMinutes: Int) {
    init {
        require(startMinutes in 0 until MAX_START) {
            "start must be within the plan day or the following one, was $startMinutes"
        }
        require(endMinutes > startMinutes) { "end ($endMinutes) must be after start ($startMinutes)" }
        require(endMinutes - startMinutes <= MINUTES_PER_DAY) {
            "a block may last at most a day, was ${endMinutes - startMinutes} minutes"
        }
    }

    val durationMinutes: Int get() = endMinutes - startMinutes

    /** True when the two spans share any minute. Touching end-to-start is not an overlap. */
    fun overlaps(other: DaySpan): Boolean =
        startMinutes < other.endMinutes && other.startMinutes < endMinutes

    operator fun contains(minute: Int): Boolean = minute in startMinutes until endMinutes

    /** Whether the block runs into the next calendar date. */
    val crossesMidnight: Boolean get() = endMinutes > MINUTES_PER_DAY

    companion object {
        /** A plan day may start blocks up to the end of the following calendar date. */
        const val MAX_START: Int = 2 * MINUTES_PER_DAY

        fun of(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): DaySpan =
            DaySpan(startHour * 60 + startMinute, endHour * 60 + endMinute)
    }
}

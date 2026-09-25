package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.time.atPlanMinute
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.time.startInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

class PlanTimeTest {

    private val london = TimeZone.of("Europe/London")
    private val tbilisi = TimeZone.of("Asia/Tbilisi")

    @Test
    fun planMinutesPastMidnightLandOnTheNextCalendarDate() {
        assertEquals(LocalDateTime(2026, 9, 21, 0, 0), MONDAY.atPlanMinute(0))
        assertEquals(LocalDateTime(2026, 9, 22, 0, 0), MONDAY.atPlanMinute(at(24)))
        assertEquals(LocalDateTime(2026, 9, 22, 1, 0), MONDAY.atPlanMinute(at(25)))
        assertEquals(LocalDateTime(2026, 9, 22, 23, 59), MONDAY.atPlanMinute(at(47, 59)))
    }

    @Test
    fun negativePlanMinutesAreRejected() {
        assertFailsWith<IllegalArgumentException> { MONDAY.atPlanMinute(-1) }
    }

    @Test
    fun theSleepBlockAtTheEndOfMondaysPlanStartsOnTuesday() {
        val sleep = block("sleep", MONDAY, at(24), at(32))
        assertEquals(Instant.parse("2026-09-21T23:00:00Z"), sleep.startInstant(london)) // BST: UTC+1
        assertEquals(Instant.parse("2026-09-22T07:00:00Z"), sleep.endInstant(london))
    }

    @Test
    fun travellingMovesTheInstantButNeverThePlan() {
        // The plan is wall-clock. 09:00 is 09:00 in whichever zone the user is in.
        val work = block("work", LocalDate(2026, 1, 15), at(9), at(12))
        assertEquals(Instant.parse("2026-01-15T09:00:00Z"), work.startInstant(london)) // GMT
        assertEquals(Instant.parse("2026-01-15T05:00:00Z"), work.startInstant(tbilisi)) // UTC+4
        assertEquals(at(9), work.span.startMinutes)
    }

    @Test
    fun aBlockAcrossSpringForwardLastsItsRealLength() {
        // 29 March 2026, Europe/London: 01:00 GMT jumps to 02:00 BST, so 01:00–01:59 never happens.
        // A 00:30–03:00 plan is 150 wall-clock minutes but only 90 real ones.
        val night = block("night", LocalDate(2026, 3, 29), at(0, 30), at(3))
        assertEquals(Instant.parse("2026-03-29T00:30:00Z"), night.startInstant(london))
        assertEquals(Instant.parse("2026-03-29T02:00:00Z"), night.endInstant(london))
        assertEquals(90.minutes, night.endInstant(london) - night.startInstant(london))
    }

    @Test
    fun aStartInsideTheSpringForwardGapMovesForwardByTheGap() {
        // 01:30 does not exist that night. It resolves to 02:30 BST: the first moment the wall
        // clock reads at or after the plan, shifted by the one-hour gap.
        val early = block("early", LocalDate(2026, 3, 29), at(1, 30), at(4))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), early.startInstant(london))
    }

    @Test
    fun aBlockAcrossFallBackLastsItsRealLength() {
        // 25 October 2026, Europe/London: 02:00 BST falls back to 01:00 GMT, so 01:00–01:59
        // happens twice. A 00:30–02:30 plan is 120 wall-clock minutes but three real hours.
        val night = block("night", LocalDate(2026, 10, 25), at(0, 30), at(2, 30))
        assertEquals(Instant.parse("2026-10-24T23:30:00Z"), night.startInstant(london))
        assertEquals(Instant.parse("2026-10-25T02:30:00Z"), night.endInstant(london))
        assertEquals(3.hours, night.endInstant(london) - night.startInstant(london))
    }
}

package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class ResolveNowTest {

    private val resolve = ResolveNow()
    private val zone = TimeZone.of("Asia/Tbilisi") // UTC+4, no DST — keeps arithmetic obvious
    private fun monday(h: Int, m: Int = 0): Instant = LocalDateTime(2026, 9, 21, h, m).toInstant(zone)
    private fun tuesday(h: Int, m: Int = 0): Instant = LocalDateTime(2026, 9, 22, h, m).toInstant(zone)

    private val work = block("work", MONDAY, at(9), at(12))
    private val rest = block("rest", MONDAY, at(12), at(13))
    private val read = block("read", MONDAY, at(13), at(15))
    private val sleep = block("sleep", MONDAY, at(24), at(32), title = "Sleep")
    private val mondayPlan = listOf(work, rest, read, sleep)

    @Test
    fun midBlockItReportsTheBlockItsCountdownAndWhatIsNext() {
        val now = resolve(mondayPlan, monday(10, 30), zone)
        assertEquals(work, now.current)
        assertEquals(90.minutes, now.timeLeft)
        assertEquals(rest, now.next)
        assertEquals(90.minutes, now.untilNext)
    }

    @Test
    fun atTheBoundaryTheNewBlockHasBegun() {
        val now = resolve(mondayPlan, monday(12), zone)
        assertEquals(rest, now.current)
        assertEquals(1.hours, now.timeLeft)
        assertEquals(read, now.next)
    }

    @Test
    fun inAGapThereIsNoCurrentBlockOnlyANextOne() {
        val now = resolve(mondayPlan, monday(16), zone)
        assertNull(now.current)
        assertNull(now.timeLeft)
        assertEquals(sleep, now.next)
        assertEquals(8.hours, now.untilNext)
    }

    @Test
    fun atHalfPastMidnightTheCurrentBlockIsMondaysSleep() {
        // The case a today-only lookup gets wrong: it is Tuesday, and the user is inside a block
        // that belongs to Monday's plan. Callers pass yesterday's and today's blocks together.
        val tuesdayWork = block("tue-work", TUESDAY, at(9), at(12))
        val now = resolve(mondayPlan + tuesdayWork, tuesday(0, 30), zone)
        assertEquals(sleep, now.current)
        assertEquals(7.5.hours, now.timeLeft)
        assertEquals(tuesdayWork, now.next)
    }

    @Test
    fun afterTheLastBlockNothingIsCurrentOrNext() {
        val now = resolve(listOf(work), monday(20), zone)
        assertNull(now.current)
        assertNull(now.next)
        assertNull(now.untilNext)
    }

    @Test
    fun anEmptyPlanResolvesToNothing() {
        val now = resolve(emptyList(), monday(10), zone)
        assertNull(now.current)
        assertNull(now.next)
    }

    @Test
    fun whenTwoBlocksOverlapTheLaterStartedOneIsCurrent() {
        val call = block("call", MONDAY, at(11), at(11, 30))
        val now = resolve(listOf(work, call), monday(11, 10), zone)
        assertEquals(call, now.current)
        assertEquals(20.minutes, now.timeLeft)
    }

    @Test
    fun theCountdownIsRealTimeAcrossSpringForward() {
        // 29 March 2026 in London loses 01:00–01:59. At 00:45 a 00:30–03:00 block has 1h15m
        // left in real time — the wall clock would suggest 2h15m, and the countdown must not.
        val london = TimeZone.of("Europe/London")
        val night = block("night", LocalDate(2026, 3, 29), at(0, 30), at(3))
        val now = resolve(listOf(night), Instant.parse("2026-03-29T00:45:00Z"), london)
        assertEquals(night, now.current)
        assertEquals(75.minutes, now.timeLeft)
    }
}

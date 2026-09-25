package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDateTime

class PlanningDayRuleTest {

    private val rule = PlanningDayRule() // rolls over at 04:00

    @Test
    fun beforeTheRolloverItIsStillYesterdaysPlan() {
        // Awake at 00:40 on Tuesday: still living the end of Monday.
        assertEquals(MONDAY, rule.planDateAt(LocalDateTime(2026, 9, 22, 0, 40)))
        assertEquals(MONDAY, rule.planDateAt(LocalDateTime(2026, 9, 22, 3, 59)))
    }

    @Test
    fun fromTheRolloverItIsTodaysPlan() {
        assertEquals(TUESDAY, rule.planDateAt(LocalDateTime(2026, 9, 22, 4, 0)))
        assertEquals(TUESDAY, rule.planDateAt(LocalDateTime(2026, 9, 22, 23, 59)))
    }

    @Test
    fun aMidnightRolloverFollowsTheCalendarExactly() {
        val calendar = PlanningDayRule(rolloverMinutes = 0)
        assertEquals(TUESDAY, calendar.planDateAt(LocalDateTime(2026, 9, 22, 0, 0)))
        assertEquals(MONDAY, calendar.planDateAt(LocalDateTime(2026, 9, 21, 23, 59)))
    }

    @Test
    fun theRolloverMustFallWithinADay() {
        assertFailsWith<IllegalArgumentException> { PlanningDayRule(rolloverMinutes = -1) }
        assertFailsWith<IllegalArgumentException> { PlanningDayRule(rolloverMinutes = 24 * 60) }
    }
}

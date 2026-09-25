package io.github.meko123456.dayblocks.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeTextTest {

    @Test
    fun twentyFourHourClockIsZeroPadded() {
        assertEquals("09:00", formatClock(9 * 60, is24Hour = true))
        assertEquals("13:05", formatClock(13 * 60 + 5, is24Hour = true))
        assertEquals("00:00", formatClock(0, is24Hour = true))
    }

    @Test
    fun twelveHourClockHandlesNoonAndMidnight() {
        assertEquals("12:00 AM", formatClock(0, is24Hour = false))
        assertEquals("12:00 PM", formatClock(12 * 60, is24Hour = false))
        assertEquals("1:00 PM", formatClock(13 * 60, is24Hour = false))
        assertEquals("11:59 PM", formatClock(23 * 60 + 59, is24Hour = false))
    }

    @Test
    fun planMinutesPastMidnightReadAsTheWallClockWould() {
        // "00:00 Sleep" at the end of Monday is minute 1440 — and the clock says midnight.
        assertEquals("00:00", formatClock(24 * 60, is24Hour = true))
        assertEquals("01:30", formatClock(25 * 60 + 30, is24Hour = true))
        assertEquals("1:30 AM", formatClock(25 * 60 + 30, is24Hour = false))
    }

    @Test
    fun durationsReadTheWayTheBuddySaysThem() {
        assertEquals("45m", formatDuration(45))
        assertEquals("2h", formatDuration(120))
        assertEquals("1h 20m", formatDuration(80))
        assertEquals("0m", formatDuration(0))
    }
}

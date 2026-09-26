package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.LocalTime

class BuddySettingsTest {

    @Test
    fun quietHoursWrapPastMidnight() {
        val night = QuietHours(LocalTime(23, 0), LocalTime(8, 0))
        assertTrue(LocalTime(23, 0) in night)
        assertTrue(LocalTime(2, 30) in night)
        assertFalse(LocalTime(8, 0) in night, "the end is where speaking may start again")
        assertFalse(LocalTime(22, 59) in night)
    }

    @Test
    fun quietHoursWithinADayAndNoneAtAll() {
        val nap = QuietHours(LocalTime(13, 0), LocalTime(14, 0))
        assertTrue(LocalTime(13, 30) in nap)
        assertFalse(LocalTime(14, 30) in nap)
        val none = QuietHours(LocalTime(0, 0), LocalTime(0, 0))
        assertFalse(LocalTime(0, 0) in none)
        assertFalse(LocalTime(12, 0) in none)
    }

    @Test
    fun settingsRefuseABlankNameAndACapOutOfRange() {
        assertFailsWith<IllegalArgumentException> { BuddySettings(name = "  ") }
        assertFailsWith<IllegalArgumentException> { BuddySettings(dailyCap = 0) }
        assertFailsWith<IllegalArgumentException> { BuddySettings(name = "x".repeat(BuddySettings.MAX_NAME_LENGTH + 1)) }
    }
}

package io.github.meko123456.dayblocks.core.data.repository

import app.cash.turbine.test
import com.russhwolf.settings.MapSettings
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalTime

class SettingsRepositoryTest {
    private val store = MapSettings()
    private val settings = PreferencesSettingsRepository(store)

    @Test
    fun aFreshInstallHasKubiNormalToneQuietNightsAndACapOfTwelve() = runTest {
        assertEquals(BuddySettings(), settings.observeBuddy().first())
        assertEquals("Kubi", settings.observeBuddy().first().name)
        assertEquals(QuietHours(LocalTime(23, 0), LocalTime(8, 0)), settings.observeBuddy().first().quietHours)
    }

    @Test
    fun aChangeIsStoredAndReachesObservers() = runTest {
        settings.observeBuddy().test {
            assertEquals(BuddySettings(), awaitItem())
            settings.updateBuddy { it.copy(name = "Bloop", tone = BuddyTone.Pushy, quietHours = QuietHours(LocalTime(22, 30), LocalTime(7, 0)), dailyCap = 6) }
            var latest = awaitItem()
            while (latest.dailyCap != 6) latest = awaitItem() // one key is written at a time
            assertEquals(BuddySettings("Bloop", BuddyTone.Pushy, QuietHours(LocalTime(22, 30), LocalTime(7, 0)), 6), latest)
            cancelAndIgnoreRemainingEvents()
        }
        // and a second repository over the same store reads it back
        assertEquals("Bloop", PreferencesSettingsRepository(store).observeBuddy().first().name)
    }

    @Test
    fun anythingUnreadableFallsBackToItsDefault() = runTest {
        store.putString("buddy.name", "   ")
        store.putString("buddy.tone", "Shouty")
        store.putInt("buddy.quietHours.start", 9999)
        store.putInt("buddy.dailyCap", 0)
        assertEquals(BuddySettings(), settings.observeBuddy().first())
    }

    @Test
    fun theLastOpeningIsRemembered() = runTest {
        assertNull(settings.observeLastOpened().first())
        val opened = Instant.parse("2026-09-25T06:00:00Z")
        settings.markOpened(opened)
        assertEquals(opened, settings.observeLastOpened().first())
    }
}

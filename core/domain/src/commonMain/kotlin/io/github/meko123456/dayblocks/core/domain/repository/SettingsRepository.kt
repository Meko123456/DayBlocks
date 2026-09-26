package io.github.meko123456.dayblocks.core.domain.repository

import io.github.meko123456.dayblocks.core.domain.model.AppSettings
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * What the user has chosen, and the one thing the app remembers about them on its own: when they
 * last opened it. Flows, because the schedule is rebuilt the moment a setting changes.
 */
interface SettingsRepository {
    fun observeBuddy(): Flow<BuddySettings>

    /** Applies [change] to the current settings and stores the result. */
    suspend fun updateBuddy(change: (BuddySettings) -> BuddySettings)

    fun observeApp(): Flow<AppSettings>

    suspend fun updateApp(change: (AppSettings) -> AppSettings)

    /** When the app last came to the foreground, or null before it ever has. Comebacks count from it. */
    fun observeLastOpened(): Flow<Instant?>

    suspend fun markOpened(at: Instant)
}

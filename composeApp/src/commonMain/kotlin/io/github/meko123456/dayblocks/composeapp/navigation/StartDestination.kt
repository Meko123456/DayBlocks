package io.github.meko123456.dayblocks.composeapp.navigation

import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Onboarding for someone new, Today for everyone else. Someone who already has a plan — an update
 * from before onboarding existed, a restored backup — has nothing to be introduced to, so they
 * are marked as onboarded and taken straight to their day.
 */
suspend fun startDestination(settings: SettingsRepository, blocks: BlockRepository): Any {
    if (settings.observeApp().first().onboarded) return TodayRoute
    if (blocks.recentTitles(limit = 1).isNotEmpty()) {
        settings.updateApp { it.copy(onboarded = true) }
        return TodayRoute
    }
    return OnboardingRoute
}

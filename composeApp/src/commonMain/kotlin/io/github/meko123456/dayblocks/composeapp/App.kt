package io.github.meko123456.dayblocks.composeapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.meko123456.dayblocks.composeapp.navigation.CheckInRoute
import io.github.meko123456.dayblocks.composeapp.navigation.EditBlockRoute
import io.github.meko123456.dayblocks.composeapp.navigation.OnboardingRoute
import io.github.meko123456.dayblocks.composeapp.navigation.SettingsRoute
import io.github.meko123456.dayblocks.composeapp.navigation.StatsRoute
import io.github.meko123456.dayblocks.composeapp.navigation.TemplatesRoute
import io.github.meko123456.dayblocks.composeapp.navigation.TodayRoute
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.feature.checkin.CheckinScreen
import io.github.meko123456.dayblocks.feature.editblock.EditBlockScreen
import io.github.meko123456.dayblocks.feature.onboarding.OnboardingScreen
import io.github.meko123456.dayblocks.feature.settings.SettingsScreen
import io.github.meko123456.dayblocks.feature.stats.StatsScreen
import io.github.meko123456.dayblocks.feature.templates.TemplatesScreen
import io.github.meko123456.dayblocks.feature.today.TodayScreen

/**
 * The shared root. Both platforms call this: :androidApp from an Activity, iosApp from a
 * UIViewController, so there is exactly one navigation graph and one theme for the whole app.
 *
 * The start destination is Today while the has-onboarded check waits for :feature:onboarding
 * (step 10).
 */
@Composable
fun App(darkTheme: Boolean = isSystemInDarkTheme()) {
    DayBlocksTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = TodayRoute) {
            composable<OnboardingRoute> { OnboardingScreen() }
            composable<TodayRoute> {
                TodayScreen(
                    onOpenEditor = { date, blockId, prefill -> navController.navigate(EditBlockRoute.of(date, blockId, prefill)) },
                    onOpenCheckIn = { navController.navigate(CheckInRoute) },
                    onOpenTemplates = { navController.navigate(TemplatesRoute) },
                    onOpenStats = { navController.navigate(StatsRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                )
            }
            composable<EditBlockRoute> { entry ->
                EditBlockScreen(args = entry.toRoute<EditBlockRoute>().toArgs(), onClose = { navController.popBackStack() })
            }
            composable<TemplatesRoute> { TemplatesScreen() }
            composable<CheckInRoute> { CheckinScreen() }
            composable<StatsRoute> { StatsScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

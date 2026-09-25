package io.github.meko123456.dayblocks.composeapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.meko123456.dayblocks.composeapp.navigation.Routes
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.feature.checkin.CheckinScreen
import io.github.meko123456.dayblocks.feature.editblock.EditblockScreen
import io.github.meko123456.dayblocks.feature.onboarding.OnboardingScreen
import io.github.meko123456.dayblocks.feature.settings.SettingsScreen
import io.github.meko123456.dayblocks.feature.stats.StatsScreen
import io.github.meko123456.dayblocks.feature.templates.TemplatesScreen
import io.github.meko123456.dayblocks.feature.today.TodayScreen

/**
 * The shared root. Both platforms call this: :androidApp from an Activity, iosApp from a
 * UIViewController, so there is exactly one navigation graph and one theme for the whole app.
 *
 * The start destination is Today rather than Onboarding while the skeleton is stood up; the
 * has-onboarded check lands with :feature:onboarding in step 10.
 */
@Composable
fun App(darkTheme: Boolean = isSystemInDarkTheme()) {
    DayBlocksTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Routes.TODAY) {
            composable(Routes.ONBOARDING) { OnboardingScreen() }
            composable(Routes.TODAY) {
                TodayScreen(
                    // The editor's own arguments land with :feature:editblock in step 4.
                    onOpenEditor = { _, _, _ -> navController.navigate(Routes.EDIT_BLOCK) },
                    onOpenCheckIn = { navController.navigate(Routes.CHECK_IN) },
                    onOpenTemplates = { navController.navigate(Routes.TEMPLATES) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.EDIT_BLOCK) { EditblockScreen() }
            composable(Routes.TEMPLATES) { TemplatesScreen() }
            composable(Routes.CHECK_IN) { CheckinScreen() }
            composable(Routes.STATS) { StatsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

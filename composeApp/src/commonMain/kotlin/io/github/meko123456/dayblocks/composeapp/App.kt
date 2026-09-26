package io.github.meko123456.dayblocks.composeapp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.meko123456.dayblocks.composeapp.navigation.AppLink
import io.github.meko123456.dayblocks.composeapp.navigation.AppLinks
import io.github.meko123456.dayblocks.composeapp.navigation.CheckInRoute
import io.github.meko123456.dayblocks.composeapp.navigation.EditBlockRoute
import io.github.meko123456.dayblocks.composeapp.navigation.OnboardingRoute
import io.github.meko123456.dayblocks.composeapp.navigation.SettingsRoute
import io.github.meko123456.dayblocks.composeapp.navigation.StatsRoute
import io.github.meko123456.dayblocks.composeapp.navigation.TemplatesRoute
import io.github.meko123456.dayblocks.composeapp.navigation.TodayRoute
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderNotice
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.feature.checkin.CheckinScreen
import io.github.meko123456.dayblocks.feature.editblock.EditBlockScreen
import io.github.meko123456.dayblocks.feature.onboarding.OnboardingScreen
import io.github.meko123456.dayblocks.feature.settings.SettingsScreen
import io.github.meko123456.dayblocks.feature.stats.StatsScreen
import io.github.meko123456.dayblocks.feature.templates.TemplatesScreen
import io.github.meko123456.dayblocks.feature.today.TodayScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
        // Every return to the app is an opening: comebacks count from the last one, and the
        // rescheduler moves them along as soon as it is written.
        val settings = koinInject<SettingsRepository>()
        val clock = koinInject<TimeProvider>()
        val scope = rememberCoroutineScope()
        LifecycleResumeEffect(settings) {
            scope.launch { settings.markOpened(clock.now()) }
            onPauseOrDispose { }
        }
        val navController = rememberNavController()
        // A tapped review notification opens that day's check-in, and a tapped widget opens Today,
        // whether the tap launched the app or arrived while it was open.
        val link by AppLinks.next.collectAsState()
        LaunchedEffect(link) {
            when (val opened = link) {
                is AppLink.Review -> navController.navigate(CheckInRoute(opened.date?.toString()))
                AppLink.Today -> navController.popBackStack(TodayRoute, inclusive = false)
                null -> Unit
            }
            if (link != null) AppLinks.consumed()
        }
        NavHost(navController = navController, startDestination = TodayRoute) {
            composable<OnboardingRoute> { OnboardingScreen() }
            composable<TodayRoute> {
                TodayScreen(
                    onOpenEditor = { date, blockId, prefill -> navController.navigate(EditBlockRoute.of(date, blockId, prefill)) },
                    onOpenCheckIn = { navController.navigate(CheckInRoute()) },
                    onOpenTemplates = { navController.navigate(TemplatesRoute) },
                    onOpenStats = { navController.navigate(StatsRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    notice = { ReminderNotice() },
                )
            }
            composable<EditBlockRoute> { entry ->
                EditBlockScreen(args = entry.toRoute<EditBlockRoute>().toArgs(), onClose = { navController.popBackStack() })
            }
            composable<TemplatesRoute> { TemplatesScreen(onClose = { navController.popBackStack() }) }
            composable<CheckInRoute> { entry ->
                CheckinScreen(args = entry.toRoute<CheckInRoute>().toArgs(), onClose = { navController.popBackStack() })
            }
            composable<StatsRoute> { StatsScreen(onClose = { navController.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}

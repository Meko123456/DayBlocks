package io.github.meko123456.dayblocks.feature.stats

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A week with a streak going and one day not rated yet, in both themes. See TodayScreenshotTest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class StatsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aWeekInTheLightTheme() = capture("stats_light", dark = false)

    @Test
    fun aWeekInTheDarkTheme() = capture("stats_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent { DayBlocksTheme(darkTheme = dark) { StatsContent(state = aWeek, onIntent = {}) } }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val scores = listOf(64, 81, null, 92, 75, 88, 70)
        val aWeek = StatsState(
            loading = false,
            streak = 3,
            week = scores.mapIndexed { i, score -> DayBar(LocalDate(2026, 9, 23 + i), score, isToday = i == scores.lastIndex) },
            average = 78,
        )
    }
}

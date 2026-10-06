package io.github.meko123456.dayblocks.feature.checkin

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An evening check-in with some blocks rated, one suggestion from a notification answer still to
 * confirm, and one not rated at all — every state a row can be in. See TodayScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CheckinScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun anEveningCheckInInTheLightTheme() = capture("checkin_light", dark = false)

    @Test
    fun anEveningCheckInInTheDarkTheme() = capture("checkin_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent { DayBlocksTheme(darkTheme = dark) { CheckinContent(state = anEvening, onIntent = {}) } }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val day = LocalDate(2026, 9, 29)

        fun block(id: String, title: String, category: Category, from: Int, to: Int) =
            TimeBlock(BlockId(id), day, title, category, DaySpan(from, to))

        val anEvening = CheckinState(
            loading = false,
            date = day,
            rows = listOf(
                CheckinRow(block("run", "Morning run", Category.Exercise, 7 * 60, 8 * 60), outcome = BlockOutcome.Done, suggested = null),
                CheckinRow(block("work", "Deep work", Category.Work, 9 * 60, 12 * 60 + 30), outcome = BlockOutcome.Partly, suggested = null),
                CheckinRow(block("lunch", "Lunch", Category.Cooking, 12 * 60 + 30, 13 * 60 + 30), outcome = null, suggested = BlockOutcome.Done),
                CheckinRow(block("reading", "Reading", Category.Reading, 21 * 60, 22 * 60), outcome = null, suggested = null),
            ),
            score = 72,
            buddy = CheckinBuddy(name = "Kubi", mood = BuddyMood.Proud, line = "Two done and one partly. A good day."),
        )
    }
}

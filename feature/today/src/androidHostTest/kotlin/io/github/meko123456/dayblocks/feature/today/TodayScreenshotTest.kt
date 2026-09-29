package io.github.meko123456.dayblocks.feature.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.BlockId
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
 * Today as a person sees it mid-morning, in both themes.
 *
 * The screen's stateless content is rendered with a fixed day, so the picture changes only when
 * the screen does. `./gradlew :feature:today:testAndroidHostTest` compares it with the PNGs beside
 * this file; add `-Proborazzi.record=true` to redraw them after a deliberate change.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TodayScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aDayUnderWayInTheLightTheme() = capture("today_light", dark = false)

    @Test
    fun aDayUnderWayInTheDarkTheme() = capture("today_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent {
            DayBlocksTheme(darkTheme = dark) { TodayContent(state = aDayUnderWay, onIntent = {}) }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val day = LocalDate(2026, 9, 29)

        fun block(id: String, title: String, category: Category, from: Int, to: Int) =
            TimeBlock(BlockId(id), day, title, category, DaySpan(from, to))

        val run = block("run", "Morning run", Category.Exercise, 7 * 60, 8 * 60)
        val deepWork = block("work", "Deep work", Category.Work, 9 * 60, 12 * 60 + 30)
        val lunch = block("lunch", "Lunch", Category.Cooking, 12 * 60 + 30, 13 * 60 + 30)
        val reviews = block("reviews", "Code reviews", Category.Work, 14 * 60, 16 * 60)
        val reading = block("reading", "Reading", Category.Reading, 21 * 60, 22 * 60)

        /** 10:15, two and a quarter hours into deep work, with lunch next. */
        val aDayUnderWay = TodayState(
            loading = false,
            planDate = day,
            nowMinute = 10 * 60 + 15,
            timeline = listOf(
                TimelineItem.Block(run, BlockStatus.Past, record = null),
                TimelineItem.FreeTime(DaySpan(8 * 60, 9 * 60)),
                TimelineItem.Block(deepWork, BlockStatus.Current, record = null),
                TimelineItem.Block(lunch, BlockStatus.Upcoming, record = null),
                TimelineItem.FreeTime(DaySpan(13 * 60 + 30, 14 * 60)),
                TimelineItem.Block(reviews, BlockStatus.Upcoming, record = null),
                TimelineItem.FreeTime(DaySpan(16 * 60, 21 * 60)),
                TimelineItem.Block(reading, BlockStatus.Upcoming, record = null),
            ),
            now = NowCard(current = deepWork, minutesLeft = 135, next = lunch, minutesUntilNext = 135),
            buddy = BuddyState(name = "Kubi", mood = BuddyMood.Happy, line = "Deep work until 12:30. You've got this."),
        )
    }
}

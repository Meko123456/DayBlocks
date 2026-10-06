package io.github.meko123456.dayblocks.feature.editblock

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A new block being typed, with suggestions, and one that overlaps lunch. See TodayScreenshotTest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class EditBlockScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aNewBlockWithSuggestions() = capture(
        "editblock_new",
        EditBlockState(
            loading = false,
            isNew = true,
            date = day,
            title = "De",
            category = Category.Work,
            span = DaySpan(9 * 60, 10 * 60 + 30),
            suggestions = listOf("Deep work", "Design review"),
        ),
    )

    @Test
    fun anEditThatOverlapsLunchInTheDarkTheme() = capture(
        "editblock_overlap_dark",
        EditBlockState(
            loading = false,
            isNew = false,
            date = day,
            title = "Deep work",
            category = Category.Work,
            span = DaySpan(9 * 60, 13 * 60),
            note = "Finish the sync engine tests",
            overlaps = listOf(TimeBlock(BlockId("lunch"), day, "Lunch", Category.Cooking, DaySpan(12 * 60 + 30, 13 * 60 + 30))),
        ),
        dark = true,
    )

    private fun capture(name: String, state: EditBlockState, dark: Boolean = false) {
        compose.setContent { DayBlocksTheme(darkTheme = dark) { EditBlockContent(state = state, onIntent = {}) } }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val day = LocalDate(2026, 9, 29)
    }
}

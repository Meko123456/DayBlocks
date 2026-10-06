package io.github.meko123456.dayblocks.feature.templates

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Two templates, one assigned to the weekdays, in both themes. See TodayScreenshotTest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class TemplatesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun twoTemplatesInTheLightTheme() = capture("templates_light", dark = false)

    @Test
    fun twoTemplatesInTheDarkTheme() = capture("templates_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent {
            DayBlocksTheme(darkTheme = dark) {
                TemplatesContent(state = twoTemplates, snackbar = remember { SnackbarHostState() }, onIntent = {})
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val workday = Template(
            TemplateId("workday"),
            "Workday",
            listOf(
                TemplateBlock("Morning run", Category.Exercise, DaySpan(7 * 60, 8 * 60)),
                TemplateBlock("Deep work", Category.Work, DaySpan(9 * 60, 12 * 60 + 30)),
                TemplateBlock("Lunch", Category.Cooking, DaySpan(12 * 60 + 30, 13 * 60 + 30)),
            ),
        )
        val weekend = Template(
            TemplateId("weekend"),
            "Slow weekend",
            listOf(TemplateBlock("Reading", Category.Reading, DaySpan(10 * 60, 11 * 60 + 30))),
        )
        val twoTemplates = TemplatesState(
            loading = false,
            today = LocalDate(2026, 9, 29),
            templates = listOf(workday, weekend),
            assignments = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                .associateWith { workday.id } + (DayOfWeek.SATURDAY to weekend.id),
            todayBlocks = 5,
            yesterdayBlocks = 4,
        )
    }
}

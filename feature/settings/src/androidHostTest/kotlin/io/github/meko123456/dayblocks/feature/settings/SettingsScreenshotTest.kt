package io.github.meko123456.dayblocks.feature.settings

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings as a person who renamed the buddy and made it gentler sees them. See TodayScreenshotTest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SettingsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsInTheLightTheme() = capture("settings_light", dark = false)

    @Test
    fun settingsInTheDarkTheme() = capture("settings_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        compose.setContent {
            DayBlocksTheme(darkTheme = dark) {
                SettingsContent(state = renamedAndGentler, snackbar = remember { SnackbarHostState() }, onIntent = {})
            }
        }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }

    private companion object {
        val renamedAndGentler = SettingsState(
            loading = false,
            buddy = BuddySettings(name = "Kubi", tone = BuddyTone.Gentle),
            nameDraft = "Kubi",
        )
    }
}

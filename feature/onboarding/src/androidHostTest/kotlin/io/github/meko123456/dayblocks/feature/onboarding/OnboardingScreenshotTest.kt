package io.github.meko123456.dayblocks.feature.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.meko123456.dayblocks.core.designsystem.DayBlocksTheme
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first page a new user meets, and the tone page with its samples. See TodayScreenshotTest. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class OnboardingScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun meetingTheBuddy() = capture("onboarding_meet", OnboardingState(page = OnboardingPage.Meet, name = "Kubi"))

    @Test
    fun choosingATone() = capture(
        "onboarding_tone",
        OnboardingState(
            page = OnboardingPage.Tone,
            name = "Kubi",
            tone = BuddyTone.Normal,
            samples = mapOf(
                BuddyTone.Gentle to "Kubi: Deep work is up, whenever you're ready.",
                BuddyTone.Normal to "Kubi: Deep work starts now.",
                BuddyTone.Pushy to "Kubi: Deep work. Now. Go!",
            ),
        ),
    )

    @Test
    fun choosingAToneInTheDarkTheme() = capture(
        "onboarding_tone_dark",
        OnboardingState(page = OnboardingPage.Tone, name = "Kubi", tone = BuddyTone.Gentle),
        dark = true,
    )

    private fun capture(name: String, state: OnboardingState, dark: Boolean = false) {
        compose.setContent { DayBlocksTheme(darkTheme = dark) { OnboardingContent(state = state, onIntent = {}) } }
        compose.onRoot().captureRoboImage("src/androidHostTest/screenshots/$name.png")
    }
}

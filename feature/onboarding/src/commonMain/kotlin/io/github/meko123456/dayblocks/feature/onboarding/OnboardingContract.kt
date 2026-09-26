package io.github.meko123456.dayblocks.feature.onboarding

import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone

enum class OnboardingPage { Meet, Tone, Reminders, FirstDay }

data class OnboardingState(
    val page: OnboardingPage = OnboardingPage.Meet,
    val name: String = BuddySettings.DEFAULT_NAME,
    val tone: BuddyTone = BuddyTone.Normal,
    /** How each tone would announce a block, in the buddy's chosen name. */
    val samples: Map<BuddyTone, String> = emptyMap(),
) {
    /** The name to keep: what was typed, or the default if nothing was. */
    val chosenName: String get() = name.trim().ifEmpty { BuddySettings.DEFAULT_NAME }
}

sealed interface OnboardingIntent {
    data class NameChanged(val text: String) : OnboardingIntent
    data class TonePicked(val tone: BuddyTone) : OnboardingIntent
    data object NextTapped : OnboardingIntent
    data object BackTapped : OnboardingIntent
    data object AllowRemindersTapped : OnboardingIntent
    data object NotNowTapped : OnboardingIntent
    data object ExampleDayTapped : OnboardingIntent
    data object OwnPlanTapped : OnboardingIntent
}

sealed interface OnboardingEffect {
    /** Ask the platform for notifications — only after the page has said what they are for. */
    data object RequestReminders : OnboardingEffect
    data object Finished : OnboardingEffect
}

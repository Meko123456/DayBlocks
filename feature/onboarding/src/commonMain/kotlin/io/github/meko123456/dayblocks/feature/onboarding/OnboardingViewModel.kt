package io.github.meko123456.dayblocks.feature.onboarding

import io.github.meko123456.dayblocks.core.buddy.BuddyVoice
import io.github.meko123456.dayblocks.core.buddy.Situation
import io.github.meko123456.dayblocks.core.buddy.Slots
import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

/**
 * Meet the buddy, name it, pick how it talks, hear why it wants notifications before being asked,
 * and start from an example day or from nothing. Nothing is saved until the end: backing out
 * halfway leaves no half-made buddy behind.
 */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val blocks: BlockRepository,
    private val templates: TemplateRepository,
    private val generate: GenerateDayFromTemplate,
    private val clock: TimeProvider,
    private val planningDay: PlanningDayRule,
    private val voice: BuddyVoice,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState(), scope) {

    init {
        reduce { copy(samples = samplesFor(chosenName)) }
    }

    override suspend fun handle(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.NameChanged -> reduce {
                val name = intent.text.take(BuddySettings.MAX_NAME_LENGTH)
                copy(name = name, samples = samplesFor(name.trim().ifEmpty { BuddySettings.DEFAULT_NAME }))
            }
            is OnboardingIntent.TonePicked -> reduce { copy(tone = intent.tone) }
            OnboardingIntent.NextTapped -> reduce { copy(page = OnboardingPage.entries.getOrElse(page.ordinal + 1) { page }) }
            OnboardingIntent.BackTapped -> reduce { copy(page = OnboardingPage.entries.getOrElse(page.ordinal - 1) { page }) }
            OnboardingIntent.AllowRemindersTapped -> {
                emit(OnboardingEffect.RequestReminders)
                reduce { copy(page = OnboardingPage.FirstDay) }
            }
            OnboardingIntent.NotNowTapped -> reduce { copy(page = OnboardingPage.FirstDay) }
            OnboardingIntent.ExampleDayTapped -> finish(withExample = true)
            OnboardingIntent.OwnPlanTapped -> finish(withExample = false)
        }
    }

    private suspend fun finish(withExample: Boolean) {
        val chosen = state.value
        settings.updateBuddy { it.copy(name = chosen.chosenName, tone = chosen.tone) }
        if (withExample) {
            templates.upsert(ExampleDay)
            val today = planningDay.planDateAt(clock.nowLocal())
            // Never over a day already planned: the example is a starting point, not a reset.
            if (blocks.observeDay(today).first().isEmpty()) generate(ExampleDay, today).forEach { blocks.upsert(it) }
        }
        settings.updateApp { it.copy(onboarded = true) }
        emit(OnboardingEffect.Finished)
    }

    private fun samplesFor(name: String): Map<BuddyTone, String> = BuddyTone.entries.associateWith { tone ->
        voice.line(Situation.BlockStart, tone, name, rotation = 0, slots = SampleBlock).body
    }

    private companion object {
        val SampleBlock = Slots(title = "Deep work", emoji = "💼", time = "09:00", length = "3 hours")
    }
}

package io.github.meko123456.dayblocks.feature.onboarding

import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.buddy.BuddyVoice
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeSettingsRepository
import io.github.meko123456.dayblocks.core.testing.FakeTemplateRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class OnboardingViewModelTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val monday = LocalDate(2026, 9, 21)
    private val settings = FakeSettingsRepository()
    private val blocks = FakeBlockRepository()
    private val templates = FakeTemplateRepository()
    private var ids = 0

    private fun TestScope.onboarding(): OnboardingViewModel {
        val clock = OffsetTimeProvider(LocalDateTime(2026, 9, 21, 8, 0).toInstant(zone), zone) { testScheduler.currentTime }
        return OnboardingViewModel(
            settings, blocks, templates, GenerateDayFromTemplate { "b${++ids}" }, clock, PlanningDayRule(), BuddyVoice(), backgroundScope,
        )
    }

    @Test
    fun eachToneSpeaksForItselfInTheChosenName() = runTest {
        val vm = onboarding()
        assertEquals("Hey! It's 09:00. Time for “Deep work” 💼 You've got 3 hours.", vm.state.value.samples[BuddyTone.Normal])
        assertEquals(3, vm.state.value.samples.values.toSet().size, "three tones, three different lines")
        vm.onIntent(OnboardingIntent.NameChanged("Bloop"))
        runCurrent()
        assertEquals("Bloop", vm.state.value.chosenName)
    }

    @Test
    fun thePagesGoForwardAndBack() = runTest {
        val vm = onboarding()
        vm.onIntent(OnboardingIntent.NextTapped)
        vm.onIntent(OnboardingIntent.NextTapped)
        runCurrent()
        assertEquals(OnboardingPage.Reminders, vm.state.value.page)
        vm.onIntent(OnboardingIntent.BackTapped)
        runCurrent()
        assertEquals(OnboardingPage.Tone, vm.state.value.page)
    }

    @Test
    fun notificationsAreAskedForOnlyWhenTheUserSaysYes() = runTest {
        val vm = onboarding()
        vm.effects.test {
            vm.onIntent(OnboardingIntent.AllowRemindersTapped)
            assertEquals(OnboardingEffect.RequestReminders, awaitItem())
        }
        assertEquals(OnboardingPage.FirstDay, vm.state.value.page)
    }

    @Test
    fun theExampleDayFillsAnEmptyTodayAndBecomesTheFirstTemplate() = runTest {
        val vm = onboarding()
        vm.onIntent(OnboardingIntent.NameChanged("Bloop"))
        vm.onIntent(OnboardingIntent.TonePicked(BuddyTone.Gentle))
        vm.effects.test {
            vm.onIntent(OnboardingIntent.ExampleDayTapped)
            assertEquals(OnboardingEffect.Finished, awaitItem())
        }
        assertEquals(ExampleDay.blocks.size, blocks.all.value.count { it.date == monday })
        assertEquals(listOf("Example day"), templates.templates.value.map { it.name })
        assertEquals("Bloop" to BuddyTone.Gentle, settings.buddy.value.name to settings.buddy.value.tone)
        assertTrue(settings.app.value.onboarded)
    }

    @Test
    fun theExampleNeverPlansOverADayAlreadyPlanned() = runTest {
        blocks.upsert(TimeBlock(BlockId("mine"), monday, "My own", Category.Personal, DaySpan(10 * 60, 11 * 60)))
        val vm = onboarding()
        vm.effects.test {
            vm.onIntent(OnboardingIntent.ExampleDayTapped)
            awaitItem()
        }
        assertEquals(listOf("My own"), blocks.all.value.filter { it.date == monday }.map { it.title })
    }

    @Test
    fun startingFromScratchLeavesTheDayEmptyAndABlankNameKeepsKubi() = runTest {
        val vm = onboarding()
        vm.onIntent(OnboardingIntent.NameChanged("  "))
        vm.effects.test {
            vm.onIntent(OnboardingIntent.OwnPlanTapped)
            assertEquals(OnboardingEffect.Finished, awaitItem())
        }
        assertTrue(blocks.all.value.isEmpty())
        assertEquals("Kubi", settings.buddy.value.name)
        assertTrue(settings.app.value.onboarded)
    }
}

package io.github.meko123456.dayblocks.composeapp.navigation

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.testing.FakeBlockRepository
import io.github.meko123456.dayblocks.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class StartDestinationTest {
    private val settings = FakeSettingsRepository()
    private val blocks = FakeBlockRepository()

    @Test
    fun someoneNewIsIntroducedFirst() = runTest {
        assertEquals(OnboardingRoute, startDestination(settings, blocks))
    }

    @Test
    fun someoneOnboardedGoesStraightToToday() = runTest {
        settings.app.value = settings.app.value.copy(onboarded = true)
        assertEquals(TodayRoute, startDestination(settings, blocks))
    }

    @Test
    fun someoneWhoAlreadyPlansIsNotIntroducedAgain() = runTest {
        blocks.upsert(TimeBlock(BlockId("b"), LocalDate(2026, 9, 21), "Deep work", Category.Work, DaySpan(9 * 60, 12 * 60)))
        assertEquals(TodayRoute, startDestination(settings, blocks))
        assertTrue(settings.app.value.onboarded, "and is remembered as onboarded")
    }
}

package io.github.meko123456.dayblocks.feature.settings

import app.cash.turbine.test
import io.github.meko123456.dayblocks.core.common.OffsetTimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode
import io.github.meko123456.dayblocks.core.domain.repository.BackupRepository
import io.github.meko123456.dayblocks.core.domain.repository.ImportResult
import io.github.meko123456.dayblocks.core.testing.FakeSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class SettingsViewModelTest {
    private val zone = TimeZone.of("Asia/Tbilisi")
    private val settings = FakeSettingsRepository()
    private val backup = RecordingBackup()

    private fun TestScope.screen(): SettingsViewModel {
        val clock = OffsetTimeProvider(LocalDateTime(2026, 9, 21, 20, 0).toInstant(zone), zone) { testScheduler.currentTime }
        return SettingsViewModel(settings, backup, clock, backgroundScope).also { runCurrent() }
    }

    private fun TestScope.send(vm: SettingsViewModel, vararg intents: SettingsIntent) {
        intents.forEach { vm.onIntent(it) }
        runCurrent()
    }

    @Test
    fun aNameIsSavedAsItIsTypedButABlankOneIsNot() = runTest {
        val vm = screen()
        send(vm, SettingsIntent.NameChanged("Bloop"))
        assertEquals("Bloop", settings.buddy.value.name)
        send(vm, SettingsIntent.NameChanged("   "))
        assertEquals("Bloop", settings.buddy.value.name, "the last real name stays")
        assertEquals("   ", vm.state.value.nameDraft, "while the field shows what was typed")
    }

    @Test
    fun toneQuietHoursAndTheCapAreSavedAndKeptSensible() = runTest {
        val vm = screen()
        send(vm, SettingsIntent.TonePicked(BuddyTone.Pushy), SettingsIntent.QuietStartMoved(30), SettingsIntent.QuietEndMoved(-30))
        assertEquals(BuddyTone.Pushy, settings.buddy.value.tone)
        assertEquals(QuietHours(LocalTime(23, 30), LocalTime(7, 30)), settings.buddy.value.quietHours)
        send(vm, SettingsIntent.QuietStartMoved(60))
        assertEquals(LocalTime(0, 30), settings.buddy.value.quietHours.start, "round the clock")
        repeat(50) { vm.onIntent(SettingsIntent.CapMoved(-1)) }
        runCurrent()
        assertEquals(1, settings.buddy.value.dailyCap)
    }

    @Test
    fun theTimelineAlwaysRunsForward() = runTest {
        val vm = screen()
        send(vm, SettingsIntent.TimelineStartMoved(-10), SettingsIntent.TimelineEndMoved(5))
        assertEquals(0 to 24, settings.app.value.timelineStartHour to settings.app.value.timelineEndHour)
        repeat(30) { vm.onIntent(SettingsIntent.TimelineStartMoved(1)) }
        runCurrent()
        assertEquals(23, settings.app.value.timelineStartHour, "it stops an hour short of the end")
    }

    @Test
    fun theThemeAndACategoryColourCanBeChosenAndReset() = runTest {
        val vm = screen()
        send(vm, SettingsIntent.ThemePicked(ThemeMode.Dark), SettingsIntent.CategoryColorPicked(Category.Work, 0xFF112233))
        assertEquals(ThemeMode.Dark, settings.app.value.theme)
        assertEquals(mapOf(Category.Work to 0xFF112233), settings.app.value.categoryColors)
        send(vm, SettingsIntent.CategoryColorPicked(Category.Work, null))
        assertEquals(emptyMap(), settings.app.value.categoryColors)
    }

    @Test
    fun anExportIsAFileNamedForTheDay() = runTest {
        val vm = screen()
        vm.effects.test {
            vm.onIntent(SettingsIntent.ExportTapped)
            assertEquals(SettingsEffect.Export("dayblocks-2026-09-21.json", "{backup}"), awaitItem())
        }
    }

    @Test
    fun anImportAsksFirstAndSaysWhatItRestored() = runTest {
        val vm = screen()
        vm.effects.test {
            vm.onIntent(SettingsIntent.ImportTapped)
            assertEquals(SettingsEffect.ChooseImport, awaitItem())
            vm.onIntent(SettingsIntent.ImportChosen("{chosen}"))
            runCurrent()
            assertEquals("{chosen}", vm.state.value.confirmingImport)
            assertNull(backup.imported, "nothing changes before the answer")
            vm.onIntent(SettingsIntent.ImportConfirmed)
            assertEquals(SettingsEffect.Message("Restored 12 blocks and 1 template."), awaitItem())
            assertEquals("{chosen}", backup.imported)
            assertNull(vm.state.value.confirmingImport)
        }
    }

    @Test
    fun aRejectedImportSaysWhyAndCancellingChangesNothing() = runTest {
        backup.answer = ImportResult.Rejected("This is not a DayBlocks backup.")
        val vm = screen()
        vm.effects.test {
            vm.onIntent(SettingsIntent.ImportChosen("nonsense"))
            vm.onIntent(SettingsIntent.ImportConfirmed)
            assertEquals(SettingsEffect.Message("This is not a DayBlocks backup."), awaitItem())
        }
        send(vm, SettingsIntent.ImportChosen("{other}"), SettingsIntent.ImportDismissed)
        assertNull(vm.state.value.confirmingImport)
        assertEquals("nonsense", backup.imported, "the dismissed one was never imported")
    }

    private class RecordingBackup : BackupRepository {
        var imported: String? = null
        var answer: ImportResult = ImportResult.Imported(blocks = 12, templates = 1, records = 3)
        override suspend fun export(): String = "{backup}"
        override suspend fun import(json: String): ImportResult {
            imported = json
            return answer
        }
    }
}

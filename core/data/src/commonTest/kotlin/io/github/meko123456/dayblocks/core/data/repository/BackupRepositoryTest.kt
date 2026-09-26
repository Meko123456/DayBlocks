package io.github.meko123456.dayblocks.core.data.repository

import com.russhwolf.settings.MapSettings
import io.github.meko123456.dayblocks.core.common.FixedTimeProvider
import io.github.meko123456.dayblocks.core.data.block
import io.github.meko123456.dayblocks.core.data.createTestDriver
import io.github.meko123456.dayblocks.core.data.monday
import io.github.meko123456.dayblocks.core.data.testDispatchers
import io.github.meko123456.dayblocks.core.data.tuesday
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode
import io.github.meko123456.dayblocks.core.domain.repository.ImportResult
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek

class BackupRepositoryTest {
    private val clock = FixedTimeProvider(Instant.parse("2026-09-21T06:00:00Z"))
    private val source = Store()
    private val target = Store()

    @AfterTest
    fun closeDrivers() {
        source.driver.close()
        target.driver.close()
    }

    /** A database and a settings store: one phone's worth of DayBlocks. */
    private inner class Store {
        val driver = createTestDriver()
        val database = DayBlocksDatabase(driver)
        val prefs = PreferencesSettingsRepository(MapSettings())
        fun TestScope.blocks() = SqlBlockRepository(database, testDispatchers(), clock)
        fun TestScope.templates() = SqlTemplateRepository(database, testDispatchers())
        fun TestScope.outcomes() = SqlOutcomeRepository(database, testDispatchers())
        fun TestScope.backup() = SqlBackupRepository(database, prefs, testDispatchers(), clock)
    }

    private val work = block("work", "Deep work", DaySpan.of(9, 0, 12, 0), note = "the big one")
    private val run = block("run", "Run", DaySpan.of(7, 0, 8, 0), date = tuesday, category = Category.Exercise)
    private val weekday = Template(TemplateId("weekday"), "Weekday", listOf(TemplateBlock("Deep work", Category.Work, DaySpan.of(9, 0, 12, 0))))

    private suspend fun TestScope.fillSource() = with(source) {
        blocks().upsert(work)
        blocks().upsert(run)
        templates().upsert(weekday)
        templates().assign(DayOfWeek.MONDAY, weekday.id)
        outcomes().recordAnswer(work.id, CheckInAnswer.OnIt, clock.now())
        outcomes().recordOutcome(work.id, BlockOutcome.Done, clock.now())
        prefs.updateBuddy { it.copy(name = "Bloop", tone = BuddyTone.Pushy, dailyCap = 8) }
        prefs.updateApp { it.copy(theme = ThemeMode.Dark, timelineStartHour = 7, categoryColors = mapOf(Category.Work to 0xFF112233)) }
    }

    @Test
    fun aBackupRestoresEverythingOntoAnotherPhone() = runTest {
        fillSource()
        val json = with(source) { backup().export() }

        val result = with(target) { backup().import(json) }

        assertEquals(ImportResult.Imported(blocks = 2, templates = 1, records = 1), result)
        with(target) {
            assertEquals(listOf(work), blocks().observeDay(monday).first())
            assertEquals(listOf(run), blocks().observeDay(tuesday).first())
            assertEquals(listOf(weekday), templates().observeTemplates().first())
            assertEquals(mapOf(DayOfWeek.MONDAY to weekday.id), templates().observeWeekdayAssignments().first())
            assertEquals(
                BlockRecord(work.id, CheckInAnswer.OnIt, BlockOutcome.Done, answeredAt = clock.now()),
                outcomes().observeDay(monday).first()[work.id],
            )
            val buddy = prefs.observeBuddy().first()
            assertEquals("Bloop", buddy.name)
            assertEquals(BuddyTone.Pushy, buddy.tone)
            assertEquals(8, buddy.dailyCap)
            val app = prefs.observeApp().first()
            assertEquals(ThemeMode.Dark, app.theme)
            assertEquals(7, app.timelineStartHour)
            assertEquals(mapOf(Category.Work to 0xFF112233), app.categoryColors)
        }
    }

    @Test
    fun importingReplacesWhatWasThere() = runTest {
        fillSource()
        val json = with(source) { backup().export() }
        with(target) {
            blocks().upsert(block("old", "Old plan", DaySpan.of(10, 0, 11, 0)))
            backup().import(json)
            assertEquals(listOf("work"), blocks().observeDay(monday).first().map { it.id.value })
        }
    }

    @Test
    fun restoringNeverSendsAnyoneBackThroughOnboarding() = runTest {
        fillSource()
        val json = with(source) { backup().export() }
        with(target) {
            prefs.updateApp { it.copy(onboarded = true) }
            backup().import(json)
            assertTrue(prefs.observeApp().first().onboarded)
        }
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() = runTest {
        with(target) {
            blocks().upsert(work)
            // Any JSON object used to decode as an empty backup, and importing it wiped the plan.
            assertIs<ImportResult.Rejected>(backup().import("{ \"hello\": 1 }"))
            assertIs<ImportResult.Rejected>(backup().import("{}"))
            assertIs<ImportResult.Rejected>(backup().import("not json at all"))
            assertEquals(listOf(work), blocks().observeDay(monday).first())
        }
    }

    @Test
    fun aDamagedOrNewerBackupIsRefusedWholeBeforeAnythingChanges() = runTest {
        fillSource()
        val json = with(source) { backup().export() }
        with(target) {
            blocks().upsert(block("keep", "Keep me", DaySpan.of(10, 0, 11, 0)))
            val damaged = json.replace("\"title\": \"Run\"", "\"title\": \"   \"")
            val damagedResult = backup().import(damaged)
            assertIs<ImportResult.Rejected>(damagedResult)
            assertTrue(damagedResult.reason.contains("damaged"), damagedResult.reason)
            val newer = backup().import(json.replace("\"version\": 1", "\"version\": 99"))
            assertIs<ImportResult.Rejected>(newer)
            assertTrue(newer.reason.contains("newer"), newer.reason)
            assertEquals(listOf("keep"), blocks().observeDay(monday).first().map { it.id.value })
        }
    }
}

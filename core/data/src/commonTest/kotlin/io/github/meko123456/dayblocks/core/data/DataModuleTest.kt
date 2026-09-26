package io.github.meko123456.dayblocks.core.data

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import io.github.meko123456.dayblocks.core.common.commonModule
import io.github.meko123456.dayblocks.core.data.repository.PreferencesSettingsRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlBackupRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlBlockRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlOutcomeRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlTemplateRepository
import io.github.meko123456.dayblocks.core.domain.repository.BackupRepository
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class DataModuleTest {
    private val driver = createTestDriver()

    @AfterTest
    fun closeDriver() = driver.close()

    /**
     * Koin resolves lazily, so a binding whose dependencies nobody provides still starts the app
     * and fails on the first screen that asks. Resolving each repository here, against the real
     * commonModule and a database standing in for databaseModule's, makes that a test failure.
     */
    @Test
    fun everyRepositoryResolvesAgainstTheSharedModules() {
        val app = koinApplication {
            // The settings store is replaced by an in-memory one, as the database is by a test driver.
            modules(commonModule, dataModule, module {
                single { DayBlocksDatabase(driver) }
                single<ObservableSettings> { MapSettings() }
            })
        }
        try {
            assertIs<SqlBlockRepository>(app.koin.get<BlockRepository>())
            assertIs<SqlTemplateRepository>(app.koin.get<TemplateRepository>())
            assertIs<SqlOutcomeRepository>(app.koin.get<OutcomeRepository>())
            assertIs<PreferencesSettingsRepository>(app.koin.get<SettingsRepository>())
            assertIs<SqlBackupRepository>(app.koin.get<BackupRepository>())
        } finally {
            app.close()
        }
    }
}

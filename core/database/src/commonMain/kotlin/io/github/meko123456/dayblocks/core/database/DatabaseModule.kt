package io.github.meko123456.dayblocks.core.database

import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import org.koin.dsl.module

/**
 * The database itself is common; only [DriverFactory] differs per platform, and each platform's
 * Koin module supplies that.
 */
val databaseModule = module {
    single { DayBlocksDatabase(get<DriverFactory>().createDriver()) }
}

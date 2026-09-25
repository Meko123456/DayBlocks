package io.github.meko123456.dayblocks.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.github.meko123456.dayblocks.database.DayBlocksDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(DayBlocksDatabase.Schema, "dayblocks.db")
}

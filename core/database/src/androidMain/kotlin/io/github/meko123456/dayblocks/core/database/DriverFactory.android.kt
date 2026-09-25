package io.github.meko123456.dayblocks.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.meko123456.dayblocks.database.DayBlocksDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(DayBlocksDatabase.Schema, context, "dayblocks.db")
}

package io.github.meko123456.dayblocks.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.meko123456.dayblocks.database.DayBlocksDatabase
import java.util.Properties

/**
 * The app's Android driver needs a Context, so the JVM runs the same schema on sqlite-jdbc.
 * foreign_keys is the connection property that does here what ForeignKeysOnOpen does on a device;
 * without it every cascade in the schema would be ignored.
 */
actual fun createTestDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        .also { DayBlocksDatabase.Schema.create(it) }

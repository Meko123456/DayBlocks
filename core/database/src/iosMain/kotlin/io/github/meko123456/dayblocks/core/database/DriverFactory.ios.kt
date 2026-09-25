package io.github.meko123456.dayblocks.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import io.github.meko123456.dayblocks.database.DayBlocksDatabase

/**
 * @param name the database file, or null for a private in-memory database that lives exactly as
 *   long as the driver. The tests pass null, so on iOS they open the database the way the app
 *   does — same schema, same foreign-key setting — and differ only in where the bytes are kept.
 */
actual class DriverFactory(private val name: String? = DATABASE_NAME) {
    actual fun createDriver(): SqlDriver {
        val schema = DayBlocksDatabase.Schema
        return NativeSqliteDriver(
            DatabaseConfiguration(
                name = name,
                inMemory = name == null,
                version = schema.version.toInt(),
                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                upgrade = { connection, oldVersion, newVersion ->
                    wrapConnection(connection) { schema.migrate(it, oldVersion.toLong(), newVersion.toLong()) }
                },
                // SQLiter sets PRAGMA foreign_keys on every connection it opens, from this flag,
                // and the flag defaults to false.
                //
                // Unlike Android — which turns the constraints on in onOpen, after any migration —
                // this applies *before* create and upgrade run. So on iOS a migration executes with
                // foreign keys ON, and the usual way to change a parent table (create new, copy,
                // drop old) would cascade-delete every child row at the drop. The first migration
                // that rebuilds block or template must run `PRAGMA foreign_keys = OFF` itself for
                // its duration. Schema version 1 has no migrations, so nothing is affected yet.
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
            ),
        )
    }
}

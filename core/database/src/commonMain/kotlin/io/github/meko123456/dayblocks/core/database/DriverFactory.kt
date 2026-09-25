package io.github.meko123456.dayblocks.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Creating a driver needs a `Context` on Android and nothing on iOS, so it is the one piece of the
 * storage layer that cannot be common. Everything above it — queries, mappers, repositories —
 * takes the finished [SqlDriver] and is shared.
 *
 * Both actuals turn foreign keys on. SQLite ships with them off, per connection, and the schema's
 * ON DELETE CASCADE clauses are how deleting a template clears its weekdays and deleting a block
 * drops its check-in record; with the constraints off those clauses are accepted and ignored,
 * which fails nothing and leaves the orphans behind.
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

internal const val DATABASE_NAME = "dayblocks.db"

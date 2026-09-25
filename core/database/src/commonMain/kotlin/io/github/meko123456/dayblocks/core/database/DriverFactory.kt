package io.github.meko123456.dayblocks.core.database

import app.cash.sqldelight.db.SqlDriver

/**
 * Creating a driver needs a `Context` on Android and nothing on iOS, so it is the one piece of the
 * storage layer that cannot be common. Everything above it — queries, mappers, repositories —
 * takes the finished [SqlDriver] and is shared.
 */
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

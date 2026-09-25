package io.github.meko123456.dayblocks.core.data

import app.cash.sqldelight.db.SqlDriver
import io.github.meko123456.dayblocks.core.database.DriverFactory

/** The app's own factory, pointed at memory: same schema, same foreign-key setting. */
actual fun createTestDriver(): SqlDriver = DriverFactory(name = null).createDriver()

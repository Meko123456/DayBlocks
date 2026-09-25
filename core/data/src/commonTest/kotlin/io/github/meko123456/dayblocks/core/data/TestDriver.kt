package io.github.meko123456.dayblocks.core.data

import app.cash.sqldelight.db.SqlDriver

/**
 * A fresh, empty in-memory database with the full schema and foreign keys enforced: real SQLite on
 * both platforms, never a fake, because the behaviour under test — cascades, transactions, query
 * notifications — is SQLite's and SQLDelight's, not ours. Close it after the test.
 */
expect fun createTestDriver(): SqlDriver

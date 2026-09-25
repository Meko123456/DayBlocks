package io.github.meko123456.dayblocks.core.database

import app.cash.sqldelight.db.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals

class DriverFactoryTest {
    @Test
    fun theDriverEnforcesForeignKeys() {
        val driver = DriverFactory(name = null).createDriver()
        try {
            val foreignKeys = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA foreign_keys",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0))
                },
                parameters = 0,
            ).value
            assertEquals(1L, foreignKeys)
        } finally {
            driver.close()
        }
    }
}

package io.github.meko123456.dayblocks.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.github.meko123456.dayblocks.database.DayBlocksDatabase

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = DayBlocksDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback = ForeignKeysOnOpen(DayBlocksDatabase.Schema),
    )
}

/**
 * The schema's own create and upgrade, plus foreign keys switched on once the database is open.
 *
 * On open rather than on configure, which is where Android's documentation suggests: onConfigure
 * runs before onCreate and onUpgrade, so the constraints would already be on while a migration
 * runs, and the usual way to change a parent table — create the new one, copy the rows, drop the
 * old — would cascade-delete every child row at the drop. SQLite's own procedure for such changes
 * begins by turning foreign keys off. By onOpen the schema is final, and
 * setForeignKeyConstraintsEnabled reconfigures every connection in the framework's pool, including
 * ones it opens later.
 */
internal class ForeignKeysOnOpen(
    schema: SqlSchema<QueryResult.Value<Unit>>,
) : AndroidSqliteDriver.Callback(schema) {
    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        db.setForeignKeyConstraintsEnabled(true)
    }
}

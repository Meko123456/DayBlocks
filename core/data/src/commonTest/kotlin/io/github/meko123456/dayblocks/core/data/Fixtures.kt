package io.github.meko123456.dayblocks.core.data

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import io.github.meko123456.dayblocks.core.common.AppDispatchers
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.datetime.LocalDate

/** 2026-09-21 is a Monday. */
internal val monday = LocalDate(2026, 9, 21)
internal val tuesday = LocalDate(2026, 9, 22)
internal val wednesday = LocalDate(2026, 9, 23)

internal fun block(
    id: String,
    title: String,
    span: DaySpan,
    date: LocalDate = monday,
    category: Category = Category.Work,
    note: String? = null,
) = TimeBlock(BlockId(id), date, title, category, span, note)

private class TestDispatchers(dispatcher: CoroutineDispatcher) : AppDispatchers {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
}

/** Every dispatcher on the test's own scheduler, so nothing the repositories do escapes runTest. */
internal fun TestScope.testDispatchers(): AppDispatchers = TestDispatchers(StandardTestDispatcher(testScheduler))

/** A single number from [sql], bypassing the repositories — to see what is really in a table. */
internal fun SqlDriver.count(sql: String, vararg args: String): Long =
    executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = args.size,
    ) {
        args.forEachIndexed { index, arg -> bindString(index, arg) }
    }.value

/**
 * What the query returns at each moment its listeners are told it changed, read inside the listener
 * itself. asFlow re-reads later, on its own dispatcher, and conflates the notifications that arrive
 * in between — right for a screen, and exactly what would hide a half-written state from a test.
 */
internal fun <T : Any> Query<T>.everyNotifiedState(): Flow<List<T>> = callbackFlow {
    val listener = Query.Listener { trySend(executeAsList()) }
    addListener(listener)
    awaitClose { removeListener(listener) }
}

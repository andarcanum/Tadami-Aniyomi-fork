package tachiyomi.data.handlers.novel

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import tachiyomi.novel.data.NovelDatabase

class AndroidNovelDatabaseHandler(
    val db: NovelDatabase,
    private val driver: SqlDriver,
    val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val transactionDispatcher: CoroutineDispatcher = queryDispatcher,
) : NovelDatabaseHandler {

    val suspendingTransactionId = ThreadLocal<Int>()

    override suspend fun <T> await(inTransaction: Boolean, block: suspend NovelDatabase.() -> T): T {
        return dispatch(inTransaction, block)
    }

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend NovelDatabase.() -> Query<T>,
    ): List<T> {
        return dispatch(inTransaction) { block(db).executeAsList() }
    }

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend NovelDatabase.() -> Query<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend NovelDatabase.() -> ExecutableQuery<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend NovelDatabase.() -> Query<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend NovelDatabase.() -> ExecutableQuery<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override fun <T : Any> subscribeToList(block: NovelDatabase.() -> Query<T>): Flow<List<T>> {
        return block(db).asFlow().mapToList(queryDispatcher)
    }

    override fun <T : Any> subscribeToOne(block: NovelDatabase.() -> Query<T>): Flow<T> {
        return block(db).asFlow().mapToOne(queryDispatcher)
    }

    override fun <T : Any> subscribeToOneOrNull(block: NovelDatabase.() -> Query<T>): Flow<T?> {
        return block(db).asFlow().mapToOneOrNull(queryDispatcher)
    }

    override fun <T : Any> subscribeToPagingSource(
        countQuery: NovelDatabase.() -> Query<Long>,
        queryProvider: NovelDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingNovelSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = { limit, offset ->
                queryProvider.invoke(db, limit, offset)
            },
        )
    }

    private suspend fun <T> dispatch(inTransaction: Boolean, block: suspend NovelDatabase.() -> T): T {
        if (inTransaction) {
            return withNovelTransaction { block(db) }
        }

        if (driver.currentTransaction() != null) {
            return block(db)
        }

        val context = getCurrentNovelDatabaseContext()
        return withContext(context) { block(db) }
    }
}

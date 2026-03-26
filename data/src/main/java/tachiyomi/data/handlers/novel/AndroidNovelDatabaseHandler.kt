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
import tachiyomi.novel.data.NovelDatabase as NovelDb

class AndroidNovelDatabaseHandler(
    val db: NovelDb,
    private val driver: SqlDriver,
    val queryDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val transactionDispatcher: CoroutineDispatcher = queryDispatcher,
) : NovelDatabaseHandler {

    val suspendingTransactionId = ThreadLocal<Int>()

    override suspend fun <T> await(inTransaction: Boolean, block: suspend (NovelDb) -> T): T {
        return dispatch(inTransaction, block)
    }

    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend (NovelDb) -> Query<T>,
    ): List<T> {
        return dispatch(inTransaction) { block(db).executeAsList() }
    }

    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend (NovelDb) -> Query<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend (NovelDb) -> ExecutableQuery<T>,
    ): T {
        return dispatch(inTransaction) { block(db).executeAsOne() }
    }

    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend (NovelDb) -> Query<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend (NovelDb) -> ExecutableQuery<T>,
    ): T? {
        return dispatch(inTransaction) { block(db).executeAsOneOrNull() }
    }

    override fun <T : Any> subscribeToList(block: (NovelDb) -> Query<T>): Flow<List<T>> {
        return block(db).asFlow().mapToList(queryDispatcher)
    }

    override fun <T : Any> subscribeToOne(block: (NovelDb) -> Query<T>): Flow<T> {
        return block(db).asFlow().mapToOne(queryDispatcher)
    }

    override fun <T : Any> subscribeToOneOrNull(block: (NovelDb) -> Query<T>): Flow<T?> {
        return block(db).asFlow().mapToOneOrNull(queryDispatcher)
    }

    override fun <T : Any> subscribeToPagingSource(
        countQuery: (NovelDb) -> Query<Long>,
        queryProvider: (NovelDb, Long, Long) -> Query<T>,
    ): PagingSource<Long, T> {
        return QueryPagingNovelSource(
            handler = this,
            countQuery = countQuery,
            queryProvider = queryProvider,
        )
    }

    private suspend fun <T> dispatch(inTransaction: Boolean, block: suspend (NovelDb) -> T): T {
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


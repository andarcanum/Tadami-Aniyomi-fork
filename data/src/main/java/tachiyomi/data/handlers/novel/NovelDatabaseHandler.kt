package tachiyomi.data.handlers.novel

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.Flow
import tachiyomi.novel.data.NovelDatabase as NovelDb

interface NovelDatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend (NovelDb) -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend (NovelDb) -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend (NovelDb) -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend (NovelDb) -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend (NovelDb) -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend (NovelDb) -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: (NovelDb) -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: (NovelDb) -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: (NovelDb) -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: (NovelDb) -> Query<Long>,
        queryProvider: (NovelDb, Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}

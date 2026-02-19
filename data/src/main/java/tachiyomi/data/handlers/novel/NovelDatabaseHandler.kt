package tachiyomi.data.handlers.novel

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.Flow
import tachiyomi.novel.data.NovelDatabase

interface NovelDatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend NovelDatabase.() -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend NovelDatabase.() -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend NovelDatabase.() -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend NovelDatabase.() -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend NovelDatabase.() -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend NovelDatabase.() -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: NovelDatabase.() -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: NovelDatabase.() -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: NovelDatabase.() -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: NovelDatabase.() -> Query<Long>,
        queryProvider: NovelDatabase.(Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}

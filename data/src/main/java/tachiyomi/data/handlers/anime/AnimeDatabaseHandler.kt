package tachiyomi.data.handlers.anime

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.Flow
import tachiyomi.mi.data.AnimeDatabase as AnimeDb

interface AnimeDatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend (AnimeDb) -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend (AnimeDb) -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend (AnimeDb) -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend (AnimeDb) -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend (AnimeDb) -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend (AnimeDb) -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: (AnimeDb) -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: (AnimeDb) -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: (AnimeDb) -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: (AnimeDb) -> Query<Long>,
        queryProvider: (AnimeDb, Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}

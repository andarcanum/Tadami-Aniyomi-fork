package tachiyomi.data.handlers.manga

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database as MangaDb

interface MangaDatabaseHandler {

    suspend fun <T> await(inTransaction: Boolean = false, block: suspend (MangaDb) -> T): T

    suspend fun <T : Any> awaitList(
        inTransaction: Boolean = false,
        block: suspend (MangaDb) -> Query<T>,
    ): List<T>

    suspend fun <T : Any> awaitOne(
        inTransaction: Boolean = false,
        block: suspend (MangaDb) -> Query<T>,
    ): T

    suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean = false,
        block: suspend (MangaDb) -> ExecutableQuery<T>,
    ): T

    suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean = false,
        block: suspend (MangaDb) -> Query<T>,
    ): T?

    suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean = false,
        block: suspend (MangaDb) -> ExecutableQuery<T>,
    ): T?

    fun <T : Any> subscribeToList(block: (MangaDb) -> Query<T>): Flow<List<T>>

    fun <T : Any> subscribeToOne(block: (MangaDb) -> Query<T>): Flow<T>

    fun <T : Any> subscribeToOneOrNull(block: (MangaDb) -> Query<T>): Flow<T?>

    fun <T : Any> subscribeToPagingSource(
        countQuery: (MangaDb) -> Query<Long>,
        queryProvider: (MangaDb, Long, Long) -> Query<T>,
    ): PagingSource<Long, T>
}

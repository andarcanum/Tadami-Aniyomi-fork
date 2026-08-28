package tachiyomi.data.reels.anime

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dataanime.Reels_favorites
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsFavorite
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

class ReelsFavoriteRepositoryImplTest {

    private fun buildRepository(): Pair<ReelsFavoriteRepositoryImpl, JdbcSqliteDriver> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AnimeDatabase.Schema.create(driver)
        val db = AnimeDatabase(
            driver = driver,
            episodesAdapter = Episodes.Adapter(memoAdapter = MemoColumnAdapter),
            animehistoryAdapter = Animehistory.Adapter(last_seenAdapter = DateColumnAdapter),
            animesAdapter = Animes.Adapter(
                memoAdapter = MemoColumnAdapter,
                genreAdapter = StringListColumnAdapter,
                custom_genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = AnimeUpdateStrategyColumnAdapter,
                fetch_typeAdapter = FetchTypeColumnAdapter,
            ),
            reels_favoritesAdapter = Reels_favorites.Adapter(added_atAdapter = DateColumnAdapter),
        )
        // Real IO dispatchers (the handler defaults): SQLDelight transactions deadlock on a
        // single-threaded virtual-time dispatcher because the transaction blocks the only
        // driven thread waiting for a nested dispatch.
        val handler = AndroidAnimeDatabaseHandler(db, driver)
        return ReelsFavoriteRepositoryImpl(handler) to driver
    }

    private fun favorite(videoId: String, sourceId: Long, addedAt: Date = Date(1000L)) = ReelsFavorite(
        videoId = videoId,
        sourceId = sourceId,
        title = "Title $videoId",
        author = "Author",
        videoUrlHd = "https://example.com/$videoId.mp4",
        videoUrlSd = null,
        posterUrl = "https://example.com/$videoId.jpg",
        posterUrlVertical = null,
        webUrl = "https://example.com/watch/$videoId",
        durationSec = 12.0,
        hasAudio = true,
        addedAt = addedAt,
    )

    @Test
    fun `insert, getBySource and delete roundtrip`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insert(favorite("vid-1", 101L))
        repo.insert(favorite("vid-2", 101L, addedAt = Date(2000L)))
        repo.insert(favorite("vid-3", 202L))

        repo.getBySource(101L).map { it.videoId } shouldBe listOf("vid-2", "vid-1")
        repo.getIdsBySource(202L) shouldBe listOf("vid-3")
        repo.getAll() shouldHaveSize 3

        repo.delete("vid-1", 101L)
        repo.getBySource(101L).map { it.videoId } shouldBe listOf("vid-2")

        driver.close()
    }

    @Test
    fun `insertAll persists all rows in one call`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insertAll(
            listOf(
                favorite("vid-1", 101L),
                favorite("vid-2", 101L),
                favorite("vid-3", 202L),
            ),
        )

        repo.getAll() shouldHaveSize 3
        repo.getBySource(101L) shouldHaveSize 2

        driver.close()
    }

    @Test
    fun `sqlite failures are swallowed and not propagated to the caller`() = runBlocking {
        val repo = ReelsFavoriteRepositoryImpl(ThrowingHandler(android.database.sqlite.SQLiteException("disk full")))

        // Like taps are fire-and-forget: DB failures are logged, not surfaced to the caller.
        repo.insert(favorite("vid-1", 101L))
        repo.insertAll(listOf(favorite("vid-2", 101L)))
        repo.delete("vid-1", 101L)
    }

    @Test
    fun `cancellation is rethrown and not swallowed`() = runBlocking<Unit> {
        val repo = ReelsFavoriteRepositoryImpl(ThrowingHandler(CancellationException("scope cancelled")))

        org.junit.jupiter.api.assertThrows<CancellationException> {
            repo.insert(favorite("vid-1", 101L))
        }
        org.junit.jupiter.api.assertThrows<CancellationException> {
            repo.delete("vid-1", 101L)
        }
    }

    @Test
    fun `schema has an index on source_id for per-source favorite lookups`() {
        val (_, driver) = buildRepository()

        val names = indexNames(driver, "reels_favorites")
        // sqlite_autoindex_reels_favorites_1 also exists for the composite PK.
        names shouldContainAll listOf("reels_favorites_added_at_index", "reels_favorites_source_id_index")
    }

    private fun indexNames(driver: JdbcSqliteDriver, tableName: String): List<String> {
        return driver.executeQuery(
            identifier = null,
            sql = "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$tableName' ORDER BY name",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) {
                            add(cursor.getString(0).orEmpty())
                        }
                    },
                )
            },
            parameters = 0,
        ).value
    }
}

private class ThrowingHandler(private val error: Exception) : AnimeDatabaseHandler {
    override suspend fun <T> await(inTransaction: Boolean, block: suspend (AnimeDatabase) -> T): T = throw error
    override suspend fun <T : Any> awaitList(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): List<T> = throw error
    override suspend fun <T : Any> awaitOne(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): T = throw error
    override suspend fun <T : Any> awaitOneExecutable(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> ExecutableQuery<T>,
    ): T = throw error
    override suspend fun <T : Any> awaitOneOrNull(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> Query<T>,
    ): T? = throw error
    override suspend fun <T : Any> awaitOneOrNullExecutable(
        inTransaction: Boolean,
        block: suspend (AnimeDatabase) -> ExecutableQuery<T>,
    ): T? = throw error
    override fun <T : Any> subscribeToList(block: (AnimeDatabase) -> Query<T>): Flow<List<T>> = flowOf(emptyList())
    override fun <T : Any> subscribeToOne(block: (AnimeDatabase) -> Query<T>): Flow<T> = flow { throw error }
    override fun <T : Any> subscribeToOneOrNull(block: (AnimeDatabase) -> Query<T>): Flow<T?> = flowOf(null)
    override fun <T : Any> subscribeToPagingSource(
        countQuery: (AnimeDatabase) -> Query<Long>,
        queryProvider: (AnimeDatabase, Long, Long) -> Query<T>,
    ): PagingSource<Long, T> = throw error
}

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
import dataanime.Reels_follows
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
import tachiyomi.domain.reels.anime.model.ReelsFollow
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

/**
 * Mirrors [ReelsFavoriteRepositoryImplTest] for the contract-v18 creator follows table.
 * The 100-follows-per-source cap is a host-side rule, so this repository must happily
 * store any number of rows.
 */
class ReelsFollowRepositoryImplTest {

    private fun buildRepository(): Pair<ReelsFollowRepositoryImpl, JdbcSqliteDriver> {
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
            reels_followsAdapter = Reels_follows.Adapter(added_atAdapter = DateColumnAdapter),
        )
        // Real IO dispatchers (the handler defaults): SQLDelight transactions deadlock on a
        // single-threaded virtual-time dispatcher because the transaction blocks the only
        // driven thread waiting for a nested dispatch.
        val handler = AndroidAnimeDatabaseHandler(db, driver)
        return ReelsFollowRepositoryImpl(handler) to driver
    }

    private fun follow(sourceId: Long, creator: String, addedAt: Date = Date(1000L)) = ReelsFollow(
        sourceId = sourceId,
        creator = creator,
        addedAt = addedAt,
    )

    @Test
    fun `insert, getBySource, getCreatorsBySource and delete roundtrip`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insert(follow(101L, "alice"))
        repo.insert(follow(101L, "bob", addedAt = Date(2000L)))
        repo.insert(follow(202L, "carol"))

        // added_at DESC
        repo.getBySource(101L).map { it.creator } shouldBe listOf("bob", "alice")
        repo.getCreatorsBySource(202L) shouldBe listOf("carol")
        repo.getAll().map { it.creator }.sorted() shouldBe listOf("alice", "bob", "carol")

        repo.delete(101L, "alice")
        repo.getBySource(101L).map { it.creator } shouldBe listOf("bob")
        // Deletes are keyed per source: carol on the other source is untouched.
        repo.getBySource(202L) shouldHaveSize 1

        driver.close()
    }

    @Test
    fun `re-insert of the same creator replaces the row, PK keeps one entry`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insert(follow(101L, "alice", addedAt = Date(1000L)))
        repo.insert(follow(101L, "alice", addedAt = Date(5000L)))

        repo.getBySource(101L) shouldHaveSize 1
        repo.getBySource(101L).single().addedAt shouldBe Date(5000L)

        driver.close()
    }

    @Test
    fun `sqlite failures are swallowed and not propagated to the caller`() = runBlocking {
        val repo =
            ReelsFollowRepositoryImpl(ThrowingFollowHandler(android.database.sqlite.SQLiteException("disk full")))

        // Follow taps are fire-and-forget: DB failures are logged, not surfaced to the caller.
        repo.insert(follow(101L, "alice"))
        repo.delete(101L, "alice")
    }

    @Test
    fun `cancellation is rethrown and not swallowed`() = runBlocking<Unit> {
        val repo = ReelsFollowRepositoryImpl(ThrowingFollowHandler(CancellationException("scope cancelled")))

        org.junit.jupiter.api.assertThrows<CancellationException> {
            repo.insert(follow(101L, "alice"))
        }
        org.junit.jupiter.api.assertThrows<CancellationException> {
            repo.delete(101L, "alice")
        }
    }

    @Test
    fun `schema has an index on source_id for per-source follow lookups`() {
        val (_, driver) = buildRepository()

        val names = indexNames(driver, "reels_follows")
        // sqlite_autoindex_reels_follows_1 also exists for the composite PK.
        names shouldContainAll listOf("reels_follows_source_id_index")
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

private class ThrowingFollowHandler(private val error: Exception) : AnimeDatabaseHandler {
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

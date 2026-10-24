package tachiyomi.data.reels.anime

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dataanime.Animehistory
import dataanime.Animes
import dataanime.Episodes
import dataanime.Reels_album_watched
import dataanime.Reels_albums
import dataanime.Reels_candidate
import dataanime.Reels_consumption
import dataanime.Reels_favorites
import dataanime.Reels_follows
import dataanime.Reels_hidden
import dataanime.Reels_watch_history
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import tachiyomi.domain.reels.anime.model.ReelsHiddenEntry
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

class ReelsHiddenRepositoryImplTest {

    private fun buildRepository(): Pair<ReelsHiddenRepositoryImpl, JdbcSqliteDriver> {
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
            reels_watch_historyAdapter = Reels_watch_history.Adapter(watched_atAdapter = DateColumnAdapter),
            reels_hiddenAdapter = Reels_hidden.Adapter(hidden_atAdapter = DateColumnAdapter),
            reels_albumsAdapter = Reels_albums.Adapter(added_atAdapter = DateColumnAdapter),
            reels_album_watchedAdapter = Reels_album_watched.Adapter(marked_atAdapter = DateColumnAdapter),
            reels_consumptionAdapter = Reels_consumption.Adapter(
                watched_qualified_atAdapter = DateColumnAdapter,
                skipped_forward_atAdapter = DateColumnAdapter,
                last_action_atAdapter = DateColumnAdapter,
            ),
            reels_candidateAdapter = Reels_candidate.Adapter(
                discovered_atAdapter = DateColumnAdapter,
                last_validated_atAdapter = DateColumnAdapter,
                retry_afterAdapter = DateColumnAdapter,
            ),
        )
        val handler = AndroidAnimeDatabaseHandler(db, driver)
        return ReelsHiddenRepositoryImpl(handler) to driver
    }

    private fun entry(
        value: String,
        kind: String = ReelsHiddenEntry.KIND_VIDEO,
        sourceId: Long = 101L,
        label: String? = null,
    ) = ReelsHiddenEntry(
        sourceId = sourceId,
        kind = kind,
        value = value,
        hiddenAt = Date(1000L),
        label = label,
    )

    @Test
    fun `insert, getBySource, subscribeAll and delete roundtrip with label`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insert(entry("vid-1", label = "Some reel title"))
        repo.insert(entry("alice", kind = ReelsHiddenEntry.KIND_AUTHOR))
        repo.insert(entry("vid-9", sourceId = 202L))

        repo.getBySource(101L) shouldHaveSize 2
        val video = repo.getBySource(101L).first { it.kind == ReelsHiddenEntry.KIND_VIDEO }
        video.value shouldBe "vid-1"
        video.label shouldBe "Some reel title"
        repo.subscribeAll().first() shouldHaveSize 3

        // Re-insert of the same key replaces the row (PK dedupe), label updates.
        repo.insert(entry("vid-1", label = "Renamed"))
        repo.getBySource(101L).first { it.value == "vid-1" }.label shouldBe "Renamed"

        repo.delete(101L, ReelsHiddenEntry.KIND_VIDEO, "vid-1")
        repo.getBySource(101L) shouldHaveSize 1

        driver.close()
    }

    @Test
    fun `deleteBySource removes one source only and deleteAll clears everything`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.insert(entry("vid-1"))
        repo.insert(entry("vid-2", sourceId = 202L))
        repo.insert(entry("bob", kind = ReelsHiddenEntry.KIND_AUTHOR, sourceId = 202L))

        repo.deleteBySource(202L)
        repo.getBySource(202L) shouldHaveSize 0
        repo.getBySource(101L) shouldHaveSize 1

        repo.deleteAll()
        repo.subscribeAll().first() shouldHaveSize 0

        driver.close()
    }

    @Test
    fun `cancellation is rethrown and not swallowed`() = runBlocking<Unit> {
        val repo = ReelsHiddenRepositoryImpl(ThrowingHiddenHandler(CancellationException("scope cancelled")))

        org.junit.jupiter.api.assertThrows<CancellationException> {
            repo.insert(entry("vid-1"))
        }
    }
}

private class ThrowingHiddenHandler(private val error: Exception) : AnimeDatabaseHandler {
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

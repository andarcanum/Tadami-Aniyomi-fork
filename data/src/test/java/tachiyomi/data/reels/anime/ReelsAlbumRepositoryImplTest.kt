package tachiyomi.data.reels.anime

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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

class ReelsAlbumRepositoryImplTest {

    private fun buildRepository(): Pair<ReelsAlbumRepositoryImpl, JdbcSqliteDriver> {
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
        return ReelsAlbumRepositoryImpl(handler) to driver
    }

    private fun album(albumId: String, sourceId: Long = 401L, name: String = "Album $albumId") = ReelsAlbum(
        sourceId = sourceId,
        albumId = albumId,
        name = name,
        coverUrl = "https://example.com/$albumId.jpg",
        addedAt = Date(1_700_000_000_000L),
    )

    @Test
    fun `albums round trip per source with newest first`() = runBlocking {
        val (repo, driver) = buildRepository()
        try {
            repo.insert(album("a1"))
            repo.insert(album("a2", name = "Renamed a2"))
            repo.insert(album("b1", sourceId = 402L))

            repo.getBySource(401L).map { it.albumId } shouldContainExactly listOf("a2", "a1")
            repo.subscribeBySource(402L).first().map { it.albumId } shouldContainExactly listOf("b1")
            // INSERT OR REPLACE: re-adding keeps one row with the fresh name.
            repo.getBySource(401L).first { it.albumId == "a2" }.name shouldBe "Renamed a2"
        } finally {
            driver.close()
        }
    }

    @Test
    fun `watched marks round trip and unmark removes`() = runBlocking {
        val (repo, driver) = buildRepository()
        try {
            repo.markWatched(401L, "v1")
            repo.markWatched(401L, "v2")
            repo.markWatched(402L, "v1")

            repo.subscribeWatched(401L).first() shouldContainExactly listOf("v1", "v2")
            repo.unmarkWatched(401L, "v1")
            repo.subscribeWatched(401L).first() shouldContainExactly listOf("v2")
        } finally {
            driver.close()
        }
    }

    @Test
    fun `deleteBySource cascades albums and watched marks of one source only`() = runBlocking {
        val (repo, driver) = buildRepository()
        try {
            repo.insert(album("a1"))
            repo.insert(album("b1", sourceId = 402L))
            repo.markWatched(401L, "v1")
            repo.markWatched(402L, "v9")

            repo.deleteBySource(401L)

            repo.getBySource(401L) shouldBe emptyList()
            repo.subscribeWatched(401L).first() shouldBe emptySet()
            // The other source survives untouched.
            repo.getBySource(402L).map { it.albumId } shouldContainExactly listOf("b1")
            repo.subscribeWatched(402L).first() shouldContainExactly listOf("v9")
        } finally {
            driver.close()
        }
    }

    @Test
    fun `delete album keeps watched marks of the source`() = runBlocking {
        val (repo, driver) = buildRepository()
        try {
            repo.insert(album("a1"))
            repo.markWatched(401L, "v1")

            repo.delete(401L, "a1")

            repo.getBySource(401L) shouldBe emptyList()
            // Marks are keyed by (source, video) without an album ref: they survive so a
            // re-added album restores its watched state.
            repo.subscribeWatched(401L).first() shouldContainExactly listOf("v1")
        } finally {
            driver.close()
        }
    }
}

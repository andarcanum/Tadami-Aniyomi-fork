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
import io.kotest.matchers.collections.shouldHaveSize
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
import tachiyomi.domain.reels.anime.model.ReelsWatchEntry
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

class ReelsWatchRepositoryImplTest {

    private fun buildRepository(): Pair<ReelsWatchRepositoryImpl, JdbcSqliteDriver> {
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
        return ReelsWatchRepositoryImpl(handler) to driver
    }

    private fun entry(videoId: String, positionMs: Long = 1500L, watchedAt: Long = 1000L) = ReelsWatchEntry(
        videoId = videoId,
        sourceId = 101L,
        title = "Title $videoId",
        author = "Author",
        posterUrl = null,
        webUrl = null,
        videoUrl = "https://example.com/$videoId.mp4",
        durationSec = 30.0,
        positionMs = positionMs,
        watchedAt = Date(watchedAt),
    )

    @Test
    fun `upsert replaces by pk and getByVideo reads it back`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.upsert(entry("vid-1", positionMs = 1000L, watchedAt = 100L))
        repo.upsert(entry("vid-1", positionMs = 9000L, watchedAt = 200L))

        val read = repo.getByVideo("vid-1", 101L)
        read?.positionMs shouldBe 9000L
        repo.subscribeAll().first() shouldHaveSize 1

        repo.delete("vid-1", 101L)
        repo.getByVideo("vid-1", 101L) shouldBe null

        driver.close()
    }

    @Test
    fun `upsert prunes the table to the 500 most recent entries`() = runBlocking {
        val (repo, driver) = buildRepository()

        // 505 distinct clips with strictly increasing watchedAt: the cap must keep the
        // NEWEST 500 and drop the 5 oldest (audit: history must not grow without bound).
        repeat(505) { index ->
            repo.upsert(entry("vid-$index", watchedAt = index.toLong()))
        }

        val all = repo.subscribeAll().first()
        all shouldHaveSize 500
        all.map { it.videoId } shouldHaveSize 500
        all.any { it.videoId == "vid-0" } shouldBe false
        all.any { it.videoId == "vid-4" } shouldBe false
        all.any { it.videoId == "vid-5" } shouldBe true
        all.any { it.videoId == "vid-504" } shouldBe true

        driver.close()
    }

    @Test
    fun `deleteAll clears the history`() = runBlocking {
        val (repo, driver) = buildRepository()

        repo.upsert(entry("vid-1"))
        repo.upsert(entry("vid-2"))
        repo.deleteAll()

        repo.subscribeAll().first() shouldHaveSize 0

        driver.close()
    }
}

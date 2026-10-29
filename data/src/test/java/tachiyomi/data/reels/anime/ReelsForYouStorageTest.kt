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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.mi.data.AnimeDatabase
import java.util.Date

/**
 * Durable For You storage: the summary must survive restarts, keep watch and skip as separate
 * facts, and fence writers that outlived a history reset. The inbox must keep unseen
 * candidates without ever resurrecting a consumed one.
 */
class ReelsForYouStorageTest {

    private class Fixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val handler: AndroidAnimeDatabaseHandler

        init {
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
            handler = AndroidAnimeDatabaseHandler(db, driver)
        }

        fun close() = driver.close()
    }

    private fun candidate(itemId: String, discoveredAt: Long = 1_000L): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = SCOPE,
            sourceId = SOURCE,
            itemId = itemId,
            title = "Title $itemId",
            author = "@channel",
            videoUrl = "https://cdn.example/$itemId.mp4",
            videoUrlHd = null,
            posterUrl = null,
            posterUrlVertical = null,
            webUrl = "https://t.me/channel/$itemId",
            durationSec = 12.0,
            hasAudio = true,
            createdAtEpochSec = 900L,
            discoveredAt = Date(discoveredAt),
            lastValidatedAt = Date(discoveredAt),
            availability = ReelsCandidate.AVAILABILITY_AVAILABLE,
            retryAfter = null,
            resumePositionMs = null,
            metadataSchemaVersion = 1,
        )
    }

    @Test
    fun `watch is idempotent and keeps the first qualification time`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val generation = repo.ensureScope(SCOPE, 1)

        repo.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, generation) shouldBe true
        repo.markWatched(SCOPE, SOURCE, "a", Date(9_000), "foryou", 1, generation) shouldBe true

        val entry = repo.entry(SCOPE, SOURCE, "a")
        entry?.watchedQualifiedAt shouldBe Date(1_000)
        entry?.lastActionAt shouldBe Date(9_000)
        repo.countConsumed(SCOPE, SOURCE) shouldBe 1L

        fixture.close()
    }

    @Test
    fun `a later skip never erases an earlier watch`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val generation = repo.ensureScope(SCOPE, 1)

        repo.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, generation)
        repo.markSkipped(SCOPE, SOURCE, "a", Date(2_000), "foryou", 1, generation)

        val entry = repo.entry(SCOPE, SOURCE, "a")
        entry?.watchedQualifiedAt shouldBe Date(1_000)
        entry?.skippedForwardAt shouldBe Date(2_000)
        entry?.isConsumed shouldBe true

        fixture.close()
    }

    @Test
    fun `consumedIds answers a whole page in one batch`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val generation = repo.ensureScope(SCOPE, 1)

        repo.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, generation)
        repo.markSkipped(SCOPE, SOURCE, "b", Date(1_000), "foryou", 1, generation)
        repo.markWatched(SCOPE, OTHER_SOURCE, "c", Date(1_000), "foryou", 1, generation)

        val consumed = repo.consumedIds(SCOPE, SOURCE, listOf("a", "b", "c", "d"))
        consumed shouldContain "a"
        consumed shouldContain "b"
        consumed shouldNotContain "c"
        consumed shouldNotContain "d"
        consumed.size shouldBe 2

        fixture.close()
    }

    @Test
    fun `a writer from before a history reset is fenced`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val before = repo.ensureScope(SCOPE, 1)
        repo.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, before)

        val after = repo.resetHistory(SCOPE, 1)
        (after > before) shouldBe true

        // The in-flight writer still carries the old generation: it must NOT resurrect the row.
        repo.markWatched(SCOPE, SOURCE, "a", Date(2_000), "foryou", 1, before) shouldBe false
        repo.entry(SCOPE, SOURCE, "a") shouldBe null

        // A writer that picked up the new generation works again.
        repo.markWatched(SCOPE, SOURCE, "a", Date(3_000), "foryou", 1, after) shouldBe true
        repo.entry(SCOPE, SOURCE, "a")?.watchedQualifiedAt shouldBe Date(3_000)

        fixture.close()
    }

    @Test
    fun `reset keeps other accounts untouched`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val mine = repo.ensureScope(SCOPE, 1)
        val other = repo.ensureScope("src:999", 1)

        repo.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, mine)
        repo.markWatched("src:999", SOURCE, "a", Date(1_000), "foryou", 1, other)
        repo.resetHistory(SCOPE, 1)

        repo.entry(SCOPE, SOURCE, "a") shouldBe null
        (repo.entry("src:999", SOURCE, "a") != null) shouldBe true

        fixture.close()
    }

    @Test
    fun `the inbox hides consumed items without deleting them`() = runBlocking {
        val fixture = Fixture()
        val candidates = ReelsCandidateRepositoryImpl(fixture.handler)
        val consumption = ReelsConsumptionRepositoryImpl(fixture.handler)
        val generation = consumption.ensureScope(SCOPE, 1)

        candidates.ingest(SCOPE, SOURCE, listOf(candidate("a"), candidate("b"))) shouldBe 2
        candidates.unconsumed(SCOPE, SOURCE, 10).map { it.itemId } shouldBe listOf("a", "b")

        consumption.markWatched(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, generation)

        candidates.unconsumed(SCOPE, SOURCE, 10).map { it.itemId } shouldBe listOf("b")

        fixture.close()
    }

    @Test
    fun `re-ingest refreshes metadata but keeps discovery time and resume position`() = runBlocking {
        val fixture = Fixture()
        val candidates = ReelsCandidateRepositoryImpl(fixture.handler)

        candidates.ingest(SCOPE, SOURCE, listOf(candidate("a", discoveredAt = 1_000L)))
        candidates.saveResumePosition(SCOPE, SOURCE, "a", 4_200L)
        candidates.ingest(
            SCOPE,
            SOURCE,
            listOf(candidate("a", discoveredAt = 9_000L).copy(title = "Fresh title")),
        )

        val row = candidates.unconsumed(SCOPE, SOURCE, 10).single()
        row.title shouldBe "Fresh title"
        row.discoveredAt shouldBe Date(1_000)
        row.resumePositionMs shouldBe 4_200L
        candidates.count(SCOPE, SOURCE) shouldBe 1L

        fixture.close()
    }

    @Test
    fun `a consumed candidate is removed from the inbox but stays in the summary`() = runBlocking {
        val fixture = Fixture()
        val candidates = ReelsCandidateRepositoryImpl(fixture.handler)
        val consumption = ReelsConsumptionRepositoryImpl(fixture.handler)
        val generation = consumption.ensureScope(SCOPE, 1)

        candidates.ingest(SCOPE, SOURCE, listOf(candidate("a")))
        consumption.markSkipped(SCOPE, SOURCE, "a", Date(1_000), "foryou", 1, generation)
        candidates.remove(SCOPE, SOURCE, listOf("a"))

        candidates.unconsumed(SCOPE, SOURCE, 10).shouldBeEmpty()
        consumption.entry(SCOPE, SOURCE, "a")?.isConsumed shouldBe true

        fixture.close()
    }

    @Test
    fun `fallback-scope history moves into the account scope and the copy wins nothing`() = runBlocking {
        val fixture = Fixture()
        val repo = ReelsConsumptionRepositoryImpl(fixture.handler)
        val fallback = "src:$SOURCE"
        val generation = repo.ensureScope(fallback, 1)

        repo.markWatched(fallback, SOURCE, "a", Date(1_000), "foryou", 1, generation)
        repo.markSkipped(fallback, SOURCE, "b", Date(2_000), "foryou", 1, generation)
        // Same item already known under the real scope: the copy must not overwrite it.
        repo.markWatched(SCOPE, SOURCE, "b", Date(500), "foryou", 1, generation)

        repo.mergeScope(fallback, SCOPE, SOURCE)

        repo.entry(SCOPE, SOURCE, "a")?.watchedQualifiedAt shouldBe Date(1_000)
        repo.entry(SCOPE, SOURCE, "b")?.watchedQualifiedAt shouldBe Date(500)
        repo.entry(SCOPE, SOURCE, "b")?.skippedForwardAt shouldBe null
        repo.entry(fallback, SOURCE, "a") shouldBe null
        repo.countConsumed(fallback, SOURCE) shouldBe 0L

        // A second run is a no-op instead of a duplicate or a resurrection.
        repo.mergeScope(fallback, SCOPE, SOURCE)
        repo.countConsumed(SCOPE, SOURCE) shouldBe 2L

        fixture.close()
    }

    private companion object {
        const val SCOPE = "acc:101:tg-42"
        const val SOURCE = 101L
        const val OTHER_SOURCE = 202L
    }
}

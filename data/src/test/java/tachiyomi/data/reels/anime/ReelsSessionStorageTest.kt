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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.domain.reels.anime.model.ReelsActiveCheckpoint
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.domain.reels.anime.model.ReelsSessionHandle
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.mi.data.AnimeDatabase
import java.io.File
import java.util.Date

class ReelsSessionStorageTest {

    @TempDir
    lateinit var tempDir: File

    private fun createDb(
        dbFile: File,
        foreignKeys: Boolean = true,
    ): Pair<JdbcSqliteDriver, AndroidAnimeDatabaseHandler> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (foreignKeys) {
            driver.execute(null, "PRAGMA foreign_keys = ON;", 0)
        } else {
            driver.execute(null, "PRAGMA foreign_keys = OFF;", 0)
        }
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
        return driver to AndroidAnimeDatabaseHandler(db, driver)
    }

    private fun candidate(
        scope: String,
        sourceId: Long,
        itemId: String,
        positionMs: Long? = null,
        availability: String = ReelsCandidate.AVAILABILITY_AVAILABLE,
        author: String = "@author",
    ): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = scope,
            sourceId = sourceId,
            itemId = itemId,
            title = "Title $itemId",
            author = author,
            videoUrl = "https://cdn.example/$itemId.mp4",
            videoUrlHd = null,
            posterUrl = null,
            posterUrlVertical = null,
            webUrl = "https://t.me/author/$itemId",
            durationSec = 60.0,
            hasAudio = true,
            createdAtEpochSec = 1700000000L,
            discoveredAt = Date(1000L),
            lastValidatedAt = Date(1000L),
            availability = availability,
            retryAfter = null,
            resumePositionMs = positionMs,
            metadataSchemaVersion = 2,
        )
    }

    @Test
    fun `order 1 to 80 survives reopen and raw ordinal advances across consumed`() = runBlocking {
        val dbFile = File(tempDir, "session_test_1.db")
        val (driver1, handler1) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver1)
        val repo1 = ReelsSessionRepositoryImpl(handler1)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle1 = repo1.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        val candidates80 = (1..80).map { i ->
            candidate("tg_123", 8529104719284712L, "clip_$i")
        }
        repo1.appendPage(handle1, candidates80) shouldBe 80

        // Mark clip_1 as qualified watched
        handler1.await { db ->
            db.reels_consumptionQueries.markWatched(
                account_scope_id = "tg_123",
                source_id = 8529104719284712L,
                item_id = "clip_1",
                watched_qualified_at = Date(2000L),
                last_action_at = Date(2000L),
                last_surface = "feed",
                policy_version = 1L,
                history_generation = 1L,
            )
        }

        // Close driver 1
        driver1.close()

        // Reopen with driver 2
        val (driver2, handler2) = createDb(dbFile)
        val repo2 = ReelsSessionRepositoryImpl(handler2)
        val handle2 = repo2.open(key, ownerNonce = "nonce_2", historyGeneration = 1L, reset = false)
        handle2.sessionId shouldBe handle1.sessionId

        // Window 1: 40 items. Since clip_1 was consumed, entries will have 39 items, but nextOrdinal should be 40!
        val window1 = repo2.window(handle2, afterOrdinal = 0L, limit = 40)
        window1.nextOrdinal shouldBe 40L
        window1.entries.size shouldBe 39
        window1.entries.first().candidate.itemId shouldBe "clip_2"
        window1.hasMore shouldBe true

        // Window 2: items 41..80
        val window2 = repo2.window(handle2, afterOrdinal = 40L, limit = 40)
        window2.nextOrdinal shouldBe 80L
        window2.entries.size shouldBe 40
        window2.entries.first().candidate.itemId shouldBe "clip_41"
        window2.hasMore shouldBe true

        driver2.close()
    }

    @Test
    fun `qualified A removed from candidate inbox but active checkpoint survives`() = runBlocking {
        val dbFile = File(tempDir, "session_test_2.db")
        val (driver1, handler1) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver1)
        val repo1 = ReelsSessionRepositoryImpl(handler1)
        val candidateRepo1 = ReelsCandidateRepositoryImpl(handler1)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle1 = repo1.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        val itemA = candidate("tg_123", 8529104719284712L, "clip_A")
        repo1.appendPage(handle1, listOf(itemA))

        // Save active checkpoint for A @ 37200 ms
        val checkpoint = ReelsActiveCheckpoint(
            item = itemA,
            positionMs = 37200L,
            completed = false,
            sequence = 1L,
        )
        repo1.checkpoint(handle1, checkpoint) shouldBe true

        // Simulate qualified watch removing A from candidate inbox
        candidateRepo1.remove("tg_123", 8529104719284712L, listOf("clip_A"))

        // Reopen DB to verify independence of active checkpoint
        driver1.close()

        val (driver2, handler2) = createDb(dbFile)
        val repo2 = ReelsSessionRepositoryImpl(handler2)
        val handle2 = repo2.open(key, ownerNonce = "nonce_2", historyGeneration = 1L, reset = false)

        val active = repo2.active(handle2)
        active shouldNotBe null
        active!!.item.itemId shouldBe "clip_A"
        active.positionMs shouldBe 37200L
        active.completed shouldBe false

        driver2.close()
    }

    @Test
    fun `a watched checkpoint does not lead the restored feed`() = runBlocking {
        val dbFile = File(tempDir, "session_test_watched_checkpoint.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        val itemA = candidate("tg_123", 8529104719284712L, "clip_A")
        repo.appendPage(handle, listOf(itemA))
        repo.checkpoint(
            handle,
            ReelsActiveCheckpoint(item = itemA, positionMs = 6_000L, completed = false, sequence = 1L),
        ) shouldBe true

        // Left the feed mid-clip AFTER the watch threshold: a qualified watch, no skip.
        handler.await { db ->
            db.reels_consumptionQueries.markWatched(
                account_scope_id = "tg_123",
                source_id = 8529104719284712L,
                item_id = "clip_A",
                watched_qualified_at = Date(2_000L),
                last_action_at = Date(2_000L),
                last_surface = "foryou",
                policy_version = 1L,
                history_generation = 1L,
            )
        }

        // Nothing left to resume: the checkpoint must not resurrect the reel as card #1.
        repo.active(handle) shouldBe null

        driver.close()
    }

    @Test
    fun `skipped or completed or hidden active item is excluded`() = runBlocking {
        val dbFile = File(tempDir, "session_test_3.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        val itemA = candidate("tg_123", 8529104719284712L, "clip_A")
        repo.appendPage(handle, listOf(itemA))

        // Checkpoint marked completed -> active returns null
        repo.checkpoint(
            handle,
            ReelsActiveCheckpoint(item = itemA, positionMs = 60000L, completed = true, sequence = 1L),
        ) shouldBe true
        repo.active(handle) shouldBe null

        // Checkpoint uncompleted, but consumption recorded forward skip -> active returns null
        repo.checkpoint(
            handle,
            ReelsActiveCheckpoint(item = itemA, positionMs = 20000L, completed = false, sequence = 2L),
        ) shouldBe true
        handler.await { db ->
            db.reels_consumptionQueries.markSkipped(
                account_scope_id = "tg_123",
                source_id = 8529104719284712L,
                item_id = "clip_A",
                skipped_forward_at = Date(3000L),
                last_action_at = Date(3000L),
                last_surface = "feed",
                policy_version = 1L,
                history_generation = 1L,
            )
        }
        repo.active(handle) shouldBe null

        driver.close()
    }

    @Test
    fun `duplicate checkpoint sequence is not acknowledged as written`(): Unit = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "duplicate-checkpoint.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)
        val key = ReelsSessionKey("acc:901:12", 901L, "context")
        val handle = repo.open(key, "owner", 0L, reset = false)
        val item = candidate(key.accountScopeId, key.sourceId, "current")
        try {
            repo.checkpoint(handle, ReelsActiveCheckpoint(item, 1_000L, false, 1L)) shouldBe true
            repo.checkpoint(handle, ReelsActiveCheckpoint(item, 9_000L, false, 1L)) shouldBe false
            repo.active(handle)?.positionMs shouldBe 1_000L
        } finally {
            driver.close()
        }
    }

    @Test
    fun `checkpoint sequence 9 cannot overwrite sequence 10`() = runBlocking {
        val dbFile = File(tempDir, "session_test_seq.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        val itemA = candidate("tg_123", 8529104719284712L, "clip_A")
        repo.appendPage(handle, listOf(itemA))

        repo.checkpoint(
            handle,
            ReelsActiveCheckpoint(item = itemA, positionMs = 10000L, completed = false, sequence = 10L),
        ) shouldBe true

        // Sequence 9 write fails
        repo.checkpoint(
            handle,
            ReelsActiveCheckpoint(item = itemA, positionMs = 9000L, completed = false, sequence = 9L),
        ) shouldBe false

        val active = repo.active(handle)
        active!!.positionMs shouldBe 10000L
        active.sequence shouldBe 10L

        driver.close()
    }

    @Test
    fun `late invalidation cannot revoke the replacement owner`(): Unit = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "late-invalidation.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)
        val key = ReelsSessionKey("acc:901:12", 901L, "context")
        try {
            val old = repo.open(key, "old", 0L, reset = false)
            val replacement = repo.open(key, "new", 0L, reset = false)
            repo.invalidate(old, dropCheckpoint = true)
            repo.appendPage(replacement, listOf(candidate(key.accountScopeId, key.sourceId, "new"))) shouldBe 1
        } finally {
            driver.close()
        }
    }

    @Test
    fun `reopened owner starts fresh sequence without losing saved position`(): Unit = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "reopened-sequence.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)
        val key = ReelsSessionKey("acc:901:12", 901L, "context")
        val old = repo.open(key, "old", 0L, reset = false)
        val item = candidate(key.accountScopeId, key.sourceId, "current")
        try {
            repo.checkpoint(old, ReelsActiveCheckpoint(item, 10_000L, false, 10L)) shouldBe true
            val reopened = repo.open(key, "new", 0L, reset = false)
            repo.active(reopened)?.positionMs shouldBe 10_000L
            repo.checkpoint(reopened, ReelsActiveCheckpoint(item, 12_000L, false, 1L)) shouldBe true
            repo.active(reopened)?.positionMs shouldBe 12_000L
        } finally {
            driver.close()
        }
    }

    @Test
    fun `revoked owner cannot append or mutate availability`(): Unit = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "revoked-owner.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)
        val key = ReelsSessionKey("acc:901:12", 901L, "context")
        val handle = repo.open(key, "owner", 0L, reset = false)
        val item = candidate(key.accountScopeId, key.sourceId, "queued")
        try {
            repo.appendPage(handle, listOf(item))
            repo.invalidate(handle, dropCheckpoint = false)
            assertThrows<ReelsSessionStaleException> {
                repo.appendPage(handle, listOf(candidate(key.accountScopeId, key.sourceId, "late")))
            }
            assertThrows<ReelsSessionStaleException> {
                repo.setAvailability(handle, "queued", "unavailable", null)
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun `stale owner cannot write checkpoint or append page`() = runBlocking {
        val dbFile = File(tempDir, "session_test_stale.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle1 = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        // New owner opens session
        val handle2 = repo.open(key, ownerNonce = "nonce_2", historyGeneration = 1L, reset = false)

        val itemA = candidate("tg_123", 8529104719284712L, "clip_A")
        // Stale handle1 checkpoint write fails
        repo.checkpoint(
            handle1,
            ReelsActiveCheckpoint(item = itemA, positionMs = 5000L, completed = false, sequence = 1L),
        ) shouldBe false

        // Stale handle1 appendPage throws ReelsSessionStaleException
        assertThrows<ReelsSessionStaleException> {
            repo.appendPage(handle1, listOf(itemA))
        }

        driver.close()
    }

    @Test
    fun `capacity boundary 1999 plus 2 throws and no partial insert`() = runBlocking {
        val dbFile = File(tempDir, "session_test_cap.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)

        val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
        val handle = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

        // Fill to 1999 candidates
        val batch1 = (1..1999).map { i -> candidate("tg_123", 8529104719284712L, "clip_$i") }
        repo.appendPage(handle, batch1) shouldBe 1999

        // Try adding 2 new candidates -> 1999 + 2 = 2001 > 2000
        val batch2 = listOf(
            candidate("tg_123", 8529104719284712L, "new_clip_1"),
            candidate("tg_123", 8529104719284712L, "new_clip_2"),
        )
        val ex = assertThrows<ReelsInboxCapacityException> {
            repo.appendPage(handle, batch2)
        }
        ex.required shouldBe 2L
        ex.available shouldBe 1L

        // Verify neither candidate was partially inserted
        handler.awaitOne { db ->
            db.reels_candidateQueries.countForSource("tg_123", 8529104719284712L)
        } shouldBe 1999L

        driver.close()
    }

    @Test
    fun `append drops consumed and hidden candidates inside the transaction`(): Unit = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "append-eligibility.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsSessionRepositoryImpl(handler)
        val key = ReelsSessionKey(accountScopeId = "acc:901:12", sourceId = 901L, contextKey = "context")
        val handle = repo.open(key, ownerNonce = "owner", historyGeneration = 0L, reset = false)
        val scope = key.accountScopeId
        val sourceId = key.sourceId
        try {
            handler.await { db ->
                db.reels_consumptionQueries.markWatched(
                    account_scope_id = scope,
                    source_id = sourceId,
                    item_id = "watched",
                    watched_qualified_at = Date(1000L),
                    last_action_at = Date(1000L),
                    last_surface = "feed",
                    policy_version = 1L,
                    history_generation = 0L,
                )
                db.reels_consumptionQueries.markSkipped(
                    account_scope_id = scope,
                    source_id = sourceId,
                    item_id = "skipped",
                    skipped_forward_at = Date(1000L),
                    last_action_at = Date(1000L),
                    last_surface = "feed",
                    policy_version = 1L,
                    history_generation = 0L,
                )
                db.reels_hiddenQueries.insert(sourceId, "item", "hidden_item", null, Date(1000L))
                db.reels_hiddenQueries.insert(sourceId, "author", "@blocked", "Blocked", Date(1000L))
            }

            repo.appendPage(
                handle,
                listOf(
                    candidate(scope, sourceId, "keep"),
                    candidate(scope, sourceId, "watched"),
                    candidate(scope, sourceId, "skipped"),
                    candidate(scope, sourceId, "hidden_item"),
                    candidate(scope, sourceId, "blocked_author", author = "@blocked"),
                ),
            ) shouldBe 1

            val window = repo.window(handle, afterOrdinal = 0L, limit = 40)
            window.entries.map { it.candidate.itemId } shouldBe listOf("keep")
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(scope, sourceId) } shouldBe 1L
        } finally {
            driver.close()
        }
    }

    @Test
    fun `foreign keys ON and OFF both leave no session orphans on clearScope`() = runBlocking {
        for (fk in listOf(true, false)) {
            val dbFile = File(tempDir, "session_test_fk_$fk.db")
            val (driver, handler) = createDb(dbFile, foreignKeys = fk)
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsSessionRepositoryImpl(handler)

            val key = ReelsSessionKey(accountScopeId = "tg_123", sourceId = 8529104719284712L, contextKey = "ctx_v1")
            val handle = repo.open(key, ownerNonce = "nonce_1", historyGeneration = 1L, reset = false)

            repo.appendPage(handle, (1..10).map { candidate("tg_123", 8529104719284712L, "clip_$it") })

            repo.clearScope("tg_123")

            // Verify both tables are empty for this scope
            val sessionRows = handler.awaitList { db ->
                db.reels_sessionQueries.getSession("tg_123", 8529104719284712L)
            }
            sessionRows.isEmpty() shouldBe true

            val queueRows = handler.awaitList { db ->
                db.reels_session_queueQueries.rawQueueWindow("tg_123", 8529104719284712L, handle.sessionId, 0L, 100L)
            }
            queueRows.isEmpty() shouldBe true

            driver.close()
        }
    }
}

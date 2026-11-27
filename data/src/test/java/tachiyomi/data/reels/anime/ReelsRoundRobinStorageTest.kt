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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.handlers.anime.AndroidAnimeDatabaseHandler
import tachiyomi.domain.reels.anime.interactor.ReelsRoundRobinScheduler
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinFrame
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinHandle
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinIdentity
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSkip
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.domain.reels.anime.model.ReelsSessionStaleException
import tachiyomi.mi.data.AnimeDatabase
import java.io.File
import java.util.Date

class ReelsRoundRobinStorageTest {

    @TempDir
    lateinit var tempDir: File

    private fun createDb(
        dbFile: File,
        foreignKeys: Boolean = true,
    ): Pair<JdbcSqliteDriver, AndroidAnimeDatabaseHandler> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        driver.execute(null, "PRAGMA foreign_keys = ${if (foreignKeys) "ON" else "OFF"};", 0)
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

    private fun candidate(itemId: String, author: String = "@channel"): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = SCOPE,
            sourceId = SOURCE,
            itemId = itemId,
            title = "Title $itemId",
            author = author,
            videoUrl = "https://cdn.example/$itemId.mp4",
            videoUrlHd = null,
            posterUrl = null,
            posterUrlVertical = null,
            webUrl = "https://t.me/channel/$itemId",
            durationSec = 30.0,
            hasAudio = true,
            createdAtEpochSec = 1_700_000_000L,
            discoveredAt = Date(1_000L),
            lastValidatedAt = Date(1_000L),
            availability = ReelsCandidate.AVAILABILITY_AVAILABLE,
            retryAfter = null,
            resumePositionMs = null,
            metadataSchemaVersion = 2,
        )
    }

    private fun source(key: String, previousKeys: List<String> = emptyList()): ReelsRoundRobinCatalogSource {
        return ReelsRoundRobinCatalogSource(
            key = key,
            title = "chat $key",
            author = null,
            contentRevision = null,
            excluded = false,
            previousKeys = previousKeys,
        )
    }

    private fun catalog(
        revision: String,
        complete: Boolean,
        sources: List<ReelsRoundRobinCatalogSource>,
    ): ReelsRoundRobinCatalog {
        return ReelsRoundRobinCatalog(revision = revision, complete = complete, sources = sources)
    }

    private fun entry(
        sourceKey: String,
        itemId: String,
        orderKey: Long,
        validation: ReelsRoundRobinValidation = ReelsRoundRobinValidation.ELIGIBLE,
        contentKey: String? = null,
    ): ReelsRoundRobinEntry {
        return ReelsRoundRobinEntry(
            candidate = candidate(itemId),
            sourceKey = sourceKey,
            orderKey = orderKey,
            contentKey = contentKey,
            checkedContext = null,
            validation = validation,
            round = null,
            ordinal = null,
        )
    }

    private fun page(
        sourceKey: String,
        fromCheckpoint: String?,
        nextCheckpoint: String,
        entries: List<ReelsRoundRobinEntry>,
        historyExhausted: Boolean = false,
        nextHeadCheckAt: Long? = null,
    ): ReelsRoundRobinUpdate.Page {
        return ReelsRoundRobinUpdate.Page(
            sourceKey = sourceKey,
            fromCheckpoint = fromCheckpoint,
            nextCheckpoint = nextCheckpoint,
            entries = entries,
            historyExhausted = historyExhausted,
            nextHeadCheckAt = nextHeadCheckAt,
        )
    }

    private fun key(contextKey: String = "ctx_v1") = ReelsSessionKey(SCOPE, SOURCE, contextKey)

    private suspend fun markConsumed(handler: AndroidAnimeDatabaseHandler, itemId: String) {
        handler.await { db ->
            db.reels_consumptionQueries.markWatched(
                account_scope_id = SCOPE,
                source_id = SOURCE,
                item_id = itemId,
                watched_qualified_at = Date(2_000L),
                last_action_at = Date(2_000L),
                last_surface = "foryou",
                policy_version = 1L,
                history_generation = 1L,
            )
        }
    }

    @Test
    fun `pump commits candidate checkpoint and slot atomically and survives reopen`() = runBlocking {
        val dbFile = File(tempDir, "rr-pump.db")
        val (driver1, handler1) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver1)
        val repo = ReelsRoundRobinRepositoryImpl(handler1)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)

        val opened = repo.mutate(
            handle,
            expectedRevision = 0L,
            change = ReelsRoundRobinMutation.Catalog(
                value = catalog("cat-1", complete = true, sources = listOf(source("a"), source("b"))),
                nowMs = 1_000L,
            ),
        )
        opened.snapshot.revision shouldBe 1L

        val pumped = repo.mutate(
            handle,
            expectedRevision = opened.snapshot.revision,
            change = ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    page("a", null, "cp-a", listOf(entry("a", "a1", 20), entry("a", "a2", 10))),
                ),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        pumped.page.map { it.itemId } shouldContainExactly listOf("a1")

        val snapshot = repo.read(handle)
        snapshot.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a"
        val reserved = snapshot.entries.first { it.candidate.itemId == "a1" }
        reserved.round shouldBe 0L
        reserved.ordinal shouldBe 0L
        snapshot.entries.first { it.candidate.itemId == "a2" }.round shouldBe null
        handler1.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 2L

        driver1.close()

        val (driver2, handler2) = createDb(dbFile)
        val repo2 = ReelsRoundRobinRepositoryImpl(handler2)
        val handle2 = repo2.open(key(), "owner-2", 1L, unseenOnly = true)
        val restored = repo2.read(handle2)
        restored.revision shouldBe snapshot.revision
        restored.frames shouldBe snapshot.frames
        restored.anchor shouldBe snapshot.anchor
        restored.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a"
        restored.entries.map { it.candidate.itemId } shouldContain "a1"
        driver2.close()
    }

    @Test
    fun `capacity overflow rolls back checkpoint and slots`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-capacity.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)

        handler.await(inTransaction = true) { db ->
            ReelsCandidateWriter.writeCandidates(
                db = db,
                scope = SCOPE,
                sourceId = SOURCE,
                candidates = (1..1999).map { candidate("filled_$it") },
            )
        }

        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                value = catalog("cat-cap", complete = true, sources = listOf(source("a"))),
                nowMs = 1_000L,
            ),
        )

        assertThrows<tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException> {
            repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page("a", null, "cp-full", listOf(entry("a", "new_1", 20), entry("a", "new_2", 10))),
                    ),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
        }

        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1999L
        handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
        val snapshot = repo.read(handle)
        snapshot.revision shouldBe catalogCommit.snapshot.revision
        snapshot.sources.first { it.key == "a" }.checkpoint shouldBe null
        snapshot.frames.flatMap { it.turns.values }.shouldBeEmpty()
        driver.close()
    }

    @Test
    fun `stale owner and stale global revision are fenced without writes`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-fences.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)

        val owner1 = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val owner2 = repo.open(key(), "owner-2", 1L, unseenOnly = true)
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                owner1,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-owner", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
        }

        val commit = repo.mutate(
            owner2,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-owner", complete = true, sources = listOf(source("a"))),
                1_000L,
            ),
        )
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(owner2, 0L, ReelsRoundRobinMutation.Activate("unknown", 2_000L))
        }
        repo.read(owner2).revision shouldBe commit.snapshot.revision
        driver.close()
    }

    @Test
    fun `independent source responses may rebase but a stale source checkpoint cannot be overwritten`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-from-checkpoint.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-order", complete = true, sources = listOf(source("a"), source("b"))),
                1_000L,
            ),
        )

        val first = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a1", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        first.snapshot.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a1"

        // Same source, old checkpoint: must not advance or create a second ordinal.
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                handle,
                first.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a2", listOf(entry("a", "a2", 10)))),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
        }
        val unchanged = repo.read(handle)
        unchanged.revision shouldBe first.snapshot.revision
        unchanged.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a1"
        unchanged.entries.first { it.candidate.itemId == "a1" }.ordinal shouldBe 0L

        // A different source response arriving with an older global revision is rebased on the
        // matching fromCheckpoint instead of being lost.
        val rebased = repo.mutate(
            handle,
            first.snapshot.revision - 1,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("b", null, "cp-b1", listOf(entry("b", "b1", 20)))),
                limit = 12,
                nowMs = 4_000L,
            ),
        )
        rebased.snapshot.sources.first { it.key == "b" }.checkpoint shouldBe "cp-b1"
        rebased.snapshot.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a1"
        driver.close()
    }

    @Test
    fun `non page updates are revision fenced while matching page responses rebase`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-stale-nonpage.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-nonpage", complete = true, sources = listOf(source("a"), source("b"))),
                1_000L,
            ),
        )
        val pumped = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )

        // Deferred/Unavailable mutate source state: a stale caller revision cannot rebase.
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                handle,
                pumped.snapshot.revision - 1,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(ReelsRoundRobinUpdate.Deferred("b", "retry", 9_000L)),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
        }
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                handle,
                pumped.snapshot.revision - 1,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(ReelsRoundRobinUpdate.Unavailable("b")),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
        }
        val unchanged = repo.read(handle)
        unchanged.revision shouldBe pumped.snapshot.revision
        unchanged.sources.first { it.key == "b" }.retryAt shouldBe null
        unchanged.sources.first { it.key == "b" }.present shouldBe true

        // The same non-page update with the live revision is accepted.
        val deferred = repo.mutate(
            handle,
            pumped.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(ReelsRoundRobinUpdate.Deferred("b", "retry", 9_000L)),
                limit = 12,
                nowMs = 3_000L,
            ),
        )
        deferred.snapshot.sources.first { it.key == "b" }.retryAt shouldBe 9_000L

        // A mixed pump is not page-only and stays revision-fenced.
        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                handle,
                deferred.snapshot.revision - 1,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page("b", null, "cp-b", listOf(entry("b", "b1", 20))),
                        ReelsRoundRobinUpdate.Deferred("a", "retry", 9_000L),
                    ),
                    limit = 12,
                    nowMs = 4_000L,
                ),
            )
        }
        repo.read(handle).sources.first { it.key == "b" }.checkpoint shouldBe null

        // A page-only response with a matching fromCheckpoint still rebases on a stale revision.
        val rebased = repo.mutate(
            handle,
            deferred.snapshot.revision - 1,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("b", null, "cp-b", listOf(entry("b", "b1", 20)))),
                limit = 12,
                nowMs = 4_000L,
            ),
        )
        rebased.snapshot.revision shouldBe deferred.snapshot.revision + 1
        rebased.snapshot.sources.first { it.key == "b" }.checkpoint shouldBe "cp-b"
        driver.close()
    }

    @Test
    fun `two independent source responses out of order keep one slot per source`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-out-of-order.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-ooo", complete = true, sources = listOf(source("a"), source("b"))),
                1_000L,
            ),
        )

        // B answers first; A arrives afterwards carrying the old global revision.
        val afterB = repo.mutate(
            handle,
            1L,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("b", null, "cp-b", listOf(entry("b", "b1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        val afterA = repo.mutate(
            handle,
            1L,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 3_000L,
            ),
        )
        afterA.snapshot.revision shouldBe afterB.snapshot.revision + 1

        val snapshot = repo.read(handle)
        snapshot.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a"
        snapshot.sources.first { it.key == "b" }.checkpoint shouldBe "cp-b"
        val frame = snapshot.frames.first()
        frame.members shouldContainExactly listOf("a", "b")
        frame.turns["a"]?.itemId shouldBe "a1"
        frame.turns["b"]?.itemId shouldBe "b1"
        snapshot.entries.single { it.candidate.itemId == "a1" }.ordinal shouldBe 0L
        snapshot.entries.single { it.candidate.itemId == "b1" }.ordinal shouldBe 1L

        val queue = handler.awaitList { db ->
            db.reels_session_queueQueries.rawQueueWindow(SCOPE, SOURCE, handle.session.sessionId, 0L, 100L)
        }
        queue.map { it.item_id } shouldContainExactly listOf("a1", "b1")
        queue.map { it.ordinal }.distinct().size shouldBe queue.size
        driver.close()
    }

    @Test
    fun `repeat pump does not create a second ordinal`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-repeat.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-repeat", complete = true, sources = listOf(source("a"))),
                1_000L,
            ),
        )
        val first = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        val revision = first.snapshot.revision

        assertThrows<ReelsSessionStaleException> {
            repo.mutate(
                handle,
                revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
        }
        val snapshot = repo.read(handle)
        snapshot.revision shouldBe revision
        snapshot.entries.single { it.candidate.itemId == "a1" }.ordinal shouldBe 0L
        val queue = handler.awaitList { db ->
            db.reels_session_queueQueries.rawQueueWindow(SCOPE, SOURCE, handle.session.sessionId, 0L, 100L)
        }
        queue.count { it.item_id == "a1" } shouldBe 1
        driver.close()
    }

    @Test
    fun `show watched protection keeps a future slot for prune and direct remove until release`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-protect-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val candidates = ReelsCandidateRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-protect", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
            repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            markConsumed(handler, "a1")

            candidates.remove(SCOPE, SOURCE, listOf("a1"))
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L

            handler.await(inTransaction = true) { db ->
                db.reels_candidateQueries.pruneConsumedRemnants(SCOPE, SOURCE)
            }
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L

            repo.release(handle)
            candidates.remove(SCOPE, SOURCE, listOf("a1"))
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            driver.close()
        }
    }

    @Test
    fun `prune protection lasts only while the RR owner is active`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-prune-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-prune", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
            repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            markConsumed(handler, "a1")

            handler.await(inTransaction = true) { db ->
                ReelsCandidateWriter.writeCandidates(db, SCOPE, SOURCE, listOf(candidate("b")))
            }
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 2L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L

            repo.release(handle)
            handler.await(inTransaction = true) { db ->
                ReelsCandidateWriter.writeCandidates(db, SCOPE, SOURCE, listOf(candidate("c")))
            }
            val remaining = handler.awaitList { db ->
                db.reels_candidateQueries.getByItems(SCOPE, SOURCE, listOf("a1", "b", "c"))
            }.map { it.item_id }
            remaining.toSet() shouldBe setOf("b", "c")
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            driver.close()
        }
    }

    @Test
    fun `pump after a released consumed payload drops it instead of reinserting an orphan`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-pruned-pump-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-pruned", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
            val first = repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page("a", null, "cp-a1", listOf(entry("a", "a1", 30), entry("a", "a2", 20))),
                    ),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            first.page.map { it.itemId } shouldContainExactly listOf("a1", "a2")

            markConsumed(handler, "a1")
            // Activating the later turn releases both the anchor and a1's reservation.
            val activated = repo.mutate(
                handle,
                first.snapshot.revision,
                ReelsRoundRobinMutation.Activate("a2", 3_000L),
            )
            val released = handler.awaitList { db -> db.reels_rr_itemQueries.all(SCOPE, SOURCE) }
                .first { it.item_id == "a1" }
            released.reserved_round shouldBe null

            // The pump writes a fresh candidate; the writer prunes the consumed a1 payload first.
            // The projection must neither re-insert its association nor return it as a card.
            val pumped = repo.mutate(
                handle,
                activated.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", "cp-a1", "cp-a2", listOf(entry("a", "a3", 40)))),
                    limit = 12,
                    nowMs = 4_000L,
                ),
            )

            pumped.snapshot.revision shouldBe activated.snapshot.revision + 1
            pumped.snapshot.sources.first { it.key == "a" }.checkpoint shouldBe "cp-a2"
            pumped.page.map { it.itemId } shouldContainExactly listOf("a3")
            pumped.snapshot.entries.map { it.candidate.itemId } shouldContainExactly listOf("a2", "a3")
            pumped.snapshot.frames
                .flatMap { it.turns.values }
                .mapNotNull { it.itemId } shouldContainExactly listOf("a2", "a3")
            pumped.snapshot.unmappedItemIds.shouldBeEmpty()

            val payloads = handler.awaitList { db ->
                db.reels_candidateQueries.getByItems(SCOPE, SOURCE, listOf("a1", "a2", "a3"))
            }.map { it.item_id }
            payloads.toSet() shouldBe setOf("a2", "a3")

            val rrIds = handler.awaitList { db -> db.reels_rr_itemQueries.all(SCOPE, SOURCE) }
                .map { it.item_id }
            rrIds shouldContainExactly listOf("a3", "a2")
            handler.awaitList { db -> db.reels_rr_itemQueries.unmappedIds(SCOPE, SOURCE) }.shouldBeEmpty()

            val reopened = repo.read(handle)
            reopened.entries.map { it.candidate.itemId } shouldContainExactly listOf("a3", "a2")
            reopened.frames.flatMap { it.turns.values }.mapNotNull { it.itemId } shouldContainExactly listOf("a2", "a3")
            driver.close()
        }
    }

    @Test
    fun `a legacy claim with a different context retains no prune protection`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-legacy-context-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-legacy", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
            repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            markConsumed(handler, "a1")

            // The active round-robin owner context keeps the payload alive.
            handler.await(inTransaction = true) { db ->
                db.reels_candidateQueries.pruneConsumedRemnants(SCOPE, SOURCE)
            }
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L

            // A legacy mode claims its own context key; it must not inherit RR protection.
            ReelsSessionRepositoryImpl(handler).open(
                key("legacy_v27"),
                "legacy-owner",
                1L,
                reset = false,
            )
            handler.await(inTransaction = true) { db ->
                ReelsCandidateWriter.writeCandidates(db, SCOPE, SOURCE, listOf(candidate("b")))
            }
            val payloads = handler.awaitList { db ->
                db.reels_candidateQueries.getByItems(SCOPE, SOURCE, listOf("a1", "b"))
            }.map { it.item_id }
            payloads shouldContain "b"
            payloads.contains("a1") shouldBe false
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            driver.close()
        }
    }

    @Test
    fun `unseen consumption excludes a reserved item and reports the missing payload`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-unseen-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val candidates = ReelsCandidateRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-unseen", complete = true, sources = listOf(source("a"))),
                    1_000L,
                ),
            )
            repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            markConsumed(handler, "a1")

            candidates.remove(SCOPE, SOURCE, listOf("a1"))
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L

            val snapshot = repo.read(handle)
            snapshot.entries.shouldBeEmpty()
            snapshot.unmappedItemIds shouldContain "a1"
            driver.close()
        }
    }

    @Test
    fun `recheck clears future skips of a rewound source and keeps the played prefix`() = runBlocking {
        val dbFile = File(tempDir, "rr-recheck-skips.db")
        val (driver, handler) = createDb(dbFile)
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog(
                    "cat-skips",
                    complete = true,
                    sources = listOf(source("a"), source("b")),
                ),
                1_000L,
            ),
        )
        val pumped = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    page(
                        sourceKey = "a",
                        fromCheckpoint = null,
                        nextCheckpoint = "cp-a",
                        entries = listOf(
                            entry("a", "a1", 200),
                            entry("a", "a2", 100, validation = ReelsRoundRobinValidation.FILTERED),
                        ),
                    ),
                    page(
                        sourceKey = "b",
                        fromCheckpoint = null,
                        nextCheckpoint = "cp-b",
                        entries = listOf(entry("b", "b1", 200)),
                        historyExhausted = true,
                    ),
                ),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        pumped.page.map { it.itemId } shouldContainExactly listOf("a1", "b1")

        val activated = repo.mutate(
            handle,
            pumped.snapshot.revision,
            ReelsRoundRobinMutation.Activate("a1", 3_000L),
        )
        val skipped = repo.mutate(
            handle,
            activated.snapshot.revision,
            ReelsRoundRobinMutation.Pump(emptyList(), 12, 4_000L),
        )
        skipped.stop shouldBe ReelsRoundRobinStop.RESERVATION_HORIZON
        skipped.page.map { it.itemId } shouldContainExactly listOf("b1")
        val active = skipped.snapshot.frames.first { it.number == 0L }
        active.members shouldContainExactly listOf("a", "b")
        active.turns["a"] shouldBe ReelsRoundRobinTurn("a1", null)
        active.turns["b"] shouldBe ReelsRoundRobinTurn("b1", null)
        val prepared = skipped.snapshot.frames.first { it.number == 1L }
        prepared.turns["a"] shouldBe ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.FILTER_BUDGET)
        prepared.turns["b"] shouldBe ReelsRoundRobinTurn(null, ReelsRoundRobinSkip.EMPTY)
        val anchor = skipped.snapshot.anchor
        anchor?.itemId shouldBe "a1"

        val rewound = repo.mutate(
            handle,
            skipped.snapshot.revision,
            ReelsRoundRobinMutation.Recheck(setOf("a", "b"), 5_000L),
        )

        rewound.snapshot.anchor shouldBe anchor
        // Future skips are cleared, the played prefix keeps its turn, and the re-decided tail
        // is released for the next pump.
        rewound.snapshot.frames.map { it.number } shouldContainExactly listOf(0L, 1L)
        rewound.snapshot.frames.first { it.number == 0L }.turns shouldBe
            mapOf("a" to ReelsRoundRobinTurn("a1", null))
        rewound.snapshot.frames.first { it.number == 1L }.turns.shouldBeEmpty()
        rewound.snapshot.entries
            .first { it.candidate.itemId == "a1" }
            .validation shouldBe ReelsRoundRobinValidation.UNKNOWN
        rewound.snapshot.entries
            .first { it.candidate.itemId == "b1" }
            .validation shouldBe ReelsRoundRobinValidation.UNKNOWN
        driver.close()
    }

    @Test
    fun `recontext starts at the next round with rotated members and no old prefix`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-recontext.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key("ctx_v1"), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-ctx", complete = true, sources = listOf(source("a"), source("b"))),
                1_000L,
            ),
        )
        repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )

        val framesJson = Json.encodeToString(
            listOf(
                ReelsRoundRobinFrame(
                    number = 5L,
                    members = listOf("a", "b"),
                    turns = mapOf(
                        "a" to ReelsRoundRobinTurn("a1", null),
                        "b" to ReelsRoundRobinTurn("b1", null),
                    ),
                ),
            ),
        )
        val escaped = framesJson.replace("'", "''")
        driver.execute(
            null,
            """
            UPDATE reels_rr_state
            SET frames_json = '$escaped',
                anchor_round = 5,
                anchor_source_key = 'a',
                anchor_item_id = 'a1'
            WHERE account_scope_id = '$SCOPE' AND source_id = $SOURCE
            """.trimIndent(),
            0,
        )

        val reopened = repo.open(key("ctx_v2"), "owner-2", 1L, unseenOnly = false)
        val recontext = repo.read(reopened)
        recontext.key.contextKey shouldBe "ctx_v2"
        recontext.anchor shouldBe null
        recontext.unseenOnly shouldBe false
        recontext.frames.first().number shouldBe 6L
        recontext.frames.first().members shouldContainExactly listOf("b", "a")
        recontext.sources.all { it.joinRound == 6L } shouldBe true
        recontext.sources.all { it.checkpoint == null } shouldBe true
        recontext.entries.all { it.validation == ReelsRoundRobinValidation.UNKNOWN } shouldBe true
        recontext.entries.all { it.round == null && it.ordinal == null } shouldBe true

        // The existing members are available immediately in round 6, not after another six rounds.
        val afterCatalog = repo.mutate(
            reopened,
            recontext.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-ctx-2", complete = true, sources = listOf(source("a"), source("b"))),
                3_000L,
            ),
        )
        afterCatalog.snapshot.frames.first().number shouldBe 6L
        afterCatalog.snapshot.frames.first().members shouldContainExactly listOf("b", "a")
        driver.close()
    }

    @Test
    fun `catalog previousKeys merge keeps one slot and the third source order with both rows present`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-alias-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
            val first = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog(
                        "cat-alias-1",
                        complete = true,
                        sources = listOf(source("old"), source("new"), source("third")),
                    ),
                    1_000L,
                ),
            )
            repo.mutate(
                handle,
                first.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page("old", null, "cp-old", listOf(entry("old", "o1", 20))),
                        page("new", null, "cp-new", listOf(entry("new", "n1", 20))),
                        page("third", null, "cp-third", listOf(entry("third", "t1", 20))),
                    ),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            driver.execute(
                null,
                """
                UPDATE reels_rr_state
                SET anchor_round = NULL, anchor_source_key = NULL, anchor_item_id = NULL
                WHERE account_scope_id = '$SCOPE' AND source_id = $SOURCE
                """.trimIndent(),
                0,
            )

            val merged = repo.mutate(
                handle,
                repo.read(handle).revision,
                ReelsRoundRobinMutation.Catalog(
                    catalog(
                        "cat-alias-2",
                        complete = true,
                        sources = listOf(
                            source("new", previousKeys = listOf("old")),
                            source("third"),
                        ),
                    ),
                    3_000L,
                ),
            )

            merged.snapshot.sources.map { it.key } shouldContainExactly listOf("new", "third")
            merged.snapshot.sources.first { it.key == "new" }.order shouldBe 0L
            merged.snapshot.sources.first { it.key == "third" }.order shouldBe 2L
            merged.snapshot.frames.first().members shouldContainExactly listOf("new", "third")
            // The canonical source occupies the earlier slot, so its own reserved item wins;
            // the alias item keeps its payload but becomes staged.
            merged.snapshot.frames.first().turns["new"]?.itemId shouldBe "n1"
            merged.snapshot.frames.first().turns["third"]?.itemId shouldBe "t1"
            merged.snapshot.entries.first { it.candidate.itemId == "o1" }.sourceKey shouldBe "new"
            merged.snapshot.entries.first { it.candidate.itemId == "o1" }.round shouldBe null
            merged.snapshot.entries.first { it.candidate.itemId == "n1" }.round shouldBe 0L
            merged.snapshot.entries.first { it.candidate.itemId == "t1" }.round shouldBe 0L

            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 3L
            val sourceKeys = handler.awaitList { db -> db.reels_rr_sourceQueries.all(SCOPE, SOURCE) }
                .map { it.source_key }
            sourceKeys shouldContain "new"
            sourceKeys shouldContain "third"
            sourceKeys.contains("old") shouldBe false
            driver.close()
        }
    }

    @Test
    fun `corrupt frames JSON is a read error and never deletes inbox or history`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-corrupt.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-corrupt", complete = true, sources = listOf(source("a"))),
                1_000L,
            ),
        )
        repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )

        driver.execute(
            null,
            "UPDATE reels_rr_state SET frames_json = 'not-json' WHERE account_scope_id = '$SCOPE' AND source_id = $SOURCE",
            0,
        )
        assertThrows<ReelsRoundRobinStateCorruptException> { runBlocking { repo.read(handle) } }
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
        handler.awaitOne { db -> db.reels_rr_sourceQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L

        val duplicateMembers = """[{"number":0,"members":["a","a"],"turns":{}}]"""
        driver.execute(
            null,
            "UPDATE reels_rr_state SET frames_json = '$duplicateMembers' WHERE account_scope_id = '$SCOPE' AND source_id = $SOURCE",
            0,
        )
        assertThrows<ReelsRoundRobinStateCorruptException> { runBlocking { repo.read(handle) } }
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
        driver.close()
    }

    @Test
    fun `history reset removes the rr projection and fences the old owner`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-reset-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val (repo, handle) = preparedRoundRobin(handler)

            val generation = ReelsConsumptionRepositoryImpl(handler).resetHistory(SCOPE, 1)

            generation shouldBe 1L
            assertRrCounts(handler, 0L)
            handler.awaitOneOrNull { db -> db.reels_sessionQueries.getSession(SCOPE, SOURCE) } shouldBe null
            handler.awaitOne { db -> db.reels_for_you_stateQueries.getGeneration(SCOPE) } shouldBe 1L
            assertThrows<ReelsSessionStaleException> {
                repo.mutate(
                    handle,
                    0L,
                    ReelsRoundRobinMutation.Catalog(
                        catalog("cat-late-reset", complete = true, sources = listOf(source("a"))),
                        3_000L,
                    ),
                )
            }
            assertRrCounts(handler, 0L)
            driver.close()
        }
    }

    @Test
    fun `candidate cache clear removes the rr projection but keeps the account session`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-cache-clear-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val (repo, handle) = preparedRoundRobin(handler)

            ReelsCandidateRepositoryImpl(handler).clear(SCOPE)

            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            assertRrCounts(handler, 0L)
            val session = handler.awaitOneOrNull { db ->
                db.reels_sessionQueries.getSession(SCOPE, SOURCE)
            }
            session?.owner_nonce shouldBe "owner-1"
            assertThrows<ReelsSessionStaleException> {
                repo.mutate(
                    handle,
                    0L,
                    ReelsRoundRobinMutation.Catalog(
                        catalog("cat-late-clear", complete = true, sources = listOf(source("a"))),
                        3_000L,
                    ),
                )
            }
            assertRrCounts(handler, 0L)
            driver.close()
        }
    }

    @Test
    fun `session scope clear deletes the rr projection and the old handle can never write again`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-session-clear-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val (repo, handle) = preparedRoundRobin(handler)

            ReelsSessionRepositoryImpl(handler).clearScope(SCOPE)

            assertRrCounts(handler, 0L)
            handler.awaitOneOrNull { db -> db.reels_sessionQueries.getSession(SCOPE, SOURCE) } shouldBe null
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
            assertThrows<ReelsSessionStaleException> {
                repo.mutate(
                    handle,
                    0L,
                    ReelsRoundRobinMutation.Catalog(
                        catalog("cat-late-scope", complete = true, sources = listOf(source("a"))),
                        3_000L,
                    ),
                )
            }
            assertRrCounts(handler, 0L)
            driver.close()
        }
    }

    @Test
    fun `release keeps the rr account state for the next owner`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-release-keep.db"))
        AnimeDatabase.Schema.create(driver)
        val (repo, handle) = preparedRoundRobin(handler)
        val before = repo.read(handle)

        repo.release(handle)

        assertRrCounts(handler, 1L)
        handler.awaitOne { db -> db.reels_sessionQueries.getSession(SCOPE, SOURCE) }.owner_nonce shouldBe null
        val reopened = repo.open(key(), "owner-2", 1L, unseenOnly = false)
        val after = repo.read(reopened)
        after.revision shouldBe before.revision
        after.anchor shouldBe before.anchor
        after.frames shouldBe before.frames
        // Reopening sanitizes transport hints; the projection identity itself must survive.
        after.entries.map { it.candidate.itemId } shouldBe before.entries.map { it.candidate.itemId }
        driver.close()
    }

    @Test
    fun `direct remove loses show watched protection after the context changes`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-remove-context-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            preparedRoundRobin(handler, unseenOnly = false)
            markConsumed(handler, "a1")

            // A different mode claims its own context; RR protection must not outlive that switch.
            ReelsSessionRepositoryImpl(handler).open(
                key("legacy_v27"),
                "legacy-owner",
                1L,
                reset = false,
            )
            ReelsCandidateRepositoryImpl(handler).remove(SCOPE, SOURCE, listOf("a1"))

            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            driver.close()
        }
    }

    @Test
    fun `a future frame without a payload is reported instead of being returned as a shown card`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-missing-payload-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val (repo, handle) = preparedRoundRobin(handler, unseenOnly = false)
            val revision = repo.read(handle).revision

            // A legacy writer removes the payload directly; the frame keeps the reserved ID.
            driver.execute(
                null,
                "DELETE FROM reels_candidate WHERE account_scope_id = '$SCOPE' AND source_id = $SOURCE AND item_id = 'a1'",
                0,
            )

            val snapshot = repo.read(handle)
            snapshot.entries.shouldBeEmpty()
            snapshot.unmappedItemIds shouldContain "a1"

            val commit = repo.mutate(
                handle,
                revision,
                ReelsRoundRobinMutation.Pump(updates = emptyList(), limit = 12, nowMs = 3_000L),
            )
            commit.page shouldBe emptyList()
            commit.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>()
                .map { it.itemId } shouldContain "a1"
            handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 0L
            driver.close()
        }
    }

    @Test
    fun `a missing future payload stays a revalidate need across pumps and import restores it`() = runBlocking {
        for (foreignKeys in listOf(true, false)) {
            val (driver, handler) = createDb(
                File(tempDir, "rr-missing-durable-$foreignKeys.db"),
                foreignKeys = foreignKeys,
            )
            AnimeDatabase.Schema.create(driver)
            val repo = ReelsRoundRobinRepositoryImpl(handler)
            val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
            val catalogCommit = repo.mutate(
                handle,
                0L,
                ReelsRoundRobinMutation.Catalog(
                    catalog("cat-durable", complete = true, sources = listOf(source("a"), source("b"))),
                    1_000L,
                ),
            )
            val first = repo.mutate(
                handle,
                catalogCommit.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page("a", null, "cp-a1", listOf(entry("a", "a1", 30), entry("a", "a2", 20))),
                        page("b", null, "cp-b1", listOf(entry("b", "b1", 10))),
                    ),
                    limit = 12,
                    nowMs = 2_000L,
                ),
            )
            first.snapshot.frames.first { it.number == 1L }.turns["a"]?.itemId shouldBe "a2"

            // A legacy writer removes the future payload directly; FK ON cascades the RR item
            // row, FK OFF keeps it until the next persisting pump cleans the orphan.
            driver.execute(
                null,
                "DELETE FROM reels_candidate WHERE account_scope_id = '$SCOPE' " +
                    "AND source_id = $SOURCE AND item_id = 'a2'",
                0,
            )
            val missing = repo.read(handle)
            missing.frames.first { it.number == 1L }.turns["a"]?.itemId shouldBe "a2"
            missing.unmappedItemIds shouldContain "a2"

            // A persisting pump for another source must not repair the trouble away.
            val persisted = repo.mutate(
                handle,
                missing.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(ReelsRoundRobinUpdate.Deferred("b", "retry", 9_000L)),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
            persisted.snapshot.frames.first { it.number == 1L }.turns["a"]?.itemId shouldBe "a2"
            persisted.snapshot.unmappedItemIds shouldContain "a2"
            persisted.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>()
                .map { it.itemId } shouldContain "a2"
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 2L

            // Still a durable need after another pump: no silent repair and no shown card.
            val stillMissing = repo.mutate(
                handle,
                persisted.snapshot.revision,
                ReelsRoundRobinMutation.Pump(updates = emptyList(), limit = 12, nowMs = 4_000L),
            )
            stillMissing.page.map { it.itemId } shouldNotContain "a2"
            stillMissing.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>()
                .map { it.itemId } shouldContain "a2"
            repo.read(handle).unmappedItemIds shouldContain "a2"

            // FK ON has already dropped the association, so Revalidated alone has nothing to
            // update. A scan/import with the verified identity recreates it with its orderKey.
            handler.await(inTransaction = true) { db ->
                ReelsCandidateWriter.writeCandidates(db, SCOPE, SOURCE, listOf(candidate("a2")))
            }
            val restored = repo.mutate(
                handle,
                stillMissing.snapshot.revision,
                ReelsRoundRobinMutation.Import(
                    mapOf("a2" to ReelsRoundRobinIdentity("a", 20L)),
                    nowMs = 5_000L,
                ),
            )
            restored.snapshot.entries.single { it.candidate.itemId == "a2" }.orderKey shouldBe 20L
            handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe 3L

            val revalidated = repo.mutate(
                handle,
                restored.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        ReelsRoundRobinUpdate.Revalidated(
                            "a2",
                            candidate("a2"),
                            ReelsRoundRobinValidation.ELIGIBLE,
                            null,
                        ),
                    ),
                    limit = 12,
                    nowMs = 6_000L,
                ),
            )
            revalidated.page.map { it.itemId } shouldContain "a2"
            revalidated.snapshot.entries.single { it.candidate.itemId == "a2" }.round shouldBe 1L
            driver.close()
        }
    }

    @Test
    fun `repair releases a future slot whose payload was pruned and a scan re-reserves it`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-missing-repair.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = false)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-repair", complete = true, sources = listOf(source("a"), source("b"))),
                1_000L,
            ),
        )
        val first = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    page("a", null, "cp-a1", listOf(entry("a", "a1", 30), entry("a", "a2", 20))),
                    page("b", null, "cp-b1", listOf(entry("b", "b1", 10))),
                ),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        first.snapshot.frames.first { it.number == 1L }.turns["a"]?.itemId shouldBe "a2"

        // FK ON cascades the rr_item association away with the payload.
        driver.execute(
            null,
            "DELETE FROM reels_candidate WHERE account_scope_id = '$SCOPE' " +
                "AND source_id = $SOURCE AND item_id = 'a2'",
            0,
        )
        val missing = repo.read(handle)
        missing.entries.map { it.candidate.itemId } shouldNotContain "a2"
        missing.unmappedItemIds shouldContain "a2"

        val repaired = repo.mutate(
            handle,
            missing.revision,
            ReelsRoundRobinMutation.Repair(setOf("a2"), nowMs = 3_000L),
        )
        repaired.snapshot.frames.first { it.number == 1L }.turns["a"] shouldBe null
        repo.read(handle).unmappedItemIds.shouldBeEmpty()

        val afterRepair = repo.mutate(
            handle,
            repaired.snapshot.revision,
            ReelsRoundRobinMutation.Pump(updates = emptyList(), limit = 12, nowMs = 4_000L),
        )
        afterRepair.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>()
            .map { it.itemId } shouldNotContain "a2"
        afterRepair.needs.filterIsInstance<ReelsRoundRobinNeed.Scan>()
            .map { it.sourceKey } shouldContain "a"

        // The scan/import path recreates the association with its order key.
        handler.await(inTransaction = true) { db ->
            ReelsCandidateWriter.writeCandidates(db, SCOPE, SOURCE, listOf(candidate("a2")))
        }
        val rescanned = repo.mutate(
            handle,
            afterRepair.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", "cp-a1", "cp-a2", listOf(entry("a", "a2", 15)))),
                limit = 12,
                nowMs = 5_000L,
            ),
        )
        rescanned.snapshot.frames.first { it.number == 1L }.turns["a"]?.itemId shouldBe "a2"
        rescanned.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>()
            .map { it.itemId } shouldNotContain "a2"

        // A Revalidated carrying a different candidate identity is rejected, not attached.
        val mismatched = repo.mutate(
            handle,
            rescanned.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    ReelsRoundRobinUpdate.Revalidated(
                        "a2",
                        candidate("foreign"),
                        ReelsRoundRobinValidation.ELIGIBLE,
                        null,
                    ),
                ),
                limit = 12,
                nowMs = 6_000L,
            ),
        )
        mismatched.snapshot.entries.map { it.candidate.itemId } shouldNotContain "foreign"
        mismatched.snapshot.frames.first { it.number == 1L }.turns["a"] shouldBe null
        driver.close()
    }

    @Test
    fun `a page whose cards are all locally ineligible is a budgeted filter result`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-fruitless.db"))
        AnimeDatabase.Schema.create(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = true)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-fruitless", complete = true, sources = listOf(source("a"))),
                1_000L,
            ),
        )
        markConsumed(handler, "a1")

        val commit = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a-fruitless", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 3_000L,
            ),
        )

        val source = commit.snapshot.sources.single { it.key == "a" }
        source.fruitlessScan shouldBe true
        source.nextHeadCheckAt shouldBe 3_000L + ReelsRoundRobinScheduler.FRUITLESS_RESCAN_MS
        commit.snapshot.frames.first().turns["a"]?.skip shouldBe ReelsRoundRobinSkip.FILTER_BUDGET
        commit.needs.filterIsInstance<ReelsRoundRobinNeed.Scan>() shouldBe emptyList()
        commit.needs.filterIsInstance<ReelsRoundRobinNeed.Revalidate>() shouldBe emptyList()
        driver.close()
    }

    @Test
    fun `reset projection clears rr rows but keeps the session and candidate inbox`() = runBlocking {
        val (driver, handler) = createDb(File(tempDir, "rr-reset.db"))
        AnimeDatabase.Schema.create(driver)
        val (repo, _) = preparedRoundRobin(handler, unseenOnly = false)
        assertRrCounts(handler, 1L)
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
        handler.awaitOneOrNull { db -> db.reels_sessionQueries.getSession(SCOPE, SOURCE) } shouldNotBe null

        repo.resetProjection(SCOPE, SOURCE)

        assertRrCounts(handler, 0L)
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, SOURCE) } shouldBe 1L
        handler.awaitOneOrNull { db -> db.reels_sessionQueries.getSession(SCOPE, SOURCE) } shouldNotBe null
        driver.close()
    }

    private suspend fun preparedRoundRobin(
        handler: AndroidAnimeDatabaseHandler,
        unseenOnly: Boolean = false,
    ): Pair<ReelsRoundRobinRepositoryImpl, ReelsRoundRobinHandle> {
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 1L, unseenOnly = unseenOnly)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-lifecycle", complete = true, sources = listOf(source("a"))),
                1_000L,
            ),
        )
        repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(page("a", null, "cp-a", listOf(entry("a", "a1", 20)))),
                limit = 12,
                nowMs = 2_000L,
            ),
        )
        return repo to handle
    }

    private suspend fun assertRrCounts(handler: AndroidAnimeDatabaseHandler, expected: Long) {
        handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, SOURCE) } shouldBe expected
        handler.awaitOne { db -> db.reels_rr_sourceQueries.countForSource(SCOPE, SOURCE) } shouldBe expected
        handler.awaitList { db -> db.reels_rr_stateQueries.get(SCOPE, SOURCE) }.size.toLong() shouldBe expected
    }

    private companion object {
        const val SCOPE = "acc:101:12345"
        const val SOURCE = 101L
    }
}

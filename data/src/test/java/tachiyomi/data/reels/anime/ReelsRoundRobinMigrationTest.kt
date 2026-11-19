package tachiyomi.data.reels.anime

import app.cash.sqldelight.db.QueryResult
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
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
import tachiyomi.domain.reels.anime.model.ReelsCandidate
import tachiyomi.domain.reels.anime.model.ReelsInboxCapacityException
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinAnchor
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalog
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinCatalogSource
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinEntry
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinIdentity
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinMutation
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinNeed
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinSkip
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinStop
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinTurn
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinUpdate
import tachiyomi.domain.reels.anime.model.ReelsRoundRobinValidation
import tachiyomi.domain.reels.anime.model.ReelsSessionKey
import tachiyomi.mi.data.AnimeDatabase
import java.io.File
import java.util.Date

/**
 * Proves the real 158 -> 159 upgrade on pre-upgrade rows and the one-time legacy queue import.
 *
 * The fixture is schema 158 (before 158.sqm), not a current Schema.create, so the test would also
 * catch a destructive statement or a missing table in the migration.
 */
class ReelsRoundRobinMigrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `migration 158 to 159 preserves payloads history and session and creates empty rr tables`() = runBlocking {
        (AnimeDatabase.Schema.version >= 159L) shouldBe true
        val (driver, handler) = migratedFixture("rr-migration.db")

        // Telegram payload: every durable column is still there.
        oneLong(
            driver,
            "SELECT COUNT(*) FROM reels_candidate WHERE account_scope_id = '$SCOPE' AND source_id = $TELEGRAM_SOURCE",
        ) shouldBe 5L
        val telegram = candidateRow(handler, "tg_a1", TELEGRAM_SOURCE)
        telegram.title shouldBe "A1 title"
        telegram.author shouldBe "@chat-a"
        telegram.video_url shouldBe "https://cdn.example/a1.mp4"
        telegram.video_url_hd shouldBe "https://cdn.example/a1.hd.mp4"
        telegram.views_count shouldBe 10L
        telegram.resume_position_ms shouldBe 4000L
        telegram.tags_json shouldBe """["tag-a1"]"""

        // Another provider keeps its transport URLs untouched (only 157 sanitizes Telegram).
        val other = candidateRow(handler, "other_1", OTHER_SOURCE)
        other.video_url shouldBe "https://other.example/1.mp4"
        other.poster_url shouldBe "https://other.example/1.jpg"
        other.resume_position_ms shouldBe 7000L

        // Consumption stays a separate watch/skip fact.
        oneLong(
            driver,
            "SELECT watched_qualified_at FROM reels_consumption " +
                "WHERE account_scope_id = '$SCOPE' AND source_id = $TELEGRAM_SOURCE AND item_id = 'tg_x1'",
        ) shouldBe 111L
        oneLong(
            driver,
            "SELECT skipped_forward_at FROM reels_consumption " +
                "WHERE account_scope_id = '$SCOPE' AND source_id = $OTHER_SOURCE AND item_id = 'other_1'",
        ) shouldBe 222L

        // The active legacy position and the shuffled v27 queue survive the schema upgrade.
        oneString(driver, "SELECT active_item_id FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe "tg_a1"
        oneLong(driver, "SELECT active_position_ms FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe 5000L
        oneLong(driver, "SELECT checkpoint_sequence FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe 7L
        queueIds(driver, "legacy-session") shouldContainExactly listOf("tg_a2", "tg_a3", "tg_b1")

        oneLong(driver, "SELECT history_generation FROM reels_for_you_state WHERE account_scope_id = '$SCOPE'") shouldBe
            3L
        oneLong(
            driver,
            "SELECT COUNT(*) FROM reels_hidden WHERE source_id = $OTHER_SOURCE AND hidden_value = 'other_hidden'",
        ) shouldBe 1L

        // The upgrade only adds the projection tables; no history row is transformed into them.
        tableNames(driver) shouldContain "reels_rr_state"
        tableNames(driver) shouldContain "reels_rr_source"
        tableNames(driver) shouldContain "reels_rr_item"
        oneLong(driver, "SELECT COUNT(*) FROM reels_rr_state") shouldBe 0L
        oneLong(driver, "SELECT COUNT(*) FROM reels_rr_source") shouldBe 0L
        oneLong(driver, "SELECT COUNT(*) FROM reels_rr_item") shouldBe 0L

        driver.close()
    }

    @Test
    fun `legacy head becomes the anchor and import is not repeated on reopen`() = runBlocking {
        val file = File(tempDir, "rr-import.db")
        var driver = newFixtureDriver(file)
        var handler = createHandler(driver)
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 3L, unseenOnly = false)

        // The legacy active card/position is carried into the shared session under the new own
        // owner, so the first migrated screen can resume exactly where the user left off.
        oneString(driver, "SELECT active_item_id FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe "tg_a1"
        oneLong(driver, "SELECT active_position_ms FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe 5000L
        oneString(driver, "SELECT owner_nonce FROM reels_session WHERE account_scope_id = '$SCOPE'") shouldBe "owner-1"

        // Captured from the pre-migration legacy session, not guessed from the candidate title.
        oneString(
            driver,
            "SELECT legacy_head_id FROM reels_rr_state WHERE account_scope_id = '$SCOPE' AND source_id = $TELEGRAM_SOURCE",
        ) shouldBe "tg_a1"

        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-import", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                1_000L,
            ),
        )

        val identities = linkedMapOf(
            "tg_a1" to ReelsRoundRobinIdentity("chat-a", 400L),
            "tg_a2" to ReelsRoundRobinIdentity("chat-a", 300L),
            "tg_a3" to ReelsRoundRobinIdentity("chat-a", 200L),
            "tg_b1" to ReelsRoundRobinIdentity("chat-b", 500L),
            // tg_x1 shares the author/title of known chat-a payloads, but its identity is unknown
            // and its source key is not in the catalog: it must never be guessed into a slot.
            "tg_x1" to ReelsRoundRobinIdentity("ghost", 999L),
        )
        val imported = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Import(identities, nowMs = 2_000L),
        )

        imported.snapshot.anchor shouldBe ReelsRoundRobinAnchor(round = 0L, sourceKey = "chat-a", itemId = "tg_a1")
        imported.snapshot.frames.first().turns["chat-a"] shouldBe ReelsRoundRobinTurn("tg_a1", null)
        imported.snapshot.entries.map { it.candidate.itemId }.toSet() shouldBe
            setOf("tg_a1", "tg_a2", "tg_a3", "tg_b1")
        imported.snapshot.entries
            .filter { it.sourceKey == "chat-a" }
            .associate { it.candidate.itemId to it.orderKey } shouldBe
            mapOf("tg_a1" to 400L, "tg_a2" to 300L, "tg_a3" to 200L)

        handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 4L
        handler.awaitList { db -> db.reels_rr_itemQueries.all(SCOPE, TELEGRAM_SOURCE) }
            .map { it.item_id } shouldNotContain "tg_x1"
        // Unknown identity keeps its payload and history for the legacy recovery path.
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 5L
        handler.awaitOne { db ->
            db.reels_consumptionQueries.countConsumed(SCOPE, TELEGRAM_SOURCE)
        } shouldBe 1L

        // Revalidation is the normal next step for imported legacy entries; the page projection
        // then starts after the anchor and is ordered by source position and order key.
        val a2 = payload(handler, "tg_a2")
        val b1 = payload(handler, "tg_b1")
        val pumped = repo.mutate(
            handle,
            imported.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    ReelsRoundRobinUpdate.Revalidated("tg_a2", a2, ReelsRoundRobinValidation.ELIGIBLE, null),
                    ReelsRoundRobinUpdate.Revalidated("tg_b1", b1, ReelsRoundRobinValidation.ELIGIBLE, null),
                ),
                limit = 12,
                nowMs = 3_000L,
            ),
        )
        pumped.page.map { it.itemId } shouldContainExactly listOf("tg_b1", "tg_a2")
        pumped.page.map { it.itemId } shouldNotContain "tg_a1"
        pumped.snapshot.anchor shouldBe ReelsRoundRobinAnchor(round = 0L, sourceKey = "chat-a", itemId = "tg_a1")
        pumped.snapshot.entries.first { it.candidate.itemId == "tg_b1" }.round shouldBe 0L
        pumped.snapshot.entries.first { it.candidate.itemId == "tg_a2" }.round shouldBe 1L
        pumped.snapshot.entries.first { it.candidate.itemId == "tg_a3" }.round shouldBe null

        // tg_x1 had no verified identity, hence no RR association and no show-watched
        // protection: the pump's candidate write prunes the consumed payload (its consumption
        // fact stays). Payload preservation is a migration/import property, not a promise that
        // every legacy payload survives later cache maintenance.
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 4L
        handler.awaitOneOrNull { db ->
            db.reels_candidateQueries.getByItem(SCOPE, TELEGRAM_SOURCE, "tg_x1")
        } shouldBe null

        // Payloads are reused, never rewritten by the import.
        val storedA2 = candidateRow(handler, "tg_a2", TELEGRAM_SOURCE)
        storedA2.title shouldBe "A2 title"
        storedA2.author shouldBe "@chat-a"
        storedA2.views_count shouldBe 20L
        storedA2.likes_count shouldBe 3L
        val storedOther = candidateRow(handler, "other_1", OTHER_SOURCE)
        storedOther.video_url shouldBe "https://other.example/1.mp4"
        storedOther.resume_position_ms shouldBe 7000L
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, OTHER_SOURCE) } shouldBe 1L

        // The shared queue is the RR forward projection, not a copy of the shuffled v27 array.
        queueIds(handler, handle.session.sessionId) shouldContainExactly listOf("tg_b1", "tg_a2")
        val firstRevision = pumped.snapshot.revision
        driver.close()

        // Re-open: the anchor, the tail and the legacy marker are durable, and a repeated import
        // is a no-op (it must not append a second copy or re-anchor the head).
        driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        handler = createHandler(driver)
        val reopenedRepo = ReelsRoundRobinRepositoryImpl(handler)
        val reopenedHandle = reopenedRepo.open(key(), "owner-2", 3L, unseenOnly = false)
        val restored = reopenedRepo.read(reopenedHandle)
        restored.revision shouldBe firstRevision
        restored.anchor shouldBe ReelsRoundRobinAnchor(round = 0L, sourceKey = "chat-a", itemId = "tg_a1")
        restored.frames.first().turns["chat-a"]?.itemId shouldBe "tg_a1"
        restored.entries.map { it.candidate.itemId }.toSet() shouldBe
            setOf("tg_a1", "tg_a2", "tg_a3", "tg_b1")
        oneString(
            driver,
            "SELECT legacy_head_id FROM reels_rr_state WHERE account_scope_id = '$SCOPE' AND source_id = $TELEGRAM_SOURCE",
        ) shouldBe "tg_a1"

        val repeated = reopenedRepo.mutate(
            reopenedHandle,
            restored.revision,
            ReelsRoundRobinMutation.Import(identities, nowMs = 4_000L),
        )
        repeated.snapshot.revision shouldBe restored.revision
        repeated.page shouldBe emptyList()
        handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 4L
        handler.awaitOne { db ->
            db.reels_session_queueQueries.maxOrdinal(SCOPE, TELEGRAM_SOURCE, reopenedHandle.session.sessionId)
        }
            .MAX shouldBe 2L
        driver.close()
    }

    @Test
    fun `a skip-only frame does not block anchoring the legacy head`() = runBlocking {
        val (driver, handler) = migratedFixture("rr-anchor-after-removal.db")
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 3L, unseenOnly = false)

        // Another chat disappears from the catalog before the import: the planner records a
        // REMOVED skip, which is not a card and must not look like "the feed already started".
        val full = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-full", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                1_000L,
            ),
        )
        val removed = repo.mutate(
            handle,
            full.snapshot.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-removed-b", complete = true, sources = listOf(source("chat-a"))),
                2_000L,
            ),
        )
        removed.snapshot.frames.first().turns["chat-b"]?.skip shouldBe ReelsRoundRobinSkip.REMOVED

        val imported = repo.mutate(
            handle,
            removed.snapshot.revision,
            ReelsRoundRobinMutation.Import(
                linkedMapOf(
                    "tg_a1" to ReelsRoundRobinIdentity("chat-a", 400L),
                    "tg_a2" to ReelsRoundRobinIdentity("chat-a", 300L),
                    "tg_a3" to ReelsRoundRobinIdentity("chat-a", 200L),
                    "tg_b1" to ReelsRoundRobinIdentity("chat-b", 500L),
                ),
                nowMs = 3_000L,
            ),
        )

        imported.snapshot.anchor shouldBe ReelsRoundRobinAnchor(round = 0L, sourceKey = "chat-a", itemId = "tg_a1")
        imported.snapshot.frames.first().turns["chat-a"] shouldBe ReelsRoundRobinTurn("tg_a1", null)
        imported.snapshot.frames.first().turns["chat-b"]?.skip shouldBe ReelsRoundRobinSkip.REMOVED
        driver.close()
    }

    @Test
    fun `a stale skip on the anchor slot is replaced by the anchor turn`() = runBlocking {
        val (driver, handler) = migratedFixture("rr-anchor-stale-skip.db")
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 3L, unseenOnly = false)

        // chat-a is excluded after it became a frame member, so its slot holds a HIDDEN skip;
        // re-including it does not clear that skip by itself. The imported anchor must still
        // materialize over it.
        val full = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-full", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                1_000L,
            ),
        )
        val hidden = repo.mutate(
            handle,
            full.snapshot.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog(
                    "cat-hide-a",
                    complete = true,
                    sources = listOf(source("chat-a", excluded = true), source("chat-b")),
                ),
                2_000L,
            ),
        )
        hidden.snapshot.frames.first().turns["chat-a"]?.skip shouldBe ReelsRoundRobinSkip.HIDDEN
        val shown = repo.mutate(
            handle,
            hidden.snapshot.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-show-a", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                3_000L,
            ),
        )
        shown.snapshot.frames.first().turns["chat-a"]?.skip shouldBe ReelsRoundRobinSkip.HIDDEN

        val imported = repo.mutate(
            handle,
            shown.snapshot.revision,
            ReelsRoundRobinMutation.Import(
                linkedMapOf(
                    "tg_a1" to ReelsRoundRobinIdentity("chat-a", 400L),
                    "tg_a2" to ReelsRoundRobinIdentity("chat-a", 300L),
                    "tg_a3" to ReelsRoundRobinIdentity("chat-a", 200L),
                    "tg_b1" to ReelsRoundRobinIdentity("chat-b", 500L),
                ),
                nowMs = 4_000L,
            ),
        )

        imported.snapshot.anchor shouldBe ReelsRoundRobinAnchor(round = 0L, sourceKey = "chat-a", itemId = "tg_a1")
        imported.snapshot.frames.first().turns["chat-a"] shouldBe ReelsRoundRobinTurn("tg_a1", null)
        driver.close()
    }

    @Test
    fun `incomplete catalog keeps candidates and unknown identities never claim a slot`() = runBlocking {
        val (driver, handler) = migratedFixture("rr-incomplete.db")
        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 3L, unseenOnly = false)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-full", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                1_000L,
            ),
        )
        val imported = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Import(
                linkedMapOf(
                    "tg_a1" to ReelsRoundRobinIdentity("chat-a", 400L),
                    "tg_a2" to ReelsRoundRobinIdentity("chat-a", 300L),
                    "tg_a3" to ReelsRoundRobinIdentity("chat-a", 200L),
                    "tg_b1" to ReelsRoundRobinIdentity("chat-b", 500L),
                    "tg_x1" to ReelsRoundRobinIdentity("ghost", 999L),
                ),
                nowMs = 2_000L,
            ),
        )

        val partial = repo.mutate(
            handle,
            imported.snapshot.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-partial", complete = false, sources = listOf(source("chat-a"))),
                3_000L,
            ),
        )
        // complete = false is not authoritative: an omitted chat keeps its membership, slot
        // and reserved turns instead of being marked REMOVED.
        partial.snapshot.sources.first { it.key == "chat-b" }.present shouldBe true
        partial.snapshot.entries.map { it.candidate.itemId }.toSet() shouldBe
            setOf("tg_a1", "tg_a2", "tg_a3", "tg_b1")
        partial.snapshot.entries.first { it.candidate.itemId == "tg_b1" }.sourceKey shouldBe "chat-b"
        partial.snapshot.frames.forEach { frame ->
            frame.turns.values.mapNotNull { it.skip } shouldBe emptyList()
        }
        partial.snapshot.entries.map { it.candidate.itemId } shouldNotContain "tg_x1"

        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 5L
        handler.awaitOneOrNull { db -> db.reels_candidateQueries.getByItem(SCOPE, TELEGRAM_SOURCE, "tg_x1") }
            ?.author shouldBe "@chat-a"

        // A later complete catalog re-admits the source without duplicating or deleting rows.
        val restored = repo.mutate(
            handle,
            partial.snapshot.revision,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-full-2", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                4_000L,
            ),
        )
        restored.snapshot.sources.first { it.key == "chat-b" }.present shouldBe true
        restored.snapshot.entries.map { it.candidate.itemId }.toSet() shouldBe
            setOf("tg_a1", "tg_a2", "tg_a3", "tg_b1")
        handler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 4L
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 5L
        driver.close()
    }

    @Test
    fun `a full inbox drains the legacy head and pauses honestly`() = runBlocking {
        val file = File(tempDir, "rr-full-inbox.db")
        val driver = newFixtureDriver(file)
        val handler = createHandler(driver)

        // Fill the per-source inbox to its 2000 cap around the five fixture payloads. The legacy
        // head and the imported tail are already cached, so the first page must not write/evict.
        handler.await(inTransaction = true) { db ->
            repeat(1995) { index ->
                ReelsCandidateWriter.upsert(
                    db.reels_candidateQueries,
                    candidate("filler_$index", author = "@filler"),
                )
            }
        }
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 2_000L
        // Keep the fixture history but do not let the consumed remnant prune shrink the full
        // inbox during the revalidation write; this test is about cap pressure, not pruning.
        driver.execute(
            null,
            "DELETE FROM reels_consumption WHERE account_scope_id = '$SCOPE' " +
                "AND source_id = $TELEGRAM_SOURCE AND item_id = 'tg_x1'",
            0,
        )

        val repo = ReelsRoundRobinRepositoryImpl(handler)
        val handle = repo.open(key(), "owner-1", 3L, unseenOnly = false)
        val catalogCommit = repo.mutate(
            handle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-full", complete = true, sources = listOf(source("chat-a"), source("chat-b"))),
                1_000L,
            ),
        )
        val imported = repo.mutate(
            handle,
            catalogCommit.snapshot.revision,
            ReelsRoundRobinMutation.Import(
                linkedMapOf(
                    "tg_a1" to ReelsRoundRobinIdentity("chat-a", 400L),
                    "tg_a2" to ReelsRoundRobinIdentity("chat-a", 300L),
                    "tg_a3" to ReelsRoundRobinIdentity("chat-a", 200L),
                    "tg_b1" to ReelsRoundRobinIdentity("chat-b", 500L),
                ),
                nowMs = 2_000L,
            ),
        )
        val pumped = repo.mutate(
            handle,
            imported.snapshot.revision,
            ReelsRoundRobinMutation.Pump(
                updates = listOf(
                    ReelsRoundRobinUpdate.Revalidated(
                        "tg_a2",
                        payload(handler, "tg_a2"),
                        ReelsRoundRobinValidation.ELIGIBLE,
                        null,
                    ),
                    ReelsRoundRobinUpdate.Revalidated(
                        "tg_b1",
                        payload(handler, "tg_b1"),
                        ReelsRoundRobinValidation.ELIGIBLE,
                        null,
                    ),
                ),
                limit = 12,
                nowMs = 3_000L,
            ),
        )
        pumped.page.map { it.itemId } shouldContainExactly listOf("tg_b1", "tg_a2")
        pumped.snapshot.anchor shouldBe ReelsRoundRobinAnchor(0L, "chat-a", "tg_a1")
        handler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 2_000L
        handler.awaitOneOrNull { db -> db.reels_candidateQueries.getByItem(SCOPE, TELEGRAM_SOURCE, "filler_0") }
            ?.title shouldBe "Title filler_0"
        driver.close()

        // Nothing eligible is cached: the source is neither EOF nor evicted, and a new page that
        // would exceed capacity surfaces the real backpressure instead of a silent drop.
        val pauseFile = File(tempDir, "rr-capacity-pause.db")
        val pauseDriver = JdbcSqliteDriver("jdbc:sqlite:${pauseFile.absolutePath}")
        pauseDriver.execute(null, "PRAGMA foreign_keys = ON", 0)
        AnimeDatabase.Schema.create(pauseDriver)
        val pauseHandler = createHandler(pauseDriver)
        pauseHandler.await(inTransaction = true) { db ->
            ReelsCandidateWriter.writeCandidates(
                db = db,
                scope = SCOPE,
                sourceId = TELEGRAM_SOURCE,
                candidates = (1..2_000).map { candidate("blocked_$it", author = "@blocked") },
            )
            db.reels_hiddenQueries.insert(
                source_id = TELEGRAM_SOURCE,
                kind = "author",
                hidden_value = "@blocked",
                label = null,
                hidden_at = Date(1_000L),
            )
        }
        val pauseRepo = ReelsRoundRobinRepositoryImpl(pauseHandler)
        val pauseHandle = pauseRepo.open(key(), "owner-1", 1L, unseenOnly = true)
        val pauseCatalog = pauseRepo.mutate(
            pauseHandle,
            0L,
            ReelsRoundRobinMutation.Catalog(
                catalog("cat-pause", complete = true, sources = listOf(source("chat-a"))),
                1_000L,
            ),
        )
        val idle = pauseRepo.mutate(
            pauseHandle,
            pauseCatalog.snapshot.revision,
            ReelsRoundRobinMutation.Pump(updates = emptyList(), limit = 12, nowMs = 2_000L),
        )
        idle.page shouldBe emptyList()
        idle.stop shouldBe ReelsRoundRobinStop.NEED_INPUT
        idle.needs.filterIsInstance<ReelsRoundRobinNeed.Scan>().map { it.sourceKey } shouldContain "chat-a"

        assertThrows<ReelsInboxCapacityException> {
            pauseRepo.mutate(
                pauseHandle,
                pauseCatalog.snapshot.revision,
                ReelsRoundRobinMutation.Pump(
                    updates = listOf(
                        page(
                            "chat-a",
                            null,
                            "cp-new",
                            listOf(entry("chat-a", "new_1", 20), entry("chat-a", "new_2", 10)),
                        ),
                    ),
                    limit = 12,
                    nowMs = 3_000L,
                ),
            )
        }
        val unchanged = pauseRepo.read(pauseHandle)
        unchanged.revision shouldBe pauseCatalog.snapshot.revision
        unchanged.sources.first().checkpoint shouldBe null
        pauseHandler.awaitOne { db -> db.reels_candidateQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 2_000L
        pauseHandler.awaitOne { db -> db.reels_rr_itemQueries.countForSource(SCOPE, TELEGRAM_SOURCE) } shouldBe 0L
        pauseDriver.close()
    }

    private fun migratedFixture(name: String): Pair<JdbcSqliteDriver, AndroidAnimeDatabaseHandler> {
        val driver = newFixtureDriver(File(tempDir, name))
        return driver to createHandler(driver)
    }

    private fun newFixtureDriver(file: File): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        applyFixture(driver)
        AnimeDatabase.Schema.migrate(driver, oldVersion = 158L, newVersion = 159L)
        return driver
    }

    private fun applyFixture(driver: JdbcSqliteDriver) {
        val script = ReelsRoundRobinMigrationTest::class.java
            .getResourceAsStream("/reels/round-robin/v158-reels.sql")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing v158-reels.sql fixture")
        script.split(Regex(";\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.lines().all { line -> line.trim().startsWith("--") || line.isBlank() } }
            .forEach { statement -> driver.execute(null, statement, 0) }
    }

    private fun createHandler(driver: JdbcSqliteDriver): AndroidAnimeDatabaseHandler {
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
        return AndroidAnimeDatabaseHandler(db, driver)
    }

    private suspend fun payload(handler: AndroidAnimeDatabaseHandler, itemId: String): ReelsCandidate {
        return ReelsCandidateWriter.mapCandidate(
            handler.awaitOne { db ->
                db.reels_candidateQueries.getByItem(SCOPE, TELEGRAM_SOURCE, itemId)
            },
        )
    }

    private fun candidate(itemId: String, author: String = "@chat-a"): ReelsCandidate {
        return ReelsCandidate(
            accountScopeId = SCOPE,
            sourceId = TELEGRAM_SOURCE,
            itemId = itemId,
            title = "Title $itemId",
            author = author,
            videoUrl = "https://cdn.example/$itemId.mp4",
            videoUrlHd = null,
            posterUrl = null,
            posterUrlVertical = null,
            webUrl = "https://t.me/chat-a/$itemId",
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

    private fun source(key: String, excluded: Boolean = false): ReelsRoundRobinCatalogSource {
        return ReelsRoundRobinCatalogSource(
            key = key,
            title = "chat $key",
            author = null,
            contentRevision = null,
            excluded = excluded,
            previousKeys = emptyList(),
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
    ): ReelsRoundRobinEntry {
        return ReelsRoundRobinEntry(
            candidate = candidate(itemId),
            sourceKey = sourceKey,
            orderKey = orderKey,
            contentKey = null,
            checkedContext = null,
            validation = ReelsRoundRobinValidation.ELIGIBLE,
            round = null,
            ordinal = null,
        )
    }

    private fun page(
        sourceKey: String,
        fromCheckpoint: String?,
        nextCheckpoint: String,
        entries: List<ReelsRoundRobinEntry>,
    ): ReelsRoundRobinUpdate.Page {
        return ReelsRoundRobinUpdate.Page(
            sourceKey = sourceKey,
            fromCheckpoint = fromCheckpoint,
            nextCheckpoint = nextCheckpoint,
            entries = entries,
            historyExhausted = false,
            nextHeadCheckAt = null,
        )
    }

    private fun key(contextKey: String = "rr:ctx") = ReelsSessionKey(SCOPE, TELEGRAM_SOURCE, contextKey)

    private suspend fun candidateRow(
        handler: AndroidAnimeDatabaseHandler,
        itemId: String,
        sourceId: Long,
    ): Reels_candidate {
        return handler.awaitOne { db ->
            db.reels_candidateQueries.getByItem(SCOPE, sourceId, itemId)
        }
    }

    private fun queueIds(driver: JdbcSqliteDriver, sessionId: String): List<String> {
        return driver.executeQuery(
            null,
            "SELECT item_id FROM reels_session_queue WHERE account_scope_id = '$SCOPE' " +
                "AND source_id = $TELEGRAM_SOURCE AND session_id = '$sessionId' ORDER BY ordinal",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) add(cursor.getString(0).orEmpty())
                    },
                )
            },
            0,
        ).value
    }

    private fun queueIds(handler: AndroidAnimeDatabaseHandler, sessionId: String): List<String> = runBlocking {
        handler.awaitList { db ->
            db.reels_session_queueQueries.rawQueueWindow(SCOPE, TELEGRAM_SOURCE, sessionId, 0L, 100L)
        }.map { it.item_id }
    }

    private fun tableNames(driver: JdbcSqliteDriver): List<String> {
        return driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            mapper = { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) add(cursor.getString(0).orEmpty())
                    },
                )
            },
            0,
        ).value
    }

    private fun oneString(driver: JdbcSqliteDriver, sql: String): String? {
        return driver.executeQuery(
            null,
            sql,
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            0,
        ).value
    }

    private fun oneLong(driver: JdbcSqliteDriver, sql: String): Long? {
        return driver.executeQuery(
            null,
            sql,
            mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            0,
        ).value
    }

    private companion object {
        const val SCOPE = "acc:tg:1"
        const val TELEGRAM_SOURCE = 8529104719284712L
        const val OTHER_SOURCE = 999_999L
    }
}

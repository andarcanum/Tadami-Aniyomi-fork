package tachiyomi.data.translation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.novel.AndroidNovelDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.novel.createTestNovelDatabase
import tachiyomi.domain.translation.model.TranslationActionPauseReason
import tachiyomi.domain.translation.model.TranslationArtifactReceipt
import tachiyomi.domain.translation.model.TranslationBatchStatus
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.repository.TranslationRunRepository
import tachiyomi.novel.data.NovelDatabase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class TranslationRunRepositoryImplTest {

    @Test
    fun `identical live enqueue joins shared ownership without resetting work`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            repo.checkpoint(run, listOf(fragment(0, translated = "done-0")), 50, 11) shouldBe true

            val joined = repo.enqueue(listOf(1, 1), 1, PROFILE_JSON, PROFILE_FP, "b", false, now = 12)
            joined.insertedChapterIds shouldBe emptyList()
            joined.joinedChapterIds shouldBe listOf(1L)
            joined.conflictingChapterIds shouldBe emptyList()
            repo.enqueue(listOf(1), 1, PROFILE_JSON, PROFILE_FP, "b", false, now = 13)

            val current = repo.getByChapterId(1).shouldNotBeNull()
            current.generation shouldBe run.generation
            current.status shouldBe TranslationRunStatus.RUNNING
            current.progress shouldBe 50
            repo.getFragments(current).map { it.translatedText } shouldBe listOf("done-0", null)
            repo.getBatch("b")?.enqueued shouldBe 1
        }
    }

    @Test
    fun `force changed profile and changed snapshot conflict with live work`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val run = claim(repo)
            val requests = listOf(
                Triple(PROFILE_JSON, PROFILE_FP, true),
                Triple("{\"provider\":\"other\"}", "profile-other", false),
                Triple("{\"provider\":\"changed\"}", PROFILE_FP, false),
            )
            requests.forEachIndexed { index, (snapshot, fingerprint, force) ->
                val result = repo.enqueue(listOf(1), 1, snapshot, fingerprint, "conflict-$index", force, now = 20)
                result.conflictingChapterIds shouldBe listOf(1L)
                result.insertedChapterIds shouldBe emptyList()
                result.joinedChapterIds shouldBe emptyList()
            }
            repo.getByChapterId(1) shouldBe run
        }
    }

    @Test
    fun `stale generation cannot checkpoint release or publish after cancel and reenqueue`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val old = claim(repo)
            prepare(repo, old, listOf(fragment(0), fragment(1)))
            repo.checkpoint(old, listOf(fragment(0, translated = "old-0")), 50, 11)
            repo.cancelChapter(1, 12) shouldBe true
            createRun(repo, batchToken = "b", now = 13)
            val current = claim(repo, 14)
            current.id shouldBe old.id
            current.generation shouldBe old.generation + 1

            repo.checkpoint(old, listOf(fragment(1, translated = "stale")), 99, 15) shouldBe false
            repo.release(old, TranslationRunStatus.FAILED, 1, "stale", now = 15) shouldBe false
            var callbacks = 0
            repo.publish(old, 15) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getFragments(current).map { it.translatedText } shouldBe listOf("old-0", null)
            repo.getFragments(old).map { it.translatedText } shouldBe listOf("old-0", null)
            repo.getBatch("a")?.cancelled shouldBe 1
        }
    }

    @Test
    fun `terminal resume preserves checked fragments and resets failed deadlines`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val old = claim(repo)
            prepare(repo, old, listOf(fragment(0), fragment(1)))
            val failed = fragment(1).copy(
                attempts = 3,
                firstFailureAt = 11,
                nextAttemptAt = 500,
                failure = TranslationFailure(TranslationFailureKind.TRANSIENT, "fixture-failure"),
            )
            repo.checkpoint(old, listOf(fragment(0, translated = "done-0"), failed), 50, 12)
            repo.release(old, TranslationRunStatus.FAILED, 50, "fixture-failure", now = 13)
            val current = createRun(repo, batchToken = "b", now = 14)

            current.generation shouldBe old.generation + 1
            current.sourceFingerprint shouldBe "source-v1"
            current.sourceSegmentCount shouldBe 2
            val copies = repo.getFragments(current)
            copies[0].translatedText shouldBe "done-0"
            copies[1] shouldBe fragment(1)
            repo.getFragments(old)[1] shouldBe failed
            repo.getBatch("a")?.failed shouldBe 1
        }
    }

    @Test
    fun `force or changed profile starts fresh and preserves old checkpoints`() = runBlocking<Unit> {
        for (force in listOf(false, true)) {
            Fixture().use { fixture ->
                val repo = fixture.repo
                createRun(repo, batchToken = "a")
                val old = claim(repo)
                prepare(repo, old, listOf(fragment(0, translated = "old")))
                repo.release(old, TranslationRunStatus.FAILED, 99, "fixture", now = 11)
                val current = createRun(
                    repo,
                    batchToken = "b",
                    profileJson = if (force) PROFILE_JSON else "{}",
                    profileFingerprint = if (force) PROFILE_FP else "different-profile",
                    force = force,
                    now = 12,
                )
                current.generation shouldBe old.generation + 1
                current.sourceFingerprint shouldBe null
                current.sourceSegmentCount shouldBe 0
                repo.getFragments(current) shouldBe emptyList()
                repo.getFragments(old).single().translatedText shouldBe "old"
            }
        }
    }

    @Test
    fun `partial checkpoints survive a repository reload and interrupted recovery`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            val failed = fragment(1).copy(
                attempts = 2,
                firstFailureAt = 11,
                nextAttemptAt = 100,
                failure = TranslationFailure(TranslationFailureKind.RATE_LIMIT, "fixture-rate-limit"),
            )
            repo.checkpoint(run, listOf(fragment(0, translated = "done-0"), failed), 50, 12)

            val reloaded = TranslationRunRepositoryImpl(fixture.handler, fixture.json)
            reloaded.recoverInterrupted(13) shouldBe 1
            reloaded.recoverInterrupted(14) shouldBe 0
            val recovered = reloaded.getByChapterId(1).shouldNotBeNull()
            recovered.generation shouldBe run.generation
            recovered.status shouldBe TranslationRunStatus.PENDING
            recovered.progress shouldBe 50
            reloaded.getFragments(recovered) shouldBe listOf(fragment(0, translated = "done-0"), failed)
        }
    }

    @Test
    fun `claim skips paused heads and future deadlines`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, chapterId = 1, batchToken = "paused", now = 1)
            repo.pauseBatch("paused", 2) shouldBe true
            createRun(repo, chapterId = 2, batchToken = "waiting", now = 2)
            val waiting = claim(repo, 3)
            waiting.chapterId shouldBe 2
            repo.release(waiting, TranslationRunStatus.WAITING, 22, "fixture", 100, 4) shouldBe true
            createRun(repo, chapterId = 3, batchToken = "ready", now = 3)
            val ready = claim(repo, 5)
            ready.chapterId shouldBe 3
            repo.release(ready, TranslationRunStatus.WAITING, 0, null, 200, 6)

            repo.claimNext(99) shouldBe null
            repo.nextWakeAt() shouldBe 100L
            claim(repo, 100).chapterId shouldBe 2
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.PAUSED
        }
    }

    @Test
    fun `claim is exclusive and higher priority precedes older work`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, chapterId = 1, batchToken = "low", priority = 0, now = 1)
            createRun(repo, chapterId = 2, batchToken = "high", priority = 2, now = 2)
            claim(repo).chapterId shouldBe 2
            claim(repo).chapterId shouldBe 1
            repo.claimNext(10) shouldBe null
        }
    }

    @Test
    fun `concurrent claims cannot return the same generation twice`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            createRun(fixture.repo)
            val claimed = coroutineScope {
                val first = async { fixture.repo.claimNext(10) }
                val second = async { fixture.repo.claimNext(10) }
                listOf(first.await(), second.await()).filterNotNull()
            }
            claimed.size shouldBe 1
            claimed.single().chapterId shouldBe 1
        }
    }

    @Test
    fun `shared batch cancellation preserves other owners and idempotent counters`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            createRun(repo, batchToken = "b")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done")))
            repo.cancelBatch("a", 11) shouldBe true
            repo.isRunnable(run) shouldBe true
            var callbacks = 0
            repo.publish(run, 12) { callbacks++ } shouldBe true
            repo.publish(run, 13) { callbacks++ } shouldBe false
            repo.cancelBatch("a", 14) shouldBe false

            callbacks shouldBe 1
            repo.getBatch("a")?.status shouldBe TranslationBatchStatus.CANCELLED
            repo.getBatch("a")?.cancelled shouldBe 1
            repo.getBatch("a")?.completed shouldBe 0
            repo.getBatch("b")?.completed shouldBe 1
            repo.getBatch("b")?.failed shouldBe 0
            repo.getBatch("b")?.status shouldBe TranslationBatchStatus.COMPLETED
        }
    }

    @Test
    fun `paused shared owner survives other cancellation and resumes the same generation`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val run = createRun(repo, batchToken = "a")
            createRun(repo, batchToken = "b")
            repo.pauseBatch("b", 2)
            repo.cancelBatch("a", 3)
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.PAUSED
            repo.claimNext(10) shouldBe null
            repo.resumeBatch("b", 11) shouldBe true
            claim(repo, 12).generation shouldBe run.generation
            repo.getBatch("a")?.cancelled shouldBe 1
            repo.getBatch("b")?.cancelled shouldBe 0
        }
    }

    @Test
    fun `implicit reader ownership survives cancellation of its shared batch`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "")
            createRun(repo, batchToken = "a")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done")))
            repo.cancelBatch("a", 11)
            repo.getByChapterId(1)?.implicitOwner shouldBe true
            repo.isRunnable(run) shouldBe true
            repo.publish(run, 12) {} shouldBe true
            repo.getBatch("a")?.cancelled shouldBe 1
            repo.getBatch("a")?.completed shouldBe 0
        }
    }

    @Test
    fun `pausing in flight permits checkpoints but blocks new requests and publication`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            repo.pauseBatch("batch", 11)
            repo.isRunnable(run) shouldBe false
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.checkpoint(run, listOf(fragment(0, translated = "done-0")), 50, 12) shouldBe true
            repo.checkpoint(run, listOf(fragment(1, translated = "done-1")), 99, 13) shouldBe true
            var callbacks = 0
            repo.publish(run, 14) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            repo.resumeBatch("batch", 15)
            repo.isRunnable(run) shouldBe true
            repo.publish(run, 16) { callbacks++ } shouldBe true
            callbacks shouldBe 1
        }
    }

    @Test
    fun `publication callback failure rolls back status and every owner counter`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            createRun(repo, batchToken = "b")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done")))
            var thrown: String? = null
            try {
                repo.publish(run, 11) {
                    fixture.driver.currentTransaction().shouldNotBeNull()
                    throw IllegalStateException("fixture-publication-failure")
                }
            } catch (error: IllegalStateException) {
                thrown = error.message
            }
            thrown shouldBe "fixture-publication-failure"
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getBatch("a")?.completed shouldBe 0
            repo.getBatch("b")?.completed shouldBe 0
            repo.getBatch("a")?.failed shouldBe 0
            repo.publish(run, 12) {} shouldBe true
            repo.getBatch("a")?.completed shouldBe 1
            repo.getBatch("b")?.completed shouldBe 1
        }
    }

    @Test
    fun `publication never marks unprepared or partial durable work complete`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            var callbacks = 0
            repo.publish(run, 11) { callbacks++ } shouldBe false
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            repo.checkpoint(run, listOf(fragment(0, translated = "done")), 100, 12)
            repo.publish(run, 13) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getByChapterId(1)?.progress shouldBe 99
        }
    }

    @Test
    fun `profile freezing fills only a null snapshot and never repairs corrupt non null data`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val legacy = createRun(repo, profileJson = null)
            val frozen = repo.freezeProfile(legacy, PROFILE_JSON, PROFILE_FP, 2).shouldNotBeNull()
            frozen.profileSnapshotJson shouldBe PROFILE_JSON
            repo.freezeProfile(frozen, "{}", "different", 3) shouldBe null
            val corrupt = createRun(repo, chapterId = 2, batchToken = "corrupt", profileJson = "not JSON")
            repo.freezeProfile(corrupt, PROFILE_JSON, PROFILE_FP, 4) shouldBe null
            repo.getByChapterId(2)?.profileSnapshotJson shouldBe "not JSON"
            repo.cancelChapter(1, 5)
            createRun(repo, batchToken = "new", profileJson = null, now = 6)
            repo.freezeProfile(legacy, PROFILE_JSON, PROFILE_FP, 7) shouldBe null
        }
    }

    @Test
    fun `checkpoints reject changed source and invalid fragment ids`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            val oversized = fragment(0).copy(sourceText = "x".repeat(2001))
            repo.prepare(run, "source-v1", PROFILE_FP, 1, listOf(oversized), 11) shouldBe false
            repo.getFragments(run) shouldBe emptyList()
            val pieces = listOf(
                fragment(0).copy(sourceIndex = 0, partCount = 2),
                fragment(1).copy(sourceIndex = 0, partIndex = 1, partCount = 2),
            )
            repo.prepare(run, "source-v1", PROFILE_FP, 1, pieces, 12) shouldBe true
            repo.checkpoint(run, listOf(pieces[0].copy(sourceText = "changed")), 50, 13) shouldBe false
            repo.checkpoint(run, listOf(fragment(2, translated = "unknown")), 50, 13) shouldBe false
            repo.checkpoint(run, listOf(pieces[0], pieces[0]), 50, 13) shouldBe false
            repo.getFragments(run) shouldBe pieces
            repo.checkpoint(run, listOf(pieces[0].copy(translatedText = "done")), 50, 14) shouldBe true
        }
    }

    @Test
    fun `session only stores no text and publishes through guarded acknowledgment`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val queued = createRun(repo, sessionOnly = true)
            queued.sessionOnly shouldBe true
            val run = claim(repo)
            val inMemory = listOf(fragment(0, translated = "private-result"))
            prepare(repo, run, inMemory)
            repo.checkpoint(run, inMemory, 99, 11) shouldBe true
            repo.getFragments(run) shouldBe emptyList()
            fixture.db.translation_checkpointQueries.countFragments(run.id, run.generation).executeAsOne() shouldBe 0L
            val current = repo.getByChapterId(1).shouldNotBeNull()
            current.sourceFingerprint shouldBe "source-v1"
            current.sourceSegmentCount shouldBe 1
            var callbacks = 0
            repo.publish(run, 12) {
                inMemory.all { it.isComplete } shouldBe true
                callbacks++
            } shouldBe true
            callbacks shouldBe 1
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.COMPLETE
        }
    }

    @Test
    fun `cancelled session only run cannot deliver a stale acknowledgment after reenqueue`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "old", sessionOnly = true)
            val old = claim(repo)
            prepare(repo, old, listOf(fragment(0, translated = "private-result")))
            repo.cancelChapter(1, 11)
            val current = createRun(repo, batchToken = "new", sessionOnly = true, now = 12)
            current.generation shouldBe old.generation + 1
            current.sourceFingerprint shouldBe null
            var callbacks = 0
            repo.publish(old, 13) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            fixture.db.translation_checkpointQueries.countFragments(old.id, old.generation).executeAsOne() shouldBe 0L
        }
    }

    @Test
    fun `run observation emits initial state and later committed changes`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val initial = CompletableDeferred<Unit>()
            val emitted = mutableListOf<List<TranslationRun>>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.repo.observeRuns(1).take(2).collect {
                    emitted += it
                    initial.complete(Unit)
                }
            }
            withTimeout(5_000) { initial.await() }
            createRun(fixture.repo)
            withTimeout(5_000) { collector.join() }
            emitted[0] shouldBe emptyList()
            emitted[1].map { it.chapterId } shouldBe listOf(1L)
        }
    }

    @Test
    fun `publication rejects missing durable parts even when remaining fragments are complete`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done-0"), fragment(1, translated = "done-1")))
            // Controlled corruption in the disposable in-memory fixture, never application data.
            fixture.driver.execute(
                null,
                "DELETE FROM translation_checkpoint WHERE queue_id = ? AND generation = ? AND fragment_id = 1",
                2,
            ) {
                bindLong(0, run.id)
                bindLong(1, run.generation)
            }
            var callbacks = 0
            repo.publish(run, 11) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
        }
    }

    @Test
    fun `session only acknowledgment failure rolls back counters without persisting text`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, sessionOnly = true)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0)))
            var failure: String? = null
            try {
                repo.publish(run, 11) { throw IllegalStateException("fixture-incomplete-session") }
            } catch (error: IllegalStateException) {
                failure = error.message
            }
            failure shouldBe "fixture-incomplete-session"
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getBatch("batch")?.completed shouldBe 0
            fixture.db.translation_checkpointQueries.countFragments(run.id, run.generation).executeAsOne() shouldBe 0L
        }
    }

    @Test
    fun `privacy mode mismatch conflicts instead of replacing a live owner`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            val result = repo.enqueue(
                listOf(1),
                1,
                PROFILE_JSON,
                PROFILE_FP,
                "private",
                false,
                now = 11,
                sessionOnly = true,
            )
            result.conflictingChapterIds shouldBe listOf(1L)
            repo.getByChapterId(1) shouldBe run
        }
    }

    @Test
    fun `completed fragment checkpoint is sticky against late same generation failures`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0)))
            repo.checkpoint(run, listOf(fragment(0, translated = "done")), 99, 11) shouldBe true
            repo.checkpoint(
                run,
                listOf(
                    fragment(0).copy(failure = TranslationFailure(TranslationFailureKind.TRANSIENT, "late-fixture")),
                ),
                1,
                12,
            ) shouldBe true
            repo.getFragments(run).single().translatedText shouldBe "done"
            repo.getByChapterId(1)?.progress shouldBe 99
        }
    }

    @Test
    fun `legacy default durable flag is promoted once to session only before any text is saved`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            fixture.db.translation_queueQueries.insert(1, 1, "", 0, PROFILE_JSON, 1)
            val legacy = claim(repo)
            legacy.sessionOnly shouldBe false
            legacy.profileFingerprint shouldBe null
            val inMemory = listOf(fragment(0, translated = "legacy-private-result"))
            prepare(repo, legacy.copy(sessionOnly = true), inMemory)
            val promoted = repo.getByChapterId(1).shouldNotBeNull()
            promoted.sessionOnly shouldBe true
            promoted.sourceFingerprint shouldBe "source-v1"
            promoted.sourceSegmentCount shouldBe 1
            repo.checkpoint(promoted, inMemory, 99, 11) shouldBe true
            repo.getFragments(promoted) shouldBe emptyList()
            fixture.db.translation_checkpointQueries.countFragments(
                legacy.id,
                legacy.generation,
            ).executeAsOne() shouldBe 0L
            var staleCallbacks = 0
            repo.publish(legacy, 12) { staleCallbacks++ } shouldBe false
            staleCallbacks shouldBe 0
            repo.publish(promoted, 13) {} shouldBe true
        }
    }

    @Test
    fun `prepared policy mismatch fails without promoting or deleting durable checkpoints`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            val pieces = listOf(fragment(0))
            prepare(repo, run, pieces)
            repo.prepare(run.copy(sessionOnly = true), "source-v1", PROFILE_FP, 1, pieces, 11) shouldBe false
            repo.checkpoint(
                run.copy(sessionOnly = true),
                listOf(fragment(0, translated = "private-should-not-save")),
                99,
                12,
            ) shouldBe false
            repo.getByChapterId(1)?.sessionOnly shouldBe false
            repo.getFragments(run) shouldBe pieces
            var callbacks = 0
            repo.publish(run.copy(sessionOnly = true), 12) { callbacks++ } shouldBe false
            callbacks shouldBe 0
        }
    }

    @Test
    fun `completed rerun loads fresh source without copying checkpoints`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val completed = claim(repo)
            prepare(repo, completed, listOf(fragment(0, translated = "old-result")))
            repo.publish(completed, 11) {} shouldBe true
            val fresh = createRun(repo, batchToken = "b", now = 12)
            fresh.generation shouldBe completed.generation + 1
            fresh.sourceFingerprint shouldBe null
            fresh.sourceSegmentCount shouldBe 0
            fresh.progress shouldBe 0
            repo.getFragments(fresh) shouldBe emptyList()
            repo.getFragments(completed).single().translatedText shouldBe "old-result"
            repo.getBatch("a")?.completed shouldBe 1
        }
    }

    @Test
    fun `chapter clear fences old work and forces fresh cache bypass`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val old = claim(repo)
            prepare(repo, old, listOf(fragment(0), fragment(1)))
            repo.checkpoint(old, listOf(fragment(0, translated = "old-result")), 50, 11)
            repo.invalidateCheckpoint(1, 12) shouldBe true
            val cleared = repo.getByChapterId(1).shouldNotBeNull()
            cleared.generation shouldBe old.generation + 1
            cleared.status shouldBe TranslationRunStatus.CANCELLED
            cleared.sourceFingerprint shouldBe null
            cleared.sourceSegmentCount shouldBe 0
            cleared.forceRetranslate shouldBe true
            repo.invalidateCheckpoint(1, 13) shouldBe false
            repo.getFragments(old).map { it.translatedText } shouldBe listOf("old-result", null)
            repo.getFragments(cleared) shouldBe emptyList()
            var callbacks = 0
            repo.publish(old, 14) { callbacks++ } shouldBe false
            repo.checkpoint(old, listOf(fragment(1, translated = "stale")), 99, 14) shouldBe false
            callbacks shouldBe 0
            val fresh = createRun(repo, batchToken = "b", now = 15)
            fresh.sourceFingerprint shouldBe null
            fresh.forceRetranslate shouldBe true
            repo.getFragments(fresh) shouldBe emptyList()
            repo.getBatch("a")?.cancelled shouldBe 1
        }
    }

    @Test
    fun `explicit global clear is idempotent and preserves completed counters and old fragments`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                createRun(repo, chapterId = 1, batchToken = "a")
                val first = claim(repo)
                prepare(repo, first, listOf(fragment(0, translated = "first-result")))
                repo.publish(first, 11) {}
                createRun(repo, chapterId = 2, batchToken = "b", now = 12)
                val second = claim(repo, 13)
                prepare(repo, second, listOf(fragment(0)))
                repo.invalidateAllCheckpoints(14) shouldBe 2
                repo.invalidateAllCheckpoints(15) shouldBe 0
                repo.getFragments(first).single().translatedText shouldBe "first-result"
                repo.getFragments(second).single().sourceText shouldBe "source-0"
                repo.getByChapterId(1)?.sourceFingerprint shouldBe null
                repo.getByChapterId(2)?.sourceFingerprint shouldBe null
                repo.getBatch("a")?.completed shouldBe 1
                repo.getBatch("b")?.cancelled shouldBe 1
            }
        }

    @Test
    fun `verified full receipt completes without fabricated fragment outputs and is idempotent`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0)))
            val receipt = TranslationArtifactReceipt("source-v1", PROFILE_FP, 1)
            repo.publish(run, 11) {} shouldBe false
            var callbacks = 0
            repo.publishCached(run, 12, receipt) { callbacks++ } shouldBe true
            repo.publishCached(run, 13, receipt) { callbacks++ } shouldBe false
            callbacks shouldBe 1
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.COMPLETE
            repo.getBatch("batch")?.completed shouldBe 1
            repo.getFragments(run).single().translatedText shouldBe null
        }
    }

    @Test
    fun `cached receipt rejects source profile and segment count mismatches`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo)
            val run = claim(repo)
            var callbacks = 0
            repo.publishCached(
                run,
                11,
                TranslationArtifactReceipt("source-v1", PROFILE_FP, 1),
            ) { callbacks++ } shouldBe false
            prepare(repo, run, listOf(fragment(0)))
            val mismatched = listOf(
                TranslationArtifactReceipt("different-source", PROFILE_FP, 1),
                TranslationArtifactReceipt("source-v1", "different-profile", 1),
                TranslationArtifactReceipt("source-v1", PROFILE_FP, 2),
            )
            for (receipt in mismatched) {
                repo.publishCached(run, 12, receipt) { callbacks++ } shouldBe false
            }
            callbacks shouldBe 0
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getBatch("batch")?.completed shouldBe 0
        }
    }

    @Test
    fun `cancelled and stale cached receipt cannot invoke a publication callback`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            val old = claim(repo)
            prepare(repo, old, listOf(fragment(0)))
            val receipt = TranslationArtifactReceipt("source-v1", PROFILE_FP, 1)
            repo.cancelChapter(1, 11)
            var callbacks = 0
            repo.publishCached(old, 12, receipt) { callbacks++ } shouldBe false
            createRun(repo, batchToken = "b", now = 13)
            val current = claim(repo, 14)
            current.generation shouldBe old.generation + 1
            repo.publishCached(old, 15, receipt) { callbacks++ } shouldBe false
            callbacks shouldBe 0
            repo.getBatch("a")?.cancelled shouldBe 1
            repo.getBatch("b")?.completed shouldBe 0
        }
    }

    @Test
    fun `cached receipt callback failure rolls back all batch terminal counters`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "a")
            createRun(repo, batchToken = "b")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0)))
            val receipt = TranslationArtifactReceipt("source-v1", PROFILE_FP, 1)
            var failure: String? = null
            try {
                repo.publishCached(run, 11, receipt) {
                    fixture.driver.currentTransaction().shouldNotBeNull()
                    throw IllegalStateException("fixture-cached-publication-failure")
                }
            } catch (error: IllegalStateException) {
                failure = error.message
            }
            failure shouldBe "fixture-cached-publication-failure"
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.RUNNING
            repo.getBatch("a")?.completed shouldBe 0
            repo.getBatch("b")?.completed shouldBe 0
            repo.publishCached(run, 12, receipt) {} shouldBe true
            repo.getBatch("a")?.completed shouldBe 1
            repo.getBatch("b")?.completed shouldBe 1
        }
    }

    @Test
    fun `paused or forced run rejects cached publication even with matching receipt`() = runBlocking<Unit> {
        for (force in listOf(false, true)) {
            Fixture().use { fixture ->
                val repo = fixture.repo
                createRun(repo, force = force)
                val run = claim(repo)
                prepare(repo, run, listOf(fragment(0)))
                if (!force) repo.pauseBatch("batch", 11)
                var callbacks = 0
                repo.publishCached(
                    run,
                    12,
                    TranslationArtifactReceipt("source-v1", PROFILE_FP, 1),
                ) { callbacks++ } shouldBe false
                callbacks shouldBe 0
                repo.getBatch("batch")?.completed shouldBe 0
            }
        }
    }

    @Test
    fun `prefetch promotion attaches implicit reader before named ownership is cancelled`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "prefetch", priority = 0)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            repo.checkpoint(run, listOf(fragment(0, translated = "retained")), 50, 11)
            repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, PROFILE_FP, 12) shouldBe true
            val promoted = repo.getByChapterId(1).shouldNotBeNull()
            promoted.id shouldBe run.id
            promoted.generation shouldBe run.generation
            promoted.implicitOwner shouldBe true
            promoted.priority shouldBe 2
            promoted.progress shouldBe 50
            repo.cancelBatch("prefetch", 13) shouldBe true
            repo.isRunnable(run) shouldBe true
            repo.getFragments(run).map { it.translatedText } shouldBe listOf("retained", null)
            repo.checkpoint(run, listOf(fragment(1, translated = "done")), 99, 14) shouldBe true
            repo.publish(run, 15) {} shouldBe true
            repo.getBatch("prefetch")?.cancelled shouldBe 1
            repo.getBatch("prefetch")?.completed shouldBe 0
        }
    }

    @Test
    fun `publication first makes prefetch promotion a no op without a fresh generation`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "prefetch", priority = 0)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done")))
            repo.publish(run, 11) {} shouldBe true
            val completed = repo.getByChapterId(1).shouldNotBeNull()
            repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, PROFILE_FP, 12) shouldBe false
            repo.getByChapterId(1) shouldBe completed
            fixture.db.translation_queueQueries.getAllRuns().executeAsList().size shouldBe 1
            repo.nextWakeAt() shouldBe null
        }
    }

    @Test
    fun `prefetch promotion requires current generation membership`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            repo.enqueue(listOf(1, 2), 1, PROFILE_JSON, PROFILE_FP, "old", false, priority = 0, now = 1)
            val old = claim(repo)
            old.chapterId shouldBe 1
            prepare(repo, old, listOf(fragment(0, translated = "done")))
            repo.publish(old, 11) {} shouldBe true
            repo.getBatch("old")?.status shouldBe TranslationBatchStatus.QUEUED
            val current = createRun(repo, batchToken = "current", priority = 0, now = 12)
            current.generation shouldBe old.generation + 1
            repo.promotePrefetchRun(1, "old", PROFILE_JSON, PROFILE_FP, 13) shouldBe false
            repo.getByChapterId(1) shouldBe current
            repo.promotePrefetchRun(1, "current", PROFILE_JSON, PROFILE_FP, 14) shouldBe true
            repo.getByChapterId(1)?.generation shouldBe current.generation
            repo.getByChapterId(1)?.implicitOwner shouldBe true
        }
    }

    @Test
    fun `prefetch promotion rejects changed profiles paused cancelled and missing named tokens`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "prefetch", priority = 0)
            val current = createRun(repo, batchToken = "other", priority = 0)
            repo.promotePrefetchRun(1, "prefetch", "{}", PROFILE_FP, 2) shouldBe false
            repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, "changed-profile", 2) shouldBe false
            repo.promotePrefetchRun(1, "missing", PROFILE_JSON, PROFILE_FP, 2) shouldBe false
            repo.getByChapterId(1) shouldBe current
            repo.pauseBatch("prefetch", 3) shouldBe true
            repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, PROFILE_FP, 4) shouldBe false
            repo.resumeBatch("prefetch", 5) shouldBe true
            repo.cancelBatch("prefetch", 6) shouldBe true
            repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.PENDING
            repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, PROFILE_FP, 7) shouldBe false
            repo.getByChapterId(1)?.implicitOwner shouldBe false
            repo.getByChapterId(1)?.generation shouldBe current.generation
        }
    }

    @Test
    fun `publication and prefetch promotion race without creating another run generation`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "prefetch", priority = 0)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "done")))
            var callbacks = 0
            val (promoted, published) = coroutineScope {
                val promotion = async { repo.promotePrefetchRun(1, "prefetch", PROFILE_JSON, PROFILE_FP, 12) }
                val publication = async { repo.publish(run, 13) { callbacks++ } }
                promotion.await() to publication.await()
            }
            published shouldBe true
            callbacks shouldBe 1
            val current = repo.getByChapterId(1).shouldNotBeNull()
            current.status shouldBe TranslationRunStatus.COMPLETE
            current.generation shouldBe run.generation
            current.implicitOwner shouldBe promoted
            fixture.db.translation_queueQueries.getAllRuns().executeAsList().size shouldBe 1
        }
    }

    @Test
    fun `different repository and handler wrappers share one authoritative mutation transaction`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val writerEntries = AtomicInteger()
                fun observed(
                    backing: NovelDatabaseHandler,
                ): NovelDatabaseHandler = object : NovelDatabaseHandler by backing {
                    override suspend fun <T> await(inTransaction: Boolean, block: suspend (NovelDatabase) -> T): T {
                        if (inTransaction) writerEntries.incrementAndGet()
                        return backing.await(inTransaction, block)
                    }
                }
                val first = TranslationRunRepositoryImpl(observed(fixture.handler), fixture.json)
                val secondBacking = AndroidNovelDatabaseHandler(fixture.db, fixture.driver)
                val second = TranslationRunRepositoryImpl(observed(secondBacking), fixture.json)
                createRun(first)
                val run = claim(first)
                prepare(first, run, listOf(fragment(0, translated = "done")))
                // Prime the second adapter's lock lookup before the controlled concurrent mutation.
                second.cancelChapter(999, 10) shouldBe false
                writerEntries.set(0)
                val enteredPublication = CompletableDeferred<Unit>()
                val allowPublication = CountDownLatch(1)
                val publication = async {
                    first.publish(run, 11) {
                        enteredPublication.complete(Unit)
                        allowPublication.await()
                    }
                }
                try {
                    withTimeout(5_000) { enteredPublication.await() }
                    // UNDISPATCHED reaches the mutation lock (or handler entry on the broken path)
                    // before returning, so this probe needs neither a sleep nor probabilistic timing.
                    val cancellation = async(start = CoroutineStart.UNDISPATCHED) { second.cancelChapter(1, 12) }
                    writerEntries.get() shouldBe 1
                    allowPublication.countDown()
                    publication.await() shouldBe true
                    cancellation.await() shouldBe false
                    second.getByChapterId(1)?.status shouldBe TranslationRunStatus.COMPLETE
                    second.getBatch("batch")?.completed shouldBe 1
                } finally {
                    allowPublication.countDown()
                }
            }
        }

    @Test
    fun `multiple repository instances keep every repeated concurrent claim exclusive`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val first = fixture.repo
            val second = TranslationRunRepositoryImpl(fixture.handler, fixture.json)
            for (round in 1..50) {
                val chapterId = 1_000L + round
                createRun(first, chapterId = chapterId, batchToken = "claim-$round", now = round.toLong())
                val claimed = coroutineScope {
                    val one = async { first.claimNext(1_000) }
                    val two = async { second.claimNext(1_000) }
                    listOf(one.await(), two.await()).filterNotNull()
                }
                claimed.size shouldBe 1
                claimed.single().chapterId shouldBe chapterId
                second.getByChapterId(chapterId)?.status shouldBe TranslationRunStatus.RUNNING
                first.cancelChapter(chapterId, 1_001L + round) shouldBe true
            }
        }
    }

    @Test
    fun `pausing implicit reader ownership blocks work and resume retains the current generation`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                val run = createRun(repo, batchToken = "")
                repo.pauseImplicitOwner(1, run.generation, 2) shouldBe true
                val paused = repo.getByChapterId(1).shouldNotBeNull()
                paused.implicitOwner shouldBe true
                paused.implicitPaused shouldBe true
                paused.status shouldBe TranslationRunStatus.PAUSED
                repo.claimNext(3) shouldBe null
                repo.resumeImplicitOwner(1, run.generation, 4) shouldBe true
                val resumed = claim(repo, 5)
                resumed.generation shouldBe run.generation
                resumed.implicitPaused shouldBe false
            }
        }

    @Test
    fun `implicit pause and detach preserve named owners and in flight checkpoint replies`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "")
            createRun(repo, batchToken = "named")
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0)))
            repo.pauseImplicitOwner(1, run.generation, 11) shouldBe true
            repo.isRunnable(run) shouldBe true
            repo.checkpoint(run, listOf(fragment(0, translated = "done")), 99, 12) shouldBe true
            repo.detachImplicitOwner(1, run.generation, 13) shouldBe true
            repo.getByChapterId(1)?.implicitOwner shouldBe false
            repo.isRunnable(run) shouldBe true
            repo.publish(run, 14) {} shouldBe true
            repo.getBatch("named")?.completed shouldBe 1
        }
    }

    @Test
    fun `last implicit owner pause blocks publication but accepts replies then detach is terminal`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                createRun(repo, batchToken = "")
                val run = claim(repo)
                prepare(repo, run, listOf(fragment(0)))
                repo.pauseImplicitOwner(1, run.generation, 11) shouldBe true
                repo.isRunnable(run) shouldBe false
                repo.checkpoint(run, listOf(fragment(0, translated = "done")), 99, 12) shouldBe true
                var publications = 0
                repo.publish(run, 13) { publications++ } shouldBe false
                repo.detachImplicitOwner(1, run.generation, 14) shouldBe true
                repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.CANCELLED
                repo.checkpoint(run, listOf(fragment(0, translated = "late")), 99, 15) shouldBe false
                publications shouldBe 0
            }
        }

    @Test
    fun `explicit owned retry reuses valid partial chunks and original frozen settings only`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val original = """{"snapshotVersion":1,"translationProvider":"GEMINI","geminiModel":"gemini-2.5-pro"}"""
            createRun(repo, batchToken = "", profileJson = original)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0), fragment(1)))
            repo.checkpoint(run, listOf(fragment(0, translated = "retained")), 50, 11)
            repo.release(run, TranslationRunStatus.FAILED, 50, "fixture", now = 12)
            repo.retryOwnedRun(1, run.generation, false, 13) shouldBe true
            val retried = repo.getByChapterId(1).shouldNotBeNull()
            retried.generation shouldBe run.generation + 1
            retried.profileSnapshotJson shouldBe original
            retried.profileFingerprint shouldBe run.profileFingerprint
            retried.implicitOwner shouldBe true
            retried.implicitPaused shouldBe false
            repo.getFragments(retried).map { it.translatedText } shouldBe listOf("retained", null)
        }
    }

    @Test
    fun `forced owned retry forgets current chunks without erasing the old generation`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val original = """{"snapshotVersion":1,"translationProvider":"GEMINI","geminiModel":"gemini-2.5-pro"}"""
            createRun(repo, batchToken = "", profileJson = original)
            val run = claim(repo)
            prepare(repo, run, listOf(fragment(0, translated = "retained")))
            repo.release(run, TranslationRunStatus.FAILED, 99, "fixture", now = 11)
            repo.retryOwnedRun(1, run.generation, true, 12) shouldBe true
            val retried = repo.getByChapterId(1).shouldNotBeNull()
            retried.sourceFingerprint shouldBe null
            retried.forceRetranslate shouldBe true
            retried.profileSnapshotJson shouldBe original
            repo.getFragments(retried) shouldBe emptyList()
            repo.getFragments(run).single().translatedText shouldBe "retained"
        }
    }

    @Test
    fun `stale owner commands and invalid stored retry snapshots never resurrect a run`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val run = createRun(repo, batchToken = "", profileJson = "not-json")
            repo.cancelChapter(1, 2)
            repo.retryOwnedRun(1, run.generation, false, 3) shouldBe false
            repo.getByChapterId(1)?.generation shouldBe run.generation
            repo.invalidateCheckpoint(1, 4)
            val current = repo.getByChapterId(1).shouldNotBeNull()
            repo.pauseImplicitOwner(1, run.generation, 5) shouldBe false
            repo.resumeImplicitOwner(1, run.generation, 5) shouldBe false
            repo.detachImplicitOwner(1, run.generation, 5) shouldBe false
            repo.retryOwnedRun(1, run.generation, false, 5) shouldBe false
            repo.getByChapterId(1) shouldBe current
        }
    }

    @Test
    fun `owned retry of a completed run revalidates source rather than copying old complete chunks`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                val snapshot = """{"snapshotVersion":1,"translationProvider":"GEMINI","geminiModel":"gemini-2.5-pro"}"""
                createRun(repo, batchToken = "", profileJson = snapshot)
                val run = claim(repo)
                prepare(repo, run, listOf(fragment(0, translated = "retained")))
                repo.publish(run, 11) {} shouldBe true
                repo.retryOwnedRun(1, run.generation, false, 12) shouldBe true
                val retried = repo.getByChapterId(1).shouldNotBeNull()
                retried.generation shouldBe run.generation + 1
                retried.sourceFingerprint shouldBe null
                retried.profileSnapshotJson shouldBe snapshot
                repo.getFragments(retried) shouldBe emptyList()
            }
        }

    @Test
    fun `owned retry never replaces an active current run`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val snapshot = """{"snapshotVersion":1,"translationProvider":"GEMINI","geminiModel":"gemini-2.5-pro"}"""
            val run = createRun(repo, batchToken = "", profileJson = snapshot)
            repo.retryOwnedRun(1, run.generation, true, 2) shouldBe false
            repo.getByChapterId(1) shouldBe run
        }
    }

    @Test
    fun `owned retry preserves local http snapshot endpoints and rejects remote http`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            val localSnapshot =
                """{"snapshotVersion":1,"translationProvider":"OLLAMA_CLOUD",""" +
                    """"ollamaCloudBaseUrl":"http://localhost:11434/api","ollamaCloudModel":"llama3"}"""
            val remoteSnapshot =
                """{"snapshotVersion":1,"translationProvider":"OLLAMA_CLOUD",""" +
                    """"ollamaCloudBaseUrl":"http://remote.example/api","ollamaCloudModel":"llama3"}"""

            createRun(repo, chapterId = 1, batchToken = "", profileJson = localSnapshot)
            val localClaimed = claim(repo, now = 10)
            repo.release(localClaimed, TranslationRunStatus.FAILED, 0, "transient", now = 11) shouldBe true
            repo.retryOwnedRun(1, localClaimed.generation, false, 12) shouldBe true
            repo.getByChapterId(1)?.generation shouldBe localClaimed.generation + 1
            repo.cancelChapter(1, 13) shouldBe true

            createRun(repo, chapterId = 2, batchToken = "", profileJson = remoteSnapshot)
            val remoteClaimed = claim(repo, now = 20)
            remoteClaimed.chapterId shouldBe 2L
            repo.release(remoteClaimed, TranslationRunStatus.FAILED, 0, "transient", now = 21) shouldBe true
            repo.retryOwnedRun(2, remoteClaimed.generation, false, 22) shouldBe false
            repo.getByChapterId(2)?.generation shouldBe remoteClaimed.generation
        }
    }

    @Test
    fun `typed action pause is sticky across owner changes and resumes only explicitly`() = runBlocking<Unit> {
        Fixture().use { fixture ->
            val repo = fixture.repo
            createRun(repo, batchToken = "")
            createRun(repo, batchToken = "named")
            val run = claim(repo)
            val namedBefore = repo.getBatch("named")
            repo.parkForAction(1, run.generation, TranslationActionPauseReason.NEEDS_ACTION, 11) shouldBe true
            val parked = repo.getByChapterId(1).shouldNotBeNull()
            parked.generation shouldBe run.generation
            parked.actionPaused shouldBe true
            parked.actionPauseReason shouldBe TranslationActionPauseReason.NEEDS_ACTION
            parked.implicitOwner shouldBe true
            parked.implicitPaused shouldBe false
            repo.getBatch("named") shouldBe namedBefore
            repo.claimNext(12) shouldBe null
            repo.pauseImplicitOwner(1, run.generation, 13) shouldBe true
            repo.resumeImplicitOwner(1, run.generation, 14) shouldBe true
            repo.getByChapterId(1)?.actionPaused shouldBe true
            repo.claimNext(15) shouldBe null
            repo.resumeActionPaused(1, run.generation, 16) shouldBe true
            val resumed = claim(repo, 17)
            resumed.generation shouldBe run.generation
            resumed.actionPaused shouldBe false
            resumed.actionPauseReason shouldBe null
        }
    }

    @Test
    fun `normal ownership pause release keeps race normalization and stale action commands do nothing`() =
        runBlocking<Unit> {
            Fixture().use { fixture ->
                val repo = fixture.repo
                createRun(repo, batchToken = "")
                val run = claim(repo)
                repo.release(run, TranslationRunStatus.PAUSED, 0, null, now = 11) shouldBe true
                repo.getByChapterId(1)?.status shouldBe TranslationRunStatus.PENDING
                repo.getByChapterId(1)?.actionPaused shouldBe false
                repo.cancelChapter(1, 12)
                repo.parkForAction(1, run.generation, TranslationActionPauseReason.CONFIGURATION, 13) shouldBe false
                repo.resumeActionPaused(1, run.generation, 13) shouldBe false
            }
        }

    private suspend fun createRun(
        repo: TranslationRunRepository,
        chapterId: Long = 1,
        batchToken: String = "batch",
        profileJson: String? = PROFILE_JSON,
        profileFingerprint: String = PROFILE_FP,
        force: Boolean = false,
        sessionOnly: Boolean = false,
        priority: Int = 1,
        now: Long = 1,
    ): TranslationRun {
        repo.enqueue(
            chapterIds = listOf(chapterId),
            novelId = 1,
            profileSnapshotJson = profileJson,
            profileFingerprint = profileFingerprint,
            batchToken = batchToken,
            forceRetranslate = force,
            priority = priority,
            now = now,
            sessionOnly = sessionOnly,
        )
        return repo.getByChapterId(chapterId).shouldNotBeNull()
    }

    private suspend fun claim(repo: TranslationRunRepository, now: Long = 10): TranslationRun =
        repo.claimNext(now).shouldNotBeNull()

    private suspend fun prepare(
        repo: TranslationRunRepository,
        run: TranslationRun,
        fragments: List<TranslationFragment>,
    ) {
        repo.prepare(run, "source-v1", PROFILE_FP, fragments.size, fragments, 10) shouldBe true
    }

    private fun fragment(id: Int, translated: String? = null) = TranslationFragment(
        id = id,
        sourceIndex = id,
        partIndex = 0,
        partCount = 1,
        sourceText = "source-$id",
        translatedText = translated,
    )

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val db = createTestNovelDatabase(driver)
        val handler = AndroidNovelDatabaseHandler(db, driver)
        val json = Json { encodeDefaults = true }
        val repo = TranslationRunRepositoryImpl(handler, json)

        override fun close() = driver.close()
    }

    private companion object {
        const val PROFILE_JSON = "{\"provider\":\"fixture\"}"
        const val PROFILE_FP = "profile-fixture"
    }
}

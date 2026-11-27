package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rx.Observable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MangaSourceUpdateLockTest {

    @Test
    fun `concurrent getMangaUpdateLocked calls for same manga are serialized without error`() = runTest {
        val source = GuardedMangaSource(id = 100L)
        val manga = SManga.create().apply {
            url = "/test/entry-1"
            title = "Entry 1"
        }

        // Fire 5 concurrent calls for the exact same manga url
        val deferreds = (1..5).map {
            async {
                source.getMangaUpdateLocked(
                    manga = manga,
                    chapters = emptyList(),
                    fetchDetails = true,
                    fetchChapters = true,
                )
            }
        }

        val results = deferreds.awaitAll()
        results.size shouldBe 5
        source.inFlightViolations.get() shouldBe 0
        source.totalCalls.get() shouldBe 5
    }

    @Test
    fun `concurrent getMangaUpdateLocked calls for different manga do not block each other`() = runTest {
        val source = GuardedMangaSource(id = 200L)
        val mangaA = SManga.create().apply { url = "/test/entry-a" }
        val mangaB = SManga.create().apply { url = "/test/entry-b" }

        val deferredA = async {
            source.getMangaUpdateLocked(mangaA, fetchDetails = true, fetchChapters = false)
        }
        val deferredB = async {
            source.getMangaUpdateLocked(mangaB, fetchDetails = true, fetchChapters = false)
        }

        val results = listOf(deferredA.await(), deferredB.await())
        results.size shouldBe 2
        source.inFlightViolations.get() shouldBe 0
        source.totalCalls.get() shouldBe 2
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cleanup above five hundred keys cannot split a pending source update lock`() = runTest {
        val referenceCaptured = CompletableDeferred<Unit>()
        val allowAcquisition = CompletableDeferred<Unit>()
        val pauseNextAcquisition = AtomicBoolean(true)
        val sourceEntered = CompletableDeferred<Unit>()
        val releaseSource = CompletableDeferred<Unit>()
        val releaseOtherUpdates = CompletableDeferred<Unit>()
        val otherUpdates = mutableListOf<Deferred<Unit>>()
        val source = GuardedMangaSource(id = 300L) {
            sourceEntered.complete(Unit)
            releaseSource.await()
        }
        val manga = SManga.create().apply {
            url = "/test/pending-entry"
            title = "Pending entry"
        }
        val coordinator = MangaSourceUpdateCoordinator { sourceId, url ->
            if (sourceId == source.id && url == manga.url) {
                PausingAcquisitionMutex(referenceCaptured, allowAcquisition, pauseNextAcquisition)
            } else {
                Mutex()
            }
        }

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching {
                coordinator.withLock(source.id, manga.url) {
                    source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
                }
            }
        }
        try {
            referenceCaptured.await()
            // These are live entries, not a cache of completed requests. The pending caller
            // already owns its mutex reference, but has not acquired that mutex yet.
            repeat(500) { index ->
                otherUpdates += async(start = CoroutineStart.UNDISPATCHED) {
                    coordinator.withLock(source.id, "/test/held-$index") { releaseOtherUpdates.await() }
                }
            }
            coordinator.withLock(source.id, "/test/cleanup-trigger") {}

            allowAcquisition.complete(Unit)
            runCurrent()
            sourceEntered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                runCatching {
                    coordinator.withLock(source.id, manga.url) {
                        source.getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true)
                    }
                }
            }

            releaseSource.complete(Unit)
            first.await().isSuccess shouldBe true
            // An evicted mutex gives the second caller a different lock and the source rejects it.
            second.await().isSuccess shouldBe true
        } finally {
            allowAcquisition.complete(Unit)
            releaseSource.complete(Unit)
            releaseOtherUpdates.complete(Unit)
            otherUpdates.awaitAll()
        }
        retainedEntryCount(coordinator) shouldBe 0
    }

    @Test
    fun `successful updates retain no historical source keys`() = runTest {
        val coordinator = MangaSourceUpdateCoordinator()
        repeat(600) { index ->
            coordinator.withLock(400L, "/test/completed-$index") { index } shouldBe index
            retainedEntryCount(coordinator) shouldBe 0
        }
    }

    @Test
    fun `failed updates release their key and preserve the source error`() = runTest {
        val coordinator = MangaSourceUpdateCoordinator()
        val sourceError = IllegalStateException("source unavailable")
        val result = runCatching {
            coordinator.withLock(401L, "/test/failed") { throw sourceError }
        }

        result.exceptionOrNull() shouldBe sourceError
        retainedEntryCount(coordinator) shouldBe 0
        coordinator.withLock(401L, "/test/failed") { "retry" } shouldBe "retry"
        retainedEntryCount(coordinator) shouldBe 0
    }

    @Test
    fun `canceling a queued update retains the holder lock until its last caller finishes`() = runTest {
        val coordinator = MangaSourceUpdateCoordinator()
        val holderEntered = CompletableDeferred<Unit>()
        val releaseHolder = CompletableDeferred<Unit>()
        var canceledBodyEntered = false
        val holder = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock(402L, "/test/queued-cancel") {
                holderEntered.complete(Unit)
                releaseHolder.await()
                "holder"
            }
        }
        try {
            holderEntered.await()
            val canceled = launch(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withLock(402L, "/test/queued-cancel") { canceledBodyEntered = true }
            }
            canceled.cancelAndJoin()
            canceledBodyEntered shouldBe false
            retainedEntryCount(coordinator) shouldBe 1

            val following = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withLock(402L, "/test/queued-cancel") { "following" }
            }
            following.isCompleted shouldBe false
            releaseHolder.complete(Unit)
            holder.await() shouldBe "holder"
            following.await() shouldBe "following"
            retainedEntryCount(coordinator) shouldBe 0
        } finally {
            releaseHolder.complete(Unit)
        }
    }

    @Test
    fun `canceling an active update releases the lock to its waiting caller`() = runTest {
        val coordinator = MangaSourceUpdateCoordinator()
        val holderEntered = CompletableDeferred<Unit>()
        val holder = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock(403L, "/test/active-cancel") {
                holderEntered.complete(Unit)
                awaitCancellation()
            }
        }
        try {
            holderEntered.await()
            val following = async(start = CoroutineStart.UNDISPATCHED) {
                coordinator.withLock(403L, "/test/active-cancel") { "following" }
            }
            following.isCompleted shouldBe false
            holder.cancelAndJoin()

            following.await() shouldBe "following"
            retainedEntryCount(coordinator) shouldBe 0
        } finally {
            holder.cancelAndJoin()
        }
    }

    @Test
    fun `canceling before mutex acquisition releases the pending reference`() = runTest {
        val referenceCaptured = CompletableDeferred<Unit>()
        val allowAcquisition = CompletableDeferred<Unit>()
        val pauseNextAcquisition = AtomicBoolean(true)
        val coordinator = MangaSourceUpdateCoordinator { _, _ ->
            PausingAcquisitionMutex(referenceCaptured, allowAcquisition, pauseNextAcquisition)
        }
        var canceledBodyEntered = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock(404L, "/test/acquisition-cancel") { canceledBodyEntered = true }
        }
        try {
            referenceCaptured.await()
            pending.cancelAndJoin()

            canceledBodyEntered shouldBe false
            retainedEntryCount(coordinator) shouldBe 0
            coordinator.withLock(404L, "/test/acquisition-cancel") { "recovered" } shouldBe "recovered"
            retainedEntryCount(coordinator) shouldBe 0
        } finally {
            allowAcquisition.complete(Unit)
            pending.cancelAndJoin()
        }
    }

    // Observe the resource bound without adding lifecycle or cleanup methods to production.
    private fun retainedEntryCount(coordinator: MangaSourceUpdateCoordinator): Int {
        val field = MangaSourceUpdateCoordinator::class.java.getDeclaredField("locks")
        field.isAccessible = true
        val entries = field.get(coordinator) as Map<*, *>
        return synchronized(entries) { entries.size }
    }

    private class PausingAcquisitionMutex(
        private val referenceCaptured: CompletableDeferred<Unit>,
        private val allowAcquisition: CompletableDeferred<Unit>,
        private val pauseNextAcquisition: AtomicBoolean,
        private val delegate: Mutex = Mutex(),
    ) : Mutex by delegate {
        override suspend fun lock(owner: Any?) {
            if (pauseNextAcquisition.compareAndSet(true, false)) {
                referenceCaptured.complete(Unit)
                allowAcquisition.await()
            }
            delegate.lock(owner)
        }
    }

    private class GuardedMangaSource(
        override val id: Long,
        private val beforeResponse: suspend () -> Unit = { delay(50) },
    ) : MangaSource {
        override val name: String = "GuardedSource"
        override val lang: String = "en"

        val inFlightViolations = AtomicInteger(0)
        val totalCalls = AtomicInteger(0)
        private val inFlight = mutableSetOf<String>()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate {
            synchronized(inFlight) {
                if (!inFlight.add(manga.url)) {
                    inFlightViolations.incrementAndGet()
                    error("getMangaUpdate must not be called concurrently for same manga")
                }
            }
            try {
                beforeResponse()
                totalCalls.incrementAndGet()
                return SMangaUpdate(manga = manga, chapters = chapters)
            } finally {
                synchronized(inFlight) {
                    inFlight.remove(manga.url)
                }
            }
        }

        override suspend fun getMangaDetails(manga: SManga): SManga = manga
        override suspend fun getChapterList(manga: SManga): List<SChapter> = emptyList()
        override suspend fun getPageList(chapter: SChapter): List<Page> = emptyList()

        @Deprecated("Not used")
        override fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw IllegalStateException()

        @Deprecated("Not used")
        override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw IllegalStateException()

        @Deprecated("Not used")
        override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw IllegalStateException()
    }
}

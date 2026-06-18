package eu.kanade.tachiyomi.data.download.engine

import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DownloadEngineFacadeTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupMainDispatcher() {
            Dispatchers.setMain(Dispatchers.Unconfined)
        }

        @JvmStatic
        @AfterAll
        fun resetMainDispatcher() {
            Dispatchers.resetMain()
        }
    }

    // --- DownloadCompletionTracker ---

    @Test
    fun `completion tracker starts at zero`() {
        val tracker = DownloadCompletionTracker()
        tracker.totalCompletions shouldBe 0
    }

    @Test
    fun `completion tracker accumulates across sections`() {
        val tracker = DownloadCompletionTracker()
        tracker.recordCompletion(DownloadSection.MANGA, 3)
        tracker.totalCompletions shouldBe 3
        tracker.recordCompletion(DownloadSection.ANIME, 2)
        tracker.totalCompletions shouldBe 5
        tracker.recordCompletion(DownloadSection.NOVEL, 1)
        tracker.totalCompletions shouldBe 6
    }

    // --- DownloadEngineSnapshot edge cases ---

    @Test
    fun `engine inputs still emit when WorkManager running flows are silent`() = runTest {
        val animeQueue = MutableStateFlow(emptyList<AnimeDownload>())
        val mangaQueue = MutableStateFlow(emptyList<MangaDownload>())
        val novelState = MutableStateFlow(NovelDownloadQueueState())

        val snapshot = withTimeout(250) {
            combineDownloadEngineInputs(
                animeQueueFlow = animeQueue,
                mangaQueueFlow = mangaQueue,
                novelStateFlow = novelState,
                animeRunningFlow = emptyFlow(),
                mangaRunningFlow = emptyFlow(),
                telemetryVersionFlow = flowOf(0L),
                buildSnapshot = { anime, manga, novel ->
                    DownloadEngineSnapshot(
                        animeItems = anime.size,
                        mangaItems = manga.size,
                        novelPending = novel.pendingCount,
                    )
                },
            ).first()
        }

        snapshot.activeCount shouldBe 0
        snapshot.queuedCount shouldBe 0
    }

    @Test
    fun `storage stats refresh does not overlap while previous refresh is running`() {
        shouldStartStorageStatsRefresh(
            nowMs = 10_000L,
            lastRefreshMs = 0L,
            refreshIntervalMs = 5_000L,
            isRefreshRunning = true,
        ) shouldBe false
    }

    @Test
    fun `storage stats refresh starts only after interval elapses`() {
        shouldStartStorageStatsRefresh(
            nowMs = 4_999L,
            lastRefreshMs = 0L,
            refreshIntervalMs = 5_000L,
            isRefreshRunning = false,
        ) shouldBe false

        shouldStartStorageStatsRefresh(
            nowMs = 5_000L,
            lastRefreshMs = 0L,
            refreshIntervalMs = 5_000L,
            isRefreshRunning = false,
        ) shouldBe true
    }

    @Test
    fun `file path free space returns null for pathless SAF directory`() {
        freeSpaceFromFilePath(null) shouldBe null
    }

    @Test
    fun `snapshot with anime-only active items computes counts correctly`() {
        val snapshot = DownloadEngineSnapshot(
            animeItems = 10,
            animeActive = 2,
            animeQueued = 7,
            animeFailed = 1,
        )
        snapshot.activeCount shouldBe 2
        snapshot.queuedCount shouldBe 7
        snapshot.failedCount shouldBe 1
        snapshot.completedCount shouldBe 0
    }

    @Test
    fun `snapshot with mixed running and idle sections`() {
        val snapshot = DownloadEngineSnapshot(
            animeItems = 0,
            animeActive = 0,
            animeQueued = 0,
            animeFailed = 0,
            mangaItems = 5,
            mangaActive = 1,
            mangaQueued = 2,
            mangaFailed = 2,
            novelPending = 8,
            novelActive = 2,
            novelFailed = 0,
            sessionCompleted = 12,
        )
        snapshot.activeCount shouldBe 3 // 0 + 1 + 2
        snapshot.queuedCount shouldBe 10
        snapshot.failedCount shouldBe 2 // 0 + 2 + 0
        snapshot.completedCount shouldBe 12
    }

    @Test
    fun `snapshot with zero items everywhere is idle`() {
        val snapshot = DownloadEngineSnapshot()
        snapshot.activeCount shouldBe 0
        snapshot.queuedCount shouldBe 0
        snapshot.completedCount shouldBe 0
        snapshot.failedCount shouldBe 0
        snapshot.currentSpeedBps shouldBe null
        snapshot.etaMillis shouldBe null
    }

    // --- DownloadTelemetryEmitter NOOP ---

    @Test
    fun `noop emitter records without side effects`() {
        val emitter = DownloadTelemetryEmitter.NOOP
        // Should not throw, should not do anything observable
        emitter.record(DownloadSection.ANIME, "key", 100L, 1000L, System.currentTimeMillis())
        emitter.record(DownloadSection.MANGA, "key2", 0L, 0L, 0L)
    }

    // --- DownloadSpeedTracker idle path ---

    @Test
    fun `speed tracker with no samples returns idle snapshot`() {
        val tracker = DownloadSpeedTracker()
        val snap = tracker.snapshot(remainingBytes = 1024L)
        snap.sampleCount shouldBe 0
        snap.currentSpeedBps shouldBe null
        snap.averageSpeedBps shouldBe null
        snap.etaMillis shouldBe null
    }
}

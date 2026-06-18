package eu.kanade.tachiyomi.data.download.engine

import android.app.Application
import android.app.usage.StorageStatsManager
import android.os.Build
import android.os.storage.StorageManager as AndroidStorageManager
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueManager
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadQueueState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Closeable
import java.io.File

/**
 * Combines anime, manga, and novel backend queue states into one aggregated
 * [DownloadEngineSnapshot] and routes global pause/resume/cancel actions.
 */
class DownloadEngineFacade(
    private val animeManager: AnimeDownloadManager,
    private val mangaManager: MangaDownloadManager,
    private val speedTracker: DownloadSpeedTracker = DownloadSpeedTracker(),
    private val completionTracker: DownloadCompletionTracker = DownloadCompletionTracker(),
) : Closeable {

    private val facadeJob = SupervisorJob()
    private val scope = CoroutineScope(facadeJob + Dispatchers.IO)
    private val telemetryCollector = DownloadTelemetryCollector(speedTracker)
    private val storageManager: StorageManager = Injekt.get()
    private val application: Application? = runCatching { Injekt.get<Application>() }.getOrNull()
    private val storageStatsManager: StorageStatsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        application?.getSystemService(StorageStatsManager::class.java)
    } else {
        null
    }
    private val storageStatsRefreshMs = 5_000L
    private var lastStorageStatsAtMs = 0L
    private var storageStatsJob: Job? = null
    private var cachedAnimeStorageBytes = 0L
    private var cachedMangaStorageBytes = 0L
    private var cachedNovelStorageBytes = 0L
    private var cachedFreeSpaceBytes: Long? = null

    private val _state = MutableStateFlow(DownloadEngineSnapshot())
    val state: StateFlow<DownloadEngineSnapshot> = _state.asStateFlow()
    private var collectJob: Job? = null

    init {
        animeManager.telemetryEmitter = telemetryCollector
        animeManager.completionTracker = completionTracker
        mangaManager.telemetryEmitter = telemetryCollector
        mangaManager.completionTracker = completionTracker
        NovelDownloadQueueManager.telemetryEmitter = telemetryCollector
        NovelDownloadQueueManager.completionTracker = completionTracker

        val animeQueueFlow = animeManager.queueState.transformLatest { queue ->
            if (queue.isEmpty()) {
                emit(queue)
            } else {
                combine(queue.map { it.statusFlow }) { _ -> queue }.collect { emit(it) }
            }
        }

        val mangaQueueFlow = mangaManager.queueState.transformLatest { queue ->
            if (queue.isEmpty()) {
                emit(queue)
            } else {
                combine(queue.map { it.statusFlow }) { _ -> queue }.collect { emit(it) }
            }
        }

        collectJob = scope.launch {
            combineDownloadEngineInputs(
                animeQueueFlow = animeQueueFlow,
                mangaQueueFlow = mangaQueueFlow,
                novelStateFlow = NovelDownloadQueueManager.state,
                animeRunningFlow = animeManager.isDownloaderRunning,
                mangaRunningFlow = mangaManager.isDownloaderRunning,
                telemetryVersionFlow = telemetryCollector.version,
                buildSnapshot = ::buildSnapshot,
            ).collect { snapshot -> _state.update { snapshot } }
        }
    }

    private fun buildSnapshot(
        animeQueue: List<AnimeDownload>,
        mangaQueue: List<MangaDownload>,
        novelState: NovelDownloadQueueState,
    ): DownloadEngineSnapshot {
        val animeActive = animeQueue.count { it.status == AnimeDownload.State.DOWNLOADING }
        val animeQueued = animeQueue.count { it.status == AnimeDownload.State.QUEUE }
        val animeFailed = animeQueue.count { it.status == AnimeDownload.State.ERROR }

        val mangaActive = mangaQueue.count { it.status == MangaDownload.State.DOWNLOADING }
        val mangaQueued = mangaQueue.count { it.status == MangaDownload.State.QUEUE }
        val mangaFailed = mangaQueue.count { it.status == MangaDownload.State.ERROR }
        requestStorageStatsRefreshIfNeeded()
        val hasWork = animeQueue.isNotEmpty() || mangaQueue.isNotEmpty() || novelState.queueCount > 0
        if (!hasWork) {
            speedTracker.reset()
            completionTracker.reset()
        }

        // ETA stays hidden until every backend exposes reliable total bytes.
        // This is intentionally honest: no fake estimate is better than a polished lie.
        val speedSnapshot = speedTracker.snapshot(remainingBytes = null)

        return DownloadEngineSnapshot(
            animeItems = animeQueue.size,
            animeActive = animeActive,
            animeQueued = animeQueued,
            animeFailed = animeFailed,
            mangaItems = mangaQueue.size,
            mangaActive = mangaActive,
            mangaQueued = mangaQueued,
            mangaFailed = mangaFailed,
            novelPending = novelState.pendingCount,
            novelActive = novelState.activeCount,
            novelFailed = novelState.failedCount,
            animeRunning = animeManager.isRunning,
            mangaRunning = mangaManager.isRunning,
            novelRunning = novelState.isRunning,
            sessionCompleted = completionTracker.totalCompletions,
            currentSpeedBps = speedSnapshot.currentSpeedBps,
            averageSpeedBps = speedSnapshot.averageSpeedBps,
            etaMillis = speedSnapshot.etaMillis,
            speedHistoryBps = speedSnapshot.speedHistoryBps,
            animeStorageBytes = cachedAnimeStorageBytes,
            mangaStorageBytes = cachedMangaStorageBytes,
            novelStorageBytes = cachedNovelStorageBytes,
            freeSpaceBytes = cachedFreeSpaceBytes,
        )
    }

    private fun requestStorageStatsRefreshIfNeeded(nowMs: Long = System.currentTimeMillis()) {
        if (!shouldStartStorageStatsRefresh(
                nowMs = nowMs,
                lastRefreshMs = lastStorageStatsAtMs,
                refreshIntervalMs = storageStatsRefreshMs,
                isRefreshRunning = storageStatsJob?.isActive == true,
            )
        ) {
            return
        }

        lastStorageStatsAtMs = nowMs
        storageStatsJob = scope.launch {
            val stats = loadStorageStats()
            cachedAnimeStorageBytes = stats.animeStorageBytes
            cachedMangaStorageBytes = stats.mangaStorageBytes
            cachedNovelStorageBytes = stats.novelStorageBytes
            cachedFreeSpaceBytes = stats.freeSpaceBytes
            _state.update { current ->
                current.copy(
                    animeStorageBytes = stats.animeStorageBytes,
                    mangaStorageBytes = stats.mangaStorageBytes,
                    novelStorageBytes = stats.novelStorageBytes,
                    freeSpaceBytes = stats.freeSpaceBytes,
                )
            }
        }
    }

    private fun loadStorageStats(): DownloadEngineStorageStats {
        val downloadsDir = runCatching { storageManager.getDownloadsDirectory() }.getOrNull()
        return DownloadEngineStorageStats(
            animeStorageBytes = runCatching { animeManager.getDownloadSize() }.getOrDefault(0L),
            mangaStorageBytes = runCatching { mangaManager.getDownloadSize() }.getOrDefault(0L),
            novelStorageBytes = runCatching { NovelDownloadQueueManager.getDownloadSize() }.getOrDefault(0L),
            freeSpaceBytes = resolveDownloadFreeSpaceBytes(downloadsDir),
        )
    }

    private fun resolveDownloadFreeSpaceBytes(downloadsDir: UniFile?): Long? {
        val filePathFreeSpace = runCatching { freeSpaceFromFilePath(downloadsDir?.filePath) }.getOrNull()
        if (filePathFreeSpace != null) return filePathFreeSpace

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val manager = storageStatsManager ?: return null
        return runCatching { manager.getFreeBytes(AndroidStorageManager.UUID_DEFAULT) }.getOrNull()
    }

    fun pauseAll() {
        animeManager.pauseDownloads()
        mangaManager.pauseDownloads()
        NovelDownloadQueueManager.pauseDownloads()
    }

    fun resumeAll() {
        animeManager.startDownloads()
        mangaManager.startDownloads()
        NovelDownloadQueueManager.startDownloads()
    }

    fun cancelAll() {
        animeManager.clearQueue()
        mangaManager.clearQueue()
        NovelDownloadQueueManager.clearQueue()
    }

    override fun close() {
        collectJob?.cancel()
        storageStatsJob?.cancel()
        facadeJob.cancel()
    }
}

private data class DownloadEngineStorageStats(
    val animeStorageBytes: Long,
    val mangaStorageBytes: Long,
    val novelStorageBytes: Long,
    val freeSpaceBytes: Long?,
)

internal fun shouldStartStorageStatsRefresh(
    nowMs: Long,
    lastRefreshMs: Long,
    refreshIntervalMs: Long,
    isRefreshRunning: Boolean,
): Boolean {
    return !isRefreshRunning && nowMs - lastRefreshMs >= refreshIntervalMs
}

internal fun freeSpaceFromFilePath(path: String?): Long? {
    return path
        ?.let(::File)
        ?.takeIf { it.exists() }
        ?.freeSpace
}

internal fun combineDownloadEngineInputs(
    animeQueueFlow: Flow<List<AnimeDownload>>,
    mangaQueueFlow: Flow<List<MangaDownload>>,
    novelStateFlow: Flow<NovelDownloadQueueState>,
    animeRunningFlow: Flow<Boolean>,
    mangaRunningFlow: Flow<Boolean>,
    telemetryVersionFlow: Flow<Long>,
    buildSnapshot: (List<AnimeDownload>, List<MangaDownload>, NovelDownloadQueueState) -> DownloadEngineSnapshot,
): Flow<DownloadEngineSnapshot> {
    return combine(
        animeQueueFlow,
        mangaQueueFlow,
        novelStateFlow,
        animeRunningFlow.onStart { emit(false) },
        mangaRunningFlow.onStart { emit(false) },
        telemetryVersionFlow.onStart { emit(0L) },
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val animeQueue = array[0] as List<AnimeDownload>

        @Suppress("UNCHECKED_CAST")
        val mangaQueue = array[1] as List<MangaDownload>
        val novelState = array[2] as NovelDownloadQueueState
        buildSnapshot(animeQueue, mangaQueue, novelState)
    }
}

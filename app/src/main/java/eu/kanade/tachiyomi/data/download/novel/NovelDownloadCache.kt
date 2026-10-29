package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed interface NovelDownloadCacheEvent {
    data object InvalidateAll : NovelDownloadCacheEvent

    data class ChaptersChanged(
        val novelId: Long,
        val chapterIds: Set<Long>,
        val downloaded: Boolean,
    ) : NovelDownloadCacheEvent

    data class NovelRemoved(
        val novelId: Long,
    ) : NovelDownloadCacheEvent
}

/**
 * Lightweight in-memory cache for per-novel download presence.
 *
 * Novel library screens only need UI feedback, so keeping the latest known download count in
 * memory is enough to avoid repeated filesystem traversal on every database emission.
 */
class NovelDownloadCache(
    private val storageManager: StorageManager = Injekt.get(),
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
    // SupervisorJob: a failure in one coroutine must not cancel the whole
    // scope, otherwise the storageManager/sourceManager subscriptions die
    // and the cache stays empty forever (see issue #141).
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val cacheFileProvider: () -> File = {
        File(Injekt.get<Application>().cacheDir, "dl_novel_cache_v1")
    },
    // Retained for existing test harnesses; production reads IDs once, not count + IDs.
    private val downloadCountLookup: ((Novel) -> Int)? = null,
    private val downloadedChapterIdsLookup: (Novel) -> Set<Long> = { novel ->
        if (storageManager.getDownloadsDirectory() == null) {
            throw IOException("Novel downloads storage is unavailable")
        }
        NovelDownloadManager(downloadCache = null).getDownloadedChapterIds(novel)
    },
) {

    private val cachedCounts = ConcurrentHashMap<Long, Int>()
    private val cachedChapterIds = ConcurrentHashMap<Long, Set<Long>>()
    private val cacheStateLock = Any()
    private val cacheStateVersion = AtomicLong(0L)
    private val storageGeneration = AtomicLong(0L)
    private val novelVersions = HashMap<Long, Long>()
    private val renewalLock = Any()
    private var renewalRequested = false
    private val diskCacheFile: File = cacheFileProvider()

    private val _changes = MutableSharedFlow<NovelDownloadCacheEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes = _changes.asSharedFlow()

    private val _downloadedIds = MutableStateFlow<Set<Long>>(emptySet())
    val downloadedIds: StateFlow<Set<Long>> = _downloadedIds.asStateFlow()

    private var updateDiskCacheJob: Job? = null
    private val writeDiskCacheMutex = Mutex()
    private val diskWriterLock = Any()
    private val scanPermits = Semaphore(2)
    private val isRenewing = AtomicBoolean(false)
    private val pendingCountRefreshIds = ConcurrentHashMap.newKeySet<Long>()

    init {
        _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)

        val restoreVersion = cacheStateVersion.get()
        scope.launch {
            try {
                var restored = false
                if (diskCacheFile.exists()) {
                    val cache = ProtoBuf.decodeFromByteArray<NovelDiskCache>(diskCacheFile.readBytes())
                    val restoredEntries = synchronized(cacheStateLock) {
                        if (cacheStateVersion.get() != restoreVersion) {
                            null
                        } else {
                            cache.data.forEach { (novelId, chapterIds) ->
                                novelVersions[novelId] = cacheStateVersion.incrementAndGet()
                                if (chapterIds.isEmpty()) {
                                    cachedChapterIds.remove(novelId)
                                    cachedCounts.remove(novelId)
                                } else {
                                    cachedChapterIds[novelId] = chapterIds
                                    cachedCounts[novelId] = chapterIds.size
                                }
                            }
                            cache.data.count { it.value.isNotEmpty() }
                        }
                    }
                    if (restoredEntries != null) {
                        logcat(LogPriority.DEBUG) {
                            "NovelDownloadCache: restored $restoredEntries entries from disk cache"
                        }
                        rebuildDownloadedIds()
                        _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)
                        restored = true
                    }
                }
                if (!restored) {
                    renewCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val fileSize = diskCacheFile.length()
                logcat(LogPriority.ERROR, e) {
                    "NovelDownloadCache: failed to restore disk cache (fileSize=${fileSize}B, error=${e::class.simpleName})"
                }
                diskCacheFile.delete()
                renewCache()
            }
        }

        storageManager.changes
            .onEach {
                invalidateAll()
                renewCache()
            }
            .launchIn(scope)

        sourceManager.isInitialized
            .drop(1)
            .onEach { initialized ->
                if (initialized) {
                    logcat(LogPriority.DEBUG) { "NovelDownloadCache: sources initialized, invalidating cache" }
                    // Do not wipe the cache wholesale: renewCache() overwrites
                    // entries itself. During the transition the UI keeps seeing
                    // the old data instead of an empty cache, and never falls
                    // back to a synchronous filesystem scan on the main thread.
                    renewCache()
                }
            }
            .launchIn(scope)
    }

    fun hasAnyDownloadedChapter(novel: Novel): Boolean {
        return getDownloadCount(novel) > 0
    }

    fun getDownloadCount(novel: Novel): Int {
        cachedCounts[novel.id]?.let { return it }
        // Cache miss: do NOT scan the filesystem on the caller (usually main)
        // thread -- that was the source of the freeze. Return 0 and refresh
        // the value in the background.
        scheduleDownloadCountRefresh(novel)
        return 0
    }

    private fun lookupChapterIds(novel: Novel): Set<Long> {
        // Compatibility with count-only harnesses for known-empty entries.
        if (downloadCountLookup?.invoke(novel) == 0) return emptySet()
        return downloadedChapterIdsLookup(novel).toSet()
    }

    private fun scheduleDownloadCountRefresh(novel: Novel) {
        if (!pendingCountRefreshIds.add(novel.id)) return
        scope.launch {
            try {
                val (generation, version) = synchronized(cacheStateLock) {
                    storageGeneration.get() to (novelVersions[novel.id] ?: 0L)
                }
                val ids = scanPermits.withPermit { lookupChapterIds(novel) }
                ensureActive()
                val applied = synchronized(cacheStateLock) {
                    if (storageGeneration.get() != generation ||
                        (novelVersions[novel.id] ?: 0L) != version
                    ) {
                        false
                    } else {
                        applyScannedIds(novel.id, ids)
                        true
                    }
                }
                if (applied) {
                    rebuildDownloadedIds()
                    writeDiskCache()
                    _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) {
                    "NovelDownloadCache: failed to refresh download count for novel ${novel.id}"
                }
            } finally {
                pendingCountRefreshIds.remove(novel.id)
            }
        }
    }

    /** Requires cacheStateLock. Empty IDs are known-empty counts, not unknown entries. */
    private fun applyScannedIds(novelId: Long, ids: Set<Long>) {
        novelVersions[novelId] = cacheStateVersion.incrementAndGet()
        cachedCounts[novelId] = ids.size
        if (ids.isEmpty()) {
            cachedChapterIds.remove(novelId)
        } else {
            cachedChapterIds[novelId] = ids
        }
    }

    fun onChaptersChanged(
        novel: Novel,
        chapterIds: Set<Long>,
        downloaded: Boolean,
    ) {
        val updatedIds = synchronized(cacheStateLock) {
            novelVersions[novel.id] = cacheStateVersion.incrementAndGet()
            val currentIds = cachedChapterIds[novel.id] ?: emptySet()
            val nextIds = if (downloaded) {
                currentIds + chapterIds
            } else {
                currentIds - chapterIds
            }
            if (nextIds.isEmpty()) {
                cachedChapterIds.remove(novel.id)
                cachedCounts[novel.id] = 0
            } else {
                cachedChapterIds[novel.id] = nextIds
                cachedCounts[novel.id] = nextIds.size
            }
            nextIds
        }

        // Log warning for novels with many chapters to monitor memory usage
        if (updatedIds.size > 500) {
            logcat(LogPriority.WARN) {
                "NovelDownloadCache: novel ${novel.id} has ${updatedIds.size} cached chapters, consider memory usage"
            }
        }

        if (updatedIds.isEmpty()) {
            persistDiskCache()
        } else {
            writeDiskCache()
        }
        rebuildDownloadedIds()
        _changes.tryEmit(
            NovelDownloadCacheEvent.ChaptersChanged(
                novelId = novel.id,
                chapterIds = chapterIds,
                downloaded = downloaded,
            ),
        )
    }

    fun onNovelRemoved(novel: Novel) {
        synchronized(cacheStateLock) {
            novelVersions[novel.id] = cacheStateVersion.incrementAndGet()
            cachedCounts.remove(novel.id)
            cachedChapterIds.remove(novel.id)
        }
        persistDiskCache()
        rebuildDownloadedIds()
        _changes.tryEmit(NovelDownloadCacheEvent.NovelRemoved(novel.id))
    }

    fun invalidateAll() {
        synchronized(cacheStateLock) {
            cacheStateVersion.incrementAndGet()
            storageGeneration.incrementAndGet()
            novelVersions.clear()
            cachedCounts.clear()
            cachedChapterIds.clear()
            _downloadedIds.value = emptySet()
        }
        // Serialize deletion with existing writers; never race a direct delete against a write.
        persistDiskCache()
        _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)
    }

    fun getDownloadedChapterIds(novelId: Long): Set<Long>? {
        return cachedChapterIds[novelId]
    }

    fun hasCacheForNovel(novelId: Long): Boolean {
        return cachedChapterIds.containsKey(novelId)
    }

    internal fun updateChapterIds(novelId: Long, chapterIds: Set<Long>) {
        val updatedIds = synchronized(cacheStateLock) {
            novelVersions[novelId] = cacheStateVersion.incrementAndGet()
            if (chapterIds.isEmpty()) {
                cachedChapterIds.remove(novelId)
                cachedCounts[novelId] = 0
            } else {
                cachedChapterIds[novelId] = chapterIds
                cachedCounts[novelId] = chapterIds.size
            }
            chapterIds
        }
        if (updatedIds.isEmpty()) {
            persistDiskCache()
            rebuildDownloadedIds()
            return
        }
        writeDiskCache()
        rebuildDownloadedIds()
    }

    private fun writeDiskCache() {
        writeDiskCache(immediate = false)
    }

    private fun persistDiskCache() {
        // runBlocking used to block the caller thread (including main) while
        // a background write held the mutex. Write asynchronously, but
        // without the debounce delay.
        writeDiskCache(immediate = true)
    }

    private fun writeDiskCache(immediate: Boolean) {
        synchronized(diskWriterLock) {
            updateDiskCacheJob?.cancel()
            updateDiskCacheJob = scope.launch {
                if (!immediate) {
                    delay(2000L)
                }
                writeDiskCacheMutex.withLock {
                    writeDiskCacheSnapshot()
                }
            }
        }
    }

    private fun writeDiskCacheSnapshot() {
        val data = synchronized(cacheStateLock) {
            cachedChapterIds.toMap()
        }
        if (data.isEmpty()) {
            if (diskCacheFile.exists() && !diskCacheFile.delete()) {
                logcat(LogPriority.ERROR) { "NovelDownloadCache: failed to delete empty disk cache" }
            }
            return
        }
        val cache = NovelDiskCache(data = data)
        try {
            val tempFile = File(diskCacheFile.parentFile, "${diskCacheFile.name}.tmp")
            tempFile.writeBytes(ProtoBuf.encodeToByteArray(cache))
            Files.move(
                tempFile.toPath(),
                diskCacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "NovelDownloadCache: failed to write disk cache (${data.size} entries)"
            }
        }
    }

    fun renewCache() {
        synchronized(renewalLock) {
            if (!isRenewing.compareAndSet(false, true)) {
                renewalRequested = true
                return
            }
        }
        scope.launch {
            try {
                val (generation, versions) = synchronized(cacheStateLock) {
                    storageGeneration.get() to novelVersions.toMap()
                }
                val libraryNovels = novelRepository.getLibraryNovel().map { it.novel }
                val readNovels = novelRepository.getReadNovelNotInLibrary()
                val allNovels = (libraryNovels + readNovels).distinctBy { it.id }

                allNovels.forEach { novel ->
                    ensureActive()
                    if (storageGeneration.get() != generation) return@launch
                    val ids = scanPermits.withPermit { lookupChapterIds(novel) }
                    synchronized(cacheStateLock) {
                        if (storageGeneration.get() == generation &&
                            (novelVersions[novel.id] ?: 0L) == (versions[novel.id] ?: 0L)
                        ) {
                            applyScannedIds(novel.id, ids)
                        }
                    }
                }
                rebuildDownloadedIds()
                writeDiskCache()
                _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to renew novel download cache" }
            } finally {
                synchronized(renewalLock) {
                    isRenewing.set(false)
                    if (renewalRequested) {
                        renewalRequested = false
                        renewCache()
                    }
                }
            }
        }
    }

    private fun rebuildDownloadedIds() {
        synchronized(cacheStateLock) {
            _downloadedIds.value = cachedChapterIds.keys.toHashSet()
        }
    }
}

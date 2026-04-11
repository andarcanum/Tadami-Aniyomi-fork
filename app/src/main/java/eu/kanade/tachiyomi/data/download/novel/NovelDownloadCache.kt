package eu.kanade.tachiyomi.data.download.novel

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val cacheFileProvider: () -> File = {
        File(Injekt.get<Application>().cacheDir, "dl_novel_cache_v1")
    },
    private val downloadCountLookup: (Novel) -> Int = { novel ->
        NovelDownloadManager(downloadCache = null).getDownloadCount(novel)
    },
) {

    private val cachedCounts = ConcurrentHashMap<Long, Int>()
    private val cachedChapterIds = ConcurrentHashMap<Long, Set<Long>>()
    private val cacheStateLock = Any()
    private val cacheStateVersion = AtomicLong(0L)
    private val diskCacheFile: File = cacheFileProvider()

    private val _changes = MutableSharedFlow<NovelDownloadCacheEvent>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes = _changes.asSharedFlow()

    init {
        _changes.tryEmit(NovelDownloadCacheEvent.InvalidateAll)

        val restoreVersion = cacheStateVersion.get()
        scope.launch {
            try {
                if (diskCacheFile.exists()) {
                    val cache = ProtoBuf.decodeFromByteArray<NovelDiskCache>(diskCacheFile.readBytes())
                    val restoredEntries = synchronized(cacheStateLock) {
                        if (cacheStateVersion.get() != restoreVersion) {
                            null
                        } else {
                            cache.data.forEach { (novelId, chapterIds) ->
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
                    } else {
                        logcat(LogPriority.DEBUG) {
                            "NovelDownloadCache: skipped disk cache restore because fresher state was already loaded"
                        }
                    }
                }
            } catch (e: Throwable) {
                val fileSize = diskCacheFile.length()
                logcat(LogPriority.ERROR, e) {
                    "NovelDownloadCache: failed to restore disk cache (fileSize=${fileSize}B, error=${e::class.simpleName})"
                }
                diskCacheFile.delete()
            }
        }

        storageManager.changes
            .onEach { invalidateAll() }
            .launchIn(scope)

        sourceManager.isInitialized
            .drop(1)
            .onEach { initialized ->
                if (initialized) {
                    logcat(LogPriority.DEBUG) { "NovelDownloadCache: sources initialized, invalidating cache" }
                    invalidateAll()
                }
            }
            .launchIn(scope)
    }

    fun hasAnyDownloadedChapter(novel: Novel): Boolean {
        return getDownloadCount(novel) > 0
    }

    fun getDownloadCount(novel: Novel): Int {
        return cachedCounts.getOrPut(novel.id) {
            downloadCountLookup(novel)
        }
    }

    fun onChaptersChanged(
        novel: Novel,
        chapterIds: Set<Long>,
        downloaded: Boolean,
    ) {
        val updatedIds = synchronized(cacheStateLock) {
            cacheStateVersion.incrementAndGet()
            val currentIds = cachedChapterIds[novel.id] ?: emptySet()
            val nextIds = if (downloaded) {
                currentIds + chapterIds
            } else {
                currentIds - chapterIds
            }
            if (nextIds.isEmpty()) {
                cachedChapterIds.remove(novel.id)
                cachedCounts.remove(novel.id)
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
            cacheStateVersion.incrementAndGet()
            cachedCounts.remove(novel.id)
            cachedChapterIds.remove(novel.id)
        }
        persistDiskCache()
        _changes.tryEmit(NovelDownloadCacheEvent.NovelRemoved(novel.id))
    }

    fun invalidateAll() {
        synchronized(cacheStateLock) {
            cacheStateVersion.incrementAndGet()
            cachedCounts.clear()
            cachedChapterIds.clear()
        }
        updateDiskCacheJob?.cancel()
        updateDiskCacheJob = null
        diskCacheFile.delete()
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
            cacheStateVersion.incrementAndGet()
            if (chapterIds.isEmpty()) {
                cachedChapterIds.remove(novelId)
                cachedCounts.remove(novelId)
            } else {
                cachedChapterIds[novelId] = chapterIds
                cachedCounts[novelId] = chapterIds.size
            }
            chapterIds
        }
        if (updatedIds.isEmpty()) {
            persistDiskCache()
            return
        }
        writeDiskCache()
    }

    private var updateDiskCacheJob: Job? = null
    private val writeDiskCacheMutex = Mutex()

    private fun writeDiskCache() {
        writeDiskCache(immediate = false)
    }

    private fun persistDiskCache() {
        updateDiskCacheJob?.cancel()
        updateDiskCacheJob = null
        runBlocking {
            writeDiskCacheMutex.withLock {
                writeDiskCacheSnapshot()
            }
        }
    }

    private fun writeDiskCache(immediate: Boolean) {
        updateDiskCacheJob?.cancel()
        updateDiskCacheJob = scope.launch {
            if (!immediate) {
                delay(1000)
            }
            writeDiskCacheMutex.withLock {
                writeDiskCacheSnapshot()
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
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) {
                "NovelDownloadCache: failed to write disk cache (${data.size} entries)"
            }
        }
    }
}

package eu.kanade.tachiyomi.data.download.novel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight in-memory cache for per-novel download presence.
 *
 * Novel library screens only need UI feedback, so keeping the latest known download count in
 * memory is enough to avoid repeated filesystem traversal on every database emission.
 */
class NovelDownloadCache(
    private val storageManager: StorageManager = Injekt.get(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val downloadCountLookup: (Novel) -> Int = { novel ->
        NovelDownloadManager(downloadCache = null).getDownloadCount(novel)
    },
) {

    private val cachedCounts = ConcurrentHashMap<Long, Int>()

    private val _changes = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes = _changes.asSharedFlow()

    init {
        _changes.tryEmit(Unit)
        storageManager.changes
            .onEach { invalidateAll() }
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

    fun onNovelDownloadsChanged(novel: Novel) {
        cachedCounts[novel.id] = downloadCountLookup(novel)
        _changes.tryEmit(Unit)
    }

    fun invalidateAll() {
        cachedCounts.clear()
        _changes.tryEmit(Unit)
    }
}

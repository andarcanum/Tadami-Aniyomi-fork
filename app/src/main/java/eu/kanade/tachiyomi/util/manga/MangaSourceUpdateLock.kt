package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide coordinator for [MangaSource.getMangaUpdate] calls.
 * Extensions built against lib 1.6 (e.g. KeiSource-based like EightMuses) reject concurrent
 * getMangaUpdate invocations for the exact same manga URL.
 *
 * This coordinator ensures any concurrent callers (e.g. MangaScreenModel, Discovery metadata
 * fetchers, background jobs) are serialized per entry rather than crashing with IllegalStateException.
 */
object MangaSourceUpdateLock {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun key(sourceId: Long, mangaUrl: String): String = "$sourceId:$mangaUrl"

    suspend fun <T> withLock(sourceId: Long, mangaUrl: String, block: suspend () -> T): T {
        val mutex = locks.computeIfAbsent(key(sourceId, mangaUrl)) { Mutex() }
        val result = mutex.withLock { block() }
        if (locks.size > 500) {
            locks.entries.removeIf { !it.value.isLocked }
        }
        return result
    }
}

/**
 * Extension on [MangaSource] to execute [MangaSource.getMangaUpdate] serialized per entry URL.
 */
suspend fun MangaSource.getMangaUpdateLocked(
    manga: SManga,
    chapters: List<SChapter> = emptyList(),
    fetchDetails: Boolean,
    fetchChapters: Boolean,
): SMangaUpdate {
    return MangaSourceUpdateLock.withLock(this.id, manga.url) {
        this.getMangaUpdate(manga, chapters, fetchDetails, fetchChapters)
    }
}

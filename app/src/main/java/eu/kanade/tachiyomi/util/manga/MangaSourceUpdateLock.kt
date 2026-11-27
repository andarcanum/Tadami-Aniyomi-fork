package eu.kanade.tachiyomi.util.manga

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide coordinator for [MangaSource.getMangaUpdate] calls.
 * Extensions built against lib 1.6 (e.g. KeiSource-based like EightMuses) reject concurrent
 * getMangaUpdate invocations for the exact same manga URL.
 *
 * This coordinator ensures any concurrent callers (e.g. MangaScreenModel, Discovery metadata
 * fetchers, background jobs) are serialized per entry rather than crashing with IllegalStateException.
 */
object MangaSourceUpdateLock {
    private val coordinator = MangaSourceUpdateCoordinator()

    suspend fun <T> withLock(sourceId: Long, mangaUrl: String, block: suspend () -> T): T {
        return coordinator.withLock(sourceId, mangaUrl, block)
    }
}

internal class MangaSourceUpdateCoordinator(
    private val mutexFactory: (Long, String) -> Mutex = { _, _ -> Mutex() },
) {
    private val locks = mutableMapOf<String, Entry>()

    private fun key(sourceId: Long, mangaUrl: String): String = "$sourceId:$mangaUrl"

    suspend fun <T> withLock(sourceId: Long, mangaUrl: String, block: suspend () -> T): T {
        val entryKey = key(sourceId, mangaUrl)
        // A reference belongs to the caller before acquisition, including suspended waiters.
        val entry = synchronized(locks) {
            locks.getOrPut(entryKey) { Entry(mutexFactory(sourceId, mangaUrl)) }
                .also { it.users++ }
        }
        try {
            return entry.mutex.withLock { block() }
        } finally {
            synchronized(locks) {
                entry.users--
                if (entry.users == 0) {
                    locks.remove(entryKey)
                }
            }
        }
    }

    private class Entry(val mutex: Mutex, var users: Int = 0)
}

/** Chapter-only combined API boundary for migration and deep-link resolution. */
internal suspend fun MangaSource.fetchMangaChaptersForUpdate(manga: SManga): List<SChapter> {
    return getMangaUpdateLocked(
        manga = manga,
        chapters = emptyList(),
        fetchDetails = false,
        fetchChapters = true,
    ).chapters
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

package tachiyomi.domain.series.manga.model

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga

data class LibraryMangaSeries(
    val series: MangaSeries,
    val entries: List<LibraryManga>,
) {
    val id: Long = series.id
    val title: String = series.title
    val categoryId: Long = series.categoryId
    val pinned: Boolean = series.pinned

    val totalChapters: Long
        get() = entries.sumOf { it.totalChapters }

    val readCount: Long
        get() = entries.sumOf { it.readCount }

    val unreadCount: Long
        get() = totalChapters - readCount

    val lastRead: Long
        get() = entries.maxOfOrNull { it.lastRead } ?: 0L

    val latestUpload: Long
        get() = entries.maxOfOrNull { it.latestUpload } ?: 0L

    val hasStarted: Boolean
        get() = readCount > 0

    val activeManga: Manga?
        get() {
            if (entries.isEmpty()) return null

            val lastProgressedIndex = entries.indexOfLast { it.hasStarted }
            if (lastProgressedIndex == -1) return entries.first().manga

            val activeEntry = entries[lastProgressedIndex]
            if (activeEntry.readCount < activeEntry.totalChapters) {
                return activeEntry.manga
            }

            return entries.getOrNull(lastProgressedIndex + 1)?.manga ?: activeEntry.manga
        }

    val coverMangas: List<Manga>
        get() = entries.take(3).map { it.manga }
}

data class LibraryMangaSeriesWithEntryIds(
    val series: LibraryMangaSeries,
    val entryIds: Map<Long, Long>, // mangaId -> entryId
)

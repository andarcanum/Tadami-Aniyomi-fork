package tachiyomi.domain.series.novel.model

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.model.SeriesCoverMode

data class LibraryNovelSeries(
    val series: NovelSeries,
    val entries: List<LibraryNovel>,
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

    val activeNovel: Novel?
        get() {
            if (entries.isEmpty()) return null

            val lastProgressedIndex = entries.indexOfLast { it.hasStarted }
            if (lastProgressedIndex == -1) return entries.first().novel

            val activeEntry = entries[lastProgressedIndex]
            if (activeEntry.readCount < activeEntry.totalChapters) {
                return activeEntry.novel
            }

            return entries.getOrNull(lastProgressedIndex + 1)?.novel ?: activeEntry.novel
        }

    val selectedCoverNovel: Novel?
        get() {
            if (series.coverMode != SeriesCoverMode.ENTRY) return null
            return entries.firstOrNull { it.id == series.coverEntryId }?.novel
        }

    val coverNovels: List<Novel>
        get() {
            val base = entries.take(5).map { it.novel }
            val selected = selectedCoverNovel ?: return base
            return listOf(selected) + base.filterNot { it.id == selected.id }
        }
}

data class LibraryNovelSeriesWithEntryIds(
    val series: LibraryNovelSeries,
    val entryIds: Map<Long, Long>, // novelId -> entryId
)

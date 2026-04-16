package tachiyomi.domain.series.novel.model

import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.novel.LibraryNovel

data class LibraryNovelSeries(
    val series: NovelSeries,
    val entries: List<LibraryNovel>,
) {
    val id: Long = series.id
    val title: String = series.title
    val categoryId: Long = series.categoryId

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

    val coverNovels: List<Novel>
        get() = entries.take(3).map { it.novel }
}

data class LibraryNovelSeriesWithEntryIds(
    val series: LibraryNovelSeries,
    val entryIds: Map<Long, Long>, // novelId -> entryId
)

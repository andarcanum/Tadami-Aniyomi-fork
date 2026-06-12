package eu.kanade.tachiyomi.ui.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

internal val novelReadingOrderComparator =
    compareBy<NovelChapter> { it.chapterNumber }
        .thenBy { it.sourceOrder }
        .thenBy { it.id }

internal fun List<NovelChapter>.sortedByNovelReadingOrder(): List<NovelChapter> {
    return sortedWith(novelReadingOrderComparator)
}

internal fun resolveNovelResumeChapter(
    chapters: List<NovelChapter>,
    fromChapterId: Long? = null,
): NovelChapter? {
    val sortedChapters = chapters.sortedByNovelReadingOrder()
    if (sortedChapters.isEmpty()) return null

    if (fromChapterId != null) {
        val currentIndex = sortedChapters.indexOfFirst { it.id == fromChapterId }
        if (currentIndex >= 0) {
            return sortedChapters[currentIndex]
        }
    }

    sortedChapters.firstOrNull { it.lastPageRead > 0L && !it.read }?.let { return it }

    val lastReadIndex = sortedChapters.indexOfLast { it.read || it.lastPageRead > 0L }
    if (lastReadIndex >= 0) {
        sortedChapters.drop(lastReadIndex + 1).firstOrNull { !it.read }?.let { return it }
        return sortedChapters[lastReadIndex]
    }

    return sortedChapters.firstOrNull { !it.read } ?: sortedChapters.firstOrNull()
}

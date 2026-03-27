package eu.kanade.tachiyomi.ui.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter

internal fun resolveNovelResumeChapter(chapters: List<NovelChapter>): NovelChapter? {
    val sortedChapters = chapters.sortedWith(
        compareBy<NovelChapter> { it.sourceOrder }
            .thenBy { it.chapterNumber }
            .thenBy { it.id },
    )
    if (sortedChapters.isEmpty()) return null

    sortedChapters.firstOrNull { it.lastPageRead > 0L && !it.read }?.let { return it }

    val lastReadIndex = sortedChapters.indexOfLast { it.read || it.lastPageRead > 0L }
    if (lastReadIndex >= 0) {
        sortedChapters.drop(lastReadIndex + 1).firstOrNull { !it.read }?.let { return it }
        return sortedChapters[lastReadIndex]
    }

    return sortedChapters.firstOrNull { !it.read } ?: sortedChapters.firstOrNull()
}

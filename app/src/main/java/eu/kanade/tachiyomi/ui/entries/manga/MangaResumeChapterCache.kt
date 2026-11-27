package eu.kanade.tachiyomi.ui.entries.manga

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.service.getChapterSort

/** Caches the reader order, independently of the title screen's display direction. */
internal class MangaResumeChapterCache {

    @Volatile
    private var entry: Entry? = null

    fun get(chapters: List<ChapterList.Item>, manga: Manga): List<Chapter> {
        val sorting = manga.sorting
        val cached = entry
        if (cached != null && cached.owner === chapters && cached.sorting == sorting) {
            return cached.chapters
        }

        val sorted = chapters.map { it.chapter }
            .sortedWith(getChapterSort(manga, sortDescending = false))
        // Publish one complete entry; concurrent CTA requests still return their own order.
        entry = Entry(chapters, sorting, sorted)
        return sorted
    }

    private class Entry(
        val owner: List<ChapterList.Item>,
        val sorting: Long,
        val chapters: List<Chapter>,
    )
}

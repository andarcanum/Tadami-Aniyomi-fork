package tachiyomi.domain.items.novelchapter.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter

fun getNovelChapterSort(novel: Novel, sortDescending: Boolean = novel.sortDescending()): (
    NovelChapter,
    NovelChapter,
) -> Int {
    return when (novel.sorting) {
        Novel.CHAPTER_SORTING_SOURCE -> when (sortDescending) {
            // Same semantics as manga:
            // `sourceOrder` is usually the index in the source list (0 = newest).
            true -> { c1, c2 -> c1.sourceOrder.compareTo(c2.sourceOrder) }
            false -> { c1, c2 -> c2.sourceOrder.compareTo(c1.sourceOrder) }
        }
        Novel.CHAPTER_SORTING_NUMBER -> when (sortDescending) {
            true -> { c1, c2 -> c2.chapterNumber.compareTo(c1.chapterNumber) }
            false -> { c1, c2 -> c1.chapterNumber.compareTo(c2.chapterNumber) }
        }
        Novel.CHAPTER_SORTING_UPLOAD_DATE -> when (sortDescending) {
            true -> { c1, c2 -> c2.dateUpload.compareTo(c1.dateUpload) }
            false -> { c1, c2 -> c1.dateUpload.compareTo(c2.dateUpload) }
        }
        Novel.CHAPTER_SORTING_ALPHABET -> when (sortDescending) {
            true -> { c1, c2 -> c2.name.compareToWithCollator(c1.name) }
            false -> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        }
        else -> throw NotImplementedError("Invalid novel chapter sorting method: ${novel.sorting}")
    }
}


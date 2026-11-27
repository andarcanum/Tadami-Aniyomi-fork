package tachiyomi.domain.items.chapter.service

import tachiyomi.core.common.util.lang.compareToWithCollator
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

fun getChapterSort(manga: Manga, sortDescending: Boolean = manga.sortDescending()): (
    Chapter,
    Chapter,
) -> Int {
    val readingOrder = when (manga.sorting) {
        Manga.CHAPTER_SORTING_NUMBER -> compareBy<Chapter> { it.chapterNumber }
        Manga.CHAPTER_SORTING_UPLOAD_DATE -> compareBy<Chapter> { it.dateUpload }
        Manga.CHAPTER_SORTING_ALPHABET -> Comparator<Chapter> { c1, c2 -> c1.name.compareToWithCollator(c2.name) }
        else -> compareByDescending<Chapter> { it.sourceOrder }
    }.thenByDescending { it.sourceOrder }
        .thenBy { it.id }

    return { c1, c2 ->
        if (sortDescending) readingOrder.compare(c2, c1) else readingOrder.compare(c1, c2)
    }
}

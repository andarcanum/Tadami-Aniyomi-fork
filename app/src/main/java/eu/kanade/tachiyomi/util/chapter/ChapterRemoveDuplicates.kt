package eu.kanade.tachiyomi.util.chapter

import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Returns a copy of the list with duplicate chapters removed
 */
inline fun <T> List<T>.removeDuplicateChapters(
    currentChapter: Chapter? = null,
    crossinline chapterSelector: (T) -> Chapter,
): List<T> {
    return groupBy { chapterSelector(it).chapterNumber }
        .map { (_, chapters) ->
            chapters.find { currentChapter?.id == chapterSelector(it).id }
                ?: chapters.find { currentChapter?.scanlator == chapterSelector(it).scanlator }
                ?: chapters.first()
        }
}

fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    return removeDuplicateChapters(currentChapter) { it }
}

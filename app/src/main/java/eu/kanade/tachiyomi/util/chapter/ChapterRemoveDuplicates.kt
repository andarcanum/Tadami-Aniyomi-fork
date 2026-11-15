package eu.kanade.tachiyomi.util.chapter

import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Removes duplicate recognized chapter numbers, keeping each unnumbered entry independently.
 */
inline fun <T> List<T>.removeDuplicateChapters(
    currentChapter: Chapter? = null,
    crossinline chapterSelector: (T) -> Chapter,
): List<T> {
    val recognizedGroups = filter { chapterSelector(it).isRecognizedNumber }
        .groupBy { chapterSelector(it).chapterNumber }
    val emittedNumbers = mutableSetOf<Double>()

    return buildList<T> {
        for (item in this@removeDuplicateChapters) {
            val chapter = chapterSelector(item)
            if (!chapter.isRecognizedNumber) {
                add(item)
            } else if (emittedNumbers.add(chapter.chapterNumber)) {
                val chapters = recognizedGroups.getValue(chapter.chapterNumber)
                add(
                    chapters.find { currentChapter?.id == chapterSelector(it).id }
                        ?: chapters.find { currentChapter?.scanlator == chapterSelector(it).scanlator }
                        ?: chapters.first(),
                )
            }
        }
    }
}

fun List<Chapter>.removeDuplicates(currentChapter: Chapter): List<Chapter> {
    return removeDuplicateChapters(currentChapter) { it }
}

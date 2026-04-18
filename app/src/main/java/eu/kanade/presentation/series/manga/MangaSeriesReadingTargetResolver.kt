package eu.kanade.presentation.series.manga

import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.manga.model.LibraryMangaSeries

data class MangaSeriesReadingTarget(
    val manga: LibraryManga,
    val chapter: Chapter,
)

fun resolveMangaSeriesReadingTarget(
    series: LibraryMangaSeries,
    chapters: List<Pair<LibraryManga, List<Chapter>>>,
): MangaSeriesReadingTarget? {
    val activeManga = series.activeManga ?: return null
    val activeEntryIndex = chapters.indexOfFirst { it.first.manga.id == activeManga.id }
        .takeIf { it >= 0 }
        ?: 0

    var fallbackTarget: MangaSeriesReadingTarget? = null
    for ((manga, mangaChapters) in chapters.drop(activeEntryIndex)) {
        val chapter = resolveMangaResumeChapter(mangaChapters) ?: continue
        val target = MangaSeriesReadingTarget(
            manga = manga,
            chapter = chapter,
        )

        if (fallbackTarget == null) {
            fallbackTarget = target
        }

        if (mangaChapters.any { !it.read }) {
            return target
        }
    }

    return fallbackTarget
}

internal fun resolveMangaResumeChapter(chapters: List<Chapter>): Chapter? {
    val sortedChapters = chapters.sortedWith(
        compareBy<Chapter> { it.chapterNumber }
            .thenBy { it.sourceOrder }
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

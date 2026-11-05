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
    resumeChapterId: Long? = null,
): MangaSeriesReadingTarget? {
    if (resumeChapterId != null) {
        for ((manga, mangaChapters) in chapters) {
            if (series.entries.none { it.id == manga.id }) continue
            val chapter = mangaChapters.firstOrNull { it.id == resumeChapterId } ?: continue
            return MangaSeriesReadingTarget(manga, chapter)
        }
    }

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

internal val mangaChapterResumeComparator: Comparator<Chapter> =
    compareBy<Chapter> { it.chapterNumber }
        .thenBy { it.sourceOrder }
        .thenBy { it.id }

internal fun resolveMangaResumeChapter(
    chapters: List<Chapter>,
    fromChapterId: Long? = null,
): Chapter? {
    return resolveMangaResumeChapterFromSorted(
        sortedChapters = chapters.sortedWith(mangaChapterResumeComparator),
        fromChapterId = fromChapterId,
    )
}

/**
 * Resume resolution over an already-sorted chapter list; skips the sort so hot paths
 * (e.g. the "Continue" CTA) can cache the ordering themselves.
 */
internal fun resolveMangaResumeChapterFromSorted(
    sortedChapters: List<Chapter>,
    fromChapterId: Long? = null,
): Chapter? {
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

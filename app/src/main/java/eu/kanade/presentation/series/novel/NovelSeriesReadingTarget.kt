package eu.kanade.presentation.series.novel

import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries

data class NovelSeriesReadingTarget(
    val novel: LibraryNovel,
    val chapter: NovelChapter,
)

fun resolveNovelSeriesReadingTarget(
    series: LibraryNovelSeries,
    chapters: List<Pair<LibraryNovel, List<NovelChapter>>>,
    bookStateOf: (Long) -> NovelBookState? = { null },
): NovelSeriesReadingTarget? {
    val activeNovel = series.activeNovel ?: return null
    val activeEntryIndex = chapters.indexOfFirst { it.first.novel.id == activeNovel.id }
        .takeIf { it >= 0 }
        ?: 0

    var fallbackTarget: NovelSeriesReadingTarget? = null
    for ((novel, novelChapters) in chapters.drop(activeEntryIndex)) {
        // Book-mode entries keep their position in the book state; the resolver overload without
        // it fell back to the per-chapter heuristic and targeted the wrong chapter.
        val chapter = resolveNovelResumeChapter(novelChapters, null, bookStateOf(novel.novel.id)) ?: continue
        val target = NovelSeriesReadingTarget(
            novel = novel,
            chapter = chapter,
        )

        if (fallbackTarget == null) {
            fallbackTarget = target
        }

        if (novelChapters.any { !it.read }) {
            return target
        }
    }

    return fallbackTarget
}

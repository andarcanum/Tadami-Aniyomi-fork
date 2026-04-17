package eu.kanade.presentation.series.novel

import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.series.novel.model.LibraryNovelSeries
import eu.kanade.tachiyomi.ui.novel.resolveNovelResumeChapter

data class NovelSeriesReadingTarget(
    val novel: LibraryNovel,
    val chapter: NovelChapter,
)

fun resolveNovelSeriesReadingTarget(
    series: LibraryNovelSeries,
    chapters: List<Pair<LibraryNovel, List<NovelChapter>>>,
): NovelSeriesReadingTarget? {
    val activeNovel = series.activeNovel ?: return null
    val activeEntry = chapters.firstOrNull { it.first.novel.id == activeNovel.id } ?: return null
    val chapter = resolveNovelResumeChapter(activeEntry.second) ?: return null
    return NovelSeriesReadingTarget(
        novel = activeEntry.first,
        chapter = chapter,
    )
}

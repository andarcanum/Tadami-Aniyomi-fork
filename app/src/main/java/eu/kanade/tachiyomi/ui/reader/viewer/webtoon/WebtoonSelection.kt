package eu.kanade.tachiyomi.ui.reader.viewer.webtoon

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

/** Routes the active RecyclerView item to the reader's existing progress/preload callbacks. */
internal fun dispatchWebtoonSelection(
    item: Any,
    currentChapter: ReaderChapter?,
    nextChapter: ReaderChapter?,
    allowChapterCompletion: Boolean,
    lastCompletionPage: ReaderPage? = null,
    selectionChanged: Boolean = true,
    onPageSelected: (ReaderPage) -> Unit,
    onTransitionSelected: (ChapterTransition) -> Unit,
) {
    // A fling can skip the final page's selection callback entirely. Preloaded chapters may have
    // no transition item at all, so both forward boundary paths must report that page first.
    val crossedChapterEnd = when (item) {
        is ChapterTransition.Next -> item.from === currentChapter
        is ReaderPage -> item.chapter === nextChapter
        else -> false
    }
    if (allowChapterCompletion && crossedChapterEnd && currentChapter?.chapter?.read == false) {
        currentChapter.pages?.lastOrNull()
            ?.takeIf { it !== lastCompletionPage && it.status == Page.State.READY }
            ?.let(onPageSelected)
    }
    if (!selectionChanged) return
    when (item) {
        is ReaderPage -> onPageSelected(item)
        is ChapterTransition -> onTransitionSelected(item)
    }
}

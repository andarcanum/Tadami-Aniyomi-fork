package eu.kanade.presentation.reader.novel

import android.webkit.WebView
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelAutoScrollChapterEndBehavior
import kotlinx.coroutines.delay

/**
 * Chapter navigation and auto-scroll end handling, extracted from [NovelReaderContentHost].
 *
 * The navigator is deliberately kept outside `remember` semantics: every dependency is a provider
 * lambda read at call time, so the instance never captures a stale snapshot of the reader state.
 * Construct it once per composition and call the methods directly.
 */
internal class NovelReaderChapterNavigator(
    private val state: () -> NovelReaderScreenModel.State.Success,
    private val isBookMode: () -> Boolean,
    private val webViewInstance: () -> WebView?,
    private val onOpenPreviousChapter: ((Long) -> Unit)?,
    private val onOpenNextChapter: ((Long) -> Unit)?,
    private val onPrepareAutoScrollHandoff: (Long, Int) -> Unit,
    private val onCancelAutoScrollHandoff: () -> Unit,
    private val bookContentHandle: () -> NovelBookContentHandle,
    private val showReaderUi: () -> Boolean,
    private val autoScrollEnabled: () -> Boolean,
    private val setAutoScrollEnabled: (Boolean) -> Unit,
    private val autoScrollSpeed: () -> Int,
    private val setAutoScrollEndStableFrames: (Int) -> Unit,
    private val autoScrollEndDwellActive: () -> Boolean,
    private val setAutoScrollEndDwellActive: (Boolean) -> Unit,
    private val setAutoScrollEndDwellRemainingSeconds: (Int) -> Unit,
    private val pageTurnChapterNavigationRequest: () -> PageTurnChapterNavigationRequest?,
    private val setPageTurnChapterNavigationRequest: (PageTurnChapterNavigationRequest?) -> Unit,
    private val pageTurnChapterNavigationRequestToken: () -> Long,
    private val setPageTurnChapterNavigationRequestToken: (Long) -> Unit,
) {
    fun requestPageTurnChapterNavigation(direction: PageTurnChapterNavigationDirection) {
        val token = pageTurnChapterNavigationRequestToken() + 1L
        setPageTurnChapterNavigationRequestToken(token)
        setPageTurnChapterNavigationRequest(
            PageTurnChapterNavigationRequest(
                direction = direction,
                token = token,
            ),
        )
    }

    fun openPreviousChapterFromReader() {
        val currentState = state()
        val chapterId = currentState.previousChapterId ?: return
        // Over a book the "chapter switch" is an in-book seek: state.chapter.id never changes, so a
        // handoff mark set here would never be consumed and would leak into the next page-reader
        // session of ANY novel (opening it at END and falsely tripping its read threshold).
        if (!isBookMode()) {
            NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
                NovelReaderPageReaderHandoffTarget.END,
            )
        }
        // A seamless in-place chapter switch can detach the reader WebView (renderer may change
        // between chapters). If the WebView still holds view focus when it is detached, ViewGroup
        // restarts a focus search from the window root while Compose is applying the composition,
        // which synchronously remeasures the lazy layout and disposes subcompositions mid-pass
        // ("Cannot start a writer when another writer is pending"). Dropping focus first avoids it.
        webViewInstance()?.clearFocus()
        onOpenPreviousChapter?.invoke(chapterId)
    }

    // True only on the last section of the spine: the single place where auto-scroll over a book is
    // really out of content.
    fun isAtEndOfBook(): Boolean {
        val sectionCount = state().bookMode.sectionCount
        return sectionCount <= 0 || state().bookMode.currentSectionIndex >= sectionCount - 1
    }

    fun openNextChapterFromReader() {
        val currentState = state()
        val chapterId = currentState.nextChapterId ?: return
        // Same book-mode reasoning as openPreviousChapterFromReader: no page-reader handoff happens
        // over a book, so no mark may be left behind for a later session to consume.
        if (!isBookMode()) {
            NovelReaderChapterHandoffPolicy.markInternalChapterHandoff(
                NovelReaderPageReaderHandoffTarget.START,
            )
        }
        webViewInstance()?.clearFocus()
        onOpenNextChapter?.invoke(chapterId)
    }

    fun handleAutoScrollChapterEnd() {
        if (isBookMode()) {
            // A book has no chapter boundary to hand off at: the spine continues inside the same
            // document and the next section is stitched in on demand. Treating the end of the
            // resident window as the end of a chapter is what stopped auto-scroll mid-book (or, with
            // continuous reading, kicked the reader out into the next chapter).
            if (!isAtEndOfBook()) {
                setAutoScrollEndStableFrames(0)
                setAutoScrollEndDwellActive(false)
                return
            }
            setAutoScrollEnabled(false)
            setAutoScrollEndStableFrames(0)
            setAutoScrollEndDwellActive(false)
            onCancelAutoScrollHandoff()
            return
        }
        val currentState = state()
        val nextChapterId = currentState.nextChapterId
        val behavior = currentState.readerSettings.autoScrollChapterEndBehavior
        if (!shouldAutoScrollAdvanceToNextChapter(behavior, nextChapterId != null) || nextChapterId == null) {
            setAutoScrollEnabled(false)
            setAutoScrollEndStableFrames(0)
            setAutoScrollEndDwellActive(false)
            onCancelAutoScrollHandoff()
            return
        }
        if (shouldAutoScrollContinueAcrossChapters(behavior)) {
            onPrepareAutoScrollHandoff(nextChapterId, autoScrollSpeed())
        } else {
            onCancelAutoScrollHandoff()
        }
        setAutoScrollEnabled(false)
        setAutoScrollEndStableFrames(0)
        setAutoScrollEndDwellActive(false)
        openNextChapterFromReader()
    }

    suspend fun handleAutoScrollStableChapterEndAfterDwell() {
        if (isBookMode() && !isAtEndOfBook()) {
            // Not the end of anything the reader should pause at - only the end of the sections that
            // are currently resident.
            setAutoScrollEndStableFrames(0)
            return
        }
        val behavior = state().readerSettings.autoScrollChapterEndBehavior
        if (behavior == NovelAutoScrollChapterEndBehavior.StopAtEnd) {
            setAutoScrollEnabled(false)
            setAutoScrollEndStableFrames(0)
            setAutoScrollEndDwellActive(false)
            onCancelAutoScrollHandoff()
            return
        }

        setAutoScrollEndStableFrames(0)
        val endPauseMs = state().readerSettings.autoScrollEndPauseMs
        val totalSeconds = ((endPauseMs + 999L) / 1000L).toInt()
        setAutoScrollEndDwellRemainingSeconds(totalSeconds)
        setAutoScrollEndDwellActive(true)

        for (sec in totalSeconds downTo 1) {
            setAutoScrollEndDwellRemainingSeconds(sec)
            delay(1000L)
            if (!autoScrollEnabled() || showReaderUi() || !autoScrollEndDwellActive()) return
        }

        setAutoScrollEndDwellRemainingSeconds(0)
        setAutoScrollEndDwellActive(false)
        handleAutoScrollChapterEnd()
    }
}

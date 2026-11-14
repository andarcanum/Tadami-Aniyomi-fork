package eu.kanade.presentation.reader.novel

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

internal class NovelPageReaderLayoutIdentity(val generation: Long)

@Composable
internal fun rememberNovelPageReaderLayoutIdentity(
    ownerKey: Any,
    contentPages: List<NovelPageContentPage>,
    viewportSize: IntSize,
    spreadColumns: Int,
    rendererRoute: NovelPageReaderRendererRoute,
): NovelPageReaderLayoutIdentity {
    val sequence = remember { longArrayOf(0L) }
    // Equivalent page output in the same geometry is the same layout. A geometry round trip
    // is a new identity even if its page list equals the last acknowledged portrait layout.
    return remember(ownerKey, contentPages, viewportSize, spreadColumns, rendererRoute) {
        NovelPageReaderLayoutIdentity(++sequence[0])
    }
}

internal suspend fun restoreNovelPagerPageAfterUserInput(
    pagerState: PagerState,
    targetPage: Int,
): Boolean {
    return try {
        pagerState.scrollToPage(targetPage)
        true
    } catch (cancelled: CancellationException) {
        currentCoroutineContext().ensureActive()
        // UserInput wins over a Default mutation. Do not acknowledge the reflow while the pager is
        // still between pages; after it settles the caller captures the actual user-selected page.
        snapshotFlow { pagerState.isScrollInProgress }.first { inProgress -> !inProgress }
        false
    }
}

internal suspend fun restoreNovelNativeScrollViewportAnchor(
    listState: LazyListState,
    anchor: NovelNativeScrollViewportAnchor,
    targetItemIndex: Int,
): Boolean {
    return try {
        // scrollToItem forces a remeasurement in the current constraints before it returns.
        // The old row may be entirely above the viewport after its retained pixel offset outgrows it.
        listState.scrollToItem(targetItemIndex, 0)
        val newItemSize = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == targetItemIndex }
            ?.size ?: 0
        val targetOffsetPx = resolveAnchoredNativeScrollOffsetPx(
            withinItemFraction = anchor.withinItemFraction,
            newItemHeightPx = newItemSize,
        )
        if (targetOffsetPx > 0) listState.scrollToItem(targetItemIndex, targetOffsetPx)
        true
    } catch (cancelled: CancellationException) {
        // A real lifecycle/layout-generation cancellation must propagate. A higher-priority
        // UserInput mutation only rejects this restore; the user's current position wins and
        // the caller's long-lived tracker must keep running.
        currentCoroutineContext().ensureActive()
        false
    }
}

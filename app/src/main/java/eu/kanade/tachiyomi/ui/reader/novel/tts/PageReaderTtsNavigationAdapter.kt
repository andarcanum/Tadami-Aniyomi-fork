package eu.kanade.tachiyomi.ui.reader.novel.tts

interface PageReaderTtsNavigator {
    suspend fun scrollToPage(pageIndex: Int)
}

class PageReaderTtsNavigationAdapter(
    private val navigator: PageReaderTtsNavigator,
) : NovelTtsNavigationAdapter {
    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        val targetPage = segment.pageCandidates.firstOrNull() ?: return
        navigator.scrollToPage(targetPage)
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        return NovelTtsNavigationAnchor(pageIndex = pageIndex)
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        anchor.pageIndex?.let { navigator.scrollToPage(it) }
    }
}

package eu.kanade.tachiyomi.ui.reader.novel.tts

interface NativeScrollTtsNavigator {
    suspend fun scrollToBlock(blockIndex: Int, scrollOffsetPx: Int = 0)
}

class NativeScrollTtsNavigationAdapter(
    private val navigator: NativeScrollTtsNavigator,
) : NovelTtsNavigationAdapter {
    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        navigator.scrollToBlock(segment.sourceBlockIndex)
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        return NovelTtsNavigationAnchor(
            blockIndex = blockIndex,
            scrollOffsetPx = scrollOffsetPx,
        )
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        anchor.blockIndex?.let { navigator.scrollToBlock(it, anchor.scrollOffsetPx) }
    }
}

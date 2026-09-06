package eu.kanade.tachiyomi.ui.reader.novel.tts

import eu.kanade.presentation.reader.novel.NovelTtsHighlightCss
import eu.kanade.presentation.reader.novel.buildWebReaderTtsSyncJavascript

interface WebViewTtsNavigator {
    suspend fun evaluateJavascript(script: String): String?
}

class WebViewTtsNavigationAdapter(
    private val navigator: WebViewTtsNavigator,
    private val totalBlocks: Int,
    /** Theme-derived highlight colors, resolved per call so a theme/background change applies live. */
    private val highlightCss: () -> NovelTtsHighlightCss = { NovelTtsHighlightCss.DEFAULT },
) : NovelTtsNavigationAdapter {
    override suspend fun syncToSegment(segment: NovelTtsSegment) {
        val progressPercent = resolveProgressPercent(segment.sourceBlockIndex)
        navigator.evaluateJavascript(
            buildWebReaderTtsSyncJavascript(
                snippet = segment.text,
                progressPercent = progressPercent,
                highlightCss = highlightCss(),
            ),
        )
    }

    override fun captureManualAnchor(
        pageIndex: Int?,
        blockIndex: Int?,
        scrollOffsetPx: Int,
    ): NovelTtsNavigationAnchor {
        return NovelTtsNavigationAnchor(
            blockIndex = blockIndex,
            scrollOffsetPx = scrollOffsetPx,
            webProgressPercent = resolveProgressPercent(blockIndex ?: 0),
        )
    }

    override suspend fun restorePosition(anchor: NovelTtsNavigationAnchor) {
        val progressPercent = anchor.webProgressPercent ?: resolveProgressPercent(anchor.blockIndex ?: 0)
        navigator.evaluateJavascript(
            buildWebReaderTtsSyncJavascript(
                snippet = "",
                progressPercent = progressPercent,
                highlightCss = highlightCss(),
            ),
        )
    }

    private fun resolveProgressPercent(sourceBlockIndex: Int): Int {
        if (totalBlocks <= 0) return 0
        return (((sourceBlockIndex + 1).toFloat() / totalBlocks.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
    }
}

package eu.kanade.tachiyomi.ui.reader

import kotlin.math.abs

private const val WEBTOON_SCROLL_MARKER = 7_000_000_000L
private const val WEBTOON_SCROLL_RATIO_MARKER = 8_000_000_000L
private const val WEBTOON_SCROLL_OFFSET_BASE = 1_000_000L

internal data class WebtoonScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val pageHeightPx: Int = 0,
    val chapterId: Long? = null,
    val offsetRatioPpm: Int? = null,
)

internal data class ChapterScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val offsetRatioPpm: Int? = null,
)

internal fun encodeWebtoonScrollProgress(
    index: Int,
    offsetPx: Int,
    pageHeightPx: Int = 0,
): Long {
    val safeIndex = index.coerceAtLeast(0).toLong()
    if (pageHeightPx > 0) {
        val safeOffset = offsetPx.coerceAtLeast(0).toLong()
        val safeHeight = pageHeightPx.coerceAtLeast(1).toLong()
        val ratioPpm = ((safeOffset * WEBTOON_SCROLL_OFFSET_BASE) / safeHeight)
            .coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1)
        return WEBTOON_SCROLL_RATIO_MARKER + (safeIndex * WEBTOON_SCROLL_OFFSET_BASE) + ratioPpm
    }

    val safeOffset = offsetPx.coerceIn(0, (WEBTOON_SCROLL_OFFSET_BASE - 1).toInt()).toLong()
    return WEBTOON_SCROLL_MARKER + (safeIndex * WEBTOON_SCROLL_OFFSET_BASE) + safeOffset
}

internal fun decodeWebtoonScrollProgress(value: Long): WebtoonScrollProgress? {
    if (value >= WEBTOON_SCROLL_RATIO_MARKER) {
        val payload = value - WEBTOON_SCROLL_RATIO_MARKER
        val index = (payload / WEBTOON_SCROLL_OFFSET_BASE)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        val ratioPpm = (payload % WEBTOON_SCROLL_OFFSET_BASE)
            .coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1)
            .toInt()
        return WebtoonScrollProgress(
            index = index,
            offsetPx = 0,
            offsetRatioPpm = ratioPpm,
        )
    }

    if (value < WEBTOON_SCROLL_MARKER) return null

    val payload = value - WEBTOON_SCROLL_MARKER
    val index = (payload / WEBTOON_SCROLL_OFFSET_BASE)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
    val offset = (payload % WEBTOON_SCROLL_OFFSET_BASE)
        .coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1)
        .toInt()

    return WebtoonScrollProgress(index = index, offsetPx = offset)
}

internal fun decodeStoredChapterProgress(
    value: Long,
    restoreOffset: Boolean,
): ChapterScrollProgress {
    val decoded = decodeWebtoonScrollProgress(value)
    if (decoded != null) {
        return ChapterScrollProgress(
            index = decoded.index,
            offsetPx = if (restoreOffset) decoded.offsetPx else 0,
            offsetRatioPpm = if (restoreOffset) decoded.offsetRatioPpm else null,
        )
    }
    val legacyIndex = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    return ChapterScrollProgress(index = legacyIndex, offsetPx = 0)
}

internal fun resolveWebtoonRestoreOffsetPx(
    progress: ChapterScrollProgress,
    currentPageHeightPx: Int,
): Int {
    val ratioPpm = progress.offsetRatioPpm
    if (ratioPpm != null && currentPageHeightPx > 0) {
        return ((ratioPpm.toLong() * currentPageHeightPx.toLong()) / WEBTOON_SCROLL_OFFSET_BASE)
            .coerceAtLeast(0L)
            .toInt()
    }
    return progress.offsetPx.coerceAtLeast(0)
}

internal data class WebtoonRestoreSettleDecision(
    val stableHeightFrames: Int,
    val readyFrames: Int,
    val settled: Boolean,
    val canClearPending: Boolean,
)

internal fun evaluateWebtoonRestoreSettle(
    currentOffsetPx: Int,
    targetOffsetPx: Int,
    currentPageHeightPx: Int,
    previousPageHeightPx: Int,
    previousStableHeightFrames: Int,
    isPageReady: Boolean,
    imageDecoded: Boolean,
    previousReadyFrames: Int,
    minStableHeightFrames: Int,
    offsetTolerancePx: Int,
    minReadyFramesFallback: Int,
): WebtoonRestoreSettleDecision {
    val stableHeightFrames = if (currentPageHeightPx == previousPageHeightPx) {
        previousStableHeightFrames + 1
    } else {
        0
    }
    val readyFrames = if (isPageReady) previousReadyFrames + 1 else 0
    val settled = abs(currentOffsetPx - targetOffsetPx) <= offsetTolerancePx &&
        stableHeightFrames >= minStableHeightFrames
    val hasReadySignal = imageDecoded || readyFrames >= minReadyFramesFallback

    return WebtoonRestoreSettleDecision(
        stableHeightFrames = stableHeightFrames,
        readyFrames = readyFrames,
        settled = settled,
        canClearPending = settled && hasReadySignal,
    )
}

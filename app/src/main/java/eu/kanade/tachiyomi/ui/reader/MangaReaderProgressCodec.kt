package eu.kanade.tachiyomi.ui.reader

import kotlin.math.abs

private const val PAGED_CHAPTER_PROGRESS_MARKER = 4_000_000_000L
private const val PAGED_CHAPTER_PROGRESS_END = 5_000_000_000L
private const val PAGED_CHAPTER_TOTAL_BASE = 100_000L
private const val WEBTOON_SCROLL_MARKER = 7_000_000_000L
private const val WEBTOON_SCROLL_RATIO_MARKER = 8_000_000_000L
private const val WEBTOON_SCROLL_OFFSET_BASE = 1_000_000L
private const val WEBTOON_SCROLL_WITH_TOTAL_MARKER = 900_000_000_000_000L
private const val WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE = 2_000_000_000_000L
private const val WEBTOON_SCROLL_WITH_TOTAL_TOTAL_BASE = 2_000_000L
private const val WEBTOON_SCROLL_WITH_TOTAL_RATIO_OFFSET = 1_000_000L

internal data class PagedChapterProgress(
    val index: Int,
    val totalPages: Int,
)

internal data class WebtoonScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val pageHeightPx: Int = 0,
    val chapterId: Long? = null,
    val offsetRatioPpm: Int? = null,
    val totalPages: Int? = null,
)

internal data class ChapterScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val offsetRatioPpm: Int? = null,
)

internal fun encodePagedChapterProgress(
    index: Int,
    totalPages: Int,
): Long {
    val safeTotalPages = totalPages.coerceAtLeast(1).coerceAtMost((PAGED_CHAPTER_TOTAL_BASE - 1).toInt())
    val safeIndex = index.coerceIn(0, safeTotalPages - 1)
    return PAGED_CHAPTER_PROGRESS_MARKER + (safeIndex.toLong() * PAGED_CHAPTER_TOTAL_BASE) + safeTotalPages.toLong()
}

internal fun decodePagedChapterProgress(value: Long): PagedChapterProgress? {
    if (value < PAGED_CHAPTER_PROGRESS_MARKER || value >= PAGED_CHAPTER_PROGRESS_END) return null
    val payload = value - PAGED_CHAPTER_PROGRESS_MARKER
    val index = (payload / PAGED_CHAPTER_TOTAL_BASE)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
    val totalPages = (payload % PAGED_CHAPTER_TOTAL_BASE)
        .coerceIn(1L, (PAGED_CHAPTER_TOTAL_BASE - 1))
        .toInt()
    return PagedChapterProgress(
        index = index.coerceIn(0, totalPages - 1),
        totalPages = totalPages,
    )
}

internal fun encodeWebtoonScrollProgress(
    index: Int,
    offsetPx: Int,
    pageHeightPx: Int = 0,
    totalPages: Int? = null,
): Long {
    val safeOffset = offsetPx.coerceIn(0, (WEBTOON_SCROLL_OFFSET_BASE - 1).toInt()).toLong()
    val ratioPpm = if (pageHeightPx > 0) {
        val safeOffsetForRatio = offsetPx.coerceAtLeast(0).toLong()
        val safeHeight = pageHeightPx.coerceAtLeast(1).toLong()
        ((safeOffsetForRatio * WEBTOON_SCROLL_OFFSET_BASE) / safeHeight)
            .coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1)
    } else {
        null
    }

    if (totalPages != null) {
        val maxIndex = ((Long.MAX_VALUE - WEBTOON_SCROLL_WITH_TOTAL_MARKER) / WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val safeTotalPages = totalPages.coerceAtLeast(1)
            .coerceAtMost(((WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE / WEBTOON_SCROLL_WITH_TOTAL_TOTAL_BASE) - 1).toInt())
            .coerceAtMost(maxIndex + 1)
        val safeIndex = index.coerceIn(0, safeTotalPages - 1).toLong()
        val positionPayload = if (ratioPpm != null) {
            WEBTOON_SCROLL_WITH_TOTAL_RATIO_OFFSET + ratioPpm
        } else {
            safeOffset
        }
        return WEBTOON_SCROLL_WITH_TOTAL_MARKER +
            (safeIndex * WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE) +
            (safeTotalPages.toLong() * WEBTOON_SCROLL_WITH_TOTAL_TOTAL_BASE) +
            positionPayload
    }

    val safeIndex = index.coerceAtLeast(0).toLong()
    if (ratioPpm != null) {
        return WEBTOON_SCROLL_RATIO_MARKER + (safeIndex * WEBTOON_SCROLL_OFFSET_BASE) + ratioPpm
    }

    return WEBTOON_SCROLL_MARKER + (safeIndex * WEBTOON_SCROLL_OFFSET_BASE) + safeOffset
}

internal fun decodeWebtoonScrollProgress(value: Long): WebtoonScrollProgress? {
    if (value >= WEBTOON_SCROLL_WITH_TOTAL_MARKER) {
        val payload = value - WEBTOON_SCROLL_WITH_TOTAL_MARKER
        val index = (payload / WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        val chapterPayload = payload % WEBTOON_SCROLL_WITH_TOTAL_ITEM_BASE
        val totalPages = (chapterPayload / WEBTOON_SCROLL_WITH_TOTAL_TOTAL_BASE)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val positionPayload = chapterPayload % WEBTOON_SCROLL_WITH_TOTAL_TOTAL_BASE
        val ratioPpm = if (positionPayload >= WEBTOON_SCROLL_WITH_TOTAL_RATIO_OFFSET) {
            (positionPayload - WEBTOON_SCROLL_WITH_TOTAL_RATIO_OFFSET)
                .coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1)
                .toInt()
        } else {
            null
        }
        val offset = if (ratioPpm == null) {
            positionPayload.coerceIn(0L, WEBTOON_SCROLL_OFFSET_BASE - 1).toInt()
        } else {
            0
        }
        return WebtoonScrollProgress(
            index = index.coerceIn(0, totalPages - 1),
            offsetPx = offset,
            offsetRatioPpm = ratioPpm,
            totalPages = totalPages,
        )
    }

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
    val pagedProgress = decodePagedChapterProgress(value)
    if (pagedProgress != null) {
        return ChapterScrollProgress(index = pagedProgress.index, offsetPx = 0)
    }

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

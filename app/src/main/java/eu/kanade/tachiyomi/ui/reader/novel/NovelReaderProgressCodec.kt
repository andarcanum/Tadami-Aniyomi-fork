package eu.kanade.tachiyomi.ui.reader.novel

private const val NATIVE_SCROLL_MARKER = 5_000_000_000L
private const val NATIVE_SCROLL_OFFSET_BASE = 1_000_000L
private const val WEB_SCROLL_MARKER = 6_000_000_000L
private const val WEB_SCROLL_MAX_PERCENT = 100L
private const val PAGE_READER_MARKER = 7_000_000_000L
private const val PAGE_READER_TOTAL_BASE = 1_000_000L
private const val PAGE_READER_END = 8_000_000_000_000_000L
private const val NATIVE_SCROLL_WITH_TOTAL_MARKER = PAGE_READER_END
private const val NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE = 1_000_000_000_000L
private const val NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE = 1_000_000L

internal data class NativeScrollProgress(
    val index: Int,
    val offsetPx: Int,
    val totalItems: Int? = null,
)

data class PageReaderProgress(
    val index: Int,
    val totalItems: Int,
)

internal fun encodeNativeScrollProgress(
    index: Int,
    offsetPx: Int,
    totalItems: Int? = null,
): Long {
    val safeOffset = offsetPx.coerceIn(0, (NATIVE_SCROLL_OFFSET_BASE - 1).toInt()).toLong()
    if (totalItems != null) {
        val maxIndex = ((Long.MAX_VALUE - NATIVE_SCROLL_WITH_TOTAL_MARKER) / NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val safeTotalItems = totalItems.coerceAtLeast(1)
            .coerceAtMost((NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE - 1).toInt())
            .coerceAtMost(maxIndex + 1)
        val safeIndex = index.coerceIn(0, safeTotalItems - 1).toLong()
        return NATIVE_SCROLL_WITH_TOTAL_MARKER +
            (safeIndex * NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE) +
            (safeTotalItems.toLong() * NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE) +
            safeOffset
    }

    val safeIndex = index.coerceAtLeast(0).toLong()
    return NATIVE_SCROLL_MARKER + (safeIndex * NATIVE_SCROLL_OFFSET_BASE) + safeOffset
}

internal fun decodeNativeScrollProgress(value: Long): NativeScrollProgress? {
    if (value >= NATIVE_SCROLL_WITH_TOTAL_MARKER) {
        val payload = value - NATIVE_SCROLL_WITH_TOTAL_MARKER
        val index = (payload / NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE)
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toInt()
        val totalItems = ((payload % NATIVE_SCROLL_WITH_TOTAL_ITEM_BASE) / NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE)
            .coerceIn(1L, Int.MAX_VALUE.toLong())
            .toInt()
        val offset = (payload % NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE)
            .coerceIn(0L, NATIVE_SCROLL_WITH_TOTAL_TOTAL_BASE - 1)
            .toInt()
        return NativeScrollProgress(
            index = index.coerceIn(0, totalItems - 1),
            offsetPx = offset,
            totalItems = totalItems,
        )
    }

    if (value < NATIVE_SCROLL_MARKER || value >= WEB_SCROLL_MARKER) return null
    val payload = value - NATIVE_SCROLL_MARKER
    val index = (payload / NATIVE_SCROLL_OFFSET_BASE).toInt().coerceAtLeast(0)
    val offset = (payload % NATIVE_SCROLL_OFFSET_BASE).toInt().coerceAtLeast(0)
    return NativeScrollProgress(index = index, offsetPx = offset)
}

internal fun encodeWebScrollProgressPercent(percent: Int): Long {
    val safePercent = percent.coerceIn(0, WEB_SCROLL_MAX_PERCENT.toInt()).toLong()
    return WEB_SCROLL_MARKER + safePercent
}

internal fun decodeWebScrollProgressPercent(value: Long): Int? {
    if (value < WEB_SCROLL_MARKER || value > WEB_SCROLL_MARKER + WEB_SCROLL_MAX_PERCENT) {
        return null
    }
    return (value - WEB_SCROLL_MARKER).toInt().coerceIn(0, WEB_SCROLL_MAX_PERCENT.toInt())
}

internal fun encodePageReaderProgress(
    index: Int,
    totalItems: Int,
): Long {
    val safeTotalItems = totalItems.coerceAtLeast(1).coerceAtMost((PAGE_READER_TOTAL_BASE - 1).toInt())
    val safeIndex = index.coerceIn(0, safeTotalItems - 1)
    return PAGE_READER_MARKER + (safeIndex.toLong() * PAGE_READER_TOTAL_BASE) + safeTotalItems.toLong()
}

internal fun decodePageReaderProgress(value: Long): PageReaderProgress? {
    if (value < PAGE_READER_MARKER || value >= PAGE_READER_END) return null
    val payload = value - PAGE_READER_MARKER
    val index = (payload / PAGE_READER_TOTAL_BASE).toInt().coerceAtLeast(0)
    val totalItems = (payload % PAGE_READER_TOTAL_BASE).toInt().coerceAtLeast(1)
    val safeIndex = index.coerceIn(0, totalItems - 1)
    return PageReaderProgress(index = safeIndex, totalItems = totalItems)
}

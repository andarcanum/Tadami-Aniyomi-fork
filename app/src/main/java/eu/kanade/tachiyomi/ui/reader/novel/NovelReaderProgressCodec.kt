package eu.kanade.tachiyomi.ui.reader.novel

private const val NATIVE_SCROLL_MARKER = 5_000_000_000L
private const val NATIVE_SCROLL_OFFSET_BASE = 1_000_000L
private const val WEB_SCROLL_MARKER = 6_000_000_000L
private const val WEB_SCROLL_MAX_PERCENT = 100L

internal data class NativeScrollProgress(
    val index: Int,
    val offsetPx: Int,
)

internal fun encodeNativeScrollProgress(
    index: Int,
    offsetPx: Int,
): Long {
    val safeIndex = index.coerceAtLeast(0).toLong()
    val safeOffset = offsetPx.coerceIn(0, (NATIVE_SCROLL_OFFSET_BASE - 1).toInt()).toLong()
    return NATIVE_SCROLL_MARKER + (safeIndex * NATIVE_SCROLL_OFFSET_BASE) + safeOffset
}

internal fun decodeNativeScrollProgress(value: Long): NativeScrollProgress? {
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

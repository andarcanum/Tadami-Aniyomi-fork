package eu.kanade.tachiyomi.util.lang

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Single-pass date-grouped list builder replacing `items.map(::Item).insertSeparators { ... }`.
 *
 * Resolves [zoneId] once per pass, converts each timestamp to [LocalDate] once (instead of
 * twice per adjacent pair), and builds the final list without an intermediate mapped list.
 */
internal inline fun <T, R> buildDateGroupedUiModels(
    items: List<T>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    timestampMillis: (T) -> Long?,
    header: (LocalDate) -> R,
    item: (T) -> R,
): List<R> {
    if (items.isEmpty()) return emptyList()
    val result = ArrayList<R>(items.size + 16)
    var lastDate: LocalDate? = null
    for (entry in items) {
        val epochMillis = timestampMillis(entry)
        val entryDate = epochMillis?.let { LocalDate.ofInstant(Instant.ofEpochMilli(it), zoneId) }
        if (entryDate != null && entryDate != lastDate) {
            result.add(header(entryDate))
        }
        lastDate = entryDate
        result.add(item(entry))
    }
    return result
}

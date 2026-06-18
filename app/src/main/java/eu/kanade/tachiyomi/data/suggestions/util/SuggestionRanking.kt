package eu.kanade.tachiyomi.data.suggestions.util

import eu.kanade.tachiyomi.data.suggestions.SuggestionItem
import eu.kanade.tachiyomi.data.suggestions.SuggestionSeed
import eu.kanade.tachiyomi.data.suggestions.SuggestionSourceWeight
import eu.kanade.tachiyomi.data.suggestions.SuggestionTitleResolver

/**
 * F2.2 — Global dedup by cleaned title.
 *
 * Two items collide when their [SuggestionTitleResolver.cleanTitle] matches.
 * On a collision we keep the entry with the higher
 * [SuggestionSourceWeight] so the strongest signal wins.
 */
fun List<SuggestionItem>.dedupeByCleanTitle(): List<SuggestionItem> {
    if (isEmpty()) return this
    val ordered = sortedWith(
        compareByDescending<SuggestionItem> { SuggestionSourceWeight.of(it.reason) }
            .thenByDescending { !it.thumbnailUrl.isNullOrBlank() }
            .thenBy { it.title.length },
    )
    return ordered.dedupeByCleanTitleKeepingFirst()
}

fun List<SuggestionItem>.dedupeByCleanTitle(seed: SuggestionSeed): List<SuggestionItem> {
    if (isEmpty()) return this
    val ordered = sortedWith(
        compareByDescending<SuggestionItem> {
            SuggestionSourceWeight.finalScore(it.reason, it.bestMatchScoreFor(seed))
        }
            .thenByDescending { SuggestionSourceWeight.of(it.reason) }
            .thenByDescending { !it.thumbnailUrl.isNullOrBlank() }
            .thenBy { it.title.length },
    )
    return ordered.dedupeByCleanTitleKeepingFirst()
}

private fun List<SuggestionItem>.dedupeByCleanTitleKeepingFirst(): List<SuggestionItem> {
    val seenKeys = LinkedHashMap<String, SuggestionItem>()
    for (item in this) {
        val key = SuggestionTitleResolver.cleanTitle(item.title)
        if (key.isBlank()) {
            val pk = item.providerId ?: item.providerUrl
            if (seenKeys.values.none { (it.providerId ?: it.providerUrl) == pk }) {
                seenKeys["__blank__:${seenKeys.size}:$pk"] = item
            }
            continue
        }
        if (key !in seenKeys) {
            seenKeys[key] = item
        }
    }
    return seenKeys.values.toList()
}

/**
 * Best match score for this item against the seed's candidate titles.
 * Used to drive the final score = weight × bestMatchScore in the ranker.
 */
fun SuggestionItem.bestMatchScoreFor(seed: SuggestionSeed): Int {
    val candidates = (listOf(seed.primaryTitle) + seed.candidateTitles)
        .filter { it.isNotBlank() }
        .distinct()
    if (candidates.isEmpty()) return 0
    return candidates.maxOfOrNull { candidate ->
        SuggestionTitleResolver.scoreMatch(candidate, title)
    } ?: 0
}

package eu.kanade.tachiyomi.ui.reels

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

enum class ReelsDurationFilter {
    All,
    Short,
    Medium,
    Long,
}

enum class ReelsVideoSort {
    Default,
    Newest,
    Longest,
    Shortest,
    Popular,
}

enum class ReelsWatchFilter {
    All,
    Unwatched,
    Watched,
}

enum class ReelsNichesSort {
    Default,
    Name,
    MostVideos,
    ;

    companion object {
        fun fromStored(raw: String): ReelsNichesSort =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: Default
    }
}

/**
 * Client-side filter & sort state for Reels album grids and niche feeds.
 * Works uniformly across every Reels plugin regardless of server-side filter support.
 */
@Immutable
data class ReelsLocalFilterState(
    val duration: ReelsDurationFilter = ReelsDurationFilter.All,
    val sort: ReelsVideoSort = ReelsVideoSort.Default,
    val selectedTags: ImmutableSet<String> = persistentSetOf(),
    val watchFilter: ReelsWatchFilter = ReelsWatchFilter.All,
) {
    val isActive: Boolean
        get() = duration != ReelsDurationFilter.All ||
            sort != ReelsVideoSort.Default ||
            selectedTags.isNotEmpty() ||
            watchFilter != ReelsWatchFilter.All

    val activeCount: Int
        get() = (if (duration != ReelsDurationFilter.All) 1 else 0) +
            (if (sort != ReelsVideoSort.Default) 1 else 0) +
            selectedTags.size +
            (if (watchFilter != ReelsWatchFilter.All) 1 else 0)

    /**
     * Serializes portable user preferences (`duration`, `sort`, `watchFilter`) for persistence
     * across albums/niches. Album-specific [selectedTags] are intentionally omitted so opening a
     * different album never filters down to an empty grid because of a stale tag.
     */
    fun serializePersistent(): String {
        if (duration == ReelsDurationFilter.All &&
            sort == ReelsVideoSort.Default &&
            watchFilter == ReelsWatchFilter.All
        ) {
            return ""
        }
        return "duration=${duration.name}|sort=${sort.name}|watch=${watchFilter.name}"
    }

    companion object {
        fun deserializePersistent(raw: String): ReelsLocalFilterState {
            if (raw.isBlank()) return ReelsLocalFilterState()
            var duration = ReelsDurationFilter.All
            var sort = ReelsVideoSort.Default
            var watch = ReelsWatchFilter.All
            raw.split('|').forEach { token ->
                val idx = token.indexOf('=')
                if (idx <= 0) return@forEach
                val key = token.substring(0, idx).trim()
                val value = token.substring(idx + 1).trim()
                when (key) {
                    "duration" ->
                        duration = ReelsDurationFilter.entries
                            .firstOrNull { it.name.equals(value, ignoreCase = true) }
                            ?: ReelsDurationFilter.All
                    "sort" ->
                        sort = ReelsVideoSort.entries
                            .firstOrNull { it.name.equals(value, ignoreCase = true) }
                            ?: ReelsVideoSort.Default
                    "watch" ->
                        watch = ReelsWatchFilter.entries
                            .firstOrNull { it.name.equals(value, ignoreCase = true) }
                            ?: ReelsWatchFilter.All
                }
            }
            return ReelsLocalFilterState(
                duration = duration,
                sort = sort,
                watchFilter = watch,
            )
        }
    }
}

internal const val REELS_DURATION_SHORT_MAX_SEC = 60f
internal const val REELS_DURATION_MEDIUM_MAX_SEC = 300f

internal fun matchesDurationFilter(durationSec: Float?, filter: ReelsDurationFilter): Boolean {
    if (filter == ReelsDurationFilter.All) return true
    val dur = durationSec?.takeIf { it.isFinite() && it > 0f } ?: return false
    return when (filter) {
        ReelsDurationFilter.All -> true
        ReelsDurationFilter.Short -> dur < REELS_DURATION_SHORT_MAX_SEC
        ReelsDurationFilter.Medium -> dur in REELS_DURATION_SHORT_MAX_SEC..REELS_DURATION_MEDIUM_MAX_SEC
        ReelsDurationFilter.Long -> dur > REELS_DURATION_MEDIUM_MAX_SEC
    }
}

/**
 * Collects the most frequent non-blank tags across [items], deduplicated case-insensitively
 * while preserving the first seen display spelling.
 */
internal fun extractAvailableTags(
    items: List<ShortVideoItem>,
    maxTags: Int = 24,
): ImmutableList<String> {
    if (items.isEmpty() || maxTags <= 0) return emptyList<String>().toImmutableList()
    val counts = LinkedHashMap<String, Int>()
    val display = HashMap<String, String>()
    for (item in items) {
        val seenInItem = HashSet<String>()
        for (raw in item.tags) {
            val cleaned = raw.trim().removePrefix("#").trim()
            if (cleaned.isEmpty()) continue
            val key = cleaned.lowercase()
            if (seenInItem.add(key)) {
                counts[key] = (counts[key] ?: 0) + 1
                display.putIfAbsent(key, cleaned)
            }
        }
    }
    return counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(maxTags)
        .mapNotNull { display[it.key] }
        .toImmutableList()
}

/**
 * Applies text search, duration, tag/category, watch-status filters and stable sorting to [items].
 * Items missing the sort key (e.g. null timestamp or null duration) are placed at the end in their
 * original relative order.
 */
internal fun applyReelsLocalFilter(
    items: List<ShortVideoItem>,
    filter: ReelsLocalFilterState,
    query: String = "",
    watchedIds: Set<String> = emptySet(),
    progressMap: Map<String, Float> = emptyMap(),
): ImmutableList<ShortVideoItem> {
    val needle = query.trim()
    val normalizedTags = filter.selectedTags.map { it.trim().removePrefix("#").lowercase() }.toSet()

    val filtered = if (needle.isEmpty() && !filter.isActive) {
        items
    } else {
        items.filter { item ->
            if (needle.isNotEmpty()) {
                val matchesQuery = item.title.orEmpty().contains(needle, ignoreCase = true) ||
                    item.author.orEmpty().contains(needle, ignoreCase = true) ||
                    item.tags.any { it.contains(needle, ignoreCase = true) }
                if (!matchesQuery) return@filter false
            }
            if (!matchesDurationFilter(item.durationSec, filter.duration)) {
                return@filter false
            }
            if (normalizedTags.isNotEmpty()) {
                val matchesTag = item.tags.any { raw ->
                    raw.trim().removePrefix("#").lowercase() in normalizedTags
                }
                if (!matchesTag) return@filter false
            }
            if (filter.watchFilter != ReelsWatchFilter.All) {
                val isWatched = item.id in watchedIds || (progressMap[item.id] ?: 0f) >= 0.9f
                when (filter.watchFilter) {
                    ReelsWatchFilter.All -> Unit
                    ReelsWatchFilter.Unwatched -> if (isWatched) return@filter false
                    ReelsWatchFilter.Watched -> if (!isWatched) return@filter false
                }
            }
            true
        }
    }

    val sorted = when (filter.sort) {
        ReelsVideoSort.Default -> filtered
        ReelsVideoSort.Newest -> filtered.sortedWith(
            compareBy<ShortVideoItem> { it.createdAtEpochSec == null }
                .thenByDescending { it.createdAtEpochSec ?: Long.MIN_VALUE },
        )
        ReelsVideoSort.Longest -> filtered.sortedWith(
            compareBy<ShortVideoItem> { (it.durationSec ?: 0f) <= 0f }
                .thenByDescending { it.durationSec ?: 0f },
        )
        ReelsVideoSort.Shortest -> filtered.sortedWith(
            compareBy<ShortVideoItem> { (it.durationSec ?: 0f) <= 0f }
                .thenBy { it.durationSec ?: Float.MAX_VALUE },
        )
        ReelsVideoSort.Popular -> filtered.sortedWith(
            compareBy<ShortVideoItem> { (it.likesCount ?: it.viewsCount) == null }
                .thenByDescending { it.likesCount ?: it.viewsCount ?: Long.MIN_VALUE },
        )
    }

    return sorted.toImmutableList()
}

/**
 * Local search, saved-only filter, and sort for the Niches category directory.
 */
internal fun applyNichesFilterAndSort(
    categories: List<FeedCategory>,
    searchQuery: String,
    sort: ReelsNichesSort,
    savedOnly: Boolean,
    savedAlbumIds: Set<String>,
): ImmutableList<FeedCategory> {
    val needle = searchQuery.trim().removePrefix("@").trim()
    val filtered = categories.filter { category ->
        if (savedOnly && category.id !in savedAlbumIds) return@filter false
        if (needle.isNotEmpty()) {
            val matches = category.name.contains(needle, ignoreCase = true) ||
                category.id.contains(needle, ignoreCase = true)
            if (!matches) return@filter false
        }
        true
    }
    val sorted = when (sort) {
        ReelsNichesSort.Default -> filtered
        ReelsNichesSort.Name -> filtered.sortedBy { it.name.lowercase() }
        ReelsNichesSort.MostVideos -> filtered.sortedWith(
            compareBy<FeedCategory> { it.itemCount == null }
                .thenByDescending { it.itemCount ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() },
        )
    }
    return sorted.toImmutableList()
}

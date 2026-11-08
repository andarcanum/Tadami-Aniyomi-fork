package tachiyomi.domain.easteregg.crystal

/**
 * A current local chapter/episode row. [isCompleted] is its read/seen flag, including old/manual flags.
 * No history, event count or timestamps are needed. [rowId] must be stable within the local entry.
 * A finite [number] >= 0 is known (including fractions); null, negatives and non-finite values are unknown.
 */
data class CrystalProgressItem(
    val rowId: Long,
    val number: Double?,
    val url: String?,
    val isCompleted: Boolean,
)

/** One entry/adaptation snapshot. [isLocal] means persisted locally, not a remote tracking-only entry. */
data class CrystalProgressEntry(
    val entryId: Long,
    val title: CrystalTitleInput,
    val items: List<CrystalProgressItem>,
    val isLocal: Boolean,
)

/** Per-entry diagnostics are retained even when its title, medium or local status cannot qualify. */
data class CrystalEntryProgress(
    val entryId: Long,
    val titleMatch: CrystalTitleMatch,
    val distinctCompleted: Int,
    val requiredCount: Int,
    val isLocal: Boolean,
) {
    val qualifies: Boolean get() = isLocal && titleMatch.adaptation != null && distinctCompleted >= requiredCount
}

data class CrystalProgressResult(val entries: List<CrystalEntryProgress>) {
    val eligibleEntries: List<CrystalEntryProgress> get() = entries.filter { it.qualifies }
    val isEligible: Boolean get() = entries.any { it.qualifies }
}

/** Counts each entry independently: never pools works, media, translations or separate local entries. */
class CrystalProgressCounter(private val titleMatcher: CrystalTitleMatcher = CrystalTitleMatcher()) {

    fun evaluate(entries: List<CrystalProgressEntry>): CrystalProgressResult = CrystalProgressResult(
        entries.map { entry ->
            CrystalEntryProgress(
                entryId = entry.entryId,
                titleMatch = titleMatcher.match(entry.title),
                distinctCompleted = countDistinctCompleted(entry.items),
                requiredCount = when (entry.title.media) {
                    CrystalMedia.ANIME -> 5
                    CrystalMedia.MANGA, CrystalMedia.NOVEL -> 10
                },
                isLocal = entry.isLocal,
            )
        },
    )

    private fun countDistinctCompleted(items: List<CrystalProgressItem>): Int {
        // A shared known number OR URL links copies, including transitive links in any row order.
        // Unknown rows use URL, falling back to stable row ID only when there is no nonblank URL.
        val parents = IntArray(items.size) { it }
        val numbers = mutableMapOf<Double, Int>()
        val urls = mutableMapOf<String, Int>()
        val unknownRows = mutableMapOf<Long, Int>()

        fun root(index: Int): Int {
            var current = index
            while (parents[current] != current) {
                parents[current] = parents[parents[current]]
                current = parents[current]
            }
            return current
        }

        fun link(index: Int, previous: Int?) {
            if (previous != null) parents[root(index)] = root(previous)
        }

        items.forEachIndexed { index, item ->
            val number = item.number?.takeIf { it.isFinite() && it >= 0.0 }
            val url = item.url?.trim()?.takeIf { it.isNotEmpty() }
            if (number != null) {
                // Boxed Double equality distinguishes signed zero, unlike the domain identity.
                link(index, numbers.putIfAbsent(if (number == 0.0) 0.0 else number, index))
            }
            if (url != null) link(index, urls.putIfAbsent(url, index))
            if (number == null && url == null) link(index, unknownRows.putIfAbsent(item.rowId, index))
        }

        // Any completed copy completes its identity; unread copies can connect but never add progress.
        val completedRoots = mutableSetOf<Int>()
        items.forEachIndexed { index, item ->
            if (item.isCompleted) completedRoots.add(root(index))
        }
        return completedRoots.size
    }
}

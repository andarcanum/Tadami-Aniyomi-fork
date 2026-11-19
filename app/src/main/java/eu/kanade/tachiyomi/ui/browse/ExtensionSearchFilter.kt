package eu.kanade.tachiyomi.ui.browse

internal data class ParsedExtensionSearchTerm(
    val text: String,
    val sourceId: Long?,
)

/**
 * Pre-parses comma-separated extension search queries once per query emission so filtering
 * thousands of extensions does not repeatedly allocate split lists, trimmed strings, or
 * parse numeric source IDs inside inner source loops.
 */
internal object ExtensionSearchFilter {
    fun parseTerms(query: String): List<ParsedExtensionSearchTerm> {
        if (query.isEmpty()) return emptyList()
        return buildList {
            var start = 0
            val length = query.length
            while (start <= length) {
                val comma = query.indexOf(',', start).let { if (it < 0) length else it }
                val trimmed = query.substring(start, comma).trim()
                if (trimmed.isNotEmpty()) {
                    add(ParsedExtensionSearchTerm(text = trimmed, sourceId = trimmed.toLongOrNull()))
                }
                start = comma + 1
            }
        }
    }

    /**
     * Counts distinct repository URLs across extension variants with zero intermediate
     * allocations in the overwhelmingly common 0- or 1-variant case.
     */
    inline fun <T> distinctRepoCount(variants: List<T>, repoUrlSelector: (T) -> String): Int {
        return when (variants.size) {
            0 -> 0
            1 -> 1
            2 -> if (repoUrlSelector(variants[0]) == repoUrlSelector(variants[1])) 1 else 2
            else -> {
                val seen = HashSet<String>(variants.size)
                for (variant in variants) {
                    seen.add(repoUrlSelector(variant))
                }
                seen.size
            }
        }
    }
}

package eu.kanade.tachiyomi.data.suggestions

import java.util.concurrent.ConcurrentHashMap

object SuggestionCache {
    private val cache = ConcurrentHashMap<String, Pair<Long, List<SuggestionItem>>>()
    private const val TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val MAX_ENTRIES = 300

    fun get(key: String): List<SuggestionItem>? {
        val entry = cache[key] ?: return null
        val (timestamp, list) = entry
        return if (System.currentTimeMillis() - timestamp < TTL_MS) {
            list
        } else {
            cache.remove(key)
            null
        }
    }

    fun put(key: String, list: List<SuggestionItem>) {
        evictExpiredAndOverflow()
        cache[key] = Pair(System.currentTimeMillis(), list)
    }

    private fun evictExpiredAndOverflow() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - it.value.first >= TTL_MS }
        if (cache.size < MAX_ENTRIES) return
        val overflow = cache.size - MAX_ENTRIES + 1
        cache.entries
            .sortedBy { it.value.first }
            .take(overflow)
            .forEach { cache.remove(it.key) }
    }

    fun invalidateAll() {
        cache.clear()
    }

    fun invalidateForSeed(seed: SuggestionSeed, entryUrl: String? = null) {
        val title = seed.primaryTitle.lowercase().trim()
        val url = entryUrl?.lowercase()?.trim()
        cache.keys.forEach { key ->
            if (key.contains(title) || (url != null && key.contains(url))) {
                cache.remove(key)
            }
        }
    }

    /**
     * Build a cache key that includes a fingerprint of the candidate title
     * list, plus optional description/author fingerprint when those are
     * provided. This ensures that:
     *  - a metadata-enriched seed (with more aliases) produces a different
     *    cache key than the initial weak seed, enabling a real second fetch;
     *  - changes in the novel's description or author invalidate the cache
     *    entry, since both are inputs to the suggestion pipeline.
     */
    fun makeKey(
        sourceName: String,
        primaryTitle: String,
        mediaType: String,
        candidateTitles: List<String> = emptyList(),
        description: String? = null,
        author: String? = null,
    ): String {
        val baseKey = "$sourceName:${primaryTitle.lowercase().trim()}:$mediaType"
        val parts = mutableListOf(baseKey)
        if (candidateTitles.isNotEmpty()) {
            val fingerprint = candidateTitles
                .map { it.lowercase().trim() }
                .sorted()
                .joinToString("|")
            parts.add(fingerprint)
        }
        if (!description.isNullOrBlank()) {
            parts.add("d:" + description.lowercase().trim().hashCode().toString())
        }
        if (!author.isNullOrBlank()) {
            parts.add("a:" + author.lowercase().trim().hashCode().toString())
        }
        return parts.joinToString(":")
    }

    /**
     * Legacy key without candidate fingerprint – kept for backward compatibility.
     * Prefer [makeKey] with candidateTitles / description / author for new code.
     */
    fun makeKey(sourceName: String, title: String, mediaType: String): String =
        makeKey(sourceName, title, mediaType, emptyList(), null, null)
}

package eu.kanade.tachiyomi.ui.reels

import eu.kanade.tachiyomi.animesource.AnimeFeedSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import java.security.MessageDigest

/**
 * Frozen copy of source filters and canonical context hash (feed contract v27).
 * UI mutations cannot affect this frozen request snapshot.
 */
data class FrozenReelsFilters(
    val filters: AnimeFilterList,
    val fingerprint: String,
)

object ReelsSessionContext {

    const val METADATA_SCHEMA_VERSION = 2

    fun serializeFilters(filters: AnimeFilterList): String {
        return buildString {
            filters.forEachIndexed { index, filter ->
                if (index > 0) append(";")
                append(index).append("=").append(filter.name).append(":")
                when (filter) {
                    is AnimeFilter.Select<*> -> append("select=").append(filter.state)
                    is AnimeFilter.CheckBox -> append("check=").append(filter.state)
                    is AnimeFilter.Text -> append("text=").append(filter.state)
                    is AnimeFilter.Sort -> {
                        val idx = filter.state?.index ?: -1
                        val asc = filter.state?.ascending ?: true
                        append("sort=").append("$idx:$asc")
                    }
                    else -> append("other=").append(filter.state.toString())
                }
            }
        }
    }

    fun restoreFilters(filters: AnimeFilterList, serialized: String) {
        if (serialized.isBlank()) return
        val entries = serialized.split(";").associate {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].toIntOrNull() to parts[1] else null to null
        }
        filters.forEachIndexed { index, filter ->
            val raw = entries[index] ?: return@forEachIndexed
            val rawValue = if (raw.contains("=")) raw.substringAfter("=") else raw
            try {
                when (filter) {
                    is AnimeFilter.Select<*> -> rawValue.toIntOrNull()?.let { filter.state = it }
                    is AnimeFilter.CheckBox -> rawValue.toBooleanStrictOrNull()?.let { filter.state = it }
                    is AnimeFilter.Text -> filter.state = rawValue
                    is AnimeFilter.Sort -> {
                        val sortParts = rawValue.split(":")
                        if (sortParts.size == 2) {
                            val sortIdx = sortParts[0].toIntOrNull() ?: -1
                            val asc = sortParts[1].toBooleanStrictOrNull() ?: true
                            if (sortIdx >= 0) {
                                filter.state = AnimeFilter.Sort.Selection(sortIdx, asc)
                            }
                        }
                    }
                    else -> Unit
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Clones fresh filter list from the source and restores current serialized states,
     * ensuring caller thread or UI mutations do not affect the frozen instance.
     */
    fun freezeFilters(source: AnimeFeedSource, currentFilters: AnimeFilterList): FrozenReelsFilters {
        val fresh = source.getFilterList()
        val serialized = serializeFilters(currentFilters)
        restoreFilters(fresh, serialized)
        return FrozenReelsFilters(
            filters = fresh,
            fingerprint = serialized,
        )
    }

    /**
     * Computes canonical context hash from source, route, query, unseen policy,
     * filter serialization, metadata schema version, and continuity key.
     */
    fun computeContextKey(
        sourceId: Long,
        route: String = "main",
        query: String = "",
        unseenOnly: Boolean = true,
        filterFingerprint: String,
        continuityKey: String,
        schemaVersion: Int = METADATA_SCHEMA_VERSION,
    ): String {
        val raw = "s=$sourceId:r=$route:q=$query:u=$unseenOnly:f=$filterFingerprint:v=$schemaVersion:c=$continuityKey"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

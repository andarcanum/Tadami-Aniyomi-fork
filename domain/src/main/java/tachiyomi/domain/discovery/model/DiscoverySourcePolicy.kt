package tachiyomi.domain.discovery.model

import java.util.Collections
import java.util.Locale

/** Canonical identities emitted by Discovery's external recommendation producers. */
enum class DiscoveryExternalProvider {
    ANILIST,
    SHIKIMORI,
    MY_ANIME_LIST,
    MANGA_UPDATES,
    NOVEL_UPDATES,
    MANGADEX,
    ;

    companion object {
        fun fromProvenance(
            provider: String,
        ): DiscoveryExternalProvider? = when (provider.trim().lowercase(Locale.ROOT)) {
            "anilist", "anilist_trend" -> ANILIST
            "shikimori", "shikimori_trend" -> SHIKIMORI
            "myanimelist", "jikan_trend" -> MY_ANIME_LIST
            "mangaupdates" -> MANGA_UPDATES
            "novelupdates" -> NOVEL_UPDATES
            "mangadex_trend" -> MANGADEX
            else -> null
        }
    }
}

/** One installed extension and every catalogue source it owns, independent of registration timing. */
class DiscoverySourcePlugin(
    val key: String,
    sourceIds: Collection<Long>,
    langs: Map<Long, String> = emptyMap(),
    val version: String = "",
) {
    val sourceIds: Set<Long> = immutableSet(sourceIds)
    val langs: Map<Long, String> = immutableMap(langs)

    override fun equals(other: Any?): Boolean = other is DiscoverySourcePlugin &&
        key == other.key && sourceIds == other.sourceIds && langs == other.langs && version == other.version

    override fun hashCode(): Int = arrayOf(key, sourceIds, langs, version).contentHashCode()
}

/** Completeness is explicit: a loaded empty inventory is not an unavailable inventory. */
class DiscoverySourceInventory(
    val complete: Boolean,
    plugins: Collection<DiscoverySourcePlugin>,
) {
    val plugins: List<DiscoverySourcePlugin> = Collections.unmodifiableList(plugins.toList())

    companion object {
        val Unavailable = DiscoverySourceInventory(complete = false, plugins = emptyList())
    }
}

/**
 * Immutable authorization snapshot shared by generation, publication, cached projections and actions.
 * AUTO permits every nonexcluded extension; its top-three representatives only control retrieval.
 */
class DiscoverySourcePolicy(
    val mediaType: DiscoveryMediaType,
    val revision: String,
    val inventoryComplete: Boolean,
    allowedPluginKeys: Collection<String>,
    pluginMembers: Map<String, Set<Long>>,
    representativeSourceIds: List<Long> = emptyList(),
    sourceWeights: Map<Long, Double> = emptyMap(),
    allowedExternalProviders: Map<DiscoveryRowType, Set<DiscoveryExternalProvider>> = emptyMap(),
) {
    val allowedPluginKeys: Set<String> = immutableSet(allowedPluginKeys)
    val pluginMembers: Map<String, Set<Long>> = immutableMap(pluginMembers.mapValues { immutableSet(it.value) })
    val allowedExternalProviders: Map<DiscoveryRowType, Set<DiscoveryExternalProvider>> =
        immutableMap(allowedExternalProviders.mapValues { immutableSet(it.value) })

    /** A colliding id is safe only if every owning extension is selected. */
    val allowedSourceIds: Set<Long> = if (inventoryComplete) {
        val owners = this.pluginMembers.entries.flatMap { (key, ids) -> ids.map { it to key } }
            .groupBy({ it.first }, { it.second })
        immutableSet(owners.filter { (id, keys) -> id > 0L && keys.all { it in this.allowedPluginKeys } }.keys)
    } else {
        emptySet()
    }

    val representativeSourceIds: List<Long> = Collections.unmodifiableList(
        representativeSourceIds.filter { it in allowedSourceIds }.distinct(),
    )
    val sourceWeights: Map<Long, Double> = immutableMap(sourceWeights.filterKeys { it in allowedSourceIds })

    fun allowsNative(sourceId: Long): Boolean = sourceId > 0L && sourceId in allowedSourceIds

    fun allows(rowType: DiscoveryRowType, sourceId: Long?, provider: String): Boolean {
        if (sourceId != null) return allowsNative(sourceId)
        if (rowType == DiscoveryRowType.SOURCE) return false
        val external = DiscoveryExternalProvider.fromProvenance(provider) ?: return false
        return external in allowedExternalProviders[rowType].orEmpty()
    }

    fun allows(candidate: DiscoverySuggestion): Boolean = candidate.mediaType == mediaType &&
        allows(candidate.rowType, candidate.sourceId, candidate.provider)

    fun primarySourceId(lastUsedId: Long): Long = when {
        allowsNative(lastUsedId) -> lastUsedId
        representativeSourceIds.isNotEmpty() -> representativeSourceIds.first()
        else -> -1L
    }

    override fun equals(other: Any?): Boolean = other is DiscoverySourcePolicy &&
        mediaType == other.mediaType && revision == other.revision && inventoryComplete == other.inventoryComplete &&
        allowedPluginKeys == other.allowedPluginKeys && pluginMembers == other.pluginMembers &&
        representativeSourceIds == other.representativeSourceIds && sourceWeights == other.sourceWeights &&
        allowedExternalProviders == other.allowedExternalProviders

    override fun hashCode(): Int = arrayOf(
        mediaType,
        revision,
        inventoryComplete,
        allowedPluginKeys,
        pluginMembers,
        representativeSourceIds,
        sourceWeights,
        allowedExternalProviders,
    ).contentHashCode()
}

private fun <T> immutableSet(values: Collection<T>): Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))

private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))

package tachiyomi.domain.discovery.model

const val DISCOVERY_HOME_BATCH_SIZE = 20

/** Canonical keys that identify the same work across sources and aliases. */
fun DiscoverySuggestion.identityKeys(): Set<String> =
    buildSet {
        add(identityKey ?: legacyDiscoveryKey(mediaType, cleanTitle))
        addAll(trustedAliases)
    }

/**
 * Checks [exclusions] by both canonical [identityKeys] and title-level fallback
 * keys so hidden, known, or memory-excluded works cannot re-enter under a new source binding.
 */
fun DiscoverySuggestion.isExcludedBy(exclusions: DiscoveryEligibilitySnapshot): Boolean {
    if (mediaType != exclusions.mediaType) return false
    val keys = identityKeys()
    if (keys.any { it in exclusions.excludedIdentityKeys || it in exclusions.excludedLookupKeys }) {
        return true
    }
    if (cleanTitle.isNotBlank() &&
        (
            cleanTitle in exclusions.excludedIdentityKeys ||
                legacyDiscoveryKey(mediaType, cleanTitle) in exclusions.excludedLookupKeys
            )
    ) {
        return true
    }
    return false
}

fun legacyDiscoveryKey(mediaType: DiscoveryMediaType, cleanTitle: String): String =
    "title:${mediaType.key}:$cleanTitle"

/** Source-bound identity wins over a title-only legacy identity. */
fun canonicalDiscoveryKey(
    mediaType: DiscoveryMediaType,
    sourceId: Long?,
    sourceUrl: String?,
    cleanTitle: String,
): String = if (sourceId != null && !sourceUrl.isNullOrBlank()) {
    "src:${mediaType.key}:$sourceId:${sourceUrl.trim()}"
} else {
    legacyDiscoveryKey(mediaType, cleanTitle)
}

/** Physical key of a canonical taste/fact row; distinct from legacy normalized titles. */
fun canonicalStorageKey(identityKey: String): String = "@D2/$identityKey"

/**
 * Selects the next Home batch out of the published pool.
 *
 * Candidates are filtered by canonical identity before the batch limit, so an
 * excluded or already issued work can never consume one of the twenty slots.
 */
fun selectNextDiscoveryBatch(
    ranked: List<DiscoverySuggestion>,
    current: Set<String>,
    issued: Set<String>,
    exclusions: DiscoveryEligibilitySnapshot,
    limit: Int = DISCOVERY_HOME_BATCH_SIZE,
): List<DiscoverySuggestion> {
    if (limit <= 0) return emptyList()
    val blocked = HashSet<String>(current.size + issued.size)
    blocked += current
    blocked += issued

    val selected = ArrayList<DiscoverySuggestion>(limit)
    val selectedKeys = HashSet<String>()
    for (candidate in ranked) {
        if (candidate.mediaType != exclusions.mediaType) continue
        if (candidate.isExcludedBy(exclusions)) continue
        val keys = candidate.identityKeys()
        if (keys.any { it in blocked }) continue
        if (keys.any { it in selectedKeys }) continue
        selected += candidate
        selectedKeys += keys
        if (selected.size >= limit) break
    }
    return selected
}

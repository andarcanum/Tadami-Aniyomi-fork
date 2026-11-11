package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryMediaType

internal val DEFAULT_STARTER_GENRES: Map<DiscoveryMediaType, List<String>> = mapOf(
    DiscoveryMediaType.ANIME to listOf("Action", "Comedy", "Fantasy", "Romance", "Sci-Fi"),
    DiscoveryMediaType.MANGA to listOf("Action", "Adventure", "Fantasy", "Comedy", "Drama"),
    DiscoveryMediaType.NOVEL to listOf("Fantasy", "Action", "Adventure", "Mystery", "Sci-Fi"),
)

/** Explicit interests also cover a nonempty library profile whose genres are all excluded. */
internal fun resolveDiscoveryTasteProfile(
    profile: List<Pair<String, Double>>,
    policy: DiscoveryCandidatePolicy,
    mediaType: DiscoveryMediaType? = null,
): List<Pair<String, Double>> {
    val allowedProfile = profile.filterNot { (genre, _) -> policy.isForbidden(listOf(genre)) }
    if (allowedProfile.isNotEmpty()) return allowedProfile

    val explicit = (policy.requiredGenres + policy.priorityGenres)
        .filterNot { policy.isForbidden(listOf(it)) }
        .distinct()
        .map { it to 1.0 }
    if (explicit.isNotEmpty()) return explicit

    val starters = mediaType?.let { DEFAULT_STARTER_GENRES[it] }.orEmpty()
    return starters
        .filterNot { policy.isForbidden(listOf(it)) }
        .distinct()
        .map { it to 1.0 }
}

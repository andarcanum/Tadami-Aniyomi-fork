package eu.kanade.tachiyomi.data.discovery

/** Explicit interests also cover a nonempty library profile whose genres are all excluded. */
internal fun resolveDiscoveryTasteProfile(
    profile: List<Pair<String, Double>>,
    policy: DiscoveryCandidatePolicy,
): List<Pair<String, Double>> {
    val allowedProfile = profile.filterNot { (genre, _) -> policy.isForbidden(listOf(genre)) }
    return allowedProfile.ifEmpty {
        (policy.requiredGenres + policy.priorityGenres)
            .filterNot { policy.isForbidden(listOf(it)) }
            .map { it to 1.0 }
    }
}

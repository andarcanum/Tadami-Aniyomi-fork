package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeroMode

/**
 * Whether the discovery block keeps the hero slot while it is empty or small.
 *
 * Explicit Stage/Collage/Hybrid always own the slot. Auto only keeps it once
 * discovery has produced content before: a cold start must not replace the
 * Continue hero with a "not loaded yet" placeholder. Explicit Continue and text
 * filtering keep the previous layout.
 */
internal fun shouldKeepDiscoveryHeroSlot(
    preferredMode: HomeHeroMode,
    discoveryEnabled: Boolean,
    isFiltering: Boolean,
    hasPreviousDiscoveryContent: Boolean,
): Boolean {
    if (!discoveryEnabled || isFiltering) return false
    return when (preferredMode) {
        HomeHeroMode.Continue -> false
        HomeHeroMode.Auto -> hasPreviousDiscoveryContent
        HomeHeroMode.Stage, HomeHeroMode.Collage, HomeHeroMode.Hybrid -> true
    }
}

internal enum class DiscoveryHeroSkeletonStyle {
    STAGE,
    COLLAGE,
    HYBRID,
    TEASER_ROW,
}

/**
 * Resolves the cold-start / refreshing skeleton geometry matching the user's selected
 * hero discovery style so there is no layout jump when cards arrive.
 */
internal fun resolveDiscoveryHeroSkeletonStyle(
    preferredMode: HomeHeroMode,
    discoveryEnabled: Boolean,
    isFiltering: Boolean,
    keepDiscoveryHeroSlot: Boolean,
): DiscoveryHeroSkeletonStyle {
    if (!discoveryEnabled || isFiltering || !keepDiscoveryHeroSlot) {
        return DiscoveryHeroSkeletonStyle.TEASER_ROW
    }
    return when (preferredMode) {
        HomeHeroMode.Stage -> DiscoveryHeroSkeletonStyle.STAGE
        HomeHeroMode.Collage -> DiscoveryHeroSkeletonStyle.COLLAGE
        HomeHeroMode.Hybrid -> DiscoveryHeroSkeletonStyle.HYBRID
        HomeHeroMode.Auto -> DiscoveryHeroSkeletonStyle.STAGE
        HomeHeroMode.Continue -> DiscoveryHeroSkeletonStyle.TEASER_ROW
    }
}

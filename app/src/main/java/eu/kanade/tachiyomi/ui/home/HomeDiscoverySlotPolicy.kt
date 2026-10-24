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

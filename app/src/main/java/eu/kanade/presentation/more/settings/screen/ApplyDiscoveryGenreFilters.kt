package eu.kanade.presentation.more.settings.screen

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import tachiyomi.domain.discovery.model.DiscoveryMediaType

internal data class DiscoveryGenreFilterValues(
    val priorityCsv: String = "",
    val requiredCsv: String = "",
    val ignoredCsv: String = "",
)

internal fun changedDiscoveryGenreFilters(
    initial: Map<DiscoveryMediaType, DiscoveryGenreFilterValues>,
    updated: Map<DiscoveryMediaType, DiscoveryGenreFilterValues>,
): Map<DiscoveryMediaType, DiscoveryGenreFilterValues> = updated.filter { (mediaType, filters) ->
    filters != initial[mediaType]
}

/** Applying constraints revalidates the existing feed; it is not a request for different titles. */
internal fun applyDiscoveryGenreFilters(
    context: Context,
    preferences: DiscoveryPreferences,
    mediaType: DiscoveryMediaType,
    priorityCsv: String,
    requiredCsv: String,
    ignoredCsv: String,
) {
    preferences.priorityGenres(mediaType).set(priorityCsv)
    preferences.requiredGenres(mediaType).set(requiredCsv)
    preferences.ignoredGenres(mediaType).set(ignoredCsv)
    DiscoveryUpdateJob.refreshConfiguration(context, mediaType)
}

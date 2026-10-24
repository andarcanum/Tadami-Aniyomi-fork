package eu.kanade.presentation.more.settings.screen

import android.content.Context
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import tachiyomi.domain.discovery.model.DiscoveryMediaType

/** Applying constraints revalidates the existing feed; it is not a request for different titles. */
internal fun applyDiscoveryGenreFilters(
    context: Context,
    preferences: DiscoveryPreferences,
    priorityCsv: String,
    requiredCsv: String,
    ignoredCsv: String,
) {
    preferences.priorityGenres().set(priorityCsv)
    preferences.requiredGenres().set(requiredCsv)
    preferences.ignoredGenres().set(ignoredCsv)
    DiscoveryMediaType.entries.forEach { media ->
        DiscoveryUpdateJob.refreshConfiguration(context, media)
    }
}

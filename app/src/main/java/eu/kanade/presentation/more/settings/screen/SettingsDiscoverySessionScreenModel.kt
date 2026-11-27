package eu.kanade.presentation.more.settings.screen

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun computeDiscoverySessionFingerprint(
    preferences: DiscoveryPreferences,
    sourcePreferences: SourcePreferences,
    mediaType: DiscoveryMediaType,
): String {
    // Без GenreMatcher / GenreOntology / SHA-256: при открытии экрана настроек
    // достаточно точной сигнатуры сырых префов на главном потоке.
    val configParts = buildString {
        append(mediaType.key).append('|')
        append(preferences.rowLikeEnabled().get()).append('|')
        append(preferences.rowTasteEnabled().get()).append('|')
        append(preferences.rowTrendEnabled().get()).append('|')
        append(preferences.rowSourceEnabled().get()).append('|')
        append(preferences.seedCount().get()).append('|')
        append(preferences.seedCompleted().get()).append('|')
        append(preferences.seedActive14().get()).append('|')
        append(preferences.seedAdded().get()).append('|')
        append(preferences.seedRated().get()).append('|')
        append(preferences.seedCompletedDays().get()).append('|')
        append(preferences.seedActiveDays().get()).append('|')
        append(preferences.trendSeason().get()).append('|')
        append(preferences.trendSort().get()).append('|')
        append(preferences.releaseStatusFilter().get()).append('|')
        append(preferences.ignoredGenres(mediaType).get()).append('|')
        append(preferences.requiredGenres(mediaType).get()).append('|')
        append(preferences.priorityGenres(mediaType).get()).append('|')
        append(preferences.requiredMatchMode().get()).append('|')
        append(preferences.requiredEnforcement().get()).append('|')
        append(preferences.externalProvidersEnabled().get()).append('|')
        append(preferences.discoverySourceMode(mediaType).get()).append('|')
        append(preferences.discoverySourceExcluded(mediaType).get()).append('|')
        append(preferences.discoverySourceKnown(mediaType).get()).append('|')
        append(preferences.filterNsfw().get())
    }
    val shikimori = sourcePreferences.suggestionsUseShikimori().get()
    val providers = when (mediaType) {
        DiscoveryMediaType.MANGA -> {
            val mu = sourcePreferences.suggestionsUseMangaUpdatesNovel().get()
            "$shikimori|$mu"
        }
        DiscoveryMediaType.NOVEL -> {
            val mu = sourcePreferences.suggestionsUseMangaUpdatesNovel().get()
            val nu = sourcePreferences.suggestionsUseNovelUpdates().get()
            "$shikimori|$mu|$nu"
        }
        DiscoveryMediaType.ANIME -> "$shikimori"
    }
    return "$configParts||$providers"
}

class SettingsDiscoverySessionScreenModel(
    private val context: Context,
    private val preferences: DiscoveryPreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
) : ScreenModel {

    private val initialFingerprints: Map<DiscoveryMediaType, String> =
        DiscoveryMediaType.entries.associateWith {
            computeDiscoverySessionFingerprint(preferences, sourcePreferences, it)
        }

    override fun onDispose() {
        DiscoveryMediaType.entries.forEach { mediaType ->
            val current = computeDiscoverySessionFingerprint(preferences, sourcePreferences, mediaType)
            if (current != initialFingerprints[mediaType]) {
                DiscoveryUpdateJob.refreshConfiguration(
                    context = context,
                    mediaType = mediaType,
                    initialDelaySeconds = SETTINGS_EXIT_REFRESH_DELAY_SECONDS,
                )
            }
        }
    }

    companion object {
        private const val SETTINGS_EXIT_REFRESH_DELAY_SECONDS = 2L
    }
}

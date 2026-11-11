package eu.kanade.presentation.more.settings.screen

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.data.discovery.computeDiscoveryConfigurationKey
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun computeDiscoverySessionFingerprint(
    preferences: DiscoveryPreferences,
    sourcePreferences: SourcePreferences,
    mediaType: DiscoveryMediaType,
): String {
    val configKey = computeDiscoveryConfigurationKey(preferences, mediaType)
    val shikimori = sourcePreferences.suggestionsUseShikimori().get()
    val providers = when (mediaType) {
        DiscoveryMediaType.MANGA -> {
            val mu = sourcePreferences.suggestionsUseMangaUpdatesNovel().get()
            "$shikimori|$mu"
        }
        DiscoveryMediaType.NOVEL -> {
            val nu = sourcePreferences.suggestionsUseNovelUpdates().get()
            "$shikimori|$nu"
        }
        DiscoveryMediaType.ANIME -> "$shikimori"
    }
    return "$configKey|$providers"
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
                DiscoveryUpdateJob.refreshConfiguration(context, mediaType)
            }
        }
    }
}

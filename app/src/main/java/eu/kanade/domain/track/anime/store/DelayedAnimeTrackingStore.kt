package eu.kanade.domain.track.anime.store

import android.content.Context
import androidx.core.content.edit
import eu.kanade.domain.track.DelayedTrackingStoreKeys
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DelayedAnimeTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored. Media-specific: the legacy
     * shared "tracking_queue" file let the manga and anime delayed jobs read each other's
     * bare-_id keys (the id spaces overlap) and push progress to the wrong medium's track.
     * Legacy entries are migrated best-effort by [eu.kanade.domain.track.DelayedTrackingLegacyMigration].
     */
    private val preferences = context.getSharedPreferences("anime_tracking_queue", Context.MODE_PRIVATE)

    fun addAnime(animeId: Long, trackerId: Long, lastEpisodeSeen: Double) {
        val key = DelayedTrackingStoreKeys.build(animeId, trackerId)
        val previousLastEpisodeSeen = preferences.getFloat(key, 0f)
        if (lastEpisodeSeen > previousLastEpisodeSeen) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $key, last episode seen: $lastEpisodeSeen" }
            preferences.edit {
                putFloat(key, lastEpisodeSeen.toFloat())
            }
        }
    }

    fun removeAnimeItem(animeId: Long, trackerId: Long) {
        preferences.edit {
            remove(DelayedTrackingStoreKeys.build(animeId, trackerId))
        }
    }

    fun getAnimeItems(): List<DelayedAnimeTrackingItem> {
        return preferences.all.mapNotNull { (rawKey, value) ->
            val (animeId, trackerId) = DelayedTrackingStoreKeys.parse(rawKey) ?: return@mapNotNull null
            DelayedAnimeTrackingItem(
                animeId = animeId,
                trackerId = trackerId,
                lastEpisodeSeen = value.toString().toFloat(),
            )
        }
    }

    data class DelayedAnimeTrackingItem(
        val animeId: Long,
        val trackerId: Long,
        val lastEpisodeSeen: Float,
    )
}

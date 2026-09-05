package eu.kanade.domain.track.manga.store

import android.content.Context
import androidx.core.content.edit
import eu.kanade.domain.track.DelayedTrackingStoreKeys
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

class DelayedMangaTrackingStore(context: Context) {

    /**
     * Preference file where queued tracking updates are stored. Media-specific: the legacy
     * shared "tracking_queue" file let the manga and anime delayed jobs read each other's
     * bare-_id keys (the id spaces overlap) and push progress to the wrong medium's track.
     * Legacy entries are migrated best-effort by [eu.kanade.domain.track.DelayedTrackingLegacyMigration].
     */
    private val preferences = context.getSharedPreferences("manga_tracking_queue", Context.MODE_PRIVATE)

    fun addManga(mangaId: Long, trackerId: Long, lastChapterRead: Double) {
        val key = DelayedTrackingStoreKeys.build(mangaId, trackerId)
        val previousLastChapterRead = preferences.getFloat(key, 0f)
        if (lastChapterRead > previousLastChapterRead) {
            logcat(LogPriority.DEBUG) { "Queuing track item: $key, last chapter read: $lastChapterRead" }
            preferences.edit {
                putFloat(key, lastChapterRead.toFloat())
            }
        }
    }

    fun removeMangaItem(mangaId: Long, trackerId: Long) {
        preferences.edit {
            remove(DelayedTrackingStoreKeys.build(mangaId, trackerId))
        }
    }

    fun getMangaItems(): List<DelayedTrackingItem> {
        return preferences.all.mapNotNull { (rawKey, value) ->
            val (mangaId, trackerId) = DelayedTrackingStoreKeys.parse(rawKey) ?: return@mapNotNull null
            DelayedTrackingItem(
                mangaId = mangaId,
                trackerId = trackerId,
                lastChapterRead = value.toString().toFloat(),
            )
        }
    }

    data class DelayedTrackingItem(
        val mangaId: Long,
        val trackerId: Long,
        val lastChapterRead: Float,
    )
}

package eu.kanade.domain.track

import android.content.Context
import androidx.core.content.edit
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * One-shot best-effort migration of the legacy shared "tracking_queue" prefs file.
 *
 * The legacy file stored manga AND anime delayed updates under the same bare track-row `_id`
 * keys, and both id spaces start at 1, so old entries are ambiguous by construction. Each key is
 * resolved against both track tables: when exactly one medium has a track with that `_id`, the
 * pending progress moves to that medium's new stable-key store; ambiguous or unresolvable keys
 * are dropped (these are delayed best-effort updates, and dropping mirrors the old jobs'
 * not-found behavior). The file is cleared at the end; the migration is idempotent (a second run
 * sees an empty file), so both delayed jobs can call it without coordination.
 */
object DelayedTrackingLegacyMigration {

    private const val LEGACY_FILE = "tracking_queue"

    suspend fun migrate(context: Context) = withIOContext {
        val legacy = context.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE)
        val entries = legacy.all
        if (entries.isEmpty()) return@withIOContext

        val mangaStore = Injekt.get<DelayedMangaTrackingStore>()
        val animeStore = Injekt.get<DelayedAnimeTrackingStore>()
        val getMangaTracks = Injekt.get<GetMangaTracks>()
        val getAnimeTracks = Injekt.get<GetAnimeTracks>()

        entries.forEach { (rawKey, value) ->
            val trackId = rawKey.toLongOrNull() ?: return@forEach
            val progress = value.toString().toDoubleOrNull() ?: return@forEach
            val mangaTrack = getMangaTracks.awaitOne(trackId)
            val animeTrack = getAnimeTracks.awaitOne(trackId)
            when {
                mangaTrack != null && animeTrack == null ->
                    mangaStore.addManga(mangaTrack.mangaId, mangaTrack.trackerId, progress)

                animeTrack != null && mangaTrack == null ->
                    animeStore.addAnime(animeTrack.animeId, animeTrack.trackerId, progress)

                else -> logcat(LogPriority.WARN) {
                    "Dropping ambiguous legacy delayed tracking entry: $rawKey"
                }
            }
        }
        legacy.edit { clear() }
    }
}

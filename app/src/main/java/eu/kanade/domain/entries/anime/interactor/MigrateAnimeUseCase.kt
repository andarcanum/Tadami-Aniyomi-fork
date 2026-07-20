package eu.kanade.domain.entries.anime.interactor

import eu.kanade.domain.entries.anime.model.hasCustomBackground
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.toEpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

/**
 * Migrates an anime (and optionally its episodes, categories, custom cover/background,
 * downloads) from one source to another. Extracted from MigrateAnimeDialogScreenModel so it
 * can be reused by both the single-entry migration dialog and bulk migration.
 */
class MigrateAnimeUseCase(
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val getCategories: GetAnimeCategories = Injekt.get(),
    private val setAnimeCategories: SetAnimeCategories = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val insertTrack: InsertAnimeTrack = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get(),
    private val backgroundCache: AnimeBackgroundCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {

    private val enhancedServices by lazy {
        trackerManager.trackers.filterIsInstance<EnhancedAnimeTracker>()
    }

    suspend fun migrateAnime(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
    ) {
        val source = sourceManager.get(newAnime.source) ?: return
        val prevSource = sourceManager.get(oldAnime.source)
        val localNewAnime = networkToLocalAnime.await(newAnime)
        if (oldAnime.id == localNewAnime.id) return

        val episodes = source.getEpisodeList(localNewAnime.toSAnime())

        migrateAnimeInternal(
            oldSource = prevSource,
            newSource = source,
            oldAnime = oldAnime,
            newAnime = localNewAnime,
            sourceEpisodes = episodes,
            replace = replace,
            flags = flags,
        )
    }

    private suspend fun migrateAnimeInternal(
        oldSource: AnimeSource?,
        newSource: AnimeSource,
        oldAnime: Anime,
        newAnime: Anime,
        sourceEpisodes: List<SEpisode>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateEpisodes = AnimeMigrationFlags.hasEpisodes(flags)
        val migrateCategories = AnimeMigrationFlags.hasCategories(flags)
        val migrateCustomCover = AnimeMigrationFlags.hasCustomCover(flags)
        val migrateCustomBackground = AnimeMigrationFlags.hasCustomBackground(flags)
        val deleteDownloaded = AnimeMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncEpisodesWithSource.await(sourceEpisodes, newAnime, newSource)
        } catch (_: Exception) {
            // Worst case, episodes won't be synced
        }

        // Update episodes seen, bookmark and dateFetch
        if (migrateEpisodes) {
            val prevAnimeEpisodes = getEpisodesByAnimeId.await(oldAnime.id)
            val animeEpisodes = getEpisodesByAnimeId.await(newAnime.id)

            val maxEpisodeSeen = prevAnimeEpisodes
                .filter { it.seen }
                .maxOfOrNull { it.episodeNumber }

            val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                var updatedEpisode = animeEpisode
                if (updatedEpisode.isRecognizedNumber) {
                    val prevEpisode = prevAnimeEpisodes
                        .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                    if (prevEpisode != null) {
                        updatedEpisode = updatedEpisode.copy(
                            dateFetch = prevEpisode.dateFetch,
                            bookmark = prevEpisode.bookmark,
                        )
                    }

                    if (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen) {
                        updatedEpisode = updatedEpisode.copy(seen = true)
                    }
                }

                updatedEpisode
            }

            val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
            updateEpisode.awaitAll(episodeUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldAnime.id).map { it.id }
            setAnimeCategories.await(newAnime.id, categoryIds)
        }

        // Update track
        getTracks.await(oldAnime.id).mapNotNull { track ->
            val updatedTrack = track.copy(animeId = newAnime.id)

            val service = enhancedServices
                .firstOrNull { it.isTrackFrom(updatedTrack, oldAnime, oldSource) }

            if (service != null) {
                service.migrateTrack(updatedTrack, newAnime, newSource)
            } else {
                updatedTrack
            }
        }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Delete downloaded
        if (deleteDownloaded) {
            if (oldSource != null) {
                downloadManager.deleteAnime(oldAnime, oldSource)
            }
        }

        if (replace) {
            updateAnime.awaitUpdateFavorite(oldAnime.id, favorite = false)
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldAnime.hasCustomCover(coverCache)) {
            coverCache.setCustomCoverToCache(
                newAnime,
                coverCache.getCustomCoverFile(oldAnime.id).inputStream(),
            )
        }

        // Update custom background (recheck if custom background exists)
        if (migrateCustomBackground && oldAnime.hasCustomBackground(backgroundCache)) {
            backgroundCache.setCustomBackgroundToCache(
                newAnime,
                backgroundCache.getCustomBackgroundFile(oldAnime.id).inputStream(),
            )
        }

        updateAnime.await(
            AnimeUpdate(
                id = newAnime.id,
                favorite = true,
                episodeFlags = oldAnime.episodeFlags,
                viewerFlags = oldAnime.viewerFlags,
                dateAdded = if (replace) oldAnime.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }
}

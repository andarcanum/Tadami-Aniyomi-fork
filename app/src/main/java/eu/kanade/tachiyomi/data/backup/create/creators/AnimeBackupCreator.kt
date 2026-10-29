package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeTracking
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.models.backupAnimeTrackWithAnimeIdMapper
import eu.kanade.tachiyomi.data.backup.models.backupEpisodeWithAnimeIdMapper
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.entries.anime.model.Anime
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeBackupCreator(
    private val handler: AnimeDatabaseHandler = Injekt.get(),
) {

    suspend operator fun invoke(animes: List<Anime>, options: BackupOptions): List<BackupAnime> {
        if (animes.isEmpty()) return emptyList()
        val scopeIds = animes.mapTo(hashSetOf()) { it.id }

        val episodesByAnime = if (!options.chapters) {
            emptyMap()
        } else {
            // Single-pass grouping: filter().groupBy() would allocate an intermediate list
            // of every episode row on a large library.
            val grouped = LinkedHashMap<Long, MutableList<BackupEpisode>>()
            handler.awaitList { db ->
                db.episodesQueries.getAllEpisodesForBackup(backupEpisodeWithAnimeIdMapper)
            }.forEach { (animeId, episode) ->
                if (animeId in scopeIds) {
                    grouped.getOrPut(animeId) { ArrayList() }.add(episode)
                }
            }
            grouped
        }

        val categoryOrdersByAnime = if (!options.categories) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.categoriesQueries.getAllAnimeCategoryOrders() }
                .filter { it.anime_id in scopeIds }
                .groupBy({ it.anime_id }, { it.`order` })
        }

        val tracksByAnime = if (!options.tracking) {
            emptyMap()
        } else {
            handler.awaitList { db ->
                db.anime_syncQueries.getAnimeTracks(backupAnimeTrackWithAnimeIdMapper)
            }
                .filter { it.first in scopeIds }
                .groupBy({ it.first }, { it.second })
        }

        val historyByAnime = if (!options.history) {
            emptyMap()
        } else {
            handler.awaitList { db -> db.animehistoryQueries.getAllAnimeHistoryWithEpisode() }
                .filter { it.anime_id in scopeIds }
                .groupBy { it.anime_id }
        }

        return animes.map { anime ->
            anime.toBackupAnime().apply {
                episodesByAnime[anime.id]?.takeUnless(List<BackupEpisode>::isEmpty)
                    ?.let { episodes = it }
                categoryOrdersByAnime[anime.id]?.takeUnless(List<Long>::isEmpty)
                    ?.let { categories = it }
                tracksByAnime[anime.id]?.takeUnless(List<BackupAnimeTracking>::isEmpty)
                    ?.let { tracking = it }
                historyByAnime[anime.id]?.map {
                    BackupAnimeHistory(it.url, it.last_seen?.time ?: 0L)
                }?.takeUnless(List<BackupAnimeHistory>::isEmpty)
                    ?.let { history = it }
            }
        }
    }
}

private fun Anime.toBackupAnime() =
    BackupAnime(
        url = this.url,
        title = this.title,
        artist = this.artist,
        author = this.author,
        description = this.description,
        notes = this.notes.takeIf { it.isNotBlank() },
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer_flags = this.viewerFlags.toInt(),
        episodeFlags = this.episodeFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        fetchType = this.fetchType,
        parentId = this.parentId,
        id = this.id,
        seasonFlags = this.seasonFlags,
        seasonNumber = this.seasonNumber,
        seasonSourceOrder = this.seasonSourceOrder,
        customTitle = this.customTitle,
        customArtist = this.customArtist,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customStatus = this.customStatus,
        completedAt = this.completedAt,
    )

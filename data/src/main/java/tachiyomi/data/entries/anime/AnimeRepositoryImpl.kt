package tachiyomi.data.entries.anime

import aniyomi.domain.anime.SeasonAnime
import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.AnimeUpdateStrategyColumnAdapter
import tachiyomi.data.FetchTypeColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.model.DeletableAnime
import java.time.LocalDate
import java.time.ZoneId

class AnimeRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
    private val eventBus: AchievementEventBus,
) : AnimeRepository {

    override suspend fun getAnimeById(id: Long): Anime {
        return handler.awaitOne { db -> db.animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime> {
        return handler.subscribeToOne { db -> db.animesQueries.getAnimeById(id, AnimeMapper::mapAnime) }
    }

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime? {
        return handler.awaitOneOrNull { db ->
            db.animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            )
        }
    }

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?> {
        return handler.subscribeToOneOrNull { db ->
            db.animesQueries.getAnimeByUrlAndSource(
                url,
                sourceId,
                AnimeMapper::mapAnime,
            )
        }
    }

    override suspend fun getAnimeFavorites(): List<Anime> {
        return handler.awaitList { db -> db.animesQueries.getFavorites(AnimeMapper::mapAnime) }
    }

    override suspend fun getWatchedAnimeNotInLibrary(): List<Anime> {
        return handler.awaitList { db -> db.animesQueries.getWatchedAnimeNotInLibrary(AnimeMapper::mapAnime) }
    }

    override suspend fun getLibraryAnime(): List<LibraryAnime> {
        return handler.awaitList { db -> db.animelibViewQueries.animelib(AnimeMapper::mapLibraryAnime) }
    }

    override fun getLibraryAnimeAsFlow(): Flow<List<LibraryAnime>> {
        return handler.subscribeToList { db -> db.animelibViewQueries.animelib(AnimeMapper::mapLibraryAnime) }
    }

    override fun getRecentLibraryAnime(limit: Long): Flow<List<LibraryAnime>> {
        return handler.subscribeToList { db ->
            db.animelibViewQueries.getRecentLibraryAnime(limit, AnimeMapper::mapLibraryAnime)
        }
    }

    override fun getRecentFavorites(limit: Long): Flow<List<Anime>> {
        return handler.subscribeToList { db ->
            db.animesQueries.getRecentFavorites(limit, AnimeMapper::mapAnime)
        }
    }

    override fun getAnimeFavoritesBySourceId(sourceId: Long): Flow<List<Anime>> {
        return handler.subscribeToList { db -> db.animesQueries.getFavoriteBySourceId(sourceId, AnimeMapper::mapAnime) }
    }

    override suspend fun getDuplicateLibraryAnime(id: Long, title: String): List<Anime> {
        return handler.awaitList { db ->
            db.animesQueries.getDuplicateLibraryAnime(title, id, AnimeMapper::mapAnime)
        }
    }

    override suspend fun getUpcomingAnime(statuses: Set<Long>): Flow<List<Anime>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList { db ->
            db.animesQueries.getUpcomingAnime(epochMillis, statuses, AnimeMapper::mapAnime)
        }
    }

    override suspend fun resetAnimeViewerFlags(): Boolean {
        return try {
            handler.await { db -> db.animesQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) { db ->
            db.animes_categoriesQueries.deleteAnimeCategoryByAnimeId(animeId)
            categoryIds.map { categoryId ->
                db.animes_categoriesQueries.insert(animeId, categoryId)
            }
        }
    }

    override suspend fun insertNetworkAnimes(animes: List<Anime>, autoFavorite: Boolean): List<Anime> {
        if (animes.isEmpty()) return emptyList()
        return handler.await(inTransaction = true) { db ->
            animes.map { anime ->
                val local = db.animesQueries
                    .getAnimeByUrlAndSource(anime.url, anime.source, AnimeMapper::mapAnime)
                    .executeAsOneOrNull()
                if (local == null) {
                    val toInsert = if (autoFavorite) {
                        anime.copy(favorite = true, dateAdded = System.currentTimeMillis())
                    } else {
                        anime
                    }
                    db.animesQueries.insert(
                        source = toInsert.source,
                        url = toInsert.url,
                        artist = toInsert.artist,
                        author = toInsert.author,
                        description = toInsert.description,
                        notes = toInsert.notes,
                        genre = toInsert.genre,
                        title = toInsert.title,
                        status = toInsert.status,
                        thumbnailUrl = toInsert.thumbnailUrl,
                        backgroundUrl = toInsert.backgroundUrl,
                        favorite = toInsert.favorite,
                        pinned = toInsert.pinned,
                        lastUpdate = toInsert.lastUpdate,
                        nextUpdate = toInsert.nextUpdate,
                        calculateInterval = toInsert.fetchInterval.toLong(),
                        initialized = toInsert.initialized,
                        viewerFlags = toInsert.viewerFlags,
                        episodeFlags = toInsert.episodeFlags,
                        coverLastModified = toInsert.coverLastModified,
                        backgroundLastModified = toInsert.backgroundLastModified,
                        dateAdded = toInsert.dateAdded,
                        updateStrategy = toInsert.updateStrategy,
                        version = toInsert.version,
                        fetchType = toInsert.fetchType,
                        parentId = toInsert.parentId,
                        seasonFlags = toInsert.seasonFlags,
                        seasonNumber = toInsert.seasonNumber,
                        seasonSourceOrder = toInsert.seasonSourceOrder,
                    )
                    val insertedId = db.animesQueries.selectLastInsertedRowId().executeAsOne()
                    toInsert.copy(id = insertedId)
                } else if (!local.favorite && !autoFavorite) {
                    val thumbnailUrl = if (local.thumbnailUrl.isNullOrBlank()) {
                        anime.thumbnailUrl
                    } else {
                        local.thumbnailUrl
                    }
                    val updated = local.copy(
                        title = anime.title,
                        thumbnailUrl = thumbnailUrl,
                    )
                    db.animesQueries.update(
                        source = updated.source,
                        url = updated.url,
                        artist = updated.artist,
                        author = updated.author,
                        description = updated.description,
                        notes = updated.notes,
                        genre = updated.genre?.let(StringListColumnAdapter::encode),
                        title = updated.title,
                        status = updated.status,
                        thumbnailUrl = updated.thumbnailUrl,
                        backgroundUrl = updated.backgroundUrl,
                        favorite = updated.favorite,
                        pinned = updated.pinned,
                        lastUpdate = updated.lastUpdate,
                        nextUpdate = updated.nextUpdate,
                        calculateInterval = updated.fetchInterval.toLong(),
                        initialized = updated.initialized,
                        viewer = updated.viewerFlags,
                        episodeFlags = updated.episodeFlags,
                        coverLastModified = updated.coverLastModified,
                        backgroundLastModified = updated.backgroundLastModified,
                        dateAdded = updated.dateAdded,
                        animeId = updated.id,
                        updateStrategy = AnimeUpdateStrategyColumnAdapter.encode(updated.updateStrategy),
                        version = updated.version,
                        isSyncing = 0,
                        fetchType = FetchTypeColumnAdapter.encode(updated.fetchType),
                        parentId = updated.parentId,
                        seasonFlags = updated.seasonFlags,
                        seasonNumber = updated.seasonNumber,
                        seasonSourceOrder = updated.seasonSourceOrder,
                    )
                    updated
                } else if (autoFavorite && !local.favorite) {
                    val updated = local.copy(favorite = true, dateAdded = System.currentTimeMillis())
                    db.animesQueries.update(
                        source = updated.source,
                        url = updated.url,
                        artist = updated.artist,
                        author = updated.author,
                        description = updated.description,
                        notes = updated.notes,
                        genre = updated.genre?.let(StringListColumnAdapter::encode),
                        title = updated.title,
                        status = updated.status,
                        thumbnailUrl = updated.thumbnailUrl,
                        backgroundUrl = updated.backgroundUrl,
                        favorite = updated.favorite,
                        pinned = updated.pinned,
                        lastUpdate = updated.lastUpdate,
                        nextUpdate = updated.nextUpdate,
                        calculateInterval = updated.fetchInterval.toLong(),
                        initialized = updated.initialized,
                        viewer = updated.viewerFlags,
                        episodeFlags = updated.episodeFlags,
                        coverLastModified = updated.coverLastModified,
                        backgroundLastModified = updated.backgroundLastModified,
                        dateAdded = updated.dateAdded,
                        animeId = updated.id,
                        updateStrategy = AnimeUpdateStrategyColumnAdapter.encode(updated.updateStrategy),
                        version = updated.version,
                        isSyncing = 0,
                        fetchType = FetchTypeColumnAdapter.encode(updated.fetchType),
                        parentId = updated.parentId,
                        seasonFlags = updated.seasonFlags,
                        seasonNumber = updated.seasonNumber,
                        seasonSourceOrder = updated.seasonSourceOrder,
                    )
                    updated
                } else {
                    local
                }
            }
        }
    }

    override suspend fun insertAnime(anime: Anime): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) { db ->
            db.animesQueries.insert(
                source = anime.source,
                url = anime.url,
                artist = anime.artist,
                author = anime.author,
                description = anime.description,
                notes = anime.notes,
                genre = anime.genre,
                title = anime.title,
                status = anime.status,
                thumbnailUrl = anime.thumbnailUrl,
                backgroundUrl = anime.backgroundUrl,
                favorite = anime.favorite,
                pinned = anime.pinned,
                lastUpdate = anime.lastUpdate,
                nextUpdate = anime.nextUpdate,
                calculateInterval = anime.fetchInterval.toLong(),
                initialized = anime.initialized,
                viewerFlags = anime.viewerFlags,
                episodeFlags = anime.episodeFlags,
                coverLastModified = anime.coverLastModified,
                backgroundLastModified = anime.backgroundLastModified,
                dateAdded = anime.dateAdded,
                updateStrategy = anime.updateStrategy,
                version = anime.version,
                fetchType = anime.fetchType,
                parentId = anime.parentId,
                seasonFlags = anime.seasonFlags,
                seasonNumber = anime.seasonNumber,
                seasonSourceOrder = anime.seasonSourceOrder,
            )
            db.animesQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateAnime(update: AnimeUpdate): Boolean {
        return try {
            partialUpdateAnime(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllAnime(animeUpdates: List<AnimeUpdate>): Boolean {
        return try {
            partialUpdateAnime(*animeUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime> {
        return handler.awaitList { db ->
            db.animeseasonsViewQueries.getAnimeSeasonsById(parentId, AnimeMapper::mapSeasonAnime)
        }
    }

    override fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>> {
        return handler.subscribeToList { db ->
            db.animeseasonsViewQueries.getAnimeSeasonsById(parentId, AnimeMapper::mapSeasonAnime)
        }
    }

    override suspend fun removeParentIdByIds(animeIds: List<Long>) {
        try {
            handler.await { db -> db.animesQueries.removeParentIdByIds(animeIds) }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }

    override fun getDeletableParentAnime(): Flow<List<DeletableAnime>> {
        return handler.subscribeToList { db ->
            db.animedeletableViewQueries.getDeletableParentAnime(AnimeMapper::mapDeletableAnime)
        }
    }

    override suspend fun getChildrenByParentId(parentId: Long): List<Anime> {
        return handler.awaitList { db -> db.animesQueries.getChildrenByParentId(parentId, AnimeMapper::mapAnime) }
    }

    override suspend fun updateAnimeMetadata(
        animeId: Long,
        customTitle: String?,
        customArtist: String?,
        customAuthor: String?,
        customDescription: String?,
        customGenre: List<String>?,
        customStatus: Long?,
    ): Boolean {
        return try {
            handler.await { db ->
                db.animesQueries.updateMetadata(
                    customTitle = customTitle,
                    customArtist = customArtist,
                    customAuthor = customAuthor,
                    customDescription = customDescription,
                    customGenre = customGenre,
                    customStatus = customStatus,
                    animeId = animeId,
                )
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateAnime(vararg animeUpdates: AnimeUpdate) {
        handler.await(inTransaction = true) { db ->
            animeUpdates.forEach { value ->
                db.animesQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    notes = value.notes,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    backgroundUrl = value.backgroundUrl,
                    favorite = value.favorite,
                    pinned = value.pinned,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    episodeFlags = value.episodeFlags,
                    coverLastModified = value.coverLastModified,
                    backgroundLastModified = value.backgroundLastModified,
                    dateAdded = value.dateAdded,
                    animeId = value.id,
                    updateStrategy = value.updateStrategy?.let(AnimeUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                    fetchType = value.fetchType?.let(FetchTypeColumnAdapter::encode),
                    parentId = value.parentId,
                    seasonFlags = value.seasonFlags,
                    seasonNumber = value.seasonNumber,
                    seasonSourceOrder = value.seasonSourceOrder,
                )

                // Emit achievement event if favorite status changed
                value.favorite?.let { isFavorite ->
                    val event = if (isFavorite) {
                        AchievementEvent.LibraryAdded(value.id, AchievementCategory.ANIME)
                    } else {
                        AchievementEvent.LibraryRemoved(value.id, AchievementCategory.ANIME)
                    }
                    eventBus.tryEmit(event)
                }
            }
        }
    }
}

package tachiyomi.data.entries.manga

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import java.time.LocalDate
import java.time.ZoneId

class MangaRepositoryImpl(
    private val handler: MangaDatabaseHandler,
    private val eventBus: AchievementEventBus,
) : MangaRepository {

    override suspend fun getMangaById(id: Long): Manga {
        return handler.awaitOne { db -> db.mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga> {
        return handler.subscribeToOne { db -> db.mangasQueries.getMangaById(id, MangaMapper::mapManga) }
    }

    override suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga? {
        return handler.awaitOneOrNull { db ->
            db.mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?> {
        return handler.subscribeToOneOrNull { db ->
            db.mangasQueries.getMangaByUrlAndSource(
                url,
                sourceId,
                MangaMapper::mapManga,
            )
        }
    }

    override suspend fun getMangaFavorites(): List<Manga> {
        return handler.awaitList { db -> db.mangasQueries.getFavorites(MangaMapper::mapManga) }
    }

    override suspend fun getReadMangaNotInLibrary(): List<Manga> {
        return handler.awaitList { db -> db.mangasQueries.getReadMangaNotInLibrary(MangaMapper::mapManga) }
    }

    override suspend fun getLibraryManga(): List<LibraryManga> {
        return handler.awaitList { db -> db.libraryViewQueries.library(MangaMapper::mapLibraryManga) }
    }

    override fun getLibraryMangaAsFlow(): Flow<List<LibraryManga>> {
        return handler.subscribeToList { db -> db.libraryViewQueries.library(MangaMapper::mapLibraryManga) }
    }

    override fun getMangaFavoritesBySourceId(sourceId: Long): Flow<List<Manga>> {
        return handler.subscribeToList { db -> db.mangasQueries.getFavoriteBySourceId(sourceId, MangaMapper::mapManga) }
    }

    override suspend fun getDuplicateLibraryManga(id: Long, title: String): List<Manga> {
        return handler.awaitList { db ->
            db.mangasQueries.getDuplicateLibraryManga(title, id, MangaMapper::mapManga)
        }
    }

    override suspend fun getUpcomingManga(statuses: Set<Long>): Flow<List<Manga>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList { db ->
            db.mangasQueries.getUpcomingManga(epochMillis, statuses, MangaMapper::mapManga)
        }
    }

    override suspend fun resetMangaViewerFlags(): Boolean {
        return try {
            handler.await { db -> db.mangasQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) { db ->
            db.mangas_categoriesQueries.deleteMangaCategoryByMangaId(mangaId)
            categoryIds.map { categoryId ->
                db.mangas_categoriesQueries.insert(mangaId, categoryId)
            }
        }
    }

    override suspend fun insertManga(manga: Manga): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) { db ->
            db.mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                artist = manga.artist,
                author = manga.author,
                description = manga.description,
                genre = manga.genre,
                title = manga.title,
                status = manga.status,
                thumbnailUrl = manga.thumbnailUrl,
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = manga.nextUpdate,
                calculateInterval = manga.fetchInterval.toLong(),
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
            )
            db.mangasQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun updateManga(update: MangaUpdate): Boolean {
        return try {
            partialUpdateManga(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean {
        return try {
            partialUpdateManga(*mangaUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateManga(vararg mangaUpdates: MangaUpdate) {
        handler.await(inTransaction = true) { db ->
            mangaUpdates.forEach { value ->
                db.mangasQueries.update(
                    source = value.source,
                    url = value.url,
                    artist = value.artist,
                    author = value.author,
                    description = value.description,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    mangaId = value.id,
                    updateStrategy = value.updateStrategy?.let(MangaUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                )

                // Emit achievement event if favorite status changed
                value.favorite?.let { isFavorite ->
                    val event = if (isFavorite) {
                        AchievementEvent.LibraryAdded(value.id, AchievementCategory.MANGA)
                    } else {
                        AchievementEvent.LibraryRemoved(value.id, AchievementCategory.MANGA)
                    }
                    eventBus.tryEmit(event)
                }
            }
        }
    }
}

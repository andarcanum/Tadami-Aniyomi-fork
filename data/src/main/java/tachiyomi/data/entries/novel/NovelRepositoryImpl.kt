package tachiyomi.data.entries.novel

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.library.novel.LibraryNovel

class NovelRepositoryImpl(
    private val handler: NovelDatabaseHandler,
    private val eventBus: AchievementEventBus,
) : NovelRepository {

    override suspend fun getNovelById(id: Long): Novel {
        return handler.awaitOne { db -> db.novelsQueries.getNovelById(id, NovelMapper::mapNovel) }
    }

    override suspend fun getNovelByIdAsFlow(id: Long): Flow<Novel> {
        return handler.subscribeToOne { db -> db.novelsQueries.getNovelById(id, NovelMapper::mapNovel) }
    }

    override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? {
        return handler.awaitOneOrNull { db ->
            db.novelsQueries.getNovelByUrlAndSource(
                url,
                sourceId,
                NovelMapper::mapNovel,
            )
        }
    }

    override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Novel?> {
        return handler.subscribeToOneOrNull { db ->
            db.novelsQueries.getNovelByUrlAndSource(
                url,
                sourceId,
                NovelMapper::mapNovel,
            )
        }
    }

    override suspend fun getNovelFavorites(): List<Novel> {
        return handler.awaitList { db -> db.novelsQueries.getFavorites(NovelMapper::mapNovel) }
    }

    override suspend fun getReadNovelNotInLibrary(): List<Novel> {
        return handler.awaitList { db -> db.novelsQueries.getReadNovelNotInLibrary(NovelMapper::mapNovel) }
    }

    override suspend fun getLibraryNovel(): List<LibraryNovel> {
        return handler.awaitList { db -> db.novellibraryViewQueries.library(NovelMapper::mapLibraryNovel) }
    }

    override fun getLibraryNovelAsFlow(): Flow<List<LibraryNovel>> {
        return handler.subscribeToList { db -> db.novellibraryViewQueries.library(NovelMapper::mapLibraryNovel) }
    }

    override fun getNovelFavoritesBySourceId(sourceId: Long): Flow<List<Novel>> {
        return handler.subscribeToList { db -> db.novelsQueries.getFavoriteBySourceId(sourceId, NovelMapper::mapNovel) }
    }

    override suspend fun insertNovel(novel: Novel): Long? {
        return handler.awaitOneOrNullExecutable(inTransaction = true) { db ->
            db.novelsQueries.insert(
                source = novel.source,
                url = novel.url,
                author = novel.author,
                description = novel.description,
                notes = novel.notes,
                genre = novel.genre,
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                pinned = novel.pinned,
                lastUpdate = novel.lastUpdate,
                nextUpdate = novel.nextUpdate,
                calculateInterval = novel.fetchInterval.toLong(),
                initialized = novel.initialized,
                viewerFlags = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                updateStrategy = novel.updateStrategy,
                version = novel.version,
            )
            db.novelsQueries.selectLastInsertedRowId()
        }
    }

    override suspend fun insertNetworkNovels(novels: List<Novel>, autoFavorite: Boolean): List<Novel> {
        if (novels.isEmpty()) return emptyList()
        return handler.await(inTransaction = true) { db ->
            novels.map { novel ->
                val local = db.novelsQueries
                    .getNovelByUrlAndSource(novel.url, novel.source, NovelMapper::mapNovel)
                    .executeAsOneOrNull()
                if (local == null) {
                    val toInsert = if (autoFavorite) {
                        novel.copy(favorite = true, dateAdded = System.currentTimeMillis())
                    } else {
                        novel
                    }
                    db.novelsQueries.insert(
                        source = toInsert.source,
                        url = toInsert.url,
                        author = toInsert.author,
                        description = toInsert.description,
                        notes = toInsert.notes,
                        genre = toInsert.genre,
                        title = toInsert.title,
                        status = toInsert.status,
                        thumbnailUrl = toInsert.thumbnailUrl,
                        favorite = toInsert.favorite,
                        pinned = toInsert.pinned,
                        lastUpdate = toInsert.lastUpdate,
                        nextUpdate = toInsert.nextUpdate,
                        calculateInterval = toInsert.fetchInterval.toLong(),
                        initialized = toInsert.initialized,
                        viewerFlags = toInsert.viewerFlags,
                        chapterFlags = toInsert.chapterFlags,
                        coverLastModified = toInsert.coverLastModified,
                        dateAdded = toInsert.dateAdded,
                        updateStrategy = toInsert.updateStrategy,
                        version = toInsert.version,
                    )
                    val insertedId = db.novelsQueries.selectLastInsertedRowId().executeAsOne()
                    toInsert.copy(id = insertedId)
                } else if (!local.favorite && !autoFavorite) {
                    val thumbnailUrl = if (local.thumbnailUrl.isNullOrBlank()) {
                        novel.thumbnailUrl
                    } else {
                        local.thumbnailUrl
                    }
                    val updated = local.copy(
                        title = novel.title,
                        thumbnailUrl = thumbnailUrl,
                    )
                    db.novelsQueries.update(
                        source = updated.source,
                        url = updated.url,
                        author = updated.author,
                        description = updated.description,
                        notes = updated.notes,
                        genre = updated.genre?.let(StringListColumnAdapter::encode),
                        title = updated.title,
                        status = updated.status,
                        thumbnailUrl = updated.thumbnailUrl,
                        favorite = updated.favorite,
                        pinned = updated.pinned,
                        lastUpdate = updated.lastUpdate,
                        nextUpdate = updated.nextUpdate,
                        calculateInterval = updated.fetchInterval.toLong(),
                        initialized = updated.initialized,
                        viewer = updated.viewerFlags,
                        chapterFlags = updated.chapterFlags,
                        coverLastModified = updated.coverLastModified,
                        dateAdded = updated.dateAdded,
                        novelId = updated.id,
                        updateStrategy = MangaUpdateStrategyColumnAdapter.encode(updated.updateStrategy),
                        version = updated.version,
                        isSyncing = 0,
                    )
                    updated
                } else if (autoFavorite && !local.favorite) {
                    val updated = local.copy(favorite = true, dateAdded = System.currentTimeMillis())
                    db.novelsQueries.update(
                        source = updated.source,
                        url = updated.url,
                        author = updated.author,
                        description = updated.description,
                        notes = updated.notes,
                        genre = updated.genre?.let(StringListColumnAdapter::encode),
                        title = updated.title,
                        status = updated.status,
                        thumbnailUrl = updated.thumbnailUrl,
                        favorite = updated.favorite,
                        pinned = updated.pinned,
                        lastUpdate = updated.lastUpdate,
                        nextUpdate = updated.nextUpdate,
                        calculateInterval = updated.fetchInterval.toLong(),
                        initialized = updated.initialized,
                        viewer = updated.viewerFlags,
                        chapterFlags = updated.chapterFlags,
                        coverLastModified = updated.coverLastModified,
                        dateAdded = updated.dateAdded,
                        novelId = updated.id,
                        updateStrategy = MangaUpdateStrategyColumnAdapter.encode(updated.updateStrategy),
                        version = updated.version,
                        isSyncing = 0,
                    )
                    updated
                } else {
                    local
                }
            }
        }
    }

    override suspend fun updateNovel(update: NovelUpdate): Boolean {
        return try {
            partialUpdateNovel(update)
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateAllNovel(novelUpdates: List<NovelUpdate>): Boolean {
        return try {
            partialUpdateNovel(*novelUpdates.toTypedArray())
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun resetNovelViewerFlags(): Boolean {
        return try {
            handler.await { db -> db.novelsQueries.resetViewerFlags() }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    override suspend fun updateNovelMetadata(
        novelId: Long,
        customTitle: String?,
        customAuthor: String?,
        customDescription: String?,
        customGenre: List<String>?,
        customStatus: Long?,
    ): Boolean {
        return try {
            handler.await { db ->
                db.novelsQueries.updateMetadata(
                    customTitle = customTitle,
                    customAuthor = customAuthor,
                    customDescription = customDescription,
                    customGenre = customGenre,
                    customStatus = customStatus,
                    novelId = novelId,
                )
            }
            true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
    }

    private suspend fun partialUpdateNovel(vararg novelUpdates: NovelUpdate) {
        handler.await(inTransaction = true) { db ->
            novelUpdates.forEach { value ->
                db.novelsQueries.update(
                    source = value.source,
                    url = value.url,
                    author = value.author,
                    description = value.description,
                    notes = value.notes,
                    genre = value.genre?.let(StringListColumnAdapter::encode),
                    title = value.title,
                    status = value.status,
                    thumbnailUrl = value.thumbnailUrl,
                    favorite = value.favorite,
                    pinned = value.pinned,
                    lastUpdate = value.lastUpdate,
                    nextUpdate = value.nextUpdate,
                    calculateInterval = value.fetchInterval?.toLong(),
                    initialized = value.initialized,
                    viewer = value.viewerFlags,
                    chapterFlags = value.chapterFlags,
                    coverLastModified = value.coverLastModified,
                    dateAdded = value.dateAdded,
                    novelId = value.id,
                    updateStrategy = value.updateStrategy?.let(MangaUpdateStrategyColumnAdapter::encode),
                    version = value.version,
                    isSyncing = 0,
                )

                value.favorite?.let { isFavorite ->
                    val event = if (isFavorite) {
                        AchievementEvent.LibraryAdded(value.id, AchievementCategory.NOVEL)
                    } else {
                        AchievementEvent.LibraryRemoved(value.id, AchievementCategory.NOVEL)
                    }
                    eventBus.tryEmit(event)
                }
            }
        }
    }
}

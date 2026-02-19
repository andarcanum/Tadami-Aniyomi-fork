package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import kotlin.math.max

class NovelRestorer(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
) {

    suspend fun sortByNew(backupNovels: List<BackupNovel>): List<BackupNovel> {
        val urlsBySource = handler.awaitList { novelsQueries.getAllNovelSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupNovels
            .sortedWith(
                compareBy<BackupNovel> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupNovel: BackupNovel,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbNovel = findExistingNovel(backupNovel)
            val novel = backupNovel.getNovelImpl()
            val restoredNovel = if (dbNovel == null) {
                restoreNewNovel(novel)
            } else {
                restoreExistingNovel(novel, dbNovel)
            }

            restoreNovelDetails(
                novel = restoredNovel,
                chapters = backupNovel.chapters,
                categories = backupNovel.categories,
                backupCategories = backupCategories,
                history = backupNovel.history,
                excludedScanlators = backupNovel.excludedScanlators,
            )
        }
    }

    private suspend fun findExistingNovel(backupNovel: BackupNovel): Novel? {
        return getNovelByUrlAndSourceId.await(backupNovel.url, backupNovel.source)
    }

    private suspend fun restoreExistingNovel(novel: Novel, dbNovel: Novel): Novel {
        return if (novel.version > dbNovel.version) {
            updateNovel(dbNovel.copyFrom(novel).copy(id = dbNovel.id))
        } else {
            updateNovel(novel.copyFrom(dbNovel).copy(id = dbNovel.id))
        }
    }

    private fun Novel.copyFrom(newer: Novel): Novel {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            author = newer.author,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    private suspend fun updateNovel(novel: Novel): Novel {
        handler.await(true) {
            novelsQueries.update(
                source = novel.source,
                url = novel.url,
                author = novel.author,
                description = novel.description,
                genre = novel.genre?.joinToString(separator = ", "),
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                lastUpdate = novel.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = novel.initialized,
                viewer = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                novelId = novel.id,
                updateStrategy = novel.updateStrategy.let(MangaUpdateStrategyColumnAdapter::encode),
                version = novel.version,
                isSyncing = 1,
            )
        }
        return novel
    }

    private suspend fun restoreNewNovel(novel: Novel): Novel {
        return novel.copy(
            initialized = novel.description != null,
            id = insertNovel(novel),
            version = novel.version,
        )
    }

    private suspend fun restoreChapters(novel: Novel, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = chapterRepository.getChapterByNovelId(novel.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .mapNotNull {
                val chapter = it.toNovelChapterImpl().copy(novelId = novel.id)

                val dbChapter = dbChaptersByUrl[chapter.url]
                    ?: // New chapter
                    return@mapNotNull chapter

                if (chapter.forComparison() == dbChapter.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing chapter
                var updatedChapter = chapter
                    .copyFrom(dbChapter)
                    .copy(
                        id = dbChapter.id,
                        bookmark = chapter.bookmark || dbChapter.bookmark,
                    )
                if (dbChapter.read && !updatedChapter.read) {
                    updatedChapter = updatedChapter.copy(
                        read = true,
                        lastPageRead = dbChapter.lastPageRead,
                    )
                } else if (updatedChapter.lastPageRead == 0L && dbChapter.lastPageRead != 0L) {
                    updatedChapter = updatedChapter.copy(
                        lastPageRead = dbChapter.lastPageRead,
                    )
                }
                updatedChapter
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun NovelChapter.forComparison() =
        this.copy(id = 0L, novelId = 0L, dateFetch = 0L, dateUpload = 0L, lastModifiedAt = 0L, version = 0L)

    private suspend fun insertNewChapters(chapters: List<NovelChapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                novel_chaptersQueries.insert(
                    chapter.novelId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<NovelChapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                novel_chaptersQueries.update(
                    novelId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 0,
                )
            }
        }
    }

    private suspend fun insertNovel(novel: Novel): Long {
        return handler.awaitOneExecutable(true) {
            novelsQueries.insert(
                source = novel.source,
                url = novel.url,
                author = novel.author,
                description = novel.description,
                genre = novel.genre,
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                lastUpdate = novel.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = novel.initialized,
                viewerFlags = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                updateStrategy = novel.updateStrategy,
                version = novel.version,
            )
            novelsQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreNovelDetails(
        novel: Novel,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        excludedScanlators: List<String>,
    ): Novel {
        restoreCategories(novel, categories, backupCategories)
        restoreChapters(novel, chapters)
        restoreHistory(history)
        restoreExcludedScanlators(novel, excludedScanlators)
        return novel
    }

    private suspend fun restoreCategories(
        novel: Novel,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val novelCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.id
            }
        }

        if (novelCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                novels_categoriesQueries.deleteNovelCategoryByNovelId(novel.id)
                novelCategoriesToUpdate.forEach { categoryId ->
                    novels_categoriesQueries.insert(novel.id, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { novel_historyQueries.getHistoryByChapterUrl(history.url) }
            val item = history.getNovelHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitOneOrNull { novel_chaptersQueries.getChapterByUrl(history.url) }
                return@mapNotNull if (chapter == null) {
                    // Chapter doesn't exist; skip
                    null
                } else {
                    // New history entry
                    item.copy(chapterId = chapter._id)
                }
            }

            // Update history entry
            item.copy(
                id = dbHistory._id,
                chapterId = dbHistory.chapter_id,
                readAt = max(item.readAt?.time ?: 0L, dbHistory.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbHistory.time_read) - dbHistory.time_read,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    novel_historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreExcludedScanlators(novel: Novel, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novel.id)
        }
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        if (toInsert.isNotEmpty()) {
            handler.await {
                toInsert.forEach { scanlator ->
                    novel_excluded_scanlatorsQueries.insert(novel.id, scanlator)
                }
            }
        }
    }
}

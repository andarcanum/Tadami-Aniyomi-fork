package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelBookState
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHighlight
import eu.kanade.tachiyomi.data.backup.restore.resolveRestoredText
import tachiyomi.data.MangaUpdateStrategyColumnAdapter
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.interactor.UpsertNovelBookState
import tachiyomi.domain.book.novel.model.NovelBookState
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.NovelFetchInterval
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class NovelRestorer(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val getNovelByUrlAndSourceId: GetNovelByUrlAndSourceId = Injekt.get(),
    private val getCategories: GetNovelCategories = Injekt.get(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val upsertNovelBookState: UpsertNovelBookState = Injekt.get(),
    private val novelFetchInterval: NovelFetchInterval = Injekt.get(),
    private val updateNovelUseCase: UpdateNovel = Injekt.get(),
) {

    private val now: ZonedDateTime = ZonedDateTime.now()
    private val currentFetchWindow: Pair<Long, Long> = novelFetchInterval.getWindow(now)

    suspend fun sortByNew(backupNovels: List<BackupNovel>): List<BackupNovel> {
        val urlsBySource = handler.awaitList { db -> db.novelsQueries.getAllNovelSourceAndUrl() }
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
        handler.await(inTransaction = true) { db ->
            val dbNovel = findExistingNovel(backupNovel)
            val novel = backupNovel.getNovelImpl()
            val restoredNovel = if (dbNovel == null) {
                restoreNewNovel(novel)
            } else {
                restoreExistingNovel(backupNovel, novel, dbNovel)
            }

            restoreNovelDetails(
                novel = restoredNovel,
                chapters = backupNovel.chapters,
                categories = backupNovel.categories,
                backupCategories = backupCategories,
                history = backupNovel.history,
                excludedScanlators = backupNovel.excludedScanlators,
                bookState = backupNovel.bookState,
                highlights = backupNovel.highlights,
            )
        }
    }

    private suspend fun findExistingNovel(backupNovel: BackupNovel): Novel? {
        return getNovelByUrlAndSourceId.await(backupNovel.url, backupNovel.source)
    }

    private suspend fun restoreExistingNovel(backupNovel: BackupNovel, novel: Novel, dbNovel: Novel): Novel {
        val notes = resolveRestoredText(backupNovel.notes, novel.version, dbNovel.notes, dbNovel.version)
        return if (novel.version > dbNovel.version) {
            updateNovel(
                dbNovel.copyFrom(novel)
                    .copy(id = dbNovel.id, notes = notes)
                    .markForSourceReinit(),
            )
        } else {
            updateNovel(
                novel.copyFrom(dbNovel)
                    .copy(id = dbNovel.id, notes = notes)
                    .markForSourceReinit(),
            )
        }
    }

    private fun Novel.copyFrom(newer: Novel): Novel {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            pinned = this.pinned || newer.pinned,
            author = newer.author,
            description = newer.description,
            genre = newer.genre,
            thumbnailUrl = newer.thumbnailUrl,
            status = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
            customTitle = newer.customTitle ?: this.customTitle,
            customAuthor = newer.customAuthor ?: this.customAuthor,
            customDescription = newer.customDescription ?: this.customDescription,
            customGenre = newer.customGenre ?: this.customGenre,
            customStatus = newer.customStatus ?: this.customStatus,
            completedAt = newer.completedAt ?: this.completedAt,
        )
    }

    private suspend fun updateNovel(novel: Novel): Novel {
        handler.await(true) { db ->
            db.novelsQueries.update(
                source = novel.source,
                url = novel.url,
                author = novel.author,
                description = novel.description,
                notes = novel.notes,
                genre = novel.genre?.joinToString(separator = ", "),
                title = novel.title,
                status = novel.status,
                thumbnailUrl = novel.thumbnailUrl,
                favorite = novel.favorite,
                pinned = novel.pinned,
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
                completedAt = novel.completedAt,
            )
            db.novelsQueries.updateMetadata(
                customTitle = novel.customTitle,
                customAuthor = novel.customAuthor,
                customDescription = novel.customDescription,
                customGenre = novel.customGenre,
                customStatus = novel.customStatus,
                novelId = novel.id,
            )
        }
        return novel
    }

    private suspend fun restoreNewNovel(novel: Novel): Novel {
        val restoredNovel = novel.markForSourceReinit()
        return restoredNovel.copy(
            id = insertNovel(restoredNovel),
            version = novel.version,
        )
    }

    private fun Novel.markForSourceReinit(): Novel {
        return copy(initialized = false)
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
        this.copy(
            id = 0L,
            novelId = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            dateUploadRaw = null,
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<NovelChapter>) {
        handler.await(true) { db ->
            chapters.forEach { chapter ->
                db.novel_chaptersQueries.insert(
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
                    chapter.dateUploadRaw,
                    chapter.version,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<NovelChapter>) {
        handler.await(true) { db ->
            chapters.forEach { chapter ->
                db.novel_chaptersQueries.update(
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
                    dateUploadRaw = null,
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 0,
                )
            }
        }
    }

    private suspend fun insertNovel(novel: Novel): Long {
        return handler.await(true) { db ->
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
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = novel.initialized,
                viewerFlags = novel.viewerFlags,
                chapterFlags = novel.chapterFlags,
                coverLastModified = novel.coverLastModified,
                dateAdded = novel.dateAdded,
                updateStrategy = novel.updateStrategy,
                version = novel.version,
                completedAt = novel.completedAt,
            )
            val novelId = db.novelsQueries.selectLastInsertedRowId().executeAsOne()
            db.novelsQueries.updateMetadata(
                customTitle = novel.customTitle,
                customAuthor = novel.customAuthor,
                customDescription = novel.customDescription,
                customGenre = novel.customGenre,
                customStatus = novel.customStatus,
                novelId = novelId,
            )
            novelId
        }
    }

    private suspend fun restoreNovelDetails(
        novel: Novel,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        excludedScanlators: List<String>,
        bookState: BackupNovelBookState? = null,
        highlights: List<BackupNovelHighlight> = emptyList(),
    ): Novel {
        restoreCategories(novel, categories, backupCategories)
        restoreChapters(novel, chapters)
        restoreHistory(history)
        restoreExcludedScanlators(novel, excludedScanlators)
        restoreBookState(novel, bookState)
        restoreHighlights(novel, highlights)
        // Recompute the expected next release date from the restored chapter rhythm so restored
        // novels appear in the Upcoming calendar right away (parity with MangaRestorer).
        updateNovelUseCase.awaitUpdateFetchInterval(novel, now, currentFetchWindow)
        return novel
    }

    /**
     * Restores saved highlights. Chapters resolve by URL; unknown URLs are skipped silently.
     * Duplicates of an earlier restore (same chapter, range and text) are not re-inserted.
     */
    private suspend fun restoreHighlights(novel: Novel, highlights: List<BackupNovelHighlight>) {
        if (highlights.isEmpty()) return
        val chaptersByUrl = chapterRepository.getChapterByNovelId(novel.id).associateBy { it.url }
        val existingKeys = handler.awaitList { db -> db.novel_highlightsQueries.getForNovel(novel.id) }
            .map { HighlightKey(it.chapter_id, it.block_index.toInt(), it.char_start.toInt(), it.normalized_text) }
            .toSet()
        handler.await(true) { db ->
            highlights.forEach { backup ->
                val chapterId = chaptersByUrl[backup.chapterUrl]?.id ?: return@forEach
                val key = HighlightKey(chapterId, backup.blockIndex, backup.charStart, backup.normalizedText)
                if (key in existingKeys) return@forEach
                db.novel_highlightsQueries.insert(
                    novelId = novel.id,
                    chapterId = chapterId,
                    blockIndex = backup.blockIndex.toLong(),
                    charStart = backup.charStart.toLong(),
                    charEndExclusive = backup.charEndExclusive.toLong(),
                    normalizedText = backup.normalizedText,
                    colorArgb = backup.colorArgb,
                    note = backup.note,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt,
                    pageIndex = backup.pageIndex.toLong(),
                    pageCount = backup.pageCount.toLong(),
                )
            }
        }
    }

    private data class HighlightKey(
        val chapterId: Long,
        val blockIndex: Int,
        val charStart: Int,
        val normalizedText: String,
    )

    /**
     * Restores the compiled-book state without the artifact: the reading offset and the chapter-set
     * hash come back, so the title screen can tell that the book has to be compiled again on this
     * device while the saved position is not lost. The last read chapter is resolved by URL because
     * chapter ids differ between devices.
     */
    private suspend fun restoreBookState(novel: Novel, bookState: BackupNovelBookState?) {
        if (bookState == null) return
        val lastChapterId = bookState.lastChapterUrl?.let { url ->
            runCatching { chapterRepository.getChapterByUrlAndNovelId(url, novel.id)?.id }.getOrNull()
        }
        upsertNovelBookState.await(
            NovelBookState(
                novelId = novel.id,
                enabled = bookState.enabled,
                bookVersion = bookState.bookVersion,
                sourceId = novel.source,
                chapterSetHash = bookState.chapterSetHash,
                totalChars = bookState.totalChars,
                chapterCount = bookState.chapterCount,
                charOffset = bookState.charOffset,
                lastChapterId = lastChapterId,
                complete = bookState.complete,
                builtAt = bookState.builtAt,
                updatedAt = bookState.updatedAt,
                blockIndex = bookState.blockIndex,
                chapterCharOffset = bookState.chapterCharOffset,
                progressMigrated = bookState.progressMigrated,
            ),
        )
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
            handler.await(true) { db ->
                db.novels_categoriesQueries.deleteNovelCategoryByNovelId(novel.id)
                novelCategoriesToUpdate.forEach { categoryId ->
                    db.novels_categoriesQueries.insert(novel.id, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(backupHistory: List<BackupHistory>) {
        val toUpdate = backupHistory.mapNotNull { history ->
            val dbHistory = handler.awaitOneOrNull { db -> db.novel_historyQueries.getHistoryByChapterUrl(history.url) }
            val item = history.getNovelHistoryImpl()

            if (dbHistory == null) {
                val chapter = handler.awaitOneOrNull { db -> db.novel_chaptersQueries.getChapterByUrl(history.url) }
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
            handler.await(true) { db ->
                toUpdate.forEach {
                    db.novel_historyQueries.upsert(
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
        val existingExcludedScanlators = handler.awaitList { db ->
            db.novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novel.id)
        }
        val toInsert = excludedScanlators.filter { it !in existingExcludedScanlators }
        if (toInsert.isNotEmpty()) {
            handler.await { db ->
                toInsert.forEach { scanlator ->
                    db.novel_excluded_scanlatorsQueries.insert(novel.id, scanlator)
                }
            }
        }
    }
}

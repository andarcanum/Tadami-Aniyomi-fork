package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelBookState
import eu.kanade.tachiyomi.data.backup.models.BackupNovelHighlight
import eu.kanade.tachiyomi.data.backup.models.backupNovelChapterMapper
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.book.novel.interactor.GetNovelBookState
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val categoryRepository: NovelCategoryRepository = Injekt.get(),
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
    private val getNovelBookState: GetNovelBookState = Injekt.get(),
) {

    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        return novels.map {
            backupNovel(it, options)
        }
    }

    private suspend fun backupNovel(novel: Novel, options: BackupOptions): BackupNovel {
        val novelObject = novel.toBackupNovel()

        novelObject.excludedScanlators = handler.awaitList { db ->
            db.novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novel.id)
        }

        if (options.chapters) {
            handler.awaitList { db ->
                db.novel_chaptersQueries.getChaptersByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupNovelChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { novelObject.chapters = it }

            val chapterUrlsById = handler.awaitList { db ->
                db.novel_chaptersQueries.getChaptersByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = 0,
                )
            }.associateBy({ it._id }, { it.url })
            val highlights = handler.awaitList { db ->
                db.novel_highlightsQueries.getForNovel(novel.id)
            }
            highlights.takeUnless { it.isEmpty() }?.let { rows ->
                novelObject.highlights = rows.map { row ->
                    BackupNovelHighlight(
                        chapterUrl = chapterUrlsById[row.chapter_id].orEmpty(),
                        blockIndex = row.block_index.toInt(),
                        charStart = row.char_start.toInt(),
                        charEndExclusive = row.char_end_exclusive.toInt(),
                        normalizedText = row.normalized_text,
                        colorArgb = row.color_argb,
                        note = row.note,
                        createdAt = row.created_at,
                        updatedAt = row.updated_at,
                        pageIndex = row.page_index.toInt(),
                        pageCount = row.page_count.toInt(),
                    )
                }
            }
        }

        if (options.categories) {
            val categoriesForNovel = categoryRepository.getCategoriesByNovelId(novel.id)
            if (categoriesForNovel.isNotEmpty()) {
                novelObject.categories = categoriesForNovel.map { it.order }
            }
        }

        if (options.history) {
            val historyByNovelId = historyRepository.getHistoryByNovelId(novel.id)
            if (historyByNovelId.isNotEmpty()) {
                val history = historyByNovelId.map { history ->
                    val chapter = handler.awaitOne { db -> db.novel_chaptersQueries.getChapterById(history.chapterId) }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    novelObject.history = history
                }
            }
        }

        // The compiled book artifact is derived data and stays out of the backup; only its state and
        // reading offset are stored, with the last chapter kept as a URL so it survives new ids.
        getNovelBookState.await(novel.id)?.let { bookState ->
            val lastChapterUrl = bookState.lastChapterId?.let { chapterId ->
                runCatching {
                    handler.awaitOne { db -> db.novel_chaptersQueries.getChapterById(chapterId) }.url
                }.getOrNull()
            }
            novelObject.bookState = BackupNovelBookState(
                enabled = bookState.enabled,
                bookVersion = bookState.bookVersion,
                sourceId = bookState.sourceId,
                chapterSetHash = bookState.chapterSetHash,
                totalChars = bookState.totalChars,
                chapterCount = bookState.chapterCount,
                charOffset = bookState.charOffset,
                lastChapterUrl = lastChapterUrl,
                complete = bookState.complete,
                builtAt = bookState.builtAt,
                updatedAt = bookState.updatedAt,
                blockIndex = bookState.blockIndex,
                chapterCharOffset = bookState.chapterCharOffset,
                progressMigrated = bookState.progressMigrated,
            )
        }

        return novelObject
    }
}

private fun Novel.toBackupNovel() =
    BackupNovel(
        url = this.url,
        title = this.title,
        author = this.author,
        description = this.description,
        notes = this.notes.takeIf { it.isNotBlank() },
        genre = this.genre.orEmpty(),
        status = this.status.toInt(),
        thumbnailUrl = this.thumbnailUrl,
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewerFlags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        customTitle = this.customTitle,
        customAuthor = this.customAuthor,
        customDescription = this.customDescription,
        customGenre = this.customGenre,
        customStatus = this.customStatus,
    )

package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelBackupCreator(
    private val handler: NovelDatabaseHandler = Injekt.get(),
    private val categoryRepository: NovelCategoryRepository = Injekt.get(),
    private val historyRepository: NovelHistoryRepository = Injekt.get(),
) {

    suspend operator fun invoke(novels: List<Novel>, options: BackupOptions): List<BackupNovel> {
        return novels.map {
            backupNovel(it, options)
        }
    }

    private suspend fun backupNovel(novel: Novel, options: BackupOptions): BackupNovel {
        val novelObject = novel.toBackupNovel()

        novelObject.excludedScanlators = handler.awaitList {
            novel_excluded_scanlatorsQueries.getExcludedScanlatorsByNovelId(novel.id)
        }

        if (options.chapters) {
            handler.awaitList {
                novel_chaptersQueries.getChaptersByNovelId(
                    novelId = novel.id,
                    applyScanlatorFilter = 0, // false
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { novelObject.chapters = it }
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
                    val chapter = handler.awaitOne { novel_chaptersQueries.getChapterById(history.chapterId) }
                    BackupHistory(chapter.url, history.readAt?.time ?: 0L, history.readDuration)
                }
                if (history.isNotEmpty()) {
                    novelObject.history = history
                }
            }
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
    )

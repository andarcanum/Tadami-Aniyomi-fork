package eu.kanade.domain.entries.novel.interactor

import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import tachiyomi.domain.category.novel.repository.NovelCategoryRepository
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.novelchapter.model.toNovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

/**
 * Migrates a novel (and optionally its chapters, categories, downloads) from one source to
 * another. Extracted from MigrateNovelDialogScreenModel so it can be reused by both the
 * single-entry migration dialog and bulk migration.
 */
class MigrateNovelUseCase(
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = NovelDownloadManager(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val networkToLocalNovel: NetworkToLocalNovel = Injekt.get(),
    private val novelChapterRepository: NovelChapterRepository = Injekt.get(),
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get(),
    private val categoryRepository: NovelCategoryRepository = Injekt.get(),
) {

    suspend fun migrateNovel(
        oldNovel: Novel,
        newNovel: Novel,
        replace: Boolean,
        flags: Int,
    ) {
        val source = sourceManager.get(newNovel.source) ?: return
        val prevSource = sourceManager.get(oldNovel.source)
        val localNewNovel = networkToLocalNovel.await(newNovel)
        if (oldNovel.id == localNewNovel.id) return

        val chapters = source.getChapterList(localNewNovel.toSNovel())

        migrateNovelInternal(
            oldSource = prevSource,
            newSource = source,
            oldNovel = oldNovel,
            newNovel = localNewNovel,
            sourceChapters = chapters,
            replace = replace,
            flags = flags,
        )
    }

    private suspend fun migrateNovelInternal(
        oldSource: NovelSource?,
        newSource: NovelSource,
        oldNovel: Novel,
        newNovel: Novel,
        sourceChapters: List<SNovelChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = NovelMigrationFlags.hasChapters(flags)
        val migrateCategories = NovelMigrationFlags.hasCategories(flags)
        val deleteDownloaded = NovelMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncNovelChaptersWithSource.await(sourceChapters, newNovel, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced.
        }

        if (migrateChapters) {
            val prevNovelChapters = novelChapterRepository.getChapterByNovelId(oldNovel.id)
            val novelChapters = novelChapterRepository.getChapterByNovelId(newNovel.id)

            val maxChapterRead = prevNovelChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedNovelChapters = novelChapters.map { novelChapter ->
                var updatedChapter = novelChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevNovelChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                            lastPageRead = prevChapter.lastPageRead,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true, lastPageRead = 0L)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedNovelChapters.map { it.toNovelChapterUpdate() }
            novelChapterRepository.updateAllChapters(chapterUpdates)
        }

        if (migrateCategories) {
            val categoryIds = categoryRepository.getCategoriesByNovelId(oldNovel.id).map { it.id }
            categoryRepository.setNovelCategories(newNovel.id, categoryIds)
        }

        if (deleteDownloaded && oldSource != null) {
            downloadManager.deleteNovel(oldNovel)
        }

        if (replace) {
            updateNovel.await(
                NovelUpdate(
                    id = oldNovel.id,
                    favorite = false,
                    dateAdded = 0L,
                ),
            )
        }

        updateNovel.await(
            NovelUpdate(
                id = newNovel.id,
                favorite = true,
                chapterFlags = oldNovel.chapterFlags,
                viewerFlags = oldNovel.viewerFlags,
                dateAdded = if (replace) oldNovel.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }
}

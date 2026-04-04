package eu.kanade.domain.entries.manga.interactor

import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.toChapterUpdate
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

class MigrateMangaUseCase(
    private val sourceManager: MangaSourceManager = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getTracks: GetMangaTracks = Injekt.get(),
    private val insertTrack: InsertMangaTrack = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {

    private val enhancedServices by lazy {
        trackerManager.trackers.filterIsInstance<EnhancedMangaTracker>()
    }

    suspend fun migrateManga(
        oldManga: Manga,
        newManga: Manga,
        replace: Boolean,
        flags: Int,
    ) {
        val source = sourceManager.get(newManga.source) ?: return
        val prevSource = sourceManager.get(oldManga.source)

        val chapters = source.getChapterList(newManga.toSManga())

        migrateMangaInternal(
            oldSource = prevSource,
            newSource = source,
            oldManga = oldManga,
            newManga = newManga,
            sourceChapters = chapters,
            replace = replace,
            flags = flags,
        )
    }

    private suspend fun migrateMangaInternal(
        oldSource: MangaSource?,
        newSource: MangaSource,
        oldManga: Manga,
        newManga: Manga,
        sourceChapters: List<SChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasChapters(flags)
        val migrateCategories = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasCategories(flags)
        val migrateCustomCover = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncChaptersWithSource.await(sourceChapters, newManga, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced.
        }

        if (migrateChapters) {
            val prevMangaChapters = getChaptersByMangaId.await(oldManga.id)
            val mangaChapters = getChaptersByMangaId.await(newManga.id)

            val maxChapterRead = prevMangaChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                var updatedChapter = mangaChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevMangaChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            updateChapter.awaitAll(updatedMangaChapters.map { it.toChapterUpdate() })
        }

        if (migrateCategories) {
            val categoryIds = getCategories.await(oldManga.id).map { it.id }
            setMangaCategories.await(newManga.id, categoryIds)
        }

        getTracks.await(oldManga.id)
            .mapNotNull { track ->
                val updatedTrack = track.copy(mangaId = newManga.id)
                val service = enhancedServices.firstOrNull { it.isTrackFrom(updatedTrack, oldManga, oldSource) }
                if (service != null) {
                    service.migrateTrack(updatedTrack, newManga, newSource)
                } else {
                    updatedTrack
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        if (deleteDownloaded && oldSource != null) {
            downloadManager.deleteManga(oldManga, oldSource)
        }

        if (replace) {
            updateManga.awaitUpdateFavorite(oldManga.id, favorite = false)
        }

        if (migrateCustomCover && oldManga.hasCustomCover(coverCache)) {
            coverCache.setCustomCoverToCache(
                newManga,
                coverCache.getCustomCoverFile(oldManga.id).inputStream(),
            )
        }

        updateManga.await(
            MangaUpdate(
                id = newManga.id,
                favorite = true,
                chapterFlags = oldManga.chapterFlags,
                viewerFlags = oldManga.viewerFlags,
                dateAdded = if (replace) oldManga.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }
}

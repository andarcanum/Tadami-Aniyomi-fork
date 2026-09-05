package eu.kanade.domain.entries.manga.interactor

import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
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
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getCategories: GetMangaCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val getTracks: GetMangaTracks = Injekt.get(),
    private val insertTrack: InsertMangaTrack = Injekt.get(),
    private val coverCache: MangaCoverCache = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val getHistory: GetMangaHistory = Injekt.get(),
    private val upsertHistory: UpsertMangaHistory = Injekt.get(),
    private val getExcludedScanlators: GetExcludedScanlators = Injekt.get(),
    private val setExcludedScanlators: SetExcludedScanlators = Injekt.get(),
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
        val localNewManga = networkToLocalManga.await(newManga)
        if (oldManga.id == localNewManga.id) return

        val chapters = source.getMangaUpdate(
            manga = localNewManga.toSManga(),
            chapters = emptyList(),
            fetchDetails = false,
            fetchChapters = true,
        ).chapters

        migrateMangaInternal(
            oldSource = prevSource,
            newSource = source,
            oldManga = oldManga,
            newManga = localNewManga,
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
        val migrateTracking = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasTracking(flags)
        val migrateExtra = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasExtra(flags)
        val migrateNotes = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasNotes(flags)
        val migrateCustomCover = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = eu.kanade.tachiyomi.ui.browse.manga.migration.MangaMigrationFlags.hasDeleteDownloaded(
            flags,
        )

        try {
            syncChaptersWithSource.await(sourceChapters, newManga, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced.
        }

        // F-H1: everything below is local state mutation and runs NonCancellable. A cancel or
        // dispose (progress-dialog Cancel, leaving the screen, dialog teardown) used to tear the
        // migration between its suspend writes: downloads already deleted while the old entry was
        // still favorited, or BOTH entries favorited when the cut landed between the two favorite
        // writes. Now a started entry always completes consistently; the batch loop still stops
        // between entries.
        withNonCancellableContext {
            if (migrateChapters) {
                val prevMangaChapters = getChaptersByMangaId.await(oldManga.id)
                val mangaChapters = getChaptersByMangaId.await(newManga.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }
                val prevHistoryByChapterId = getHistory.await(oldManga.id).associateBy { it.chapterId }
                val historyUpdates = mutableListOf<MangaHistoryUpdate>()

                val updatedMangaChapters = mangaChapters.map { mangaChapter ->
                    var updatedChapter = mangaChapter
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                        if (prevChapter != null) {
                            updatedChapter = updatedChapter.copy(
                                read = prevChapter.read,
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                                lastPageRead = prevChapter.lastPageRead,
                            )
                            prevHistoryByChapterId[prevChapter.id]?.let { prevHistory ->
                                historyUpdates += MangaHistoryUpdate(
                                    chapterId = mangaChapter.id,
                                    readAt = prevHistory.readAt ?: return@let,
                                    sessionReadDuration = prevHistory.readDuration,
                                )
                            }
                        } else if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            updatedChapter = updatedChapter.copy(read = true)
                        }
                    }

                    updatedChapter
                }

                updateChapter.awaitAll(updatedMangaChapters.map { it.toChapterUpdate() })
                historyUpdates.forEach { upsertHistory.await(it) }

                // F-M4: excluded scanlators are per-entry state backing the scanlator-branch
                // feature; migration silently dropped them, orphaning the old entry's branch
                // filter. They travel with the chapters flag (they filter chapter visibility).
                val excludedScanlators = getExcludedScanlators.await(oldManga.id)
                if (excludedScanlators.isNotEmpty()) {
                    setExcludedScanlators.await(newManga.id, excludedScanlators)
                }
            }

            if (migrateCategories) {
                val categoryIds = getCategories.await(oldManga.id).map { it.id }
                setMangaCategories.await(newManga.id, categoryIds)
            }

            if (migrateTracking) {
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
            }

            // F-H1 order fix: the favorite swap happens BEFORE the destructive download delete
            // (old order: delete -> favorite new -> unfavorite old).
            // DECISION-4a: the fork exposes Migrate for non-library entries; favoriting the
            // target is kept for replace-migrations and favorited sources, but copy-migrating
            // an entry that is NOT in the library no longer silently favorites the target.
            updateManga.await(
                MangaUpdate(
                    id = newManga.id,
                    favorite = replace || oldManga.favorite,
                    chapterFlags = if (migrateExtra) oldManga.chapterFlags else null,
                    viewerFlags = if (migrateExtra) oldManga.viewerFlags else null,
                    dateAdded = if (replace) oldManga.dateAdded else Instant.now().toEpochMilli(),
                    notes = if (migrateNotes) oldManga.notes else null,
                ),
            )

            if (replace) {
                updateManga.awaitUpdateFavorite(oldManga.id, favorite = false)
            }

            if (deleteDownloaded && oldSource != null) {
                downloadManager.deleteManga(oldManga, oldSource)
            }

            if (migrateCustomCover && oldManga.hasCustomCover(coverCache)) {
                // F-M7(fd): close the source stream; the callee only closes its output stream.
                coverCache.getCustomCoverFile(oldManga.id).inputStream().use { stream ->
                    coverCache.setCustomCoverToCache(newManga, stream)
                }
            }
        }
    }
}

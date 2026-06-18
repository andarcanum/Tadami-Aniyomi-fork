package eu.kanade.tachiyomi.data.library.anime

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.anime.interactor.AnimeRatingFetcher
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.track.anime.MapAnimeTrackStatusToLibrary
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateFailure
import eu.kanade.tachiyomi.data.library.LibraryUpdatePacingPolicy
import eu.kanade.tachiyomi.data.library.shouldRetryLegacyAutoUpdateRun
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorMedia
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorRunType
import eu.kanade.tachiyomi.data.library.updateerror.LibraryUpdateErrorStore
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isCharging
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueued
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.anime.model.AnimeSourceNotInstalledException
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AnimeLibraryUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadManager: AnimeDownloadManager = Injekt.get()
    private val coverCache: AnimeCoverCache = Injekt.get()
    private val backgroundCache: AnimeBackgroundCache = Injekt.get()
    private val getLibraryAnime: GetLibraryAnime = Injekt.get()
    private val getAnime: GetAnime = Injekt.get()
    private val updateAnime: UpdateAnime = Injekt.get()
    private val animeRatingFetcher: AnimeRatingFetcher = Injekt.get()
    private val syncEpisodesWithSource: SyncEpisodesWithSource = Injekt.get()
    private val animeFetchInterval: AnimeFetchInterval = Injekt.get()
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get()
    private val getAnimeSeasonsByParentId: GetAnimeSeasonsByParentId = Injekt.get()
    private val pacingPolicy = LibraryUpdatePacingPolicy(Injekt.get())

    private val notifier = AnimeLibraryUpdateNotifier(context)

    private var animeToUpdate: List<LibraryAnime> = mutableListOf()

    override suspend fun doWork(): Result {
        val uiPreferences: UiPreferences = Injekt.get()
        if (!uiPreferences.showAnimeSection().get()) {
            return Result.success()
        }

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
        }

        if (tags.contains(WORK_NAME_AUTO)) {
            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val preferences = Injekt.get<LibraryPreferences>()
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                if (shouldRetryLegacyAutoUpdateRun(
                        restrictions = restrictions,
                        isConnectedToWifi = context.isConnectedToWifi(),
                        isCharging = context.isCharging(),
                    )
                ) {
                    return Result.retry()
                }
            }
        }

        libraryPreferences.lastUpdatedTimestamp().set(Instant.now().toEpochMilli())

        val categoryId = if (inputData.keyValueMap.containsKey(KEY_CATEGORY)) {
            inputData.getLong(KEY_CATEGORY, -1L)
        } else {
            -999L
        }
        addAnimeToQueue(categoryId)

        return withIOContext {
            try {
                updateEpisodeList(isManualRun = tags.contains(WORK_NAME_MANUAL))
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                Result.failure()
            } finally {
                notifier.cancelProgressNotification()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notifier = AnimeLibraryUpdateNotifier(context)
        return ForegroundInfo(
            Notifications.ID_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun filterByCategoryId(libraryAnime: List<LibraryAnime>, categoryId: Long): List<LibraryAnime> {
        return when {
            categoryId == -1L -> {
                // Ungrouped
                libraryAnime.filter { it.category == 0L }
            }
            categoryId == -2L -> {
                // Untracked
                val getTracksPerAnime: GetTracksPerAnime = Injekt.get()
                val tracks = getTracksPerAnime.subscribe().first()
                libraryAnime.filter { tracks[it.anime.id].orEmpty().isEmpty() }
            }
            categoryId in -17L..-10L -> {
                // Tracked status
                val targetStatusInt = (-categoryId - 10L).toInt()
                val getTracksPerAnime: GetTracksPerAnime = Injekt.get()
                val tracks = getTracksPerAnime.subscribe().first()
                val trackerManager = Injekt.get<eu.kanade.tachiyomi.data.track.TrackerManager>()
                val trackMapper = MapAnimeTrackStatusToLibrary(trackerManager)
                libraryAnime.filter { item ->
                    val itemTracks = tracks[item.anime.id].orEmpty()
                    itemTracks.any { track ->
                        trackMapper.map(track.trackerId, track.status).int == targetStatusInt
                    }
                }
            }
            categoryId in -26L..-20L -> {
                // Status
                val targetStatus = when (categoryId) {
                    -21L -> SAnime.ONGOING
                    -22L -> SAnime.COMPLETED
                    -23L -> SAnime.LICENSED
                    -24L -> SAnime.PUBLISHING_FINISHED
                    -25L -> SAnime.CANCELLED
                    -26L -> SAnime.ON_HIATUS
                    else -> -1
                }
                if (targetStatus == -1) {
                    libraryAnime.filter {
                        it.anime.status.toInt() !in
                            listOf(
                                SAnime.ONGOING,
                                SAnime.COMPLETED,
                                SAnime.LICENSED,
                                SAnime.PUBLISHING_FINISHED,
                                SAnime.CANCELLED,
                                SAnime.ON_HIATUS,
                            )
                    }
                } else {
                    libraryAnime.filter { it.anime.status.toInt() == targetStatus }
                }
            }
            categoryId < -1000L -> {
                // Source
                val targetSourceId = -categoryId - 1000L
                libraryAnime.filter { it.anime.source == targetSourceId }
            }
            else -> {
                libraryAnime.filter { it.category == categoryId }
            }
        }
    }

    /**
     * Adds list of anime to be updated.
     *
     * @param categoryId the ID of the category to update, or -1 if no category specified.
     */
    private suspend fun addAnimeToQueue(categoryId: Long) {
        val libraryAnime = getLibraryAnime.await()
        val targetEntryIds = inputData.getLongArray(KEY_ENTRY_IDS)
            ?.takeIf { it.isNotEmpty() }
            ?.toSet()

        val listToUpdate = if (targetEntryIds != null) {
            libraryAnime
                .filter { it.anime.id in targetEntryIds }
                .distinctBy { it.anime.id }
        } else if (categoryId != -999L) {
            filterByCategoryId(libraryAnime, categoryId)
        } else {
            val categoriesToUpdate = libraryPreferences.animeUpdateCategories().get().map { it.toLong() }
            val includedAnime = if (categoriesToUpdate.isNotEmpty()) {
                libraryAnime.filter { it.category in categoriesToUpdate }
            } else {
                libraryAnime
            }

            val categoriesToExclude = libraryPreferences.animeUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedAnimeIds = if (categoriesToExclude.isNotEmpty()) {
                libraryAnime.filter { it.category in categoriesToExclude }.map { it.anime.id }
            } else {
                emptyList()
            }

            includedAnime
                .filterNot { it.anime.id in excludedAnimeIds }
                .distinctBy { it.anime.id }
        }

        if (targetEntryIds != null) {
            val queuedIds = listToUpdate.mapTo(mutableSetOf()) { it.anime.id }
            targetEntryIds
                .filterNot { it in queuedIds }
                .forEach { entryId ->
                    LibraryUpdateErrorStore.markResolved(
                        media = LibraryUpdateErrorMedia.Anime,
                        entryId = entryId,
                    )
                }
        }

        val includeSeasons = targetEntryIds == null && libraryPreferences.updateSeasonOnLibraryUpdate().get()
        val lastToUpdateWithSeasons = listToUpdate.flatMap { libAnime ->
            when (libAnime.anime.fetchType) {
                FetchType.Seasons -> {
                    if (includeSeasons) {
                        val seasons = getAnimeSeasonsByParentId.await(libAnime.anime.id)
                        seasons
                            .filter { s ->
                                s.anime.fetchType == FetchType.Episodes && !s.anime.favorite
                            }
                            .map { it.toLibraryAnime() }
                    } else {
                        emptyList()
                    }
                }
                FetchType.Episodes -> listOf(libAnime)
            }
        }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get().takeIf {
            targetEntryIds == null
        }.orEmpty()
        val skippedUpdates = mutableListOf<Pair<Anime, String?>>()
        val (_, fetchWindowUpperBound) = animeFetchInterval.getWindow(ZonedDateTime.now())

        animeToUpdate = lastToUpdateWithSeasons
            .filter {
                when {
                    it.anime.updateStrategy != AnimeUpdateStrategy.ALWAYS_UPDATE -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_always_update),
                        )
                        false
                    }

                    ENTRY_NON_COMPLETED in restrictions && it.anime.status.toInt() == SAnime.COMPLETED -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_completed),
                        )
                        false
                    }

                    ENTRY_HAS_UNVIEWED in restrictions && it.unseenCount != 0L -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_caught_up),
                        )
                        false
                    }

                    ENTRY_NON_VIEWED in restrictions && it.totalCount > 0L && !it.hasStarted -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_started),
                        )
                        false
                    }

                    ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && it.anime.nextUpdate > fetchWindowUpperBound -> {
                        skippedUpdates.add(
                            it.anime to context.stringResource(MR.strings.skipped_reason_not_in_release_period),
                        )
                        false
                    }
                    else -> true
                }
            }
            .sortedBy { it.anime.title }

        notifier.showQueueSizeWarningNotificationIfNeeded(animeToUpdate)

        if (skippedUpdates.isNotEmpty()) {
            // TODO: surface skipped reasons to user?
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    /**
     * Method that updates anime in [animeToUpdate]. It's called in a background thread, so it's safe
     * to do heavy operations or network calls here.
     * For each anime it calls [updateAnime] and updates the notification showing the current
     * progress.
     *
     * @return an observable delivering the progress of each update.
     */
    private suspend fun updateEpisodeList(isManualRun: Boolean) {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdatingAnime = CopyOnWriteArrayList<Anime>()
        val newUpdates = CopyOnWriteArrayList<Pair<Anime, Array<Episode>>>()
        val failedUpdates = CopyOnWriteArrayList<LibraryUpdateFailure>()
        val hasDownloads = AtomicBoolean(false)
        val fetchWindow = animeFetchInterval.getWindow(ZonedDateTime.now())

        coroutineScope {
            animeToUpdate.groupBy { it.anime.source }.values
                .map { animeInSource ->
                    async {
                        semaphore.withPermit {
                            animeInSource.forEachIndexed { index, libraryAnime ->
                                val anime = libraryAnime.anime
                                ensureActive()

                                // Don't continue to update if anime is not in library
                                if (anime.parentId == null && getAnime.await(anime.id)?.favorite != true) {
                                    return@forEachIndexed
                                }

                                withUpdateNotification(
                                    currentlyUpdatingAnime,
                                    progressCount,
                                    anime,
                                ) {
                                    try {
                                        val newEpisodes = updateAnime(anime, fetchWindow)
                                            .sortedByDescending { it.sourceOrder }

                                        LibraryUpdateErrorStore.markResolved(
                                            media = LibraryUpdateErrorMedia.Anime,
                                            entryId = anime.id,
                                        )

                                        if (newEpisodes.isNotEmpty()) {
                                            val episodesToDownload = filterEpisodesForDownload.await(anime, newEpisodes)

                                            if (episodesToDownload.isNotEmpty()) {
                                                downloadEpisodes(anime, episodesToDownload)
                                                hasDownloads.set(true)
                                            }

                                            libraryPreferences.newAnimeUpdatesCount()
                                                .getAndSet { it + newEpisodes.size }

                                            // Convert to the anime that contains new episodes
                                            newUpdates.add(anime to newEpisodes.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                        if (e is CancellationException) throw e
                                        val errorMessage = when (e) {
                                            is NoEpisodesException -> context.stringResource(
                                                AYMR.strings.no_episodes_error,
                                            )
                                            // failedUpdates will already have the source, don't need to copy it into the message
                                            is AnimeSourceNotInstalledException -> context.stringResource(
                                                MR.strings.loader_not_implemented_error,
                                            )
                                            else -> e.message
                                        }
                                        val sourceName = sourceManager.getOrStub(anime.source).toString()
                                        failedUpdates.add(
                                            LibraryUpdateFailure(
                                                title = anime.title,
                                                sourceName = sourceName,
                                                reason = errorMessage,
                                            ),
                                        )
                                        LibraryUpdateErrorStore.upsert(
                                            media = LibraryUpdateErrorMedia.Anime,
                                            entryId = anime.id,
                                            title = anime.title,
                                            sourceId = anime.source,
                                            sourceName = sourceName,
                                            thumbnailUrl = anime.thumbnailUrl,
                                            message = errorMessage ?: context.stringResource(MR.strings.unknown_error),
                                            runType = if (isManualRun) {
                                                LibraryUpdateErrorRunType.Manual
                                            } else {
                                                LibraryUpdateErrorRunType.Automatic
                                            },
                                        )
                                    }
                                }

                                pacingPolicy.delayAfterUpdate(
                                    mediaTag = LibraryUpdatePacingPolicy.MEDIA_ANIME,
                                    sourceId = anime.source,
                                    shouldDelay = index != animeInSource.lastIndex,
                                )
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateNotifications(newUpdates)
            if (hasDownloads.get()) {
                downloadManager.startDownloads()
            }
        }

        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(
                failedUpdates,
                errorFile.getUriCompat(context),
            )
        }
        if (isManualRun && newUpdates.isEmpty() && failedUpdates.isEmpty()) {
            notifier.showNoUpdatesNotification(checked = animeToUpdate.size)
        }
    }

    private fun downloadEpisodes(anime: Anime, episodes: List<Episode>) {
        // We don't want to start downloading while the library is updating, because websites
        // may don't like it and they could ban the user.
        downloadManager.downloadEpisodes(anime, episodes, false)
    }

    /**
     * Updates the episodes for the given anime and adds them to the database.
     *
     * @param anime the anime to update.
     * @return a pair of the inserted and removed episodes.
     */
    private suspend fun updateAnime(anime: Anime, fetchWindow: Pair<Long, Long>): List<Episode> {
        val source = sourceManager.getOrStub(anime.source)

        // Update anime metadata if needed
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkAnime = source.getAnimeDetails(anime.toSAnime())
            animeRatingFetcher.await(source, anime, forceRefresh = true)
            updateAnime.awaitUpdateFromSource(anime, networkAnime, manualFetch = false, coverCache, backgroundCache)
        }

        val episodes = source.getEpisodeList(anime.toSAnime())

        // Get anime from database to account for if it was removed during the update and
        // to get latest data so it doesn't get overwritten later on
        val dbAnime = getAnime.await(anime.id)?.takeIf { it.parentId != null || it.favorite } ?: return emptyList()

        return syncEpisodesWithSource.await(episodes, dbAnime, source, false, fetchWindow)
    }

    private suspend fun withUpdateNotification(
        updatingAnime: CopyOnWriteArrayList<Anime>,
        completed: AtomicInteger,
        anime: Anime,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingAnime.add(anime)
        notifier.showProgressNotification(
            updatingAnime,
            completed.get(),
            animeToUpdate.size,
        )

        try {
            block()
            ensureActive()
        } finally {
            updatingAnime.remove(anime)
            completed.getAndIncrement()
            notifier.showProgressNotification(
                updatingAnime,
                completed.get(),
                animeToUpdate.size,
            )
        }
    }

    /**
     * Writes basic file of update errors to cache dir.
     */
    private fun writeErrorFile(errors: List<LibraryUpdateFailure>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tadami_anime_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
                    // Error file format:
                    // ! Error
                    //   # Source
                    //     - Anime
                    errors.groupBy { it.reason }.forEach { (error, failures) ->
                        out.write(
                            "\n! ${error.orEmpty().ifBlank {
                                context.stringResource(MR.strings.unknown_error)
                            }}\n",
                        )
                        failures.groupBy { it.sourceName }.forEach { (sourceName, failuresForSource) ->
                            out.write("  # $sourceName\n")
                            failuresForSource.forEach {
                                out.write("    - ${it.title}\n")
                            }
                        }
                    }
                }
                return file
            }
        } catch (_: Exception) {}
        return File("")
    }

    companion object {
        private const val TAG = "AnimeLibraryUpdate"
        private const val WORK_NAME_AUTO = "AnimeLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "AnimeLibraryUpdate-manual"

        private const val ERROR_LOG_HELP_URL = "https://t.me/TadamiSupport"

        /**
         * Key for category to update.
         */
        private const val KEY_CATEGORY = "animeCategory"
        private const val KEY_ENTRY_IDS = "entryIds"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            eu.kanade.tachiyomi.data.library.LibraryAutoUpdateSchedulerJob.setupTask(context, prefInterval)
        }
        fun startNow(
            context: Context,
            category: Category? = null,
        ): Boolean {
            val inputData = category
                ?.let { workDataOf(KEY_CATEGORY to it.id) }
                ?: workDataOf()
            return enqueueManualUpdate(context, inputData)
        }

        fun startNow(
            context: Context,
            entryIds: LongArray,
        ): Boolean {
            if (entryIds.isEmpty()) return false
            return enqueueManualUpdate(context, workDataOf(KEY_ENTRY_IDS to entryIds))
        }

        private fun enqueueManualUpdate(
            context: Context,
            inputData: Data,
        ): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG) || wm.isRunningOrEnqueued(WORK_NAME_MANUAL)) {
                // Already running either as a scheduled or manual job
                return false
            }

            val request = OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(TAG)
                .addTag(WORK_NAME_MANUAL)
                .setInputData(inputData)
                .build()
            wm.enqueueUniqueWork(WORK_NAME_MANUAL, ExistingWorkPolicy.KEEP, request)

            return true
        }

        fun stop(context: Context) {
            val wm = context.workManager
            val workQuery = WorkQuery.Builder.fromTags(listOf(TAG))
                .addStates(listOf(WorkInfo.State.RUNNING))
                .build()
            val future = wm.getWorkInfos(workQuery)
            future.addListener(
                {
                    runCatching { future.get() }
                        .getOrDefault(emptyList())
                        .forEach {
                            wm.cancelWorkById(it.id)
                            if (it.tags.contains(WORK_NAME_AUTO)) {
                                setupTask(context)
                            }
                        }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }
}

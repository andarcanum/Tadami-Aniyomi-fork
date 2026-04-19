package eu.kanade.tachiyomi.data.library.novel

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.library.LibraryUpdateFailure
import eu.kanade.tachiyomi.data.library.LibraryUpdatePacingPolicy
import eu.kanade.tachiyomi.data.library.shouldRetryLegacyAutoUpdateRun
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.isCharging
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.novel.model.SourceNotInstalledException
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class NovelLibraryUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val sourceManager: NovelSourceManager = Injekt.get()
    private val libraryPreferences: LibraryPreferences = Injekt.get()
    private val downloadPreferences: DownloadPreferences = Injekt.get()
    private val getLibraryNovel: GetLibraryNovel = Injekt.get()
    private val getNovel: GetNovel = Injekt.get()
    private val updateNovel: UpdateNovel = Injekt.get()
    private val syncNovelChaptersWithSource: SyncNovelChaptersWithSource = Injekt.get()
    private val novelDownloadManager: NovelDownloadManager = NovelDownloadManager()
    private val pacingPolicy = LibraryUpdatePacingPolicy(Injekt.get())

    private val notifier = NovelLibraryUpdateNotifier(context)

    private var novelToUpdate: List<LibraryNovel> = emptyList()
    private var novelCategoryIdsByNovelId: Map<Long, Set<Long>> = emptyMap()

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Not allowed to set foreground novel update job" }
        }

        if (tags.contains(WORK_NAME_AUTO)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val restrictions = libraryPreferences.autoUpdateDeviceRestrictions().get()
                if (
                    shouldRetryLegacyAutoUpdateRun(
                        restrictions = restrictions,
                        isConnectedToWifi = context.isConnectedToWifi(),
                        isCharging = context.isCharging(),
                    )
                ) {
                    return Result.retry()
                }
            }

            if (context.workManager.isRunning(WORK_NAME_MANUAL)) {
                return Result.retry()
            }
        }

        libraryPreferences.lastUpdatedTimestamp().set(System.currentTimeMillis())

        val categoryId = inputData.getLong(KEY_CATEGORY, -1L)
        addNovelToQueue(categoryId)

        return withIOContext {
            try {
                updateChapterList(isManualRun = tags.contains(WORK_NAME_MANUAL))
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
        return ForegroundInfo(
            Notifications.ID_NOVEL_LIBRARY_PROGRESS,
            notifier.progressNotificationBuilder.build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private suspend fun addNovelToQueue(categoryId: Long) {
        val libraryNovels = getLibraryNovel.await()
        val listToUpdate = if (categoryId != -1L) {
            libraryNovels.filter { it.category == categoryId }
        } else {
            val categoriesToUpdate = libraryPreferences.novelUpdateCategories().get().map { it.toLong() }
            val includedNovels = if (categoriesToUpdate.isNotEmpty()) {
                libraryNovels.filter { it.category in categoriesToUpdate }
            } else {
                libraryNovels
            }

            val categoriesToExclude = libraryPreferences.novelUpdateCategoriesExclude().get().map { it.toLong() }
            val excludedNovelIds = if (categoriesToExclude.isNotEmpty()) {
                libraryNovels.filter { it.category in categoriesToExclude }.map { it.novel.id }
            } else {
                emptyList()
            }

            includedNovels
                .filterNot { it.novel.id in excludedNovelIds }
        }

        novelCategoryIdsByNovelId = listToUpdate
            .groupBy { it.novel.id }
            .mapValues { (_, entries) -> entries.map { it.category }.toSet() }

        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val (_, fetchWindowUpperBound) = getNovelFetchWindow(ZonedDateTime.now())
        val skippedUpdates = mutableListOf<Pair<Novel, String?>>()

        novelToUpdate = listToUpdate
            .distinctBy { it.novel.id }
            .filter { libraryNovel ->
                val isEligible = isNovelEligibleForAutoUpdate(
                    item = libraryNovel,
                    restrictions = restrictions,
                    fetchWindowUpperBound = fetchWindowUpperBound,
                )
                if (!isEligible) {
                    val reason = getNovelAutoUpdateSkipReason(
                        item = libraryNovel,
                        restrictions = restrictions,
                        fetchWindowUpperBound = fetchWindowUpperBound,
                    )
                    skippedUpdates.add(
                        libraryNovel.novel to when (reason) {
                            NovelAutoUpdateSkipReason.NOT_ALWAYS_UPDATE ->
                                context.stringResource(MR.strings.skipped_reason_not_always_update)
                            NovelAutoUpdateSkipReason.COMPLETED ->
                                context.stringResource(MR.strings.skipped_reason_completed)
                            NovelAutoUpdateSkipReason.HAS_UNREAD ->
                                context.stringResource(MR.strings.skipped_reason_not_caught_up)
                            NovelAutoUpdateSkipReason.NOT_STARTED ->
                                context.stringResource(MR.strings.skipped_reason_not_started)
                            NovelAutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD ->
                                context.stringResource(MR.strings.skipped_reason_not_in_release_period)
                            null -> null
                        },
                    )
                }
                isEligible
            }
            .sortedBy { it.novel.title }

        if (skippedUpdates.isNotEmpty()) {
            logcat {
                skippedUpdates
                    .groupBy { it.second }
                    .map { (reason, entries) -> "$reason: [${entries.map { it.first.title }.sorted().joinToString()}]" }
                    .joinToString()
            }
        }
    }

    private suspend fun updateChapterList(isManualRun: Boolean) {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val updatedCount = AtomicInteger(0)
        val failedCount = AtomicInteger(0)
        val currentlyUpdating = CopyOnWriteArrayList<Novel>()
        val newUpdates = CopyOnWriteArrayList<Pair<Novel, Int>>()
        val failedUpdates = CopyOnWriteArrayList<LibraryUpdateFailure>()
        coroutineScope {
            novelToUpdate.groupBy { it.novel.source }.values
                .map { novelsInSource ->
                    async {
                        semaphore.withPermit {
                            novelsInSource.forEachIndexed { index, libraryNovel ->
                                val novel = libraryNovel.novel
                                ensureActive()

                                if (getNovel.await(novel.id)?.favorite != true) {
                                    return@forEachIndexed
                                }

                                withUpdateNotification(
                                    updatingNovel = currentlyUpdating,
                                    completed = progressCount,
                                    updated = updatedCount,
                                    failed = failedCount,
                                    novel = novel,
                                ) {
                                    try {
                                        val newChapters = updateNovel(novel)
                                        if (newChapters.isNotEmpty()) {
                                            val chaptersToDownload = filterChaptersForDownload(
                                                novel = novel,
                                                newChapters = newChapters,
                                                categoryIds = novelCategoryIdsByNovelId[novel.id].orEmpty(),
                                            )
                                            if (chaptersToDownload.isNotEmpty()) {
                                                novelDownloadManager.downloadChapters(novel, chaptersToDownload)
                                            }
                                            newUpdates.add(novel to newChapters.size)
                                            updatedCount.incrementAndGet()
                                        }
                                    } catch (e: Throwable) {
                                        if (e is CancellationException) throw e
                                        val errorMessage = when (e) {
                                            is NoChaptersException -> context.stringResource(
                                                MR.strings.no_chapters_error,
                                            )
                                            is SourceNotInstalledException ->
                                                context.stringResource(MR.strings.loader_not_implemented_error)
                                            else -> e.message
                                        }
                                        failedUpdates.add(
                                            LibraryUpdateFailure(
                                                title = novel.title,
                                                sourceName = sourceManager.getOrStub(novel.source).toString(),
                                                reason = errorMessage,
                                            ),
                                        )
                                        failedCount.incrementAndGet()
                                    }
                                }

                                pacingPolicy.delayAfterUpdate(
                                    mediaTag = LibraryUpdatePacingPolicy.MEDIA_NOVEL,
                                    sourceId = novel.source,
                                    shouldDelay = index != novelsInSource.lastIndex,
                                )
                            }
                        }
                    }
                }
                .awaitAll()
        }

        notifier.cancelProgressNotification()

        if (newUpdates.isNotEmpty()) {
            notifier.showUpdateSummaryNotification(newUpdates)
        }
        if (failedUpdates.isNotEmpty()) {
            val errorFile = writeErrorFile(failedUpdates)
            notifier.showUpdateErrorNotification(failedUpdates, errorFile.getUriCompat(context))
        }
        if (isManualRun && newUpdates.isEmpty() && failedUpdates.isEmpty()) {
            notifier.showNoUpdatesNotification(checked = novelToUpdate.size)
        }
    }

    private suspend fun updateNovel(novel: Novel): List<NovelChapter> {
        val source = sourceManager.getOrStub(novel.source)
        if (libraryPreferences.autoUpdateMetadata().get()) {
            val networkNovel = source.getNovelDetails(novel.toSNovel())
            updateNovel.awaitUpdateFromSource(
                localNovel = novel,
                remoteNovel = networkNovel,
                manualFetch = false,
            )
        }
        val sourceChapters = source.getChapterList(novel.toSNovel())
        val dbNovel = getNovel.await(novel.id)?.takeIf { it.favorite } ?: return emptyList()

        return syncNovelChaptersWithSource.await(
            rawSourceChapters = sourceChapters,
            novel = dbNovel,
            source = source,
            manualFetch = false,
            fetchWindow = Pair(0L, 0L),
        )
    }

    private fun filterChaptersForDownload(
        novel: Novel,
        newChapters: List<NovelChapter>,
        categoryIds: Set<Long>,
    ): List<NovelChapter> {
        if (!downloadPreferences.downloadNewNovelChapters().get()) return emptyList()

        val included = downloadPreferences.downloadNewNovelChapterCategories().get().map { it.toLong() }.toSet()
        if (included.isNotEmpty() && categoryIds.intersect(included).isEmpty()) return emptyList()

        val excluded = downloadPreferences.downloadNewNovelChapterCategoriesExclude().get().map { it.toLong() }.toSet()
        if (categoryIds.any { it in excluded }) return emptyList()

        val unreadOnly = downloadPreferences.downloadNewUnreadNovelChaptersOnly().get()

        return newChapters
            .asSequence()
            .filter { !unreadOnly || !it.read }
            .filterNot { novelDownloadManager.isChapterDownloaded(novel, it.id) }
            .toList()
    }

    private fun writeErrorFile(errors: List<LibraryUpdateFailure>): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("tadami_update_errors.txt")
                file.bufferedWriter().use { out ->
                    out.write(
                        context.stringResource(MR.strings.library_errors_help, ERROR_LOG_HELP_URL) + "\n\n",
                    )
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

    private suspend fun withUpdateNotification(
        updatingNovel: CopyOnWriteArrayList<Novel>,
        completed: AtomicInteger,
        updated: AtomicInteger,
        failed: AtomicInteger,
        novel: Novel,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingNovel.add(novel)
        notifier.showProgressNotification(
            novels = updatingNovel,
            current = completed.get(),
            total = novelToUpdate.size,
            updated = updated.get(),
            failed = failed.get(),
        )

        block()

        ensureActive()

        updatingNovel.remove(novel)
        completed.getAndIncrement()
        notifier.showProgressNotification(
            novels = updatingNovel,
            current = completed.get(),
            total = novelToUpdate.size,
            updated = updated.get(),
            failed = failed.get(),
        )
    }

    companion object {
        private const val TAG = "NovelLibraryUpdate"
        private const val WORK_NAME_AUTO = "NovelLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "NovelLibraryUpdate-manual"
        private const val KEY_CATEGORY = "category"
        private const val GRACE_PERIOD_DAYS = 1L
        private const val ERROR_LOG_HELP_URL = "https://t.me/TadamiSupport"

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            eu.kanade.tachiyomi.data.library.LibraryAutoUpdateSchedulerJob.setupTask(context, prefInterval)
        }

        fun startNow(context: Context, categoryId: Long? = null): Boolean {
            val wm = context.workManager
            if (wm.isRunning(TAG)) {
                return false
            }

            val inputData = workDataOf(KEY_CATEGORY to categoryId)
            val request = OneTimeWorkRequestBuilder<NovelLibraryUpdateJob>()
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

    private fun getNovelFetchWindow(dateTime: ZonedDateTime): Pair<Long, Long> {
        val today = dateTime.toLocalDate().atStartOfDay(dateTime.zone)
        val lowerBound = today.minusDays(GRACE_PERIOD_DAYS)
        val upperBound = today.plusDays(GRACE_PERIOD_DAYS)
        return Pair(lowerBound.toEpochSecond() * 1000, upperBound.toEpochSecond() * 1000 - 1)
    }
}

internal enum class NovelAutoUpdateSkipReason {
    NOT_ALWAYS_UPDATE,
    COMPLETED,
    HAS_UNREAD,
    NOT_STARTED,
    OUTSIDE_RELEASE_PERIOD,
}

internal fun getNovelAutoUpdateSkipReason(
    item: LibraryNovel,
    restrictions: Set<String>,
    fetchWindowUpperBound: Long,
): NovelAutoUpdateSkipReason? {
    return when {
        item.novel.updateStrategy != eu.kanade.tachiyomi.source.model.UpdateStrategy.ALWAYS_UPDATE ->
            NovelAutoUpdateSkipReason.NOT_ALWAYS_UPDATE
        ENTRY_NON_COMPLETED in restrictions && item.novel.status.toInt() == SManga.COMPLETED ->
            NovelAutoUpdateSkipReason.COMPLETED
        ENTRY_HAS_UNVIEWED in restrictions && item.unreadCount != 0L ->
            NovelAutoUpdateSkipReason.HAS_UNREAD
        ENTRY_NON_VIEWED in restrictions && item.totalChapters > 0L && !item.hasStarted ->
            NovelAutoUpdateSkipReason.NOT_STARTED
        ENTRY_OUTSIDE_RELEASE_PERIOD in restrictions && item.novel.nextUpdate > fetchWindowUpperBound ->
            NovelAutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD
        else -> null
    }
}

internal fun isNovelEligibleForAutoUpdate(
    item: LibraryNovel,
    restrictions: Set<String>,
    fetchWindowUpperBound: Long,
): Boolean {
    return getNovelAutoUpdateSkipReason(
        item = item,
        restrictions = restrictions,
        fetchWindowUpperBound = fetchWindowUpperBound,
    ) == null
}

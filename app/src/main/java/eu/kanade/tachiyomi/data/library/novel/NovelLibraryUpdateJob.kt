package eu.kanade.tachiyomi.data.library.novel

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.domain.items.novelchapter.interactor.SyncNovelChaptersWithSource
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.source.model.SManga
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
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NoChaptersException
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.source.novel.model.SourceNotInstalledException
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
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
                if ((DEVICE_ONLY_ON_WIFI in restrictions) && !context.isConnectedToWifi()) {
                    return Result.retry()
                }
                if ((DEVICE_NETWORK_NOT_METERED in restrictions) && context.isConnectedToWifi()) {
                    return Result.retry()
                }
                if ((DEVICE_CHARGING in restrictions) && !context.isCharging()) {
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
                updateChapterList()
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

    private suspend fun updateChapterList() {
        val semaphore = Semaphore(5)
        val progressCount = AtomicInteger(0)
        val currentlyUpdating = CopyOnWriteArrayList<Novel>()
        val newUpdates = CopyOnWriteArrayList<Pair<Novel, Int>>()
        val failedUpdates = CopyOnWriteArrayList<Pair<Novel, String?>>()
        coroutineScope {
            novelToUpdate.groupBy { it.novel.source }.values
                .map { novelsInSource ->
                    async {
                        semaphore.withPermit {
                            novelsInSource.forEach { libraryNovel ->
                                val novel = libraryNovel.novel
                                ensureActive()

                                if (getNovel.await(novel.id)?.favorite != true) {
                                    return@forEach
                                }

                                withUpdateNotification(currentlyUpdating, progressCount, novel) {
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
                                        failedUpdates.add(novel to errorMessage)
                                    }
                                }
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
            notifier.showUpdateErrorNotification(failedUpdates.size)
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

    private suspend fun withUpdateNotification(
        updatingNovel: CopyOnWriteArrayList<Novel>,
        completed: AtomicInteger,
        novel: Novel,
        block: suspend () -> Unit,
    ) = coroutineScope {
        ensureActive()

        updatingNovel.add(novel)
        notifier.showProgressNotification(updatingNovel, completed.get(), novelToUpdate.size)

        block()

        ensureActive()

        updatingNovel.remove(novel)
        completed.getAndIncrement()
        notifier.showProgressNotification(updatingNovel, completed.get(), novelToUpdate.size)
    }

    companion object {
        private const val TAG = "NovelLibraryUpdate"
        private const val WORK_NAME_AUTO = "NovelLibraryUpdate-auto"
        private const val WORK_NAME_MANUAL = "NovelLibraryUpdate-manual"
        private const val KEY_CATEGORY = "category"
        private const val GRACE_PERIOD_DAYS = 1L

        fun cancelAllWorks(context: Context) {
            context.workManager.cancelAllWorkByTag(TAG)
        }

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()
            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val networkType = if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    NetworkType.UNMETERED
                } else {
                    NetworkType.CONNECTED
                }
                val networkRequestBuilder = NetworkRequest.Builder()
                if (DEVICE_ONLY_ON_WIFI in restrictions) {
                    networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                }
                if (DEVICE_NETWORK_NOT_METERED in restrictions) {
                    networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
                val constraints = Constraints.Builder()
                    .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                    .setRequiresCharging(DEVICE_CHARGING in restrictions)
                    .setRequiresBatteryNotLow(true)
                    .build()

                val request = PeriodicWorkRequestBuilder<NovelLibraryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG)
                    .addTag(WORK_NAME_AUTO)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(
                    WORK_NAME_AUTO,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
            } else {
                context.workManager.cancelUniqueWork(WORK_NAME_AUTO)
            }
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

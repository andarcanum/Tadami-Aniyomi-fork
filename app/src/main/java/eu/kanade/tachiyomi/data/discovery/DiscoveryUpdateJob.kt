package eu.kanade.tachiyomi.data.discovery

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID
import java.util.concurrent.TimeUnit

internal enum class DiscoveryProviderSetting {
    EXTERNAL,
    SHIKIMORI,
    MANGA_UPDATES,
    NOVEL_UPDATES,
}

internal fun resolveWorkResult(
    isManual: Boolean,
    runAttemptCount: Int,
    isSuccess: Boolean,
    maxAutoAttempts: Int = 2,
): ListenableWorker.Result = when {
    isSuccess -> ListenableWorker.Result.success()
    isManual -> ListenableWorker.Result.failure()
    runAttemptCount > maxAutoAttempts -> ListenableWorker.Result.failure()
    else -> ListenableWorker.Result.retry()
}

internal fun resolveWorkResult(
    isManual: Boolean,
    runAttemptCount: Int,
    outcomes: Collection<DiscoveryRunOutcome>,
    maxAutoAttempts: Int = 2,
): ListenableWorker.Result {
    val allSuccessfulOrSkipped = outcomes.isEmpty() || outcomes.all { it.isSuccess || it.isSkipped }
    return resolveWorkResult(
        isManual = isManual,
        runAttemptCount = runAttemptCount,
        isSuccess = allSuccessfulOrSkipped,
        maxAutoAttempts = maxAutoAttempts,
    )
}

internal fun finishManualRefresh(
    mediaType: DiscoveryMediaType,
    workId: UUID,
    preferences: DiscoveryPreferences,
    notifier: DiscoveryNotifier,
) {
    if (preferences.manualRefreshWorkId(mediaType).get() == workId.toString()) {
        notifier.cancel(mediaType)
    }
}

/**
 * Фоновое обновление ленты «Для тебя» (паттерн SyncJob):
 * периодика по настройке, отложенный one-shot после обновления библиотеки,
 * ручной рефреш с полного экрана.
 */
class DiscoveryUpdateJob(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val targetKey = inputData.getString(KEY_TARGET_MEDIA_TYPE)
        val mediaType = targetKey?.let { DiscoveryMediaType.fromKey(it) } ?: DiscoveryMediaType.ANIME
        return DiscoveryNotifier(applicationContext).getForegroundInfo(mediaType)
    }

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<DiscoveryPreferences>()
        if (!preferences.discoveryEnabled().get()) return Result.success()
        val isManual = tags.contains(TAG_MANUAL)
        if (isManual) {
            setForegroundSafely()
        }
        val targetKey = inputData.getString(KEY_TARGET_MEDIA_TYPE)
        val mediaTypes = if (targetKey != null) {
            listOfNotNull(DiscoveryMediaType.fromKey(targetKey))
        } else {
            DiscoveryMediaType.entries
        }
        val notifier = DiscoveryNotifier(applicationContext)
        return try {
            coroutineScope {
                val progressJob = if (isManual) {
                    launch {
                        mediaTypes.forEach { mediaType ->
                            launch {
                                DiscoveryProgressTracker.progress(mediaType).collect { progress ->
                                    if (progress.isRunning) {
                                        notifier.showProgress(
                                            mediaType = mediaType,
                                            progressText = progress.stageText,
                                            current = progress.current,
                                            total = progress.total,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    null
                }
                try {
                    val completed = withTimeoutOrNull(WORKER_TIMEOUT_MS) {
                        Injekt.get<DiscoveryRunner>().run(mediaTypes, isManualRefresh = isManual)
                    }
                    if (completed != null) {
                        resolveWorkResult(isManual, runAttemptCount, outcomes = completed.values)
                    } else {
                        logcat { "[DiscoveryUpdateJob] TIMEOUT after ${WORKER_TIMEOUT_MS}ms" }
                        resolveWorkResult(isManual, runAttemptCount, isSuccess = false)
                    }
                } finally {
                    progressJob?.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryUpdateJob] FAILED: ${e.message}" }
            resolveWorkResult(isManual, runAttemptCount, isSuccess = false)
        } finally {
            if (isManual) {
                mediaTypes.forEach { mediaType ->
                    finishManualRefresh(mediaType, id, preferences, notifier)
                }
            }
        }
    }

    companion object {
        private const val TAG_AUTO = "DiscoveryUpdate"
        private const val TAG_AFTER_LIBRARY = "DiscoveryUpdateAfterLibrary"
        const val TAG_MANUAL = "DiscoveryUpdateManual"
        const val WORKER_TIMEOUT_MS = 120_000L

        private fun networkType(preferences: DiscoveryPreferences): NetworkType =
            if (preferences.refreshWifiOnly().get()) NetworkType.UNMETERED else NetworkType.CONNECTED

        fun stop(context: Context, mediaType: DiscoveryMediaType? = null) {
            val wm = context.workManager
            if (mediaType == null) {
                cancelAllManualRefreshes(context)
            } else {
                wm.cancelUniqueWork(manualWorkName(mediaType))
                Injekt.get<DiscoveryPreferences>().clearManualRefreshAt(mediaType)
                DiscoveryNotifier(context).cancel(mediaType)
            }
        }

        fun cancelAllManualRefreshes(context: Context) {
            val wm = context.workManager
            wm.cancelAllWorkByTag(TAG_MANUAL)
            DiscoveryMediaType.entries.forEach { wm.cancelAllWorkByTag(manualWorkTag(it)) }
            DiscoveryNotifier(context).cancel()
        }

        @Deprecated("Use cancelAllManualRefreshes", ReplaceWith("cancelAllManualRefreshes(context)"))
        fun cleanupStaleManualWork(context: Context) {
            cancelAllManualRefreshes(context)
        }

        fun setupTask(context: Context) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            val interval = preferences.refreshIntervalHours().get()
            if (interval > 0 && preferences.discoveryEnabled().get()) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(networkType(preferences))
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<DiscoveryUpdateJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    10,
                    TimeUnit.MINUTES,
                )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                    .addTag(TAG_AUTO)
                    .setConstraints(constraints)
                    .build()
                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        /** One-shot через 30 мин после старта обновления библиотеки; REPLACE = debounce. */
        fun scheduleAfterLibraryUpdate(context: Context) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get() || !preferences.refreshAfterLibrary().get()) return
            val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                .setInitialDelay(30, TimeUnit.MINUTES)
                .addTag(TAG_AFTER_LIBRARY)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType(preferences))
                        .build(),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG_AFTER_LIBRARY, ExistingWorkPolicy.REPLACE, request)
        }

        const val KEY_TARGET_MEDIA_TYPE = "target_media_type"

        internal fun refreshAfterProviderChange(context: Context, setting: DiscoveryProviderSetting) {
            val mediaTypes = when (setting) {
                DiscoveryProviderSetting.EXTERNAL, DiscoveryProviderSetting.SHIKIMORI -> DiscoveryMediaType.entries
                DiscoveryProviderSetting.MANGA_UPDATES -> listOf(DiscoveryMediaType.MANGA, DiscoveryMediaType.NOVEL)
                DiscoveryProviderSetting.NOVEL_UPDATES -> listOf(DiscoveryMediaType.NOVEL)
            }
            mediaTypes.forEach { refreshConfiguration(context, it) }
        }

        /** Provider settings regenerate each affected feed without manual rotation or click accounting. */
        internal fun refreshConfiguration(context: Context, mediaType: DiscoveryMediaType) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get()) return
            val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                .setInputData(Data.Builder().putString(KEY_TARGET_MEDIA_TYPE, mediaType.key).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType(preferences))
                        .build(),
                )
                .build()
            context.workManager.enqueueUniqueWork(
                "DiscoveryUpdateConfiguration:${mediaType.key}",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        /**
         * Unique-имя ручного обновления — своё на медиатип: REPLACE не должен отменять
         * bootstrap/рефреш соседней вкладки при быстром переключении.
         */
        fun manualWorkName(mediaType: DiscoveryMediaType?): String =
            if (mediaType == null) TAG_MANUAL else "$TAG_MANUAL:${mediaType.key}"

        fun manualWorkTag(mediaType: DiscoveryMediaType): String = manualWorkName(mediaType)

        fun refreshNow(context: Context, mediaType: DiscoveryMediaType? = null): Map<DiscoveryMediaType, UUID> {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get()) return emptyMap()
            val mediaTypes = mediaType?.let { listOf(it) } ?: DiscoveryMediaType.entries
            return mediaTypes.associateWith { target ->
                val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                    .addTag(TAG_MANUAL)
                    .addTag(manualWorkTag(target))
                    .setInputData(Data.Builder().putString(KEY_TARGET_MEDIA_TYPE, target.key).build())
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(networkType(preferences))
                            .build(),
                    )
                    .build()
                context.workManager.enqueueUniqueWork(
                    manualWorkName(target),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
                preferences.manualRefreshWorkId(target).set(request.id.toString())
                request.id
            }
        }

        private const val TAG_BACKFILL = "DiscoveryUpdateBackfill"

        /**
         * Разовая тихая перегенерация после апгрейда: SOURCE-строки кэша до миграции 58
         * не имеют plugin-привязки (source_id NULL), и на них direct open деградирует
         * в шторку. Без TAG_MANUAL — обычная генерация (без ротации сидов) по всем медиатипам.
         */
        fun backfillRefresh(context: Context) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get()) return
            val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                .addTag(TAG_BACKFILL)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType(preferences))
                        .build(),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG_BACKFILL, ExistingWorkPolicy.REPLACE, request)
        }

        private const val TAG_AFTER_CONSUMED = "DiscoveryRefillAfterConsumed"

        fun refillWorkName(mediaType: DiscoveryMediaType): String = "$TAG_AFTER_CONSUMED:${mediaType.key}"

        /**
         * Дебаунс-дозаполнение ленты после явного сигнала («просмотрено», LIKE): 10 минут
         * REPLACE-очередью — каждое новое действие сдвигает окно, шторма обновлений нет
         * (не более одного прогона на очередь). Прогон фоновый: consumed-тайтл исключён,
         * liked-тайтл входит в сиды похожих рекомендаций, ряды добираются свежим.
         */
        fun scheduleRefill(context: Context, mediaType: DiscoveryMediaType) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get()) return
            val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                .setInitialDelay(10, TimeUnit.MINUTES)
                .addTag(TAG_AFTER_CONSUMED)
                .setInputData(
                    Data.Builder().putString(KEY_TARGET_MEDIA_TYPE, mediaType.key).build(),
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType(preferences))
                        .build(),
                )
                .build()
            context.workManager.enqueueUniqueWork(
                refillWorkName(mediaType),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun scheduleConsumedRefill(context: Context, mediaType: DiscoveryMediaType) =
            scheduleRefill(context, mediaType)
    }
}

/** Минимальный интервал между bootstrap-попытками одного медиатипа (анти-шторм). */
internal const val DISCOVERY_BOOTSTRAP_RETRY_MS = 6L * 3600_000L

/**
 * Нужна ли bootstrap-генерация ленты: кэша нет вовсе (lastUpdatedAt == null), а
 * предыдущая попытка была достаточно давно (или её не было). Гасит шторм
 * «каждый вход на вкладку = перезапуск генерации», когда генерация стабильно
 * падает (оффлайн, все ряды failed) и [tachiyomi.domain.discovery.repository.DiscoveryRepository.lastUpdatedAt]
 * так и не появляется.
 */
internal fun shouldBootstrapDiscoveryFeed(
    lastUpdatedAt: Long?,
    lastBootstrapAttemptAt: Long,
    now: Long = System.currentTimeMillis(),
    retryMs: Long = DISCOVERY_BOOTSTRAP_RETRY_MS,
): Boolean {
    if (lastUpdatedAt != null) return false
    val sinceAttempt = now - lastBootstrapAttemptAt
    return sinceAttempt < 0L || sinceAttempt >= retryMs
}

package eu.kanade.tachiyomi.data.discovery

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Фоновое обновление ленты «Для тебя» (паттерн SyncJob):
 * периодика по настройке, отложенный one-shot после обновления библиотеки,
 * ручной рефреш с полного экрана.
 */
class DiscoveryUpdateJob(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val preferences = Injekt.get<DiscoveryPreferences>()
        if (!preferences.discoveryEnabled().get()) return Result.success()
        return try {
            val targetKey = inputData.getString(KEY_TARGET_MEDIA_TYPE)
            val mediaTypes = if (targetKey != null) {
                listOfNotNull(DiscoveryMediaType.fromKey(targetKey))
            } else {
                DiscoveryMediaType.entries
            }
            val isManual = tags.contains(TAG_MANUAL)
            Injekt.get<DiscoveryRunner>().run(mediaTypes, isManualRefresh = isManual)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat { "[DiscoveryUpdateJob] FAILED: ${e.message}" }
            Result.retry()
        }
    }

    companion object {
        private const val TAG_AUTO = "DiscoveryUpdate"
        private const val TAG_AFTER_LIBRARY = "DiscoveryUpdateAfterLibrary"
        const val TAG_MANUAL = "DiscoveryUpdateManual"

        private fun networkType(preferences: DiscoveryPreferences): NetworkType =
            if (preferences.refreshWifiOnly().get()) NetworkType.UNMETERED else NetworkType.CONNECTED

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

        /**
         * Unique-имя ручного обновления — своё на медиатип: REPLACE не должен отменять
         * bootstrap/рефреш соседней вкладки при быстром переключении (тот же TAG_MANUAL
         * сохраняет работу isRunningFlow-индикаторов).
         */
        fun manualWorkName(mediaType: DiscoveryMediaType?): String =
            if (mediaType == null) TAG_MANUAL else "$TAG_MANUAL:${mediaType.key}"

        fun refreshNow(context: Context, mediaType: DiscoveryMediaType? = null) {
            val preferences = Injekt.get<DiscoveryPreferences>()
            if (!preferences.discoveryEnabled().get()) return
            val inputData = Data.Builder().apply {
                if (mediaType != null) {
                    putString(KEY_TARGET_MEDIA_TYPE, mediaType.key)
                }
            }.build()
            val request = OneTimeWorkRequestBuilder<DiscoveryUpdateJob>()
                .addTag(TAG_MANUAL)
                .setInputData(inputData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(networkType(preferences))
                        .build(),
                )
                .build()
            context.workManager.enqueueUniqueWork(
                manualWorkName(mediaType),
                ExistingWorkPolicy.REPLACE,
                request,
            )
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

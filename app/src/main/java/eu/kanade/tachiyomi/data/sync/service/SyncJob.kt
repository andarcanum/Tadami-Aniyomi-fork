package eu.kanade.tachiyomi.data.sync.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class SyncJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val syncPreferences = Injekt.get<SyncPreferences>()
        if (!syncPreferences.isSyncEnabled()) {
            return Result.success()
        }

        return try {
            val syncManager = SyncManager(context)
            syncManager.syncData(showUserNotification = false, rethrowErrors = true)
            Result.success()
        } catch (e: CancellationException) {
            logcat { "Background sync job was cancelled" }
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Background sync job failed" }
            Result.retry()
        }
    }

    companion object {
        private const val TAG_AUTO = "SyncJob"

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = Injekt.get<SyncPreferences>()
            val interval = prefInterval ?: syncPreferences.syncInterval().get()
            if (interval > 0 && syncPreferences.isSyncEnabled()) {
                val constraints = Constraints(
                    requiredNetworkType = NetworkType.CONNECTED,
                    requiresBatteryNotLow = true,
                )

                val request = PeriodicWorkRequestBuilder<SyncJob>(
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
                logcat { "Background sync enqueued every $interval hours" }
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
                logcat { "Background sync cancelled" }
            }
        }
    }
}

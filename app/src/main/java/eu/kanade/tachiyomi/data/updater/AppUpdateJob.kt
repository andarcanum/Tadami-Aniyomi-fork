package eu.kanade.tachiyomi.data.updater

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * WorkManager job for periodic app update checks.
 */
class AppUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withIOContext {
            try {
                logcat(LogPriority.INFO) { "Checking for app updates..." }

                val result = AppUpdateChecker().checkForUpdate(context, forceCheck = true)

                when (result) {
                    is GetApplicationRelease.Result.NewUpdate -> {
                        logcat(LogPriority.INFO) { "App update available: ${result.release.version}" }
                    }
                    is GetApplicationRelease.Result.NoNewUpdate -> {
                        logcat(LogPriority.INFO) { "No app updates available" }
                    }
                    else -> {}
                }

                Result.success()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e.message) { "Failed to check for app updates" }
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val WORK_NAME = "AppUpdateChecker"

        /**
         * Sets up the periodic app update check task.
         *
         * @param context Application context
         * @param intervalHours Update check interval in hours.
         *                      If null, reads from preferences.
         *                      -1 = on startup only (no periodic work)
         *                      0 = disabled
         *                      >0 = interval in hours
         */
        fun setupTask(context: Context, intervalHours: Int? = null) {
            val preferences = Injekt.get<AppUpdatePreferences>()
            val interval = intervalHours ?: preferences.appUpdateInterval().get()

            val workManager = context.workManager

            // -1 = at startup only (handled in MainActivity), 0 = never
            if (interval <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                logcat(LogPriority.INFO) { "App update periodic check disabled (interval: $interval)" }
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<AppUpdateJob>(
                interval.toLong(),
                TimeUnit.HOURS,
                15,
                TimeUnit.MINUTES,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.HOURS)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )

            logcat(LogPriority.INFO) { "App update check scheduled every $interval hours" }
        }

        /**
         * Cancels the periodic app update check task.
         */
        fun cancelTask(context: Context) {
            context.workManager.cancelUniqueWork(WORK_NAME)
            logcat(LogPriority.INFO) { "App update periodic check cancelled" }
        }
    }
}

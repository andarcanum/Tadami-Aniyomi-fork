package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.util.system.workManager
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

internal data class LibraryAutoUpdateConstraintPolicy(
    val requireWifi: Boolean,
    val requireNotMetered: Boolean,
    val requireCharging: Boolean,
)

internal fun resolveLibraryAutoUpdateConstraintPolicy(
    restrictions: Set<String>,
    forceWifiAndCharging: Boolean,
): LibraryAutoUpdateConstraintPolicy {
    val requireWifi = forceWifiAndCharging || (DEVICE_ONLY_ON_WIFI in restrictions)
    val requireCharging = forceWifiAndCharging || (DEVICE_CHARGING in restrictions)
    val requireNotMetered = DEVICE_NETWORK_NOT_METERED in restrictions
    return LibraryAutoUpdateConstraintPolicy(
        requireWifi = requireWifi,
        requireNotMetered = requireNotMetered,
        requireCharging = requireCharging,
    )
}

internal fun shouldRetryLegacyAutoUpdateRun(
    restrictions: Set<String>,
    isConnectedToWifi: Boolean,
    isCharging: Boolean,
): Boolean {
    if ((DEVICE_ONLY_ON_WIFI in restrictions) && !isConnectedToWifi) {
        return true
    }
    if ((DEVICE_NETWORK_NOT_METERED in restrictions) && !isConnectedToWifi) {
        return true
    }
    if ((DEVICE_CHARGING in restrictions) && !isCharging) {
        return true
    }
    return false
}

class LibraryAutoUpdateSchedulerJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val wm = context.workManager
        wm.enqueueUniqueWork(
            ANIME_AUTO_TRIGGER_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AnimeLibraryUpdateJob>()
                .addTag(ANIME_TAG)
                .addTag(ANIME_AUTO_TAG)
                .build(),
        )
        wm.enqueueUniqueWork(
            MANGA_AUTO_TRIGGER_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MangaLibraryUpdateJob>()
                .addTag(MANGA_TAG)
                .addTag(MANGA_AUTO_TAG)
                .build(),
        )
        wm.enqueueUniqueWork(
            NOVEL_AUTO_TRIGGER_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NovelLibraryUpdateJob>()
                .addTag(NOVEL_TAG)
                .addTag(NOVEL_AUTO_TAG)
                .build(),
        )
        return Result.success()
    }

    companion object {
        private const val TAG = "LibraryAutoUpdateScheduler"
        private const val WORK_NAME_AUTO = "LibraryAutoUpdateScheduler-auto"

        // Legacy periodic work names to cancel after coalescing.
        private const val LEGACY_MANGA_PERIODIC_WORK_NAME = "LibraryUpdate-auto"
        private const val LEGACY_ANIME_PERIODIC_WORK_NAME = "AnimeLibraryUpdate-auto"
        private const val LEGACY_NOVEL_PERIODIC_WORK_NAME = "NovelLibraryUpdate-auto"

        // Child worker tags used by existing worker logic.
        private const val MANGA_TAG = "LibraryUpdate"
        private const val MANGA_AUTO_TAG = "LibraryUpdate-auto"
        private const val ANIME_TAG = "AnimeLibraryUpdate"
        private const val ANIME_AUTO_TAG = "AnimeLibraryUpdate-auto"
        private const val NOVEL_TAG = "NovelLibraryUpdate"
        private const val NOVEL_AUTO_TAG = "NovelLibraryUpdate-auto"

        private const val MANGA_AUTO_TRIGGER_WORK_NAME = "LibraryUpdate-auto-trigger"
        private const val ANIME_AUTO_TRIGGER_WORK_NAME = "AnimeLibraryUpdate-auto-trigger"
        private const val NOVEL_AUTO_TRIGGER_WORK_NAME = "NovelLibraryUpdate-auto-trigger"

        fun setupTask(
            context: Context,
            prefInterval: Int? = null,
        ) {
            val preferences = Injekt.get<LibraryPreferences>()
            val interval = prefInterval ?: preferences.autoUpdateInterval().get()

            cancelLegacyPeriodicWorks(context)

            if (interval > 0) {
                val restrictions = preferences.autoUpdateDeviceRestrictions().get()
                val forceWifiAndCharging = preferences.autoUpdateWifiAndChargingOnly().get()
                val constraints = buildConstraints(restrictions, forceWifiAndCharging)
                val request = PeriodicWorkRequestBuilder<LibraryAutoUpdateSchedulerJob>(
                    interval.toLong(),
                    TimeUnit.HOURS,
                    15,
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

        private fun buildConstraints(
            restrictions: Set<String>,
            forceWifiAndCharging: Boolean,
        ): Constraints {
            val policy = resolveLibraryAutoUpdateConstraintPolicy(
                restrictions = restrictions,
                forceWifiAndCharging = forceWifiAndCharging,
            )
            val networkType = if (policy.requireNotMetered) {
                NetworkType.UNMETERED
            } else {
                NetworkType.CONNECTED
            }
            val networkRequestBuilder = NetworkRequest.Builder()
            if (policy.requireWifi) {
                networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            }
            if (policy.requireNotMetered) {
                networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            return Constraints.Builder()
                // 'networkRequest' only applies to Android 9+, otherwise 'networkType' is used
                .setRequiredNetworkRequest(networkRequestBuilder.build(), networkType)
                .setRequiresCharging(policy.requireCharging)
                .setRequiresBatteryNotLow(true)
                .build()
        }

        private fun cancelLegacyPeriodicWorks(context: Context) {
            val wm = context.workManager
            wm.cancelUniqueWork(LEGACY_MANGA_PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(LEGACY_ANIME_PERIODIC_WORK_NAME)
            wm.cancelUniqueWork(LEGACY_NOVEL_PERIODIC_WORK_NAME)
        }
    }
}

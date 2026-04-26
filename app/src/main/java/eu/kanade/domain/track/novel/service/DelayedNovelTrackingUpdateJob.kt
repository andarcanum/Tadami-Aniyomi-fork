package eu.kanade.domain.track.novel.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import eu.kanade.domain.track.novel.interactor.TrackNovelChapter
import eu.kanade.domain.track.novel.store.DelayedNovelTrackingStore
import eu.kanade.tachiyomi.util.system.workManager
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class DelayedNovelTrackingUpdateJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (runAttemptCount > 3) {
            return Result.failure()
        }

        val getTracks = Injekt.get<GetNovelTracks>()
        val trackNovelChapter = Injekt.get<TrackNovelChapter>()

        val delayedTrackingStore = Injekt.get<DelayedNovelTrackingStore>()

        withIOContext {
            delayedTrackingStore.getNovelItems()
                .mapNotNull {
                    val track = getTracks.awaitOne(it.trackId)
                    if (track == null) {
                        delayedTrackingStore.removeNovelItem(it.trackId)
                    }
                    track?.copy(lastChapterRead = it.lastChapterRead.toDouble())
                }
                .forEach { track ->
                    logcat(LogPriority.DEBUG) {
                        "Updating delayed novel track item: ${track.novelId}, last chapter read: ${track.lastChapterRead}"
                    }
                    trackNovelChapter.await(context, track.novelId, track.lastChapterRead, setupJobOnFailure = false)
                }
        }

        return if (delayedTrackingStore.getNovelItems().isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val TAG = "DelayedNovelTrackingUpdate"

        fun setupTask(context: Context) {
            val constraints = Constraints(
                requiredNetworkType = NetworkType.CONNECTED,
            )

            val request = OneTimeWorkRequestBuilder<DelayedNovelTrackingUpdateJob>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

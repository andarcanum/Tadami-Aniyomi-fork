package eu.kanade.tachiyomi.util.system

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

val Context.workManager: WorkManager
    get() = try {
        WorkManager.getInstance(this)
    } catch (e: IllegalStateException) {
        if (e.message?.contains("not initialized properly", ignoreCase = true) == true) {
            WorkManager.initialize(this, Configuration.Builder().build())
            WorkManager.getInstance(this)
        } else {
            throw e
        }
    }

fun WorkManager.isRunning(tag: String): Boolean {
    val list = this.getWorkInfosByTag(tag).get()
    return list.any { it.state == WorkInfo.State.RUNNING }
}

fun WorkManager.isRunningOrEnqueued(tag: String): Boolean {
    val list = this.getWorkInfosByTag(tag).get()
    return list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
}

fun WorkManager.isRunningFlow(tag: String): Flow<Boolean> {
    return this.getWorkInfosByTagLiveData(tag).asFlow()
        .map { list -> list.any { it.state == WorkInfo.State.RUNNING } }
        .distinctUntilChanged()
}

internal fun isActiveWorkState(state: WorkInfo.State): Boolean =
    state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.RUNNING

fun WorkManager.isRunningOrEnqueuedFlow(tag: String): Flow<Boolean> {
    return this.getWorkInfosByTagLiveData(tag).asFlow()
        .map { list -> list.any { isActiveWorkState(it.state) } }
        .distinctUntilChanged()
}

/**
 * Makes this worker run in the context of a foreground service.
 *
 * Note that this function is a no-op if the process is subject to foreground
 * service restrictions.
 *
 * Moving to foreground service context requires the worker to run a bit longer,
 * allowing Service.startForeground() to be called and avoiding system crash.
 */
suspend fun CoroutineWorker.setForegroundSafely() {
    try {
        setForeground(getForegroundInfo())
        delay(500)
    } catch (e: IllegalStateException) {
        logcat(LogPriority.ERROR, e) { "Not allowed to set foreground job" }
    }
}

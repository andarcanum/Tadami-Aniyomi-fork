package eu.kanade.tachiyomi.ui.reels

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.data.notification.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

internal data class ReelsExportRequest(
    val item: ShortVideoItem,
    val sourceId: Long,
    val headers: Map<String, String>,
    val qualitySnapshot: ReelsQualityDownloadSnapshot? = null,
    val cacheKeys: List<String> = emptyList(),
) : Serializable {
    val key: String get() = reelsDownloadKey(sourceId, item.id)
}

internal data class ReelsExportState(
    val bytes: Long = 0L,
    val total: Long? = null,
    val inProgress: Boolean = true,
    val uri: Uri? = null,
    val operation: Any? = null,
    val partial: Boolean = false,
    val qualitySnapshot: ReelsQualityDownloadSnapshot? = null,
)

internal sealed interface ReelsDownloadEvent {
    data class Completed(val key: String, val toCustomDocument: Boolean) : ReelsDownloadEvent
    data class Paused(val key: String) : ReelsDownloadEvent
    data class Failed(val key: String) : ReelsDownloadEvent
    data class Unsupported(val key: String) : ReelsDownloadEvent
}

/**
 * Application-scoped Reels download manager.
 *
 * Replaces the Composable-bound `rememberCoroutineScope()` download jobs inside `ReelsFeedScreen`:
 * - Pressing Back or opening a creator/album screen no longer kills running downloads.
 * - Partial cleanup runs exclusively on [Dispatchers.IO] (never synchronously on the UI thread).
 * - Posts progress / completion notifications on [Notifications.CHANNEL_DOWNLOADER_PROGRESS].
 */
internal object ReelsDownloadManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _states = MutableStateFlow<Map<String, ReelsExportState>>(emptyMap())
    val states: StateFlow<Map<String, ReelsExportState>> = _states.asStateFlow()

    private val _events = MutableSharedFlow<ReelsDownloadEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<ReelsDownloadEvent> = _events.asSharedFlow()

    fun currentState(key: String): ReelsExportState? = _states.value[key]

    fun hasActiveDownloads(): Boolean = _states.value.values.any { it.inProgress }

    fun markStarting(key: String, previous: ReelsExportState?) {
        _states.update { map ->
            map + (key to (previous?.copy(inProgress = true, partial = false) ?: ReelsExportState()))
        }
    }

    fun restoreState(key: String, state: ReelsExportState) {
        _states.update { map -> map + (key to state) }
    }

    fun cancel(context: Context, key: String) {
        jobs.remove(key)?.cancel()
        clearAsync(context.applicationContext, key)
    }

    fun clearAsync(context: Context, key: String) {
        val appContext = context.applicationContext
        var uriToDiscard: Uri? = null
        _states.update { map ->
            val removed = map[key]
            if (removed?.partial == true) uriToDiscard = removed.uri
            map - key
        }
        uriToDiscard?.let { uri ->
            scope.launch {
                ReelsDownloads(appContext).discard(uri)
            }
        }
        updateProgressNotification(appContext)
    }

    fun enqueue(
        context: Context,
        request: ReelsExportRequest,
        destination: Uri? = null,
        resumeFrom: Long = 0L,
        resumeUri: Uri? = null,
    ) {
        val appContext = context.applicationContext
        val downloads = ReelsDownloads(appContext)
        val operation = Any()
        _states.update { map ->
            map + (
                request.key to ReelsExportState(
                    operation = operation,
                    bytes = resumeFrom,
                    uri = resumeUri,
                    total = map[request.key]?.total,
                    inProgress = true,
                    partial = false,
                    qualitySnapshot = request.qualitySnapshot,
                )
                )
        }
        updateProgressNotification(appContext)

        var lastNotifyAtMs = 0L
        val job = scope.launch {
            val result = try {
                downloads.download(
                    item = request.item,
                    sourceId = request.sourceId,
                    headers = request.headers,
                    destination = destination,
                    resumeFrom = resumeFrom,
                    resumeUri = resumeUri,
                    keepPartial = destination == null,
                    cacheKeys = request.cacheKeys,
                ) { bytes, total ->
                    _states.update { map ->
                        val current = map[request.key]
                        if (current?.inProgress == true && current.operation === operation) {
                            map + (request.key to current.copy(bytes = bytes, total = total))
                        } else {
                            map
                        }
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotifyAtMs >= NOTIFICATION_THROTTLE_MS) {
                        lastNotifyAtMs = now
                        updateProgressNotification(appContext)
                    }
                }
            } catch (e: CancellationException) {
                clearAsync(appContext, request.key)
                throw e
            } finally {
                jobs.remove(request.key)
            }

            when (result) {
                is ReelsDownloadResult.Saved -> {
                    _states.update { map ->
                        map + (
                            request.key to ReelsExportState(
                                bytes = result.bytes,
                                total = result.bytes,
                                inProgress = false,
                                uri = result.uri,
                                partial = false,
                                qualitySnapshot = request.qualitySnapshot,
                            )
                            )
                    }
                    _events.tryEmit(ReelsDownloadEvent.Completed(request.key, destination != null))
                }
                is ReelsDownloadResult.Partial -> {
                    _states.update { map ->
                        map + (
                            request.key to ReelsExportState(
                                bytes = result.bytes,
                                total = map[request.key]?.total,
                                inProgress = false,
                                uri = result.uri,
                                partial = true,
                                qualitySnapshot = request.qualitySnapshot,
                            )
                            )
                    }
                    _events.tryEmit(ReelsDownloadEvent.Paused(request.key))
                }
                ReelsDownloadResult.Failed -> {
                    clearAsync(appContext, request.key)
                    _events.tryEmit(ReelsDownloadEvent.Failed(request.key))
                }
                ReelsDownloadResult.Unsupported -> {
                    clearAsync(appContext, request.key)
                    _events.tryEmit(ReelsDownloadEvent.Unsupported(request.key))
                }
            }
            updateProgressNotification(appContext)
        }
        jobs[request.key] = job
    }

    private fun updateProgressNotification(appContext: Context) {
        runCatching {
            val nm = NotificationManagerCompat.from(appContext)
            val active = _states.value.values.filter { it.inProgress }
            if (active.isEmpty()) {
                nm.cancel(Notifications.ID_DOWNLOAD_REELS_PROGRESS)
                return
            }
            val totalKnown = active.all { (it.total ?: 0L) > 0L }
            val sumBytes = active.sumOf { it.bytes }
            val sumTotal = active.sumOf { it.total ?: 0L }
            val progressPct = if (totalKnown && sumTotal > 0L) {
                ((sumBytes * 100L) / sumTotal).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val title = if (active.size == 1) {
                appContext.stringResource(MR.strings.reels_download_saved)
            } else {
                "${active.size} Reels"
            }
            val notification = NotificationCompat.Builder(appContext, Notifications.CHANNEL_DOWNLOADER_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setProgress(100, progressPct, !totalKnown)
                .build()
            nm.notify(Notifications.ID_DOWNLOAD_REELS_PROGRESS, notification)
        }
    }

    private const val NOTIFICATION_THROTTLE_MS = 500L
}

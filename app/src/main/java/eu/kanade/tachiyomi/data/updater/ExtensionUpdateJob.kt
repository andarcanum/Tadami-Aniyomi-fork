package eu.kanade.tachiyomi.data.updater

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tadami.aurora.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelPluginUpdateClassifier
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

class ExtensionUpdateJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val kind = inputData.getString(KEY_KIND)?.let {
            runCatching { ApkExtensionKind.valueOf(it) }.getOrNull()
        }
        val progress = kind?.let { batchStates[it]?.value }
        return createForegroundInfo(progress?.current ?: 0, progress?.total ?: 0)
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS,
            createNotification(current, total),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun createNotification(current: Int, total: Int): Notification {
        val cancelIntent = context.workManager.createCancelPendingIntent(id)
        val title = runCatching { context.stringResource(MR.strings.ext_update_all) }
            .getOrDefault("Updating extensions")
        val cancelText = runCatching { context.stringResource(MR.strings.action_cancel) }
            .getOrDefault("Cancel")

        val builder = NotificationCompat.Builder(context, Notifications.CHANNEL_EXTENSIONS_UPDATE)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_extension_24dp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_close_24dp,
                cancelText,
                cancelIntent,
            )

        if (total > 0) {
            builder.setContentText("$current / $total")
            builder.setProgress(total, current, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(current: Int, total: Int) {
        if (!isStopped) {
            try {
                val notification = createNotification(current, total)
                NotificationManagerCompat.from(context).notify(
                    Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS,
                    notification,
                )
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update notification" }
            }
        }
    }

    private fun dismissNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(
                Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS,
            )
        }
    }

    override suspend fun doWork(): Result = withIOContext {
        val kindName = inputData.getString(KEY_KIND) ?: return@withIOContext Result.failure()
        val kind = try {
            ApkExtensionKind.valueOf(kindName)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Invalid ApkExtensionKind: $kindName" }
            return@withIOContext Result.failure()
        }

        setForegroundSafely()

        try {
            if (cancelHandlers.containsKey(kind)) {
                awaitCoordinatedBatch(kind)
            } else {
                when (kind) {
                    ApkExtensionKind.MANGA -> updateMangaExtensions()
                    ApkExtensionKind.ANIME -> updateAnimeExtensions()
                    ApkExtensionKind.NOVEL_KOTLIN -> updateNovelExtensions()
                }
            }
        } finally {
            if (isStopped) {
                cancelHandlers.remove(kind)?.invoke()
            }
            dismissNotification()
        }

        Result.success()
    }

    /**
     * Holds the Foreground Service and updates the system notification while the ScreenModel
     * drives the sequential queue (preserving per-row progress, reinstall dialogs, and diagnostics).
     */
    private suspend fun awaitCoordinatedBatch(kind: ApkExtensionKind) {
        val stateFlow = stateFlowFor(kind)
        var lastCurrent = -1
        var lastTotal = -1
        while (!isStopped && stateFlow.value.isRunning) {
            val snapshot = stateFlow.value
            if (snapshot.current != lastCurrent || snapshot.total != lastTotal) {
                lastCurrent = snapshot.current
                lastTotal = snapshot.total
                setProgress(workDataOf(KEY_CURRENT to snapshot.current, KEY_TOTAL to snapshot.total))
                updateNotification(snapshot.current, snapshot.total)
            }
            delay(100.milliseconds)
        }
    }

    private suspend fun updateMangaExtensions() {
        val extensionManager = Injekt.get<MangaExtensionManager>()
        val extensionsToUpdate = extensionManager.installedExtensionsFlow.value
            .filter { it.hasUpdate && !it.needsReinstall }

        val total = extensionsToUpdate.size
        setProgress(workDataOf(KEY_CURRENT to 0, KEY_TOTAL to total))
        updateNotification(0, total)

        for ((index, extension) in extensionsToUpdate.withIndex()) {
            if (isStopped) {
                extensionManager.cancelInstallUpdateExtension(extension)
                break
            }

            setProgress(workDataOf(KEY_CURRENT to index, KEY_TOTAL to total))
            updateNotification(index, total)

            try {
                extensionManager.updateExtension(extension).first { it.isCompleted() }
            } catch (e: CancellationException) {
                extensionManager.cancelInstallUpdateExtension(extension)
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update manga extension ${extension.pkgName}" }
            }

            setProgress(workDataOf(KEY_CURRENT to index + 1, KEY_TOTAL to total))
            updateNotification(index + 1, total)
        }
    }

    private suspend fun updateAnimeExtensions() {
        val extensionManager = Injekt.get<AnimeExtensionManager>()
        val extensionsToUpdate = extensionManager.installedExtensionsFlow.value
            .filter { it.hasUpdate && !it.needsReinstall }

        val total = extensionsToUpdate.size
        setProgress(workDataOf(KEY_CURRENT to 0, KEY_TOTAL to total))
        updateNotification(0, total)

        for ((index, extension) in extensionsToUpdate.withIndex()) {
            if (isStopped) {
                extensionManager.cancelInstallUpdateExtension(extension)
                break
            }

            setProgress(workDataOf(KEY_CURRENT to index, KEY_TOTAL to total))
            updateNotification(index, total)

            try {
                extensionManager.updateExtension(extension).first { it.isCompleted() }
            } catch (e: CancellationException) {
                extensionManager.cancelInstallUpdateExtension(extension)
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update anime extension ${extension.pkgName}" }
            }

            setProgress(workDataOf(KEY_CURRENT to index + 1, KEY_TOTAL to total))
            updateNotification(index + 1, total)
        }
    }

    private suspend fun updateNovelExtensions() {
        val extensionManager = Injekt.get<NovelExtensionManager>()
        val installedList = extensionManager.installedPluginsFlow.firstOrNull() ?: emptyList()
        val availableList = extensionManager.availablePluginsFlow.firstOrNull() ?: emptyList()
        val allVariants = availableList.groupBy { it.id }

        val updatesToInstall = installedList.mapNotNull { installed ->
            val updateState = NovelPluginUpdateClassifier.classify(
                installed = installed,
                variants = allVariants[installed.id].orEmpty(),
            )
            updateState.sameRepoUpdate
        }

        val total = updatesToInstall.size
        setProgress(workDataOf(KEY_CURRENT to 0, KEY_TOTAL to total))
        updateNotification(0, total)

        for ((index, plugin) in updatesToInstall.withIndex()) {
            if (isStopped) {
                extensionManager.cancelPluginInstall(plugin)
                break
            }

            setProgress(workDataOf(KEY_CURRENT to index, KEY_TOTAL to total))
            updateNotification(index, total)

            try {
                extensionManager.installPlugin(plugin)
            } catch (e: CancellationException) {
                extensionManager.cancelPluginInstall(plugin)
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Failed to update novel plugin ${plugin.id}" }
            }

            setProgress(workDataOf(KEY_CURRENT to index + 1, KEY_TOTAL to total))
            updateNotification(index + 1, total)
        }
    }

    data class BatchProgress(
        val isRunning: Boolean = false,
        val current: Int = 0,
        val total: Int = 0,
    )

    companion object {
        const val WORK_NAME_MANGA = "ExtensionUpdateJob_MANGA"
        const val WORK_NAME_ANIME = "ExtensionUpdateJob_ANIME"
        const val WORK_NAME_NOVEL = "ExtensionUpdateJob_NOVEL"
        const val KEY_KIND = "KEY_KIND"
        const val KEY_CURRENT = "KEY_CURRENT"
        const val KEY_TOTAL = "KEY_TOTAL"

        private val batchStates = ConcurrentHashMap<ApkExtensionKind, MutableStateFlow<BatchProgress>>()
        private val cancelHandlers = ConcurrentHashMap<ApkExtensionKind, () -> Unit>()

        fun stateFlowFor(kind: ApkExtensionKind): StateFlow<BatchProgress> {
            return batchStates.getOrPut(kind) { MutableStateFlow(BatchProgress()) }.asStateFlow()
        }

        fun onBatchStarted(
            kind: ApkExtensionKind,
            total: Int,
            onCancel: () -> Unit,
        ) {
            cancelHandlers[kind] = onCancel
            batchStates.getOrPut(kind) { MutableStateFlow(BatchProgress()) }.value = BatchProgress(
                isRunning = true,
                current = 0,
                total = total,
            )
        }

        fun onBatchProgress(kind: ApkExtensionKind, current: Int, total: Int) {
            val flow = batchStates.getOrPut(kind) { MutableStateFlow(BatchProgress()) }
            if (flow.value.isRunning) {
                flow.value = BatchProgress(
                    isRunning = true,
                    current = current,
                    total = total,
                )
            }
        }

        fun onBatchFinished(context: Context, kind: ApkExtensionKind) {
            cancelHandlers.remove(kind)
            batchStates.getOrPut(kind) { MutableStateFlow(BatchProgress()) }.value = BatchProgress(
                isRunning = false,
                current = 0,
                total = 0,
            )
            val workName = getWorkName(kind)
            runCatching {
                context.workManager.cancelUniqueWork(workName)
            }
            runCatching {
                NotificationManagerCompat.from(context).cancel(
                    Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS,
                )
            }
        }

        fun getWorkName(kind: ApkExtensionKind): String = when (kind) {
            ApkExtensionKind.MANGA -> WORK_NAME_MANGA
            ApkExtensionKind.ANIME -> WORK_NAME_ANIME
            ApkExtensionKind.NOVEL_KOTLIN -> WORK_NAME_NOVEL
        }

        fun isRunning(context: Context, kind: ApkExtensionKind): Boolean {
            if (batchStates[kind]?.value?.isRunning == true) return true
            val workName = getWorkName(kind)
            return try {
                val list = context.workManager.getWorkInfosForUniqueWork(workName).get()
                list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            } catch (_: Exception) {
                false
            }
        }

        fun start(context: Context, kind: ApkExtensionKind): Boolean {
            val workName = getWorkName(kind)
            val alreadyInWorkManager = try {
                val list = context.workManager.getWorkInfosForUniqueWork(workName).get()
                list.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            } catch (_: Exception) {
                false
            }
            if (alreadyInWorkManager) {
                return false
            }
            val request = OneTimeWorkRequestBuilder<ExtensionUpdateJob>()
                .addTag(workName)
                .setInputData(workDataOf(KEY_KIND to kind.name))
                .build()
            runCatching {
                context.workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
            }
            return true
        }

        fun stop(context: Context, kind: ApkExtensionKind) {
            cancelHandlers.remove(kind)?.invoke()
            batchStates.getOrPut(kind) { MutableStateFlow(BatchProgress()) }.value = BatchProgress(
                isRunning = false,
                current = 0,
                total = 0,
            )
            val workName = getWorkName(kind)
            runCatching {
                context.workManager.cancelUniqueWork(workName)
            }
            runCatching {
                NotificationManagerCompat.from(context).cancel(
                    Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS,
                )
            }
        }
    }
}

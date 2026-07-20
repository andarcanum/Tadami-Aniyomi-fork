package eu.kanade.tachiyomi.data.anilist

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anilist.AnilistImportEntry
import tachiyomi.data.anilist.AnilistImportMediaType
import tachiyomi.data.anilist.AnilistImportPlanner
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class AnilistImportJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val json: Json by lazy { Json { ignoreUnknownKeys = true } }
    private val notifier = AnilistImportNotifier(context)

    override suspend fun doWork(): Result {
        val planFile = inputData.getString(PLAN_FILE_KEY)?.let { File(it) }
            ?: return Result.failure()
        val mediaTypeName = inputData.getString(MEDIA_TYPE_KEY) ?: return Result.failure()

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "AniList import foreground not allowed" }
        }

        return try {
            val mediaType = AnilistImportMediaType.valueOf(mediaTypeName)
            val payload = json.decodeFromString<StoredAnilistImportPayload>(planFile.readText())
            val plan = payload.toPlan()
            val executor = Injekt.get<ImportAnilistExecutor>()
            val report = executor.await(mediaType, plan) { current, total ->
                notifier.notifyProgress(current, total)
            }
            notifier.showComplete(report)
            planFile.delete()
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showError(context.stringResource(AYMR.strings.anilist_import_error_canceled))
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showError(e.message ?: context.stringResource(AYMR.strings.anilist_import_error_failed))
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_ANILIST_IMPORT_PROGRESS,
            notifier.showProgress(0, 0).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG = "AnilistImport"
        private const val PLAN_FILE_KEY = "plan_file"
        private const val MEDIA_TYPE_KEY = "media_type"

        fun isRunning(context: Context): Boolean = context.workManager.isRunning(TAG)

        fun start(
            context: Context,
            mediaType: AnilistImportMediaType,
            plan: AnilistImportPlanner.Plan,
        ) {
            val file = File(context.cacheDir, "anilist_import_${System.currentTimeMillis()}.json")
            val payload = StoredAnilistImportPayload.from(plan)
            file.writeText(Json.encodeToString(payload))

            val request = OneTimeWorkRequestBuilder<AnilistImportJob>()
                .setInputData(
                    workDataOf(
                        PLAN_FILE_KEY to file.absolutePath,
                        MEDIA_TYPE_KEY to mediaType.name,
                    ),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

@Serializable
data class StoredAnilistImportPayload(
    val actions: List<StoredAnilistAction>,
) {
    fun toPlan(): AnilistImportPlanner.Plan {
        return AnilistImportPlanner.Plan(
            actions = actions.map { it.toAction() },
            skippedDisabled = 0,
            skippedNoMatch = 0,
            mergedDuplicates = 0,
        )
    }

    companion object {
        fun from(plan: AnilistImportPlanner.Plan) =
            StoredAnilistImportPayload(actions = plan.actions.map { StoredAnilistAction.from(it) })
    }
}

@Serializable
data class StoredAnilistAction(
    val entry: StoredAnilistEntry,
    val sourceId: Long,
    val url: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val categoryIds: List<Long>,
) {
    fun toAction(): AnilistImportPlanner.Action {
        return AnilistImportPlanner.Action(
            entry = entry.toEntry(),
            candidate = AnixartMatcher.SearchCandidate(
                id = (sourceId.toString() + url).hashCode().toLong(),
                sourceId = sourceId,
                displayTitle = displayTitle,
                titles = listOf(displayTitle),
                url = url,
                thumbnailUrl = thumbnailUrl,
            ),
            categoryIds = categoryIds.toSet(),
        )
    }

    companion object {
        fun from(action: AnilistImportPlanner.Action) = StoredAnilistAction(
            entry = StoredAnilistEntry.from(action.entry),
            sourceId = action.candidate.sourceId,
            url = action.candidate.url,
            displayTitle = action.candidate.displayTitle,
            thumbnailUrl = action.candidate.thumbnailUrl,
            categoryIds = action.categoryIds.toList(),
        )
    }
}

@Serializable
data class StoredAnilistEntry(
    val mediaType: String,
    val listEntryId: Long,
    val remoteId: Long,
    val name: String,
    val english: String?,
    val status: String,
    val score: Int,
    val progress: Int,
    val totalCount: Long?,
    val thumbnailUrl: String?,
) {
    fun toEntry() = AnilistImportEntry(
        mediaType = AnilistImportMediaType.valueOf(mediaType),
        listEntryId = listEntryId,
        remoteId = remoteId,
        name = name,
        english = english,
        status = status,
        score = score,
        progress = progress,
        totalCount = totalCount,
        thumbnailUrl = thumbnailUrl,
    )

    companion object {
        fun from(entry: AnilistImportEntry) = StoredAnilistEntry(
            mediaType = entry.mediaType.name,
            listEntryId = entry.listEntryId,
            remoteId = entry.remoteId,
            name = entry.name,
            english = entry.english,
            status = entry.status,
            score = entry.score,
            progress = entry.progress,
            totalCount = entry.totalCount,
            thumbnailUrl = entry.thumbnailUrl,
        )
    }
}

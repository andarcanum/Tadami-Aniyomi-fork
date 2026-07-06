package eu.kanade.tachiyomi.data.anixart

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.presentation.more.settings.screen.anixart.AnixartImportScreenModel
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.anixart.AnixartImportPlanner
import tachiyomi.data.anixart.AnixartMatcher
import tachiyomi.data.anixart.AnixartRow
import tachiyomi.data.anixart.AnixartStatus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

class AnixartImportJob(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val json: Json by lazy { Json { ignoreUnknownKeys = true } }
    private val notifier = AnixartImportNotifier(context)

    override suspend fun doWork(): Result {
        val planFile = inputData.getString(PLAN_FILE_KEY)?.let { File(it) }
            ?: return Result.failure()
        val syncToShikimori = inputData.getBoolean(SYNC_SHIKIMORI_KEY, false)

        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            logcat(LogPriority.ERROR, e) { "Anixart import foreground not allowed" }
        }

        return try {
            val payload = json.decodeFromString<StoredImportPayload>(planFile.readText())
            val plan = payload.toPlan()
            val importEntries = Injekt.get<ImportAnixartEntries>()
            val report = importEntries.await(plan) { current, total ->
                notifier.showProgress(current, total)
            }
            val trackerReport = if (syncToShikimori) {
                val trackerSync = Injekt.get<AnixartTrackerSync>()
                val getAnimeByUrl = Injekt.get<tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId>()
                val pairs = payload.trackerRows.mapNotNull { row ->
                    val animeId = getAnimeByUrl.await(row.url, row.sourceId)?.id ?: return@mapNotNull null
                    row.toAnixartRow() to animeId
                }
                trackerSync.syncToShikimori(pairs)
            } else {
                null
            }
            notifier.showComplete(report, trackerReport)
            planFile.delete()
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) {
                notifier.showError("Import canceled")
                Result.success()
            } else {
                logcat(LogPriority.ERROR, e)
                notifier.showError(e.message ?: "Import failed")
                Result.failure()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_ANIXART_IMPORT_PROGRESS,
            notifier.showProgress(0, 0).build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG = "AnixartImport"
        private const val PLAN_FILE_KEY = "plan_file"
        private const val SYNC_SHIKIMORI_KEY = "sync_shikimori"

        fun isRunning(context: Context): Boolean = context.workManager.isRunning(TAG)

        fun start(
            context: Context,
            plan: AnixartImportPlanner.Plan,
            syncToShikimori: Boolean,
            trackerRows: List<StoredTrackerRow>,
        ) {
            val file = File(context.cacheDir, "anixart_import_${System.currentTimeMillis()}.json")
            val payload = StoredImportPayload.from(plan, trackerRows)
            file.writeText(Json.encodeToString(payload))

            val request = OneTimeWorkRequestBuilder<AnixartImportJob>()
                .setInputData(
                    workDataOf(
                        PLAN_FILE_KEY to file.absolutePath,
                        SYNC_SHIKIMORI_KEY to syncToShikimori,
                    ),
                )
                .build()
            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun start(
            plan: AnixartImportPlanner.Plan,
            syncToShikimori: Boolean,
            reviewItems: List<eu.kanade.presentation.more.settings.screen.anixart.AnixartImportScreenModel.ReviewItem>,
        ) {
            val trackerRows = reviewItems.mapNotNull { item ->
                if (!item.enabled || item.selectedId == null) return@mapNotNull null
                val candidate = item.result.ranked.firstOrNull { it.candidate.id == item.selectedId }?.candidate
                    ?: return@mapNotNull null
                StoredTrackerRow(
                    row = StoredRow.from(item.row),
                    sourceId = candidate.sourceId,
                    url = candidate.url,
                    animeId = null,
                )
            }
            val app = Injekt.get<android.app.Application>()
            start(app, plan, syncToShikimori, trackerRows)
        }
    }
}

@Serializable
data class StoredImportPayload(
    val actions: List<StoredAction>,
    val trackerRows: List<StoredTrackerRow>,
) {
    fun toPlan(): AnixartImportPlanner.Plan {
        val plannerActions = actions.map { it.toAction() }
        return AnixartImportPlanner.Plan(
            actions = plannerActions,
            skippedDisabled = 0,
            skippedNoMatch = 0,
            mergedDuplicates = 0,
        )
    }

    companion object {
        fun from(plan: AnixartImportPlanner.Plan, trackerRows: List<StoredTrackerRow>) =
            StoredImportPayload(
                actions = plan.actions.map { StoredAction.from(it) },
                trackerRows = trackerRows,
            )
    }
}

@Serializable
data class StoredAction(
    val sourceId: Long,
    val url: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    val categoryIds: List<Long>,
    val status: String?,
    val rating: Int?,
    val isFavorite: Boolean,
) {
    fun toAction(): AnixartImportPlanner.Action {
        return AnixartImportPlanner.Action(
            candidate = AnixartMatcher.SearchCandidate(
                id = (sourceId.toString() + url).hashCode().toLong(),
                sourceId = sourceId,
                displayTitle = displayTitle,
                titles = listOf(displayTitle),
                url = url,
                thumbnailUrl = thumbnailUrl,
            ),
            categoryIds = categoryIds.toSet(),
            status = status?.let { s -> AnixartStatus.entries.firstOrNull { it.name == s } },
            rating = rating,
            isFavorite = isFavorite,
        )
    }

    companion object {
        fun from(action: AnixartImportPlanner.Action) = StoredAction(
            sourceId = action.candidate.sourceId,
            url = action.candidate.url,
            displayTitle = action.candidate.displayTitle,
            thumbnailUrl = action.candidate.thumbnailUrl,
            categoryIds = action.categoryIds.toList(),
            status = action.status?.name,
            rating = action.rating,
            isFavorite = action.isFavorite,
        )
    }
}

@Serializable
data class StoredTrackerRow(
    val row: StoredRow,
    val sourceId: Long,
    val url: String,
    val animeId: Long?,
) {
    fun toAnixartRow() = row.toAnixartRow()
}

@Serializable
data class StoredRow(
    val index: Int,
    val russianTitle: String,
    val originalTitle: String,
    val alternativeTitles: String,
    val favoriteRaw: String,
    val statusRaw: String,
    val ratingRaw: String,
) {
    fun toAnixartRow() = AnixartRow(
        index = index,
        russianTitle = russianTitle,
        originalTitle = originalTitle,
        alternativeTitles = alternativeTitles,
        favoriteRaw = favoriteRaw,
        statusRaw = statusRaw,
        ratingRaw = ratingRaw,
    )

    companion object {
        fun from(row: AnixartRow) = StoredRow(
            index = row.index,
            russianTitle = row.russianTitle,
            originalTitle = row.originalTitle,
            alternativeTitles = row.alternativeTitles,
            favoriteRaw = row.favoriteRaw,
            statusRaw = row.statusRaw,
            ratingRaw = row.ratingRaw,
        )
    }
}

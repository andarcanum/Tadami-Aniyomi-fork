package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.replace.replaceRulesFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelTranslationProvider
import eu.kanade.tachiyomi.ui.reader.novel.translation.GeminiTranslationCacheEntry
import eu.kanade.tachiyomi.ui.reader.novel.translation.NOVEL_TRANSLATION_EXTRACTOR_VERSION
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationCacheResolver
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.SharedTranslationRequests
import eu.kanade.tachiyomi.ui.reader.novel.translation.redactTranslationDiagnostic
import eu.kanade.tachiyomi.ui.reader.novel.translation.toTranslationCacheRequirements
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationCacheModelId
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationConcurrencyLimit
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationEndpoint
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationGenerationFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationPromptModifiersFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.translation.translationQuotaKey
import eu.kanade.tachiyomi.ui.reader.novel.tts.NovelTtsChapterRepository
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import tachiyomi.domain.translation.model.TranslationArtifactReceipt
import tachiyomi.domain.translation.model.TranslationAttemptException
import tachiyomi.domain.translation.model.TranslationFailure
import tachiyomi.domain.translation.model.TranslationFailureKind
import tachiyomi.domain.translation.model.TranslationFragment
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.model.TranslationRunStatus
import tachiyomi.domain.translation.service.TranslationFragmentPlanner
import tachiyomi.domain.translation.service.TranslationPassState
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/** WorkManager is a runner/wakeup, not a second, whole-chapter HTTP retry loop. */
class TranslationJob @JvmOverloads constructor(
    context: Context,
    workerParams: WorkerParameters,
    private val queue: TranslationQueueManager = Injekt.get(),
    private val notifications: TranslationNotificationManager = Injekt.get(),
    private val preferences: NovelReaderPreferences = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val chapters: NovelTtsChapterRepository = NovelTtsChapterRepository(),
    private val processor: NovelChapterTranslationProcessor = NovelChapterTranslationProcessor(),
    private val chapterRepository: NovelChapterRepository = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
    private val enterForeground: suspend (TranslationJob) -> Unit = { it.setForegroundSafely() },
    private val wakeScheduler: (Context, Long?) -> Unit = { ctx, due -> scheduleWake(ctx, due) },
) : CoroutineWorker(context, workerParams) {
    private val repository get() = queue.repository

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = applicationContext.notificationBuilder(Notifications.CHANNEL_TRANSLATION_PROGRESS) {
            setContentTitle(applicationContext.stringResource(MR.strings.notification_translation_in_progress))
            setSmallIcon(android.R.drawable.ic_menu_edit)
        }.build()
        return ForegroundInfo(
            Notifications.ID_TRANSLATION_PROGRESS,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override suspend fun doWork(): Result {
        repository.recoverInterrupted(System.currentTimeMillis())
        while (!isStopped) {
            val run = repository.claimNext(System.currentTimeMillis()) ?: break
            enterForeground(this)
            queue.setActiveTranslation(run.toQueueItem())
            try {
                processRun(run)
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    withContext(NonCancellable) {
                        val current = repository.getByChapterId(run.chapterId)
                        if (current?.id == run.id && current.generation == run.generation) {
                            repository.release(
                                current,
                                TranslationRunStatus.PENDING,
                                current.progress,
                                null,
                                now = System.currentTimeMillis(),
                            )
                        }
                    }
                    throw error
                }
                // A targeted cancellation ends this run's child scope, not unrelated queued work.
            } catch (error: Exception) {
                val failure = (error as? TranslationAttemptException)?.failure
                    ?: TranslationFailure(
                        TranslationFailureKind.INVALID_RESPONSE,
                        redactTranslationDiagnostic(
                            error.message?.takeIf { it.isNotBlank() }
                                ?: "Unable to prepare or save chapter (${error.javaClass.simpleName})",
                        ),
                    )
                val current = repository.getByChapterId(run.chapterId)
                if (current?.id == run.id && current.generation == run.generation) {
                    recordFailure(current, failure, "Chapter ${run.chapterId}")
                }
            } finally {
                if (queue.activeTranslation.value?.id == run.id &&
                    queue.activeTranslation.value?.generation == run.generation
                ) {
                    queue.setActiveTranslation(null)
                }
            }
        }
        wakeScheduler(applicationContext, repository.nextWakeAt())
        val batches = repository.getActiveBatches()
        batches.filter { it.status == tachiyomi.domain.translation.model.TranslationBatchStatus.PAUSED }
            .firstOrNull()?.let { notifications.showBatchPaused(it.toBatchState()) }
        if (batches.none {
                it.status == tachiyomi.domain.translation.model.TranslationBatchStatus.PAUSED
            }
        ) {
            notifications.cancelQueueProgress()
        }
        return Result.success()
    }

    private suspend fun processRun(initial: TranslationRun) = coroutineScope {
        val runScope = this
        val watch = launch {
            repository.observeRuns(initial.novelId.takeIf { it > 0 }).collect { rows ->
                val current = rows.firstOrNull { it.chapterId == initial.chapterId }
                if (current == null ||
                    current.id != initial.id ||
                    current.generation != initial.generation ||
                    current.status == TranslationRunStatus.CANCELLED
                ) {
                    runScope.cancel(CancellationException("Translation run was cancelled or replaced"))
                }
            }
        }
        try {
            var run = initial
            val chapter = chapterRepository.getChapterById(run.chapterId)
                ?: error("Chapter not found")
            val novel = getNovel.await(chapter.novelId) ?: error("Novel not found")
            val live = preferences.resolveSettings(novel.source)
            if (run.profileSnapshotJson == null) {
                val rules = preferences.enabledReplaceRules()
                val snapshot = queue.freezeProfileSnapshot(live, novel.id, rules)
                val encoded = json.encodeToString(TranslationQueueProfileSnapshot.serializer(), snapshot)
                run = repository.freezeProfile(
                    run,
                    encoded,
                    TranslationFragmentPlanner.sourceFingerprint(listOf(encoded)),
                    System.currentTimeMillis(),
                )
                    ?: throw CancellationException("Translation run is no longer current")
            }
            val snapshotJson = requireNotNull(run.profileSnapshotJson)
            val snapshot = decodeTranslationRunSnapshot(snapshotJson, json)
            val profileFingerprint = TranslationFragmentPlanner.sourceFingerprint(listOf(snapshotJson))
            if (run.profileFingerprint != null && run.profileFingerprint != profileFingerprint) {
                throw TranslationAttemptException(
                    TranslationFailure(
                        TranslationFailureKind.CONFIGURATION,
                        "Stored translation profile identity does not match its snapshot",
                        attempted = false,
                    ),
                )
            }
            val settings = snapshot.toReaderSettings(live)
            if (snapshot.geminiDisableCache != run.sessionOnly) {
                if (
                    run.sourceFingerprint != null
                ) {
                    error("Stored translation privacy policy does not match the frozen profile")
                }
                run = run.copy(sessionOnly = snapshot.geminiDisableCache)
            }
            if (snapshot.snapshotVersion != 2) {
                SharedTranslationRequests.gate.configure(
                    translationQuotaKey(settings.translationEndpoint()),
                    settings.translationConcurrencyLimit(),
                )
            }
            var fragments = if (
                run.sessionOnly
            ) {
                TranslationSessionResults.store.fragments(run)
            } else {
                repository.getFragments(run)
            }
            if (
                fragments == null && run.sourceFingerprint != null
            ) {
                error("Session-only translation was interrupted; start it again")
            }
            var source: List<String>
            val rules = snapshot.replaceRulesSnapshot ?: preferences.enabledReplaceRules()
            if (fragments.isNullOrEmpty()) {
                if (run.sourceFingerprint != null) error("Stored translation checkpoints are missing")
                if (snapshot.replaceRulesSnapshot == null &&
                    snapshot.replaceRulesFingerprint != replaceRulesFingerprint(rules)
                ) {
                    error("Legacy extraction rules changed; queue the chapter again")
                }
                val loaded = chapters.loadChapterSnapshot(run.chapterId, rules)
                source = loaded.contentBlocks.filterIsInstance<NovelReaderScreenModel.ContentBlock.Text>().map {
                    it.text
                }
                if (source.isEmpty()) error("Chapter has no translatable text")
                fragments = TranslationFragmentPlanner.plan(source, processor.maxFragmentChars(settings))
                if (run.sessionOnly && !run.forceRetranslate) {
                    val reusable = TranslationSessionResults.store.reusable(run.chapterId, run.profileFingerprint)
                    if (reusable != null && TranslationFragmentPlanner.sourceSegments(reusable) == source) {
                        fragments = reusable.map {
                            if (
                                it.isComplete
                            ) {
                                it
                            } else {
                                it.copy(attempts = 0, firstFailureAt = null, nextAttemptAt = 0, failure = null)
                            }
                        }
                    }
                }
                val sourceHash = TranslationFragmentPlanner.sourceFingerprint(source)
                val prepared = repository.prepare(
                    run,
                    sourceHash,
                    profileFingerprint,
                    source.size,
                    fragments,
                    System.currentTimeMillis(),
                )
                requireOwnedOperation(
                    run,
                    prepared,
                    TranslationFailureKind.CONFIGURATION,
                    "Could not freeze the translation source",
                )
                if (run.sessionOnly) TranslationSessionResults.store.checkpoint(run, fragments)
                run = repository.getByChapterId(run.chapterId)
                    ?: throw CancellationException("Translation run disappeared")
            } else {
                source = TranslationFragmentPlanner.sourceSegments(fragments)
                if (source.size != run.sourceSegmentCount ||
                    TranslationFragmentPlanner.sourceFingerprint(source) != run.sourceFingerprint
                ) {
                    error("Stored translation source identity is invalid")
                }
            }
            val sourceHash = requireNotNull(run.sourceFingerprint)
            val generationHash = settings.translationGenerationFingerprint()
            val requirements = settings.toTranslationCacheRequirements(snapshot.replaceRulesFingerprint, sourceHash)
            if (!run.forceRetranslate && !run.sessionOnly) {
                val cached = NovelReaderTranslationDiskCacheStore.get(run.chapterId)
                if (cached?.isTranslationComplete == true && cached.sourceFingerprint == sourceHash &&
                    cached.sourceSegmentCount == source.size &&
                    cached.translatedByIndex.keys == source.indices.toSet() &&
                    NovelReaderTranslationCacheResolver.matches(cached, requirements)
                ) {
                    val receipt = TranslationArtifactReceipt(
                        sourceHash,
                        requireNotNull(run.profileFingerprint),
                        source.size,
                    )
                    if (repository.publishCached(run, System.currentTimeMillis(), receipt) {}) {
                        queue.emitRunLog(run, "Restored a complete translation after verifying the current source")
                        notifications.showChapterComplete(chapter.name, chapter.id)
                        repository.getBatch(run.batchToken)?.takeIf {
                            it.status == tachiyomi.domain.translation.model.TranslationBatchStatus.COMPLETED
                        }
                            ?.let { notifications.showBatchComplete(it.toBatchState()) }
                    } else {
                        currentCoroutineContext().ensureActive()
                        if (
                            !repository.isRunnable(run)
                        ) {
                            repository.release(
                                run,
                                TranslationRunStatus.PAUSED,
                                99,
                                null,
                                now = System.currentTimeMillis(),
                            )
                        } else {
                            error("Cached translation could not be published")
                        }
                    }
                    return@coroutineScope
                }
            }
            val current = requireNotNull(fragments).associateBy { it.id }.toMutableMap()
            val result = processor.translateFragments(
                fragments = current.values.sortedBy { it.id },
                settings = settings,
                profileSnapshot = snapshot,
                run = run,
                onLog = { message -> /* Logs must not own state writes or outlive the run. */ },
                beforeRequest = { repository.isRunnable(run) },
                checkpoint = { changes ->
                    changes.forEach { current[it.id] = it }
                    val progress = TranslationFragmentPlanner.progress(current.values.toList())
                    requireOwnedOperation(
                        run,
                        repository.checkpoint(run, changes, progress, System.currentTimeMillis()),
                        TranslationFailureKind.STORAGE,
                        "Could not save verified translation progress",
                    )
                    if (run.sessionOnly) {
                        TranslationSessionResults.store.checkpoint(
                            run,
                            current.values.sortedBy {
                                it.id
                            },
                        )
                    }
                    queue.setActiveTranslation(run.toQueueItem().copy(progress = progress))
                    notifications.showProgress(
                        chapter.name,
                        chapter.id,
                        notificationBatchToken(run),
                        progress,
                        queue.queue.value.size,
                    )
                },
            )
            val progress = TranslationFragmentPlanner.progress(result.fragments)
            when (result.state) {
                TranslationPassState.COMPLETE -> {
                    val translated = TranslationFragmentPlanner.assemble(result.fragments)
                    if (
                        translated.keys != source.indices.toSet() || translated.values.any(String::isBlank)
                    ) {
                        error("Chapter coverage is incomplete")
                    }
                    val entry = GeminiTranslationCacheEntry(
                        chapterId = run.chapterId,
                        translatedByIndex = translated,
                        provider = settings.translationProvider,
                        model = settings.translationCacheModelId(),
                        sourceLang = settings.geminiSourceLang,
                        targetLang = settings.geminiTargetLang,
                        promptMode = settings.geminiPromptMode, stylePreset = settings.geminiStylePreset,
                        extractorVersion = NOVEL_TRANSLATION_EXTRACTOR_VERSION,
                        promptModifiersFingerprint = settings.translationPromptModifiersFingerprint(),
                        replaceRulesFingerprint = snapshot.replaceRulesFingerprint, sourceSegmentCount = source.size,
                        sourceFingerprint = sourceHash, generationFingerprint = generationHash,
                    )
                    if (repository.publish(run, System.currentTimeMillis()) {
                            if (
                                run.sessionOnly
                            ) {
                                TranslationSessionResults.store.publish(
                                    run,
                                    entry,
                                )
                            } else {
                                NovelReaderTranslationDiskCacheStore.put(entry)
                            }
                        }
                    ) {
                        notifications.showChapterComplete(chapter.name, chapter.id)
                        repository.getBatch(run.batchToken)?.takeIf {
                            it.status == tachiyomi.domain.translation.model.TranslationBatchStatus.COMPLETED
                        }
                            ?.let { notifications.showBatchComplete(it.toBatchState()) }
                    } else {
                        repository.release(
                            run,
                            TranslationRunStatus.PAUSED,
                            progress,
                            null,
                            now = System.currentTimeMillis(),
                        )
                    }
                }
                TranslationPassState.WAITING -> {
                    val due = requireNotNull(result.nextAttemptAt)
                    repository.release(
                        run,
                        TranslationRunStatus.WAITING,
                        progress,
                        result.failure?.message,
                        due,
                        System.currentTimeMillis(),
                    )
                    queue.emitRunLog(
                        run,
                        "Temporary failure; verified progress $progress%; next attempt not before $due",
                    )
                }
                TranslationPassState.PAUSED -> repository.release(
                    run,
                    TranslationRunStatus.PAUSED,
                    progress,
                    null,
                    now = System.currentTimeMillis(),
                )
                TranslationPassState.FAILED -> {
                    val failure = result.failure ?: TranslationFailure(
                        TranslationFailureKind.INVALID_RESPONSE,
                        "Chapter translation is incomplete",
                    )
                    recordFailure(run.copy(progress = progress), failure, chapter.name)
                }
            }
        } finally {
            watch.cancel()
        }
    }

    private suspend fun notificationBatchToken(run: TranslationRun): String {
        val batch = repository.getBatch(run.batchToken)
        return if (batch?.status == tachiyomi.domain.translation.model.TranslationBatchStatus.RUNNING ||
            batch?.status == tachiyomi.domain.translation.model.TranslationBatchStatus.QUEUED
        ) {
            run.batchToken
        } else {
            ""
        }
    }

    /** A validation/storage refusal is not a user cancellation and must not strand owned work. */
    private suspend fun requireOwnedOperation(
        run: TranslationRun,
        succeeded: Boolean,
        kind: TranslationFailureKind,
        message: String,
    ) {
        if (succeeded) return
        currentCoroutineContext().ensureActive()
        val current = repository.getByChapterId(run.chapterId)
        if (current == null ||
            current.id != run.id ||
            current.generation != run.generation ||
            current.status.isTerminal
        ) {
            throw CancellationException("Translation run is no longer owned")
        }
        if (!repository.isRunnable(current)) {
            repository.release(
                current,
                TranslationRunStatus.PAUSED,
                current.progress,
                null,
                now = System.currentTimeMillis(),
            )
            throw CancellationException("Translation owners are paused")
        }
        throw TranslationAttemptException(TranslationFailure(kind, message))
    }

    private suspend fun recordFailure(run: TranslationRun, failure: TranslationFailure, title: String) {
        val now = System.currentTimeMillis()
        val action = translationActionPauseReason(run.profileSnapshotJson, failure)
        val recorded = if (action != null) {
            repository.parkForAction(run.chapterId, run.generation, action, now)
        } else {
            repository.release(run, TranslationRunStatus.FAILED, run.progress, failure.message, now = now)
        }
        if (recorded) notifications.showError(title, failure.message, run.chapterId)
    }

    companion object {
        private const val TAG = "TranslationJob"
        private const val WAKE_TAG = "TranslationWakeJob"

        fun runImmediately(context: Context) {
            val request = OneTimeWorkRequestBuilder<TranslationJob>().addTag(TAG)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            // An enqueue racing the last empty poll is appended, never dropped by KEEP.
            WorkManager.getInstance(context).enqueueUniqueWork(TAG, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        internal fun scheduleWake(context: Context, due: Long?) {
            val manager = WorkManager.getInstance(context)
            if (due == null) {
                manager.cancelUniqueWork(WAKE_TAG)
            } else {
                val request = OneTimeWorkRequestBuilder<TranslationWakeJob>().addTag(WAKE_TAG)
                    .setInitialDelay((due - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).build()
                manager.enqueueUniqueWork(WAKE_TAG, ExistingWorkPolicy.REPLACE, request)
            }
        }

        fun start(context: Context) = runImmediately(context)
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
            WorkManager.getInstance(context).cancelUniqueWork(WAKE_TAG)
        }
        fun isRunning(
            context: Context,
        ): Boolean = WorkManager.getInstance(context).getWorkInfosForUniqueWork(TAG).get().any {
            it.state == WorkInfo.State.RUNNING
        }
        fun isRunningFlow(
            context: Context,
        ): Flow<Boolean> = WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(TAG).asFlow()
            .map { rows -> rows.any { it.state == WorkInfo.State.RUNNING } }
    }
}

/** Sleeps in WorkManager's scheduler, not in a foreground coroutine or an occupied provider slot. */
class TranslationWakeJob(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        TranslationJob.runImmediately(applicationContext)
        return Result.success()
    }
}

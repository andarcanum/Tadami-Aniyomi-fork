package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.data.translation.profile.freezeProfileWire
import eu.kanade.tachiyomi.data.translation.profile.withTranslationProfile
import eu.kanade.tachiyomi.ui.reader.novel.replace.ReplaceRule
import eu.kanade.tachiyomi.ui.reader.novel.replace.replaceRulesFingerprint
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderSettings
import eu.kanade.tachiyomi.ui.reader.novel.translation.NovelReaderTranslationDiskCacheStore
import eu.kanade.tachiyomi.ui.reader.novel.translation.redactTranslationDiagnostic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.data.translation.TranslationRunRepositoryImpl
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.translation.model.TranslationRun
import tachiyomi.domain.translation.profile.ConnectionProfile
import tachiyomi.domain.translation.profile.TranslationProfile
import tachiyomi.domain.translation.profile.TranslationProfileRepository
import tachiyomi.domain.translation.repository.TranslationRunRepository
import tachiyomi.domain.translation.service.TranslationFragmentPlanner
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.domain.translation.model.TranslationBatch as DomainTranslationBatch

/** Android facade over the transactional run repository. No fire-and-forget progress writes. */
class TranslationQueueManager(
    handler: NovelDatabaseHandler = Injekt.get(),
    private val json: Json = Injekt.get(),
    val repository: TranslationRunRepository = TranslationRunRepositoryImpl(handler, json),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val scheduleWork: () -> Unit = { TranslationJob.runImmediately(Injekt.get<android.app.Application>()) },
    private val profileRepository: TranslationProfileRepository? = null,
    private val profileApplication: () -> android.app.Application = { Injekt.get() },
) {
    private val mutationLock = Mutex()

    val queue: StateFlow<List<TranslationQueueItem>> = repository.observeRuns().map { runs ->
        runs.filter {
            !it.status.isTerminal &&
                it.status != tachiyomi.domain.translation.model.TranslationRunStatus.RUNNING
        }.map { it.toQueueItem() }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _activeTranslation = MutableStateFlow<TranslationQueueItem?>(null)
    val activeTranslation: StateFlow<TranslationQueueItem?> = _activeTranslation.asStateFlow()
    private val logs = MutableSharedFlow<TranslationProgressUpdate>(extraBufferCapacity = 64)

    /** Cold durable snapshots give every new subscriber current state; logs alone are ephemeral. */
    val progressUpdates: Flow<TranslationProgressUpdate> get() = observeProgress()

    fun observeProgress(novelId: Long? = null): Flow<TranslationProgressUpdate> = merge(
        flow {
            val previous = mutableMapOf<Long, TranslationQueueItem>()
            repository.observeRuns(novelId).collect { rows ->
                rows.forEach { run ->
                    val item = run.toQueueItem()
                    if (previous.put(item.chapterId, item) != item) emit(item.toProgressUpdate())
                    val active = _activeTranslation.value
                    if (active?.id == item.id && active.generation == item.generation) {
                        _activeTranslation.value = item.takeIf { it.status == TranslationStatus.IN_PROGRESS }
                    }
                }
            }
        },
        logs,
    )

    suspend fun addToQueue(chapterIds: List<Long>, novelId: Long): Int {
        val novel = Injekt.get<GetNovel>().await(novelId) ?: error("Novel not found")
        val preferences = Injekt.get<NovelReaderPreferences>()
        val settings = preferences.resolveSettings(novel.source)
        val rules = preferences.enabledReplaceRules()
        return enqueueChapters(chapterIds, novelId, settings, rules)
    }

    suspend fun enqueueChapters(
        chapterIds: List<Long>,
        novelId: Long,
        settings: NovelReaderSettings,
        rules: List<ReplaceRule>,
        forceRetranslate: Boolean = false,
        priority: Int = 2,
    ): Int {
        val effective = resolveAssignedProfile(settings, novelId)
        val snapshot = effective.toTranslationQueueProfileSnapshot(replaceRulesFingerprint(rules), rules)
        val result = enqueue(snapshot, chapterIds, novelId, "", forceRetranslate, priority)
        if (
            result.conflictingChapterIds.isNotEmpty()
        ) {
            error("A translation with different settings is already queued for this chapter")
        }
        return result.insertedChapterIds.size + result.joinedChapterIds.size
    }

    suspend fun enqueueProfileChapters(
        chapterIds: List<Long>,
        novelId: Long,
        profile: TranslationProfile,
        connection: ConnectionProfile,
        rules: List<ReplaceRule>,
        forceRetranslate: Boolean = false,
        priority: Int = 2,
    ): Int {
        val novel = Injekt.get<GetNovel>().await(novelId) ?: error("Novel not found")
        val settings = Injekt.get<NovelReaderPreferences>().resolveSettings(novel.source)
            .withTranslationProfile(profile, connection).copy(geminiEnabled = true)
        return enqueueChapters(chapterIds, novelId, settings, rules, forceRetranslate, priority)
    }

    private suspend fun resolveAssignedProfile(settings: NovelReaderSettings, novelId: Long): NovelReaderSettings {
        if (settings.translationProfile != null) {
            require(settings.connectionProfile?.id == settings.translationProfile.connectionId) {
                "Selected translation connection is missing"
            }
            return settings
        }
        val catalog = profileRepository ?: return settings
        val profile =
            catalog.resolveProfile(novelId = novelId, sourceId = settings.translationSourceId) ?: return settings
        val connection = catalog.getConnection(profile.connectionId) ?: error("Translation connection is missing")
        return settings.withTranslationProfile(profile, connection)
    }

    internal suspend fun freezeProfileSnapshot(
        settings: NovelReaderSettings,
        novelId: Long,
        rules: List<ReplaceRule>,
    ): TranslationQueueProfileSnapshot = prepareSnapshot(
        resolveAssignedProfile(
            settings,
            novelId,
        ).toTranslationQueueProfileSnapshot(replaceRulesFingerprint(rules), rules),
    )

    private suspend fun prepareSnapshot(snapshot: TranslationQueueProfileSnapshot): TranslationQueueProfileSnapshot {
        if (snapshot.snapshotVersion != 2 || snapshot.systemPromptSnapshot != null) return snapshot
        return withContext(Dispatchers.IO) { snapshot.freezeProfileWire(profileApplication()) }
    }

    suspend fun enqueueTranslationBatch(request: TranslationBatchRequest): TranslationBatchEnqueueResult {
        val rules = request.profileSnapshot.replaceRulesSnapshot
            ?: Injekt.get<NovelReaderPreferences>().enabledReplaceRules()
        val fingerprint = replaceRulesFingerprint(rules)
        if (request.profileSnapshot.replaceRulesFingerprint.isNotEmpty() &&
            request.profileSnapshot.replaceRulesFingerprint != fingerprint
        ) {
            error("Text replacement rules changed; select the batch again")
        }
        var selected = request.profileSnapshot
        if (selected.snapshotVersion < 2 && profileRepository != null) {
            val novel = Injekt.get<GetNovel>().await(request.novelId) ?: error("Novel not found")
            val base = Injekt.get<NovelReaderPreferences>().resolveSettings(novel.source)
            selected = resolveAssignedProfile(selected.toReaderSettings(base), request.novelId)
                .toTranslationQueueProfileSnapshot(fingerprint, rules)
        }
        val snapshot = selected.copy(
            snapshotVersion = if (selected.snapshotVersion == 2) 2 else 1,
            replaceRulesSnapshot = rules,
            replaceRulesFingerprint = fingerprint,
        )
        val token = request.batchToken.ifBlank { java.util.UUID.randomUUID().toString() }
        val result = enqueue(
            snapshot,
            request.chapterIds,
            request.novelId,
            token,
            request.forceRetranslate,
            request.priority,
        )
        return TranslationBatchEnqueueResult(
            batchToken = result.batchToken,
            requestedCount = request.chapterIds.distinct().size,
            enqueuedCount = result.insertedChapterIds.size + result.joinedChapterIds.size,
            skippedAlreadyTranslatedCount = 0,
            conflictingCount = result.conflictingChapterIds.size,
        )
    }

    private suspend fun enqueue(
        snapshot: TranslationQueueProfileSnapshot,
        chapters: List<Long>,
        novelId: Long,
        batchToken: String,
        force: Boolean,
        priority: Int,
    ): tachiyomi.domain.translation.model.TranslationEnqueueResult {
        val frozen = prepareSnapshot(snapshot)
        val encoded = json.encodeToString(frozen)
        val fingerprint = TranslationFragmentPlanner.sourceFingerprint(listOf(encoded))
        return withContext(NonCancellable) {
            mutationLock.withLock {
                val result = repository.enqueue(
                    chapterIds = chapters,
                    novelId = novelId,
                    profileSnapshotJson = encoded,
                    profileFingerprint = fingerprint,
                    batchToken = batchToken,
                    forceRetranslate = force,
                    priority = priority,
                    now = System.currentTimeMillis(),
                    sessionOnly = frozen.geminiDisableCache,
                )
                if (result.insertedChapterIds.isNotEmpty() || result.joinedChapterIds.isNotEmpty()) scheduleWork()
                result
            }
        }
    }

    suspend fun getNextPending(): TranslationQueueItem? = repository.claimNext(
        System.currentTimeMillis(),
    )?.toQueueItem()
    suspend fun hasNext(): Boolean = repository.nextWakeAt() != null
    suspend fun hasPendingOrActive(
        chapterId: Long,
    ): Boolean = repository.getByChapterId(chapterId)?.status?.isTerminal == false
    suspend fun getBatchState(token: String): TranslationBatchState? = repository.getBatch(token)?.toBatchState()
    suspend fun getActiveBatches(): List<TranslationBatchState> =
        repository.getActiveBatches().map { it.toBatchState() }
    suspend fun isBatchPaused(token: String): Boolean = getBatchState(token)?.status == TranslationBatchStatus.PAUSED
    suspend fun pauseBatch(token: String): Boolean = repository.pauseBatch(token, System.currentTimeMillis())
    suspend fun resumeBatch(token: String): Boolean {
        val resumed = repository.resumeBatch(token, System.currentTimeMillis())
        if (resumed) scheduleWork()
        return resumed
    }

    suspend fun pauseImplicitOwner(chapterId: Long, generation: Long): Boolean =
        repository.pauseImplicitOwner(chapterId, generation, System.currentTimeMillis())

    suspend fun resumeImplicitOwner(chapterId: Long, generation: Long): Boolean {
        val resumed = repository.resumeImplicitOwner(chapterId, generation, System.currentTimeMillis())
        if (resumed) scheduleWork()
        return resumed
    }

    suspend fun resumeActionPaused(chapterId: Long, generation: Long): Boolean {
        val resumed = repository.resumeActionPaused(chapterId, generation, System.currentTimeMillis())
        if (resumed) scheduleWork()
        return resumed
    }

    suspend fun detachImplicitOwner(chapterId: Long, generation: Long): Boolean =
        repository.detachImplicitOwner(chapterId, generation, System.currentTimeMillis())

    suspend fun retryOwnedRun(chapterId: Long, generation: Long, force: Boolean = false): Boolean {
        val retried = repository.retryOwnedRun(chapterId, generation, force, System.currentTimeMillis())
        if (retried) scheduleWork()
        return retried
    }
    suspend fun cancelBatch(token: String): Boolean = repository.cancelBatch(token, System.currentTimeMillis())
    suspend fun cancelChapter(
        chapterId: Long,
    ): Boolean = repository.cancelChapter(chapterId, System.currentTimeMillis())
    suspend fun cancelAll(): Boolean = repository.cancelAll(System.currentTimeMillis()) > 0
    suspend fun recoverStaleInProgressRows(): Int = repository.recoverInterrupted(System.currentTimeMillis())

    fun setActiveTranslation(item: TranslationQueueItem?) {
        _activeTranslation.value = item
    }

    /** Named prefetch joins the reader only while that same current generation is still active. */
    suspend fun promotePrefetch(
        snapshot: TranslationQueueProfileSnapshot,
        chapterId: Long,
        novelId: Long,
        token: String,
    ): Boolean {
        val frozen = prepareSnapshot(snapshot)
        val encoded = json.encodeToString(frozen)
        val fingerprint = TranslationFragmentPlanner.sourceFingerprint(listOf(encoded))
        val promoted = repository.promotePrefetchRun(chapterId, token, encoded, fingerprint, System.currentTimeMillis())
        if (promoted) scheduleWork()
        return promoted
    }

    /** Invalidate ownership before deleting artifacts; enqueues cannot overtake an explicit clear. */
    fun clearArtifacts(chapterId: Long? = null) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutationLock.withLock {
                if (chapterId == null) {
                    repository.invalidateAllCheckpoints(System.currentTimeMillis())
                    NovelReaderTranslationDiskCacheStore.clear()
                    TranslationSessionResults.store.clearAll()
                } else {
                    repository.invalidateCheckpoint(chapterId, System.currentTimeMillis())
                    NovelReaderTranslationDiskCacheStore.remove(chapterId)
                    TranslationSessionResults.store.clearChapter(chapterId)
                }
            }
        }
    }

    /** Cleanup remains live after the reader scope is disposed. */
    fun launchCleanup(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    suspend fun emitRunLog(run: TranslationRun, message: String) {
        val current = repository.getByChapterId(run.chapterId) ?: return
        if (current.id != run.id || current.generation != run.generation) return
        logs.emit(current.toQueueItem().toProgressUpdate().copy(logMessage = redactTranslationDiagnostic(message)))
    }
}

internal fun TranslationRun.toQueueItem(): TranslationQueueItem = TranslationQueueItem(
    id, chapterId, novelId, TranslationStatus.entries[status.ordinal], progress, errorMessage, retryCount,
    createdAt, updatedAt, batchToken, batchOrder, profileSnapshotJson,
    generation = generation, nextAttemptAt = nextAttemptAt, sourceFingerprint = sourceFingerprint,
)

internal fun TranslationQueueItem.toProgressUpdate(): TranslationProgressUpdate = TranslationProgressUpdate(
    chapterId, novelId, status, progress, 0, 0, "", errorMessage,
    generation = generation, nextAttemptAt = nextAttemptAt, sourceFingerprint = sourceFingerprint,
)

internal fun DomainTranslationBatch.toBatchState(): TranslationBatchState = TranslationBatchState(
    batchToken, novelId, TranslationBatchStatus.entries[status.ordinal], total, enqueued, skipped, completed, failed,
    lastSuccessfulChapterId, createdAt, updatedAt, cancelled = cancelled,
)

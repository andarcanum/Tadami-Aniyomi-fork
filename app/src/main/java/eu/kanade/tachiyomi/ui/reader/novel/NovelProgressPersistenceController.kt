package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState
import eu.kanade.domain.track.novel.interactor.TrackNovelChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.category.novel.interactor.GetNovelCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.items.novelchapter.model.NovelChapterUpdate
import tachiyomi.domain.items.novelchapter.repository.NovelChapterRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Host the progress persistence controller uses to reach the shared reader state owned by
 * [NovelReaderScreenModel].
 */
internal interface NovelProgressPersistenceHost {
    val progressScope: CoroutineScope

    fun progressCurrentChapterId(): Long?
    fun progressCurrentNovel(): Novel?
    fun progressOrderedChapters(): List<NovelChapter> = emptyList()
}

/**
 * A single pending chapter-progress write, coalesced per chapter before it reaches the DB.
 */
data class PendingProgressPersistence(
    val chapterId: Long,
    val novelId: Long,
    val chapterNumber: Int,
    val read: Boolean,
    val lastPageRead: Long,
    val emitReadEvent: Boolean,
    val emitNovelCompleted: Boolean,
    val sessionReadDurationMs: Long,
) {
    fun merge(other: PendingProgressPersistence): PendingProgressPersistence {
        require(chapterId == other.chapterId) {
            "Pending progress persistence can only merge updates for the same chapter"
        }
        return copy(
            read = other.read,
            lastPageRead = other.lastPageRead,
            emitReadEvent = emitReadEvent || other.emitReadEvent,
            emitNovelCompleted = emitNovelCompleted || other.emitNovelCompleted,
            sessionReadDurationMs = maxOf(sessionReadDurationMs, other.sessionReadDurationMs),
        )
    }
}

/**
 * Reading-order slot resolution for "After Reading Automatically Delete" (manga parity:
 * ReaderViewModel.deleteChapterIfNeeded): when the chapter [completedChapterId] is finished,
 * the chapter [slots] positions behind it is the one whose download gets removed.
 */
internal fun resolveSlotsDeleteTargetId(
    orderedChapters: List<NovelChapter>,
    completedChapterId: Long,
    slots: Int,
): Long? {
    if (slots < 0) return null
    val index = orderedChapters.indexOfFirst { it.id == completedChapterId }
    if (index < 0) return null
    return orderedChapters.getOrNull(index - slots)?.id
}

/**
 * Chapter progress + reading-history persistence subsystem.
 *
 * Owns the pending-progress coalescing queue, the bounded flush pipeline and the per-session
 * history snapshots. The [NovelReaderScreenModel] hosts the shared reader state through
 * [NovelProgressPersistenceHost] and forwards the reader's persistence calls here.
 */
internal class NovelProgressPersistenceController(
    private val host: NovelProgressPersistenceHost,
    private val novelChapterRepository: NovelChapterRepository,
    private val getIncognitoState: GetNovelIncognitoState,
    private val eventBus: AchievementEventBus?,
    private val activityDataRepository: ActivityDataRepository,
    private val historyRepository: NovelHistoryRepository?,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val getNovelCategories: GetNovelCategories = Injekt.get(),
    private val novelDownloadManager: eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager? = null,
) {

    private var chapterReadStartTimeMs: Long = System.currentTimeMillis()
    private var pendingHistoryReadDurationMs: Long = 0L
    private val progressPersistenceMutex = Mutex()
    private val pendingProgressPersistenceByChapterId = linkedMapOf<Long, PendingProgressPersistence>()
    private var progressPersistenceJob: Job? = null

    @Volatile
    private var progressPersistenceScheduled = false

    private val resolvedHistoryRepository by lazy {
        historyRepository ?: runCatching { Injekt.get<NovelHistoryRepository>() }.getOrNull()
    }

    /** Restarts the session read timer (chapter anchor moved or a new chapter was loaded). */
    fun resetSessionReadTimer() {
        chapterReadStartTimeMs = System.currentTimeMillis()
    }

    /** Milliseconds elapsed since the session read timer was last reset. */
    fun sessionReadDurationMs(): Long = System.currentTimeMillis() - chapterReadStartTimeMs

    fun enqueueProgressPersistence(update: PendingProgressPersistence) {
        progressPersistenceScheduled = true
        // NonCancellable must wrap the block via withContext — passing it to launch() is
        // deprecated and breaks structured concurrency (will become an error).
        host.progressScope.launch {
            withContext(NonCancellable) {
                progressPersistenceMutex.withLock {
                    pendingProgressPersistenceByChapterId[update.chapterId] =
                        pendingProgressPersistenceByChapterId[update.chapterId]?.merge(update) ?: update
                    if (progressPersistenceJob?.isActive == true) {
                        return@withLock
                    }
                    progressPersistenceJob = host.progressScope.launch {
                        withContext(NonCancellable) {
                            try {
                                flushPendingProgressPersistence()
                            } finally {
                                progressPersistenceMutex.withLock {
                                    progressPersistenceJob = null
                                    progressPersistenceScheduled =
                                        pendingProgressPersistenceByChapterId.isNotEmpty()
                                }
                                // The flush may have stopped at its per-run budget while the reader
                                // is still streaming positions; drain the rest instead of leaving the
                                // tail until the next enqueue.
                                if (progressPersistenceScheduled) {
                                    scheduleProgressPersistenceFlush()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun awaitPendingProgressPersistence() {
        while (true) {
            val activeJob = progressPersistenceMutex.withLock {
                progressPersistenceJob?.takeIf { it.isActive }
            }
            if (activeJob != null) {
                activeJob.join()
                continue
            }
            if (!progressPersistenceScheduled) return
            yield()
        }
    }

    suspend fun persistCurrentChapterExitState() {
        val chapterId = host.progressCurrentChapterId() ?: return
        val finalReadDurationMs = (System.currentTimeMillis() - chapterReadStartTimeMs).coerceAtLeast(0L)
        awaitPendingProgressPersistence()
        flushPendingHistorySnapshot(
            chapterId = chapterId,
            additionalReadDurationMs = finalReadDurationMs,
        )
    }

    /**
     * Drains pending progress writes, bounded per run.
     *
     * A reader that streams positions can keep enqueueing while this loop runs; without a budget the
     * loop never yielded and held the DB and history writer busy for the whole session. One pass
     * flushes at most [MAX_PENDING_PROGRESS_FLUSH_PER_RUN] chapters and lets the caller reschedule
     * the tail (see [scheduleProgressPersistenceFlush]).
     */
    private suspend fun flushPendingProgressPersistence() {
        var flushed = 0
        while (flushed < MAX_PENDING_PROGRESS_FLUSH_PER_RUN) {
            val nextUpdate = progressPersistenceMutex.withLock {
                val iterator = pendingProgressPersistenceByChapterId.entries.iterator()
                if (!iterator.hasNext()) return
                val next = iterator.next().value
                iterator.remove()
                next
            }
            flushed += 1

            val currentNovel = host.progressCurrentNovel()
            if (getIncognitoState.shouldPauseHistory(currentNovel?.source, currentNovel?.favorite == true)) {
                return
            }

            novelChapterRepository.updateChapter(
                NovelChapterUpdate(
                    id = nextUpdate.chapterId,
                    read = nextUpdate.read,
                    lastPageRead = nextUpdate.lastPageRead,
                ),
            )

            if (nextUpdate.read) {
                maybeDeleteReadChapterDownloads(nextUpdate)
            }

            val sessionDuration = nextUpdate.sessionReadDurationMs.coerceAtLeast(0L)
            if (nextUpdate.emitReadEvent) {
                runCatching {
                    if (eu.kanade.domain.easteregg.aurora.AuroraNight.isVeilThin()) {
                        val manager = Injekt.get<eu.kanade.domain.easteregg.aurora.AuroraHeartManager>()
                        manager.registerNightAction()
                    }
                }
                eventBus?.tryEmit(
                    AchievementEvent.NovelChapterRead(
                        novelId = nextUpdate.novelId,
                        chapterNumber = nextUpdate.chapterNumber,
                    ),
                )
                if (nextUpdate.emitNovelCompleted) {
                    eventBus?.tryEmit(AchievementEvent.NovelCompleted(nextUpdate.novelId))
                }
                activityDataRepository.recordReading(
                    id = nextUpdate.chapterId,
                    chaptersCount = 1,
                    durationMs = sessionDuration,
                )
                if (Injekt.get<eu.kanade.domain.track.service.TrackPreferences>().autoUpdateTrack().get()) {
                    val context = Injekt.get<Application>()
                    Injekt.get<TrackNovelChapter>().await(
                        context,
                        nextUpdate.novelId,
                        nextUpdate.chapterNumber.toDouble(),
                    )
                }
            } else if (sessionDuration > 0L) {
                activityDataRepository.recordReading(
                    id = nextUpdate.chapterId,
                    chaptersCount = 0,
                    durationMs = sessionDuration,
                )
            }

            val now = System.currentTimeMillis()
            pendingHistoryReadDurationMs += nextUpdate.sessionReadDurationMs.coerceAtLeast(0L)
            if (nextUpdate.emitReadEvent) {
                flushPendingHistorySnapshot(nextUpdate.chapterId)
            }
            chapterReadStartTimeMs = now
        }
    }

    /** Starts another bounded flush when the previous one stopped at its per-run budget. */
    private suspend fun scheduleProgressPersistenceFlush() {
        progressPersistenceMutex.withLock {
            if (progressPersistenceJob?.isActive == true) return
            if (pendingProgressPersistenceByChapterId.isEmpty()) return
            progressPersistenceJob = host.progressScope.launch {
                withContext(NonCancellable) {
                    try {
                        flushPendingProgressPersistence()
                    } finally {
                        progressPersistenceMutex.withLock {
                            progressPersistenceJob = null
                            progressPersistenceScheduled = pendingProgressPersistenceByChapterId.isNotEmpty()
                        }
                        if (progressPersistenceScheduled) {
                            scheduleProgressPersistenceFlush()
                        }
                    }
                }
            }
        }
    }

    suspend fun saveHistorySnapshot(chapterId: Long, sessionReadDurationMs: Long) {
        val currentNovel = host.progressCurrentNovel()
        if (getIncognitoState.shouldPauseHistory(currentNovel?.source, currentNovel?.favorite == true)) {
            return
        }
        runCatching {
            resolvedHistoryRepository?.upsertNovelHistory(
                NovelHistoryUpdate(
                    chapterId = chapterId,
                    readAt = Date(),
                    sessionReadDuration = sessionReadDurationMs.coerceAtLeast(0L),
                ),
            )
        }.onFailure { error ->
            logcat(LogPriority.ERROR, error) { "Failed to save novel history snapshot" }
        }
    }

    suspend fun flushPendingHistorySnapshot(
        chapterId: Long,
        additionalReadDurationMs: Long = 0L,
    ) {
        val readDurationMs = (pendingHistoryReadDurationMs + additionalReadDurationMs).coerceAtLeast(0L)
        if (readDurationMs <= 0L) return
        pendingHistoryReadDurationMs = 0L
        saveHistorySnapshot(chapterId, readDurationMs)
    }

    /**
     * Reader-side auto-cleanup: honors "Remove when marked as read" (delete the finished
     * chapter download) and "After Reading Automatically Delete" slots (delete the chapter
     * N behind in reading order), with the same bookmark/category protections as the screen.
     */
    private suspend fun maybeDeleteReadChapterDownloads(update: PendingProgressPersistence) {
        val novel = host.progressCurrentNovel() ?: return
        val removeMarkedRead = downloadPreferences.removeAfterMarkedAsRead().get()
        val slots = downloadPreferences.removeAfterReadSlots().get()
        if (!removeMarkedRead && slots == -1) return
        val ordered = host.progressOrderedChapters()
        val completed = ordered.firstOrNull { it.id == update.chapterId } ?: return
        val targetIds = mutableSetOf<Long>()
        if (removeMarkedRead) targetIds += completed.id
        resolveSlotsDeleteTargetId(ordered, completed.id, slots)?.let { targetIds += it }
        if (targetIds.isEmpty()) return
        val allowBookmarked = downloadPreferences.removeBookmarkedChapters().get()
        val excludedCategories = downloadPreferences.removeExcludeNovelCategories().get().mapNotNull {
            it.toLongOrNull()
        }
        if (excludedCategories.isNotEmpty()) {
            val novelCategoryIds = getNovelCategories.await(novel.id).mapTo(mutableSetOf()) { it.id }
            if (novelCategoryIds.intersect(excludedCategories).isNotEmpty()) return
        }
        val deletable = ordered.filter { it.id in targetIds && (allowBookmarked || !it.bookmark) }
        if (deletable.isEmpty()) return
        novelDownloadManager?.deleteChapters(novel, deletable.mapTo(mutableSetOf()) { it.id })
    }

    /** Cancels the flush pipeline and drops pending writes (screen model disposal). */
    fun dispose() {
        progressPersistenceJob?.cancel()
        pendingProgressPersistenceByChapterId.clear()
        progressPersistenceScheduled = false
    }

    companion object {
        /**
         * Upper bound of chapters one progress-flush run writes before yielding to the caller.
         * Streaming readers can enqueue positions faster than the DB drains them; without the budget
         * the flush loop held the persistence pipeline busy for the whole reading session.
         */
        private const val MAX_PENDING_PROGRESS_FLUSH_PER_RUN = 16
    }
}

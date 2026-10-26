package eu.kanade.tachiyomi.ui.reader.novel

import android.app.Application
import eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState
import eu.kanade.domain.track.novel.interactor.TrackNovelChapter
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import uy.kohesive.injekt.api.addSingleton

class NovelProgressPersistenceControllerTest {

    private val preferenceStore = PersistingPreferenceStore()
    private val downloadPreferences = DownloadPreferences(preferenceStore)
    private val trackPreferences = TrackPreferences(preferenceStore)
    private val getNovelCategories = mockk<GetNovelCategories>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Injekt.addSingleton(downloadPreferences)
        Injekt.addSingleton(trackPreferences)
        Injekt.addSingleton(getNovelCategories)
        Injekt.addSingleton(mockk<Application>(relaxed = true))
        Injekt.addSingleton(mockk<TrackNovelChapter>(relaxed = true))
    }

    private class TestHost(
        override val progressScope: CoroutineScope,
        var currentChapterId: Long? = 1L,
        var currentNovel: Novel? = Novel.create().copy(id = 42L, source = 7L, favorite = false),
        var orderedChapters: List<NovelChapter> = emptyList(),
    ) : NovelProgressPersistenceHost {
        override fun progressCurrentChapterId(): Long? = currentChapterId
        override fun progressCurrentNovel(): Novel? = currentNovel
        override fun progressOrderedChapters(): List<NovelChapter> = orderedChapters
    }

    @Test
    fun `blocked first write coalesces session read duration to 150ms instead of summing to 250ms`() = runTest {
        var testClock = 0L
        val firstWriteBlocked = CompletableDeferred<Unit>()
        var firstWriteStarted = false

        val updatedChapters = mutableListOf<NovelChapterUpdate>()
        val chapterRepository = mockk<NovelChapterRepository> {
            coEvery { updateChapter(any()) } coAnswers {
                val update = firstArg<NovelChapterUpdate>()
                synchronized(updatedChapters) { updatedChapters.add(update) }
                if (!firstWriteStarted) {
                    firstWriteStarted = true
                    firstWriteBlocked.await()
                }
            }
        }

        val activityReadings = mutableListOf<Triple<Long, Int, Long>>()
        val activityDataRepository = mockk<ActivityDataRepository> {
            coEvery { recordReading(any(), any(), any()) } answers {
                val id = firstArg<Long>()
                val count = secondArg<Int>()
                val duration = thirdArg<Long>()
                synchronized(activityReadings) { activityReadings.add(Triple(id, count, duration)) }
            }
        }

        val historyUpdates = mutableListOf<NovelHistoryUpdate>()
        val historyRepository = mockk<NovelHistoryRepository> {
            coEvery { upsertNovelHistory(any()) } answers {
                val update = firstArg<NovelHistoryUpdate>()
                synchronized(historyUpdates) { historyUpdates.add(update) }
            }
        }

        val incognitoState = mockk<GetNovelIncognitoState> {
            coEvery { shouldPauseHistory(any(), any()) } returns false
        }

        val hostScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val host = TestHost(progressScope = hostScope, currentChapterId = 1L)

        val controller = NovelProgressPersistenceController(
            host = host,
            novelChapterRepository = chapterRepository,
            getIncognitoState = incognitoState,
            eventBus = null,
            activityDataRepository = activityDataRepository,
            historyRepository = historyRepository,
            downloadPreferences = downloadPreferences,
            getNovelCategories = getNovelCategories,
            clockMs = { testClock },
        )

        // Capture 100 at 100
        testClock = 100L
        val d1 = controller.takeSessionReadDurationMs()
        d1 shouldBe 100L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 10L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = d1,
            ),
        )

        // Capture 50 at 150 while first write is still blocked
        testClock = 150L
        val d2 = controller.takeSessionReadDurationMs()
        d2 shouldBe 50L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = true,
                lastPageRead = 20L,
                emitReadEvent = true,
                emitNovelCompleted = false,
                sessionReadDurationMs = d2,
            ),
        )

        // Release first write and wait for completion
        firstWriteBlocked.complete(Unit)
        controller.awaitPendingProgressPersistence()

        val totalActivityDuration = activityReadings.filter { it.first == 1L }.sumOf { it.third }
        totalActivityDuration shouldBe 150L

        val totalHistoryDuration = historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        totalHistoryDuration shouldBe 150L
    }

    @Test
    fun `coalesced 20 plus 30 plus 40 equals 90 and newest page, event once`() = runTest {
        var testClock = 0L
        val writeBlocked = CompletableDeferred<Unit>()
        var writeStarted = false

        val updatedChapters = mutableListOf<NovelChapterUpdate>()
        val chapterRepository = mockk<NovelChapterRepository> {
            coEvery { updateChapter(any()) } coAnswers {
                val update = firstArg<NovelChapterUpdate>()
                synchronized(updatedChapters) { updatedChapters.add(update) }
                if (!writeStarted) {
                    writeStarted = true
                    writeBlocked.await()
                }
            }
        }

        val activityReadings = mutableListOf<Triple<Long, Int, Long>>()
        val activityDataRepository = mockk<ActivityDataRepository> {
            coEvery { recordReading(any(), any(), any()) } answers {
                val id = firstArg<Long>()
                val count = secondArg<Int>()
                val duration = thirdArg<Long>()
                synchronized(activityReadings) { activityReadings.add(Triple(id, count, duration)) }
            }
        }

        val historyUpdates = mutableListOf<NovelHistoryUpdate>()
        val historyRepository = mockk<NovelHistoryRepository> {
            coEvery { upsertNovelHistory(any()) } answers {
                val update = firstArg<NovelHistoryUpdate>()
                synchronized(historyUpdates) { historyUpdates.add(update) }
            }
        }

        val eventsEmitted = mutableListOf<AchievementEvent>()
        val eventBus = mockk<AchievementEventBus> {
            coEvery { tryEmit(any()) } answers {
                val event = firstArg<AchievementEvent>()
                synchronized(eventsEmitted) { eventsEmitted.add(event) }
                true
            }
        }

        val incognitoState = mockk<GetNovelIncognitoState> {
            coEvery { shouldPauseHistory(any(), any()) } returns false
        }

        val hostScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val host = TestHost(progressScope = hostScope, currentChapterId = 1L)

        val controller = NovelProgressPersistenceController(
            host = host,
            novelChapterRepository = chapterRepository,
            getIncognitoState = incognitoState,
            eventBus = eventBus,
            activityDataRepository = activityDataRepository,
            historyRepository = historyRepository,
            downloadPreferences = downloadPreferences,
            getNovelCategories = getNovelCategories,
            clockMs = { testClock },
        )

        testClock = 20L
        val d1 = controller.takeSessionReadDurationMs()
        d1 shouldBe 20L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 5L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = d1,
            ),
        )

        testClock = 50L
        val d2 = controller.takeSessionReadDurationMs()
        d2 shouldBe 30L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 10L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = d2,
            ),
        )

        testClock = 90L
        val d3 = controller.takeSessionReadDurationMs()
        d3 shouldBe 40L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = true,
                lastPageRead = 15L,
                emitReadEvent = true,
                emitNovelCompleted = false,
                sessionReadDurationMs = d3,
            ),
        )

        writeBlocked.complete(Unit)
        controller.awaitPendingProgressPersistence()

        val totalActivityDuration = activityReadings.filter { it.first == 1L }.sumOf { it.third }
        totalActivityDuration shouldBe 90L

        val totalHistoryDuration = historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        totalHistoryDuration shouldBe 90L

        updatedChapters.last().lastPageRead shouldBe 15L
        eventsEmitted.filterIsInstance<AchievementEvent.NovelChapterRead>().size shouldBe 1
    }

    @Test
    fun `exit at 180 yields history 180, repeat exit adds nothing, and cancellation retains tail`() = runTest {
        var testClock = 0L

        val chapterRepository = mockk<NovelChapterRepository>(relaxed = true)
        val activityDataRepository = mockk<ActivityDataRepository>(relaxed = true)

        val historyUpdates = mutableListOf<NovelHistoryUpdate>()
        val historyRepository = mockk<NovelHistoryRepository> {
            coEvery { upsertNovelHistory(any()) } answers {
                val update = firstArg<NovelHistoryUpdate>()
                synchronized(historyUpdates) { historyUpdates.add(update) }
            }
        }

        val incognitoState = mockk<GetNovelIncognitoState> {
            coEvery { shouldPauseHistory(any(), any()) } returns false
        }

        val hostScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val host = TestHost(progressScope = hostScope, currentChapterId = 1L)

        val controller = NovelProgressPersistenceController(
            host = host,
            novelChapterRepository = chapterRepository,
            getIncognitoState = incognitoState,
            eventBus = null,
            activityDataRepository = activityDataRepository,
            historyRepository = historyRepository,
            downloadPreferences = downloadPreferences,
            getNovelCategories = getNovelCategories,
            clockMs = { testClock },
        )

        testClock = 150L
        val d1 = controller.takeSessionReadDurationMs()
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 10L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = d1,
            ),
        )
        controller.awaitPendingProgressPersistence()

        // Clock moves to 180 and exit is requested
        testClock = 180L
        controller.persistCurrentChapterExitState()

        val totalHistoryAfterFirstExit = historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        totalHistoryAfterFirstExit shouldBe 180L

        // Repeat exit at clock 180 adds nothing
        val countBeforeRepeat = historyUpdates.size
        controller.persistCurrentChapterExitState()
        historyUpdates.size shouldBe countBeforeRepeat

        // Cancellation during await does not lose queued tail
        val blockDb = CompletableDeferred<Unit>()
        coEvery { chapterRepository.updateChapter(any()) } coAnswers {
            blockDb.await()
        }
        testClock = 200L
        val exitJob = hostScope.launch {
            controller.persistCurrentChapterExitState()
        }
        exitJob.cancel()
        blockDb.complete(Unit)
        exitJob.join()

        val totalHistoryAfterCancelledJob =
            historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        totalHistoryAfterCancelledJob shouldBe 200L
    }

    @Test
    fun `chapter transition tails and residual buckets attribute durations to correct chapters`() = runTest {
        var testClock = 0L

        val chapterRepository = mockk<NovelChapterRepository>(relaxed = true)
        val activityDataRepository = mockk<ActivityDataRepository>(relaxed = true)

        val historyUpdates = mutableListOf<NovelHistoryUpdate>()
        val historyRepository = mockk<NovelHistoryRepository> {
            coEvery { upsertNovelHistory(any()) } answers {
                val update = firstArg<NovelHistoryUpdate>()
                synchronized(historyUpdates) { historyUpdates.add(update) }
            }
        }

        val incognitoState = mockk<GetNovelIncognitoState> {
            coEvery { shouldPauseHistory(any(), any()) } returns false
        }

        val hostScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val host = TestHost(progressScope = hostScope, currentChapterId = 1L)

        val controller = NovelProgressPersistenceController(
            host = host,
            novelChapterRepository = chapterRepository,
            getIncognitoState = incognitoState,
            eventBus = null,
            activityDataRepository = activityDataRepository,
            historyRepository = historyRepository,
            downloadPreferences = downloadPreferences,
            getNovelCategories = getNovelCategories,
            clockMs = { testClock },
        )

        // A reads 10
        testClock = 10L
        val d1 = controller.takeSessionReadDurationMs()
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 1L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = d1,
            ),
        )

        // Transition from A to B: transition tail of A (10) queued to A
        testClock = 20L
        val tailA = controller.takeSessionReadDurationMs()
        tailA shouldBe 10L
        controller.queueHistoryReadDuration(1L, tailA)
        host.currentChapterId = 2L

        // B reads 20
        testClock = 40L
        val dB = controller.takeSessionReadDurationMs()
        dB shouldBe 20L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 2L,
                novelId = 42L,
                chapterNumber = 2,
                read = false,
                lastPageRead = 1L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = dB,
            ),
        )

        // Return to A: B transition tail 0, switch to A
        host.currentChapterId = 1L
        testClock = 70L
        val dA2 = controller.takeSessionReadDurationMs()
        dA2 shouldBe 30L
        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = false,
                lastPageRead = 2L,
                emitReadEvent = false,
                emitNovelCompleted = false,
                sessionReadDurationMs = dA2,
            ),
        )

        // Exit drains residual buckets for both A and B
        controller.persistCurrentChapterExitState()

        val totalA = historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        val totalB = historyUpdates.filter { it.chapterId == 2L }.sumOf { it.sessionReadDuration }

        totalA shouldBe 50L // 10 (d1) + 10 (tailA) + 30 (dA2)
        totalB shouldBe 20L // 20 (dB)
    }

    @Test
    fun `incognito cases no persistence and toggling does not carry over private interval`() = runTest {
        var testClock = 0L
        var incognito = true

        val chapterRepository = mockk<NovelChapterRepository>(relaxed = true)
        val activityDataRepository = mockk<ActivityDataRepository>(relaxed = true)
        val historyUpdates = mutableListOf<NovelHistoryUpdate>()
        val historyRepository = mockk<NovelHistoryRepository> {
            coEvery { upsertNovelHistory(any()) } answers {
                val update = firstArg<NovelHistoryUpdate>()
                synchronized(historyUpdates) { historyUpdates.add(update) }
            }
        }

        val incognitoState = mockk<GetNovelIncognitoState> {
            coEvery { shouldPauseHistory(any(), any()) } answers { incognito }
        }

        val hostScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val host = TestHost(progressScope = hostScope, currentChapterId = 1L)

        val controller = NovelProgressPersistenceController(
            host = host,
            novelChapterRepository = chapterRepository,
            getIncognitoState = incognitoState,
            eventBus = null,
            activityDataRepository = activityDataRepository,
            historyRepository = historyRepository,
            downloadPreferences = downloadPreferences,
            getNovelCategories = getNovelCategories,
            clockMs = { testClock },
        )

        // During incognito: takeSessionReadDurationMs returns 0
        testClock = 100L
        val privateDelta = controller.takeSessionReadDurationMs()
        privateDelta shouldBe 0L

        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = true,
                lastPageRead = 10L,
                emitReadEvent = true,
                emitNovelCompleted = false,
                sessionReadDurationMs = privateDelta,
            ),
        )
        controller.awaitPendingProgressPersistence()
        historyUpdates.isEmpty() shouldBe true

        // Toggle incognito OFF: resetSessionReadTimer resets timer to current clock
        incognito = false
        controller.resetSessionReadTimer()

        // Read 50 ms after toggle
        testClock = 150L
        val recordingDelta = controller.takeSessionReadDurationMs()
        recordingDelta shouldBe 50L // Not 150L!

        controller.enqueueProgressPersistence(
            PendingProgressPersistence(
                chapterId = 1L,
                novelId = 42L,
                chapterNumber = 1,
                read = true,
                lastPageRead = 20L,
                emitReadEvent = true,
                emitNovelCompleted = false,
                sessionReadDurationMs = recordingDelta,
            ),
        )
        controller.awaitPendingProgressPersistence()

        val totalHistory = historyUpdates.filter { it.chapterId == 1L }.sumOf { it.sessionReadDuration }
        totalHistory shouldBe 50L
    }
}

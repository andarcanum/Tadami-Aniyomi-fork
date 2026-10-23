package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DiscoveryUpdateJobTest {

    @Test
    fun `unexpected runner failure makes manual worker fail`() = runTest {
        val runner = DiscoveryRunner(
            repository = mockk(relaxed = true),
            preferences = preferences,
            seedSources = object : DiscoverySeedSources {
                override suspend fun candidates(mediaType: DiscoveryMediaType): List<DiscoverySeedInput> =
                    throw IllegalStateException("storage failed")
                override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
            },
        )
        Injekt.addSingleton(runner)
        coEvery { any<CoroutineWorker>().setForegroundSafely() } just Runs
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.id } returns UUID.randomUUID()
        every { parameters.tags } returns setOf(DiscoveryUpdateJob.TAG_MANUAL)
        every { parameters.inputData } returns Data.Builder().putString("target_media_type", "novel").build()
        val worker = DiscoveryUpdateJob(RuntimeEnvironment.getApplication(), parameters)

        worker.doWork() shouldBe androidx.work.ListenableWorker.Result.failure()
    }

    @Test
    fun `media notifications have distinct ids without incognito collision`() {
        val ids = DiscoveryMediaType.entries.map { Notifications.discoveryProgressId(it) }
        ids.toSet().size shouldBe 3
        ids.contains(Notifications.ID_INCOGNITO_MODE) shouldBe false
    }

    @Test
    fun `cancel broadcasts preserve independent media targets`() {
        val application = RuntimeEnvironment.getApplication()
        val pending = DiscoveryMediaType.entries.map {
            NotificationReceiver.cancelDiscoveryUpdatePendingBroadcast(application, it)
        }

        pending.toSet().size shouldBe 3
        pending.map {
            val extras = shadowOf(it).savedIntent.extras!!
            extras.getString(extras.keySet().single { key -> key.endsWith("EXTRA_DISCOVERY_MEDIA_TYPE") })
        } shouldBe
            listOf("anime", "manga", "novel")
    }

    @Test
    fun `notifier cancellation preserves other media and incognito notifications`() {
        unmockkConstructor(DiscoveryNotifier::class)
        val application = RuntimeEnvironment.getApplication()
        val manager = application.getSystemService(NotificationManager::class.java)
        val ids = listOf(-711, -712, -713, -701)
        ids.forEach { manager.notify(it, Notification()) }

        DiscoveryNotifier(application).cancel(DiscoveryMediaType.ANIME)

        shadowOf(manager).activeNotifications.map { it.id }.toSet() shouldBe setOf(-712, -713, -701)
        DiscoveryNotifier(application).cancel()
        shadowOf(manager).activeNotifications.map { it.id }.toSet() shouldBe setOf(-701)
    }

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private lateinit var preferences: DiscoveryPreferences
    private val requests = mutableMapOf<String, OneTimeWorkRequest>()

    @Before
    fun setUp() {
        preferences = DiscoveryPreferences(PersistingPreferenceStore())
        Injekt.addSingleton(preferences)
        preferences = Injekt.get()
        DiscoveryMediaType.entries.forEach { preferences.manualRefreshWorkId(it).delete() }
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            requests[firstArg()] = thirdArg()
            mockk(relaxed = true)
        }
        mockkConstructor(DiscoveryNotifier::class)
        every { anyConstructed<DiscoveryNotifier>().cancel() } just Runs
        every { anyConstructed<DiscoveryNotifier>().cancel(any()) } just Runs
    }

    @After
    fun tearDown() {
        DiscoveryProgressTracker.reset()
        unmockkAll()
    }

    @Test
    fun `targeted refresh only enqueues a request with its media tag and input`() {
        DiscoveryUpdateJob.refreshNow(context, DiscoveryMediaType.NOVEL)

        requests.keys shouldBe setOf("DiscoveryUpdateManual:novel")
        val request = requests.getValue("DiscoveryUpdateManual:novel")
        request.workSpec.input.getString("target_media_type") shouldBe "novel"
        request.tags.contains("DiscoveryUpdateManual:novel") shouldBe true
        request.tags.contains("DiscoveryUpdateManual") shouldBe true
        request.tags.contains("DiscoveryUpdateManual:anime") shouldBe false
    }

    @Test
    fun `unscoped refresh fans out into independent targeted requests`() {
        val result = DiscoveryUpdateJob.refreshNow(context)

        requests.keys shouldBe setOf(
            "DiscoveryUpdateManual:anime",
            "DiscoveryUpdateManual:manga",
            "DiscoveryUpdateManual:novel",
        )
        requests.values.map { it.id }.toSet().size shouldBe 3
        result.keys shouldBe DiscoveryMediaType.entries.toSet()
        listOf("anime", "manga", "novel").forEach { key ->
            val request = requests.getValue("DiscoveryUpdateManual:$key")
            request.workSpec.input.getString("target_media_type") shouldBe key
            request.tags.contains("DiscoveryUpdateManual:$key") shouldBe true
        }
    }

    @Test
    fun `refresh returns and persists latest work UUID only for target media`() {
        val first = DiscoveryUpdateJob.refreshNow(context, DiscoveryMediaType.NOVEL)
        val second = DiscoveryUpdateJob.refreshNow(context, DiscoveryMediaType.NOVEL)

        first.keys shouldBe setOf(DiscoveryMediaType.NOVEL)
        (first.getValue(DiscoveryMediaType.NOVEL) != second.getValue(DiscoveryMediaType.NOVEL)) shouldBe true
        second.getValue(DiscoveryMediaType.NOVEL) shouldBe requests.getValue("DiscoveryUpdateManual:novel").id
        preferences.manualRefreshWorkId(DiscoveryMediaType.NOVEL).get() shouldBe
            second.getValue(DiscoveryMediaType.NOVEL).toString()
        preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).get() shouldBe ""
    }

    @Test
    fun `scoped stop clears only target shared cooldown for notification cancellation`() {
        DiscoveryMediaType.entries.forEach { preferences.clearManualRefreshAt(it) }
        preferences.tryStartManualRefresh(DiscoveryMediaType.ANIME, 1_000_000L) shouldBe true
        preferences.tryStartManualRefresh(DiscoveryMediaType.MANGA, 2_000_000L) shouldBe true
        preferences.tryStartManualRefresh(DiscoveryMediaType.NOVEL, 3_000_000L) shouldBe true

        DiscoveryUpdateJob.stop(context, DiscoveryMediaType.NOVEL)

        preferences.manualRefreshAt(DiscoveryMediaType.NOVEL).get() shouldBe 0L
        preferences.manualRefreshAt(DiscoveryMediaType.ANIME).get() shouldBe 1_000_000L
        preferences.manualRefreshAt(DiscoveryMediaType.MANGA).get() shouldBe 2_000_000L
    }

    @Test
    fun `scoped stop cancels only that media work`() {
        DiscoveryUpdateJob.stop(context, DiscoveryMediaType.NOVEL)

        verify(exactly = 1) { workManager.cancelUniqueWork("DiscoveryUpdateManual:novel") }
        verify(exactly = 0) { workManager.cancelUniqueWork("DiscoveryUpdateManual:anime") }
        verify(exactly = 0) { workManager.cancelUniqueWork("DiscoveryUpdateManual:manga") }
        verify(exactly = 0) { workManager.cancelAllWorkByTag(any()) }
    }

    @Test
    fun `scoped stop preserves runner progress and cancels only selected notification`() {
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.NOVEL, stageText = "Novel")
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Anime")

        DiscoveryUpdateJob.stop(context, DiscoveryMediaType.ANIME)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Anime"
        DiscoveryProgressTracker.progress(DiscoveryMediaType.NOVEL).value.stageText shouldBe "Novel"
        verify(exactly = 1) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.ANIME) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.MANGA) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.NOVEL) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel() }
    }

    @Test
    fun `replaced manual worker cannot clear active progress or cancel successor notification`() {
        val previousId = UUID.randomUUID()
        val currentId = UUID.randomUUID()
        preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).set(currentId.toString())
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Successor")
        val notifier = DiscoveryNotifier(context)

        finishManualRefresh(DiscoveryMediaType.ANIME, previousId, preferences, notifier)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Successor"
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel(any()) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel() }
    }

    @Test
    fun `current manual worker finalization cancels its notification and preserves runner progress`() {
        val currentId = UUID.randomUUID()
        preferences.manualRefreshWorkId(DiscoveryMediaType.ANIME).set(currentId.toString())
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Background")
        val notifier = DiscoveryNotifier(context)

        finishManualRefresh(DiscoveryMediaType.ANIME, currentId, preferences, notifier)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Background"
        verify(exactly = 1) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.ANIME) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.MANGA) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel(DiscoveryMediaType.NOVEL) }
        verify(exactly = 0) { anyConstructed<DiscoveryNotifier>().cancel() }
    }

    @Test
    fun `unscoped cleanup preserves progress owned by active runners`() {
        DiscoveryProgressTracker.update(mediaType = DiscoveryMediaType.ANIME, stageText = "Background")

        DiscoveryUpdateJob.cleanupStaleManualWork(context)

        DiscoveryProgressTracker.progress(DiscoveryMediaType.ANIME).value.stageText shouldBe "Background"
    }

    @Test
    fun `startup cleanup cancels legacy and scoped manual tags`() {
        DiscoveryUpdateJob.cleanupStaleManualWork(context)

        listOf(
            "DiscoveryUpdateManual",
            "DiscoveryUpdateManual:anime",
            "DiscoveryUpdateManual:manga",
            "DiscoveryUpdateManual:novel",
        ).forEach { tag -> verify(exactly = 1) { workManager.cancelAllWorkByTag(tag) } }
    }

    @Test
    fun `manual work name is per media type to avoid REPLACE races`() {
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.ANIME) shouldBe "DiscoveryUpdateManual:anime"
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.MANGA) shouldBe "DiscoveryUpdateManual:manga"
        DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.NOVEL) shouldBe "DiscoveryUpdateManual:novel"
    }

    @Test
    fun `manual work name without media type falls back to shared tag name`() {
        DiscoveryUpdateJob.manualWorkName(null) shouldBe DiscoveryUpdateJob.TAG_MANUAL
    }

    @Test
    fun `refill work name formats work name per media type`() {
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.ANIME) shouldBe "DiscoveryRefillAfterConsumed:anime"
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.MANGA) shouldBe "DiscoveryRefillAfterConsumed:manga"
        DiscoveryUpdateJob.refillWorkName(DiscoveryMediaType.NOVEL) shouldBe "DiscoveryRefillAfterConsumed:novel"
    }

    @Test
    fun `manual refresh failure always returns failure result and never retries`() {
        resolveWorkResult(isManual = true, runAttemptCount = 0, isSuccess = false) shouldBe
            androidx.work.ListenableWorker.Result.failure()
        resolveWorkResult(isManual = true, runAttemptCount = 5, isSuccess = false) shouldBe
            androidx.work.ListenableWorker.Result.failure()
    }

    @Test
    fun `successful work returns success result`() {
        resolveWorkResult(isManual = true, runAttemptCount = 0, isSuccess = true) shouldBe
            androidx.work.ListenableWorker.Result.success()
        resolveWorkResult(isManual = false, runAttemptCount = 1, isSuccess = true) shouldBe
            androidx.work.ListenableWorker.Result.success()
    }

    @Test
    fun `auto background refresh retries on low attempt count but caps at max attempts`() {
        resolveWorkResult(isManual = false, runAttemptCount = 1, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.retry()
        resolveWorkResult(isManual = false, runAttemptCount = 2, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.retry()
        resolveWorkResult(isManual = false, runAttemptCount = 3, isSuccess = false, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.failure()
    }

    @Test
    fun `setupTask does not cancel manual work`() {
        preferences.discoveryEnabled().set(true)
        preferences.refreshIntervalHours().set(12)

        DiscoveryUpdateJob.setupTask(context)

        verify(exactly = 0) { workManager.cancelAllWorkByTag(DiscoveryUpdateJob.TAG_MANUAL) }
        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
    }

    @Test
    fun `cancelAllManualRefreshes cancels manual tags and notification`() {
        DiscoveryUpdateJob.cancelAllManualRefreshes(context)

        listOf(
            "DiscoveryUpdateManual",
            "DiscoveryUpdateManual:anime",
            "DiscoveryUpdateManual:manga",
            "DiscoveryUpdateManual:novel",
        ).forEach { tag -> verify(exactly = 1) { workManager.cancelAllWorkByTag(tag) } }
        verify(exactly = 1) { anyConstructed<DiscoveryNotifier>().cancel() }
    }

    @Test
    fun `resolveWorkResult with partial or success outcomes returns success`() {
        val outcomes = listOf(
            DiscoveryRunOutcome(
                mediaType = DiscoveryMediaType.ANIME,
                status = DiscoveryRunStatus.SUCCESS,
            ),
            DiscoveryRunOutcome(
                mediaType = DiscoveryMediaType.MANGA,
                status = DiscoveryRunStatus.PARTIAL,
            ),
        )
        resolveWorkResult(isManual = false, runAttemptCount = 1, outcomes = outcomes) shouldBe
            androidx.work.ListenableWorker.Result.success()
        resolveWorkResult(isManual = true, runAttemptCount = 0, outcomes = outcomes) shouldBe
            androidx.work.ListenableWorker.Result.success()
    }

    @Test
    fun `resolveWorkResult with failed outcome respects manual and attempt limits`() {
        val outcomes = listOf(
            DiscoveryRunOutcome(
                mediaType = DiscoveryMediaType.NOVEL,
                status = DiscoveryRunStatus.FAILED,
            ),
        )
        resolveWorkResult(isManual = true, runAttemptCount = 0, outcomes = outcomes) shouldBe
            androidx.work.ListenableWorker.Result.failure()
        resolveWorkResult(isManual = false, runAttemptCount = 1, outcomes = outcomes, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.retry()
        resolveWorkResult(isManual = false, runAttemptCount = 3, outcomes = outcomes, maxAutoAttempts = 2) shouldBe
            androidx.work.ListenableWorker.Result.failure()
    }
}

package eu.kanade.tachiyomi.data.updater

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.ProgressUpdater
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.common.util.concurrent.Futures
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.installer.ApkExtensionKind
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class ExtensionUpdateJobTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { any<Context>().workManager } returns workManager
        coEvery { any<CoroutineWorker>().setForegroundSafely() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getWorkName returns correct unique work names for each extension kind`() {
        ExtensionUpdateJob.getWorkName(ApkExtensionKind.MANGA) shouldBe ExtensionUpdateJob.WORK_NAME_MANGA
        ExtensionUpdateJob.getWorkName(ApkExtensionKind.ANIME) shouldBe ExtensionUpdateJob.WORK_NAME_ANIME
        ExtensionUpdateJob.getWorkName(ApkExtensionKind.NOVEL_KOTLIN) shouldBe ExtensionUpdateJob.WORK_NAME_NOVEL
        ExtensionUpdateJob.getWorkName(ApkExtensionKind.NOVEL) shouldBe ExtensionUpdateJob.WORK_NAME_NOVEL
    }

    @Test
    fun `start enqueues unique work with KEEP policy when not running`() {
        every {
            workManager.getWorkInfosForUniqueWork(ExtensionUpdateJob.WORK_NAME_MANGA)
        } returns Futures.immediateFuture(emptyList())

        val result = ExtensionUpdateJob.start(context, ApkExtensionKind.MANGA)

        result shouldBe true
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                ExtensionUpdateJob.WORK_NAME_MANGA,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `start returns false and does not enqueue when already running`() {
        val runningWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.RUNNING
        }
        every {
            workManager.getWorkInfosForUniqueWork(ExtensionUpdateJob.WORK_NAME_ANIME)
        } returns Futures.immediateFuture(listOf(runningWorkInfo))

        val result = ExtensionUpdateJob.start(context, ApkExtensionKind.ANIME)

        result shouldBe false
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                ExtensionUpdateJob.WORK_NAME_ANIME,
                any(),
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `stop cancels unique work for the corresponding kind`() {
        ExtensionUpdateJob.stop(context, ApkExtensionKind.NOVEL)

        verify(exactly = 1) {
            workManager.cancelUniqueWork(ExtensionUpdateJob.WORK_NAME_NOVEL)
        }
    }

    @Test
    fun `isRunning returns true when work is running or enqueued`() {
        val enqueuedWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.ENQUEUED
        }
        every {
            workManager.getWorkInfosForUniqueWork(ExtensionUpdateJob.WORK_NAME_MANGA)
        } returns Futures.immediateFuture(listOf(enqueuedWorkInfo))

        ExtensionUpdateJob.isRunning(context, ApkExtensionKind.MANGA) shouldBe true
    }

    @Test
    fun `isRunning returns false when no work is active`() {
        val succeededWorkInfo = mockk<WorkInfo> {
            every { state } returns WorkInfo.State.SUCCEEDED
        }
        every {
            workManager.getWorkInfosForUniqueWork(ExtensionUpdateJob.WORK_NAME_MANGA)
        } returns Futures.immediateFuture(listOf(succeededWorkInfo))

        ExtensionUpdateJob.isRunning(context, ApkExtensionKind.MANGA) shouldBe false
    }

    @Test
    fun `notification ID constant matches expected value`() {
        Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS shouldBe -406
    }

    @Test
    fun `getForegroundInfo creates valid ForegroundInfo`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.id } returns UUID.randomUUID()
        val worker = ExtensionUpdateJob(app, parameters)

        val foregroundInfo = worker.getForegroundInfo()
        foregroundInfo.notificationId shouldBe Notifications.ID_EXTENSION_BATCH_UPDATE_PROGRESS
        foregroundInfo.notification.shouldNotBeNull()
    }

    @Test
    fun `doWork returns failure when kind is missing`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns Data.EMPTY
        val worker = ExtensionUpdateJob(app, parameters)

        worker.doWork() shouldBe ListenableWorker.Result.failure()
    }

    @Test
    fun `doWork executes successfully for manga when no updates pending`() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val mangaExtensionManager = mockk<MangaExtensionManager>(relaxed = true) {
            every { installedExtensionsFlow } returns MutableStateFlow(emptyList())
        }
        Injekt.addSingleton(mangaExtensionManager)

        val progressUpdater = mockk<ProgressUpdater> {
            every { updateProgress(any(), any(), any()) } returns Futures.immediateFuture(null)
        }
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.id } returns UUID.randomUUID()
        every { parameters.progressUpdater } returns progressUpdater
        every { parameters.inputData } returns workDataOf(ExtensionUpdateJob.KEY_KIND to ApkExtensionKind.MANGA.name)
        val worker = ExtensionUpdateJob(app, parameters)

        worker.doWork() shouldBe ListenableWorker.Result.success()
    }
}

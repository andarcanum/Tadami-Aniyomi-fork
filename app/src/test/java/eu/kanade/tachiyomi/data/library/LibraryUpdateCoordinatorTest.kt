package eu.kanade.tachiyomi.data.library

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.common.util.concurrent.Futures
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueued
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Manual "update everything" must enqueue each enabled media as an independent unique work
 * (they run in parallel, exactly like the auto-update path and per-tab refreshes), instead of
 * chaining anime -> manga -> novel into one sequential WorkManager continuation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LibraryUpdateCoordinatorTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        // Deterministic defaults; individual tests override what they care about.
        every { workManager.isRunning(any<String>()) } returns false
        every { workManager.isRunningOrEnqueued(any<String>()) } returns false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startAll starts every enabled media even when another media is already running`() {
        // Given: anime is busy with its own run.
        // I6: the manual-enqueue guard is a single ENQUEUED-aware TAG query now (the TAG is
        // carried by both manual and auto workers), replacing isRunning(TAG) +
        // isRunningOrEnqueued(WORK_NAME_MANUAL).
        every { workManager.isRunningOrEnqueued("AnimeLibraryUpdate") } returns true

        // When
        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = true,
            updateManga = true,
            updateNovel = true,
        )

        // Then: manga & novel start independently; only anime's own guard skips it.
        result shouldBe true
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "AnimeLibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        verify {
            workManager.enqueueUniqueWork(
                "LibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
            workManager.enqueueUniqueWork(
                "NovelLibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startAll enqueues each media independently without a work chain`() {
        every { workManager.isRunning(any<String>()) } returns false

        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = true,
            updateManga = true,
            updateNovel = false,
        )

        result shouldBe true
        // The sequential chain API must not be used at all anymore.
        verify(exactly = 0) {
            workManager.beginUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
        verify {
            workManager.enqueueUniqueWork(
                "AnimeLibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
            workManager.enqueueUniqueWork(
                "LibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(
                "NovelLibraryUpdate-manual",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        }
    }

    @Test
    fun `startAll returns false when all enabled media are busy`() {
        every { workManager.isRunning(any<String>()) } returns true
        every { workManager.isRunningOrEnqueued(any<String>()) } returns true

        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = true,
            updateManga = true,
            updateNovel = true,
        )

        result shouldBe false
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `startAll returns false when no media enabled`() {
        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = false,
            updateManga = false,
            updateNovel = false,
        )

        result shouldBe false
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `stop cancels each media job independently`() {
        every { workManager.getWorkInfos(any<WorkQuery>()) } returns Futures.immediateFuture(emptyList())

        LibraryUpdateCoordinator.stop(context)

        listOf("AnimeLibraryUpdate", "LibraryUpdate", "NovelLibraryUpdate").forEach { tag ->
            verify {
                workManager.getWorkInfos(match<WorkQuery> { query -> query.tags.contains(tag) })
            }
        }
    }
}

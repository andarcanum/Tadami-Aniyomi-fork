package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.mihon.injekt.patchInjekt
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.repository.DiscoveryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DiscoveryBindingBackfillTest {
    private lateinit var previousInjekt: InjektScope
    private lateinit var preferences: DiscoveryPreferences
    private var unbound = true
    private var status = DiscoveryRunStatus.SUCCESS
    private val repository = object : FakeDiscoveryRepositoryBase() {
        override suspend fun hasUnboundSourceRows() =
            unbound
    }

    @Before
    fun isolateRuntime() {
        previousInjekt = Injekt
        patchInjekt()
        preferences = DiscoveryPreferences(PersistingPreferenceStore())
        Injekt.addSingleton(preferences)
        Injekt.addSingleton<DiscoveryRepository>(repository)
        val runner = mockk<DiscoveryRunner>()
        coEvery { runner.run(any(), any(), any(), any(), any()) } answers {
            mapOf(DiscoveryMediaType.ANIME to DiscoveryRunOutcome(DiscoveryMediaType.ANIME, status))
        }
        Injekt.addSingleton(runner)
        mockkConstructor(DiscoveryNotifier::class)
    }

    @After
    fun restoreRuntime() {
        unmockkConstructor(DiscoveryNotifier::class)
        unmockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        Injekt = previousInjekt
    }

    @Test
    fun `a restored high marker cannot suppress unbound cache repair`(): Unit = runTest {
        preferences.bindingBackfillVersion().set(60)
        var requests = 0
        scheduleDiscoveryBindingBackfill(preferences, repository, 40) { requests++ }
        requests shouldBe 1
        preferences.bindingBackfillVersion().get() shouldBe 60
    }

    @Test
    fun `failed enqueue does not mark binding repair complete`(): Unit = runTest {
        shouldThrow<IllegalStateException> {
            scheduleDiscoveryBindingBackfill(preferences, repository, 40) { error("enqueue failed") }
        }
        preferences.bindingBackfillVersion().get() shouldBe 0
    }

    @Test
    fun `worker success with unbound rows remaining requests bounded retry`(): Unit = runTest {
        worker().doWork() shouldBe ListenableWorker.Result.retry()
        preferences.bindingBackfillVersion().get() shouldBe 0
    }

    @Test
    fun `skipped generation cannot claim success while legacy binding remains`(): Unit = runTest {
        status = DiscoveryRunStatus.SKIPPED
        worker().doWork() shouldBe ListenableWorker.Result.retry()
        preferences.bindingBackfillVersion().get() shouldBe 0
    }

    @Test
    fun `successful bound worker stamps the existing version marker only after the postcondition`(): Unit = runTest {
        unbound = false
        worker().doWork() shouldBe ListenableWorker.Result.success()
        preferences.bindingBackfillVersion().get() shouldBe AppInfo.getVersionCode()
        var requests = 0
        scheduleDiscoveryBindingBackfill(preferences, repository, AppInfo.getVersionCode()) { requests++ }
        requests shouldBe 0
    }

    @Test
    fun `repair retries stop at the existing automatic attempt boundary`(): Unit = runTest {
        worker(attempt = 3).doWork() shouldBe ListenableWorker.Result.failure()
        preferences.bindingBackfillVersion().get() shouldBe 0
    }

    @Test
    fun `disabled worker does not certify unbound data or run generation`(): Unit = runTest {
        preferences.discoveryEnabled().set(false)
        worker().doWork() shouldBe ListenableWorker.Result.success()
        preferences.bindingBackfillVersion().get() shouldBe 0
    }

    @Test
    fun `already bound Home cache completes without scheduling or lowering restored version`(): Unit = runTest {
        unbound = false
        preferences.bindingBackfillVersion().set(60)
        var requests = 0
        scheduleDiscoveryBindingBackfill(preferences, repository, 40) { requests++ }
        requests shouldBe 0
        preferences.bindingBackfillVersion().get() shouldBe 60
    }

    @Test
    fun `repeated Home backfill enqueue preserves active work with KEEP`() {
        val context = RuntimeEnvironment.getApplication()
        val workManager = mockk<WorkManager>(relaxed = true)
        val policies = mutableListOf<ExistingWorkPolicy>()
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            policies += secondArg<ExistingWorkPolicy>()
            mockk(relaxed = true)
        }
        DiscoveryUpdateJob.backfillRefresh(context)
        DiscoveryUpdateJob.backfillRefresh(context)
        policies shouldBe listOf(ExistingWorkPolicy.KEEP, ExistingWorkPolicy.KEEP)
    }

    private fun worker(attempt: Int = 0): DiscoveryUpdateJob {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.id } returns UUID.randomUUID()
        every { parameters.tags } returns setOf("DiscoveryUpdateBackfill")
        every { parameters.inputData } returns Data.EMPTY
        every { parameters.runAttemptCount } returns attempt
        return DiscoveryUpdateJob(RuntimeEnvironment.getApplication(), parameters)
    }
}

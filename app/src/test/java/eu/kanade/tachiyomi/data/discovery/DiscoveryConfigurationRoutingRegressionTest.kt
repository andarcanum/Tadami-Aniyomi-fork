package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.presentation.more.settings.screen.applyDiscoveryGenreFilters
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.ui.discovery.manualRefreshFlow
import eu.kanade.tachiyomi.util.system.isRunningOrEnqueuedFlow
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DiscoveryConfigurationRoutingRegressionTest {
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val requests = mutableMapOf<String, OneTimeWorkRequest>()
    private lateinit var preferences: DiscoveryPreferences

    @Before
    fun setup() {
        preferences = DiscoveryPreferences(PersistingPreferenceStore())
        Injekt.addSingleton(preferences)
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            secondArg<ExistingWorkPolicy>() shouldBe ExistingWorkPolicy.REPLACE
            // All three values must be saved before the first request is published.
            preferences.priorityGenres().get() shouldBe "romance"
            preferences.requiredGenres().get() shouldBe "fantasy"
            preferences.ignoredGenres().get() shouldBe "horror"
            requests[firstArg()] = thirdArg()
            mockk(relaxed = true)
        }
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `real genre Apply callback queues configuration work for all media without manual ownership`() {
        val media = DiscoveryMediaType.NOVEL
        preferences.manualRefreshAt(media).set(123L)
        preferences.manualRefreshCount(media).set(7)
        preferences.manualRefreshWorkId(media).set("old-manual")
        applyDiscoveryGenreFilters(context, preferences, "romance", "fantasy", "horror")
        requests.keys shouldBe DiscoveryMediaType.entries.map { DiscoveryUpdateJob.configurationWorkName(it) }.toSet()
        for (target in DiscoveryMediaType.entries) {
            val request = requests.getValue(DiscoveryUpdateJob.configurationWorkName(target))
            request.tags.contains(DiscoveryUpdateJob.TAG_MANUAL) shouldBe false
            request.tags.contains(DiscoveryUpdateJob.configurationWorkName(target)) shouldBe true
            request.workSpec.input.getBoolean(DiscoveryUpdateJob.KEY_CONFIGURATION_REFRESH, false) shouldBe true
            request.workSpec.input.getString(DiscoveryUpdateJob.KEY_TARGET_MEDIA_TYPE) shouldBe target.key
        }
        preferences.manualRefreshAt(media).get() shouldBe 123L
        preferences.manualRefreshCount(media).get() shouldBe 7
        preferences.manualRefreshWorkId(media).get() shouldBe "old-manual"
    }

    @Test
    fun `manual request remains manual rather than configuration`() {
        preferences.priorityGenres().set("romance")
        preferences.requiredGenres().set("fantasy")
        preferences.ignoredGenres().set("horror")
        DiscoveryUpdateJob.refreshNow(context, DiscoveryMediaType.NOVEL)
        val request = requests.getValue(DiscoveryUpdateJob.manualWorkName(DiscoveryMediaType.NOVEL))
        request.tags.contains(DiscoveryUpdateJob.TAG_MANUAL) shouldBe true
        request.workSpec.input.getBoolean(DiscoveryUpdateJob.KEY_CONFIGURATION_REFRESH, false) shouldBe false
    }

    @Test
    fun `configuration jobs contribute to visible refreshing state`(): Unit = runTest {
        val media = DiscoveryMediaType.NOVEL
        val manual = MutableStateFlow(false)
        val configuration = MutableStateFlow(false)
        every { workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.manualWorkTag(media)) } returns manual
        every { workManager.isRunningOrEnqueuedFlow(DiscoveryUpdateJob.configurationWorkName(media)) } returns
            configuration
        var refreshing = false
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manualRefreshFlow(workManager, media).collect { refreshing = it }
        }
        runCurrent()
        refreshing shouldBe false
        configuration.value = true
        runCurrent()
        refreshing shouldBe true
        manual.value = true
        configuration.value = false
        runCurrent()
        refreshing shouldBe true
        manual.value = false
        runCurrent()
        refreshing shouldBe false
    }
}

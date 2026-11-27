package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.presentation.more.settings.screen.DiscoveryGenreFilterValues
import eu.kanade.presentation.more.settings.screen.applyDiscoveryGenreFilters
import eu.kanade.presentation.more.settings.screen.changedDiscoveryGenreFilters
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

    private fun genreFilters(prefix: String) = DiscoveryGenreFilterValues(
        priorityCsv = "$prefix-priority",
        requiredCsv = "$prefix-required",
        ignoredCsv = "$prefix-ignored",
    )

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
            preferences.priorityGenres(DiscoveryMediaType.NOVEL).get() shouldBe "romance"
            preferences.requiredGenres(DiscoveryMediaType.NOVEL).get() shouldBe "fantasy"
            preferences.ignoredGenres(DiscoveryMediaType.NOVEL).get() shouldBe "horror"
            requests[firstArg()] = thirdArg()
            mockk(relaxed = true)
        }
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    @Test
    fun `genre dialog diff ignores an unchanged snapshot`() {
        val initial = DiscoveryMediaType.entries.associateWith { genreFilters(it.key) }

        changedDiscoveryGenreFilters(initial, initial) shouldBe emptyMap()
    }

    @Test
    fun `genre dialog diff reports one changed media`() {
        val initial = DiscoveryMediaType.entries.associateWith { genreFilters(it.key) }
        val updated = initial + (DiscoveryMediaType.MANGA to genreFilters("changed-manga"))

        changedDiscoveryGenreFilters(initial, updated) shouldBe mapOf(
            DiscoveryMediaType.MANGA to genreFilters("changed-manga"),
        )
    }

    @Test
    fun `genre dialog diff preserves multiple changed media`() {
        val initial = DiscoveryMediaType.entries.associateWith { genreFilters(it.key) }
        val updated = initial + mapOf(
            DiscoveryMediaType.ANIME to genreFilters("changed-anime"),
            DiscoveryMediaType.NOVEL to genreFilters("changed-novel"),
        )

        changedDiscoveryGenreFilters(initial, updated) shouldBe mapOf(
            DiscoveryMediaType.ANIME to genreFilters("changed-anime"),
            DiscoveryMediaType.NOVEL to genreFilters("changed-novel"),
        )
    }

    @Test
    fun `real genre Apply callback queues configuration work only for selected media`() {
        val media = DiscoveryMediaType.NOVEL
        preferences.manualRefreshAt(media).set(123L)
        preferences.manualRefreshCount(media).set(7)
        preferences.manualRefreshWorkId(media).set("old-manual")
        applyDiscoveryGenreFilters(context, preferences, media, "romance", "fantasy", "horror")
        requests.keys shouldBe setOf(DiscoveryUpdateJob.configurationWorkName(media))
        val request = requests.getValue(DiscoveryUpdateJob.configurationWorkName(media))
        request.tags.contains(DiscoveryUpdateJob.TAG_MANUAL) shouldBe false
        request.tags.contains(DiscoveryUpdateJob.configurationWorkName(media)) shouldBe true
        request.workSpec.input.getBoolean(DiscoveryUpdateJob.KEY_CONFIGURATION_REFRESH, false) shouldBe true
        request.workSpec.input.getString(DiscoveryUpdateJob.KEY_TARGET_MEDIA_TYPE) shouldBe media.key
        preferences.manualRefreshAt(media).get() shouldBe 123L
        preferences.manualRefreshCount(media).get() shouldBe 7
        preferences.manualRefreshWorkId(media).get() shouldBe "old-manual"
        preferences.priorityGenres(DiscoveryMediaType.ANIME).get() shouldBe ""
        preferences.priorityGenres(DiscoveryMediaType.MANGA).get() shouldBe ""
    }

    @Test
    fun `manual request remains manual rather than configuration`() {
        preferences.priorityGenres(DiscoveryMediaType.NOVEL).set("romance")
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).set("fantasy")
        preferences.ignoredGenres(DiscoveryMediaType.NOVEL).set("horror")
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

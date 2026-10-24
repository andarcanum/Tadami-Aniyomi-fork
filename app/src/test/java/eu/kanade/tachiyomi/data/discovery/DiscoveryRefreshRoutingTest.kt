package eu.kanade.tachiyomi.data.discovery

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.more.settings.screen.changeDiscoveryProviderSetting
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class DiscoveryRefreshRoutingTest {
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val requests = mutableMapOf<String, OneTimeWorkRequest>()
    private lateinit var preferences: DiscoveryPreferences

    @Before
    fun setUp() {
        preferences = DiscoveryPreferences(PersistingPreferenceStore())
        Injekt.addSingleton(preferences)
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            secondArg<ExistingWorkPolicy>() shouldBe ExistingWorkPolicy.REPLACE
            requests[firstArg()] = thirdArg()
            mockk(relaxed = true)
        }
    }

    @After
    fun tearDown() {
        DiscoveryProgressTracker.reset()
        unmockkAll()
    }

    @Test
    fun `global external provider changes refresh all media independently`() {
        assertRouting(DiscoveryProviderSetting.EXTERNAL, setOf("anime", "manga", "novel"))
    }

    @Test
    fun `Shikimori changes refresh anime manga and novel`() {
        assertRouting(DiscoveryProviderSetting.SHIKIMORI, setOf("anime", "manga", "novel"))
    }

    @Test
    fun `MangaUpdates changes refresh manga and novel`() {
        assertRouting(DiscoveryProviderSetting.MANGA_UPDATES, setOf("manga", "novel"))
    }

    @Test
    fun `NovelUpdates changes refresh novel only`() {
        assertRouting(DiscoveryProviderSetting.NOVEL_UPDATES, setOf("novel"))
    }

    private fun assertRouting(setting: DiscoveryProviderSetting, targets: Set<String>) {
        val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        listOf(false, true).forEach { enabled ->
            when (setting) {
                DiscoveryProviderSetting.EXTERNAL -> preferences.externalProvidersEnabled().set(enabled)
                DiscoveryProviderSetting.SHIKIMORI -> sourcePreferences.suggestionsUseShikimori().set(enabled)
                DiscoveryProviderSetting.MANGA_UPDATES -> sourcePreferences.suggestionsUseMangaUpdatesNovel().set(
                    enabled,
                )
                DiscoveryProviderSetting.NOVEL_UPDATES -> sourcePreferences.suggestionsUseNovelUpdates().set(enabled)
            }
            requests.clear()
            DiscoveryUpdateJob.refreshAfterProviderChange(context, setting)
            requests.keys shouldBe targets.map { "DiscoveryUpdateConfiguration:$it" }.toSet()
            requests.values.map { it.id }.toSet().size shouldBe targets.size
            requests.values.map { it.workSpec.input.getString("target_media_type") }.toSet() shouldBe targets
            requests.values.forEach { request ->
                request.tags.any { it.startsWith("DiscoveryUpdateManual") } shouldBe false
            }
            preferences.rowTrendEnabled().get() shouldBe true
        }
    }

    @Test
    fun `provider callbacks persist incoming values before every enqueue and suppress duplicate write`() {
        val sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        val providerPreferences = mapOf(
            DiscoveryProviderSetting.EXTERNAL to preferences.externalProvidersEnabled(),
            DiscoveryProviderSetting.SHIKIMORI to sourcePreferences.suggestionsUseShikimori(),
            DiscoveryProviderSetting.MANGA_UPDATES to sourcePreferences.suggestionsUseMangaUpdatesNovel(),
            DiscoveryProviderSetting.NOVEL_UPDATES to sourcePreferences.suggestionsUseNovelUpdates(),
        )
        providerPreferences.forEach { (setting, preference) ->
            listOf(false, true).forEach { value ->
                preference.set(!value)
                val observedValues = mutableListOf<Boolean>()
                every {
                    workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
                } answers {
                    observedValues += preference.get()
                    mockk(relaxed = true)
                }

                changeDiscoveryProviderSetting(context, preference, setting, value) shouldBe false

                preference.get() shouldBe value
                observedValues.isNotEmpty() shouldBe true
                observedValues.all { it == value } shouldBe true
            }
        }
    }

    @Test
    fun `configuration work preserves click cooldown count and ownership while manual work remains manual`() = runTest {
        val media = DiscoveryMediaType.NOVEL
        preferences.manualRefreshAt(media).set(1_000_000L)
        preferences.manualRefreshCount(media).set(7)
        preferences.manualRefreshWorkId(media).set("current-manual")
        preferences.rowLikeEnabled().set(false)
        preferences.rowTasteEnabled().set(false)
        preferences.rowSourceEnabled().set(false)
        val row = object : DiscoveryRowBuilder {
            override val rowType = DiscoveryRowType.TREND
            override suspend fun build(context: DiscoveryBuildContext) = listOf(
                DiscoveryRowItem("New Title", "new title", null, null, null, "anilist", 1.0),
            )
        }
        Injekt.addSingleton(
            DiscoveryRunner(
                repository = mockk(relaxed = true),
                preferences = preferences,
                seedSources = object : DiscoverySeedSources {
                    override suspend fun candidates(mediaType: DiscoveryMediaType) = emptyList<DiscoverySeedInput>()
                    override suspend fun historyCleanTitles(mediaType: DiscoveryMediaType) = emptySet<String>()
                },
                coordinatorFactory = { DiscoveryCoordinator(listOf(row)) },
                trendingFactory = { mockk(relaxed = true) },
                sourcePreferencesProvider = { SourcePreferences(PersistingPreferenceStore()) },
                installedPluginsProvider = { emptyList() },
            ),
        )
        DiscoveryUpdateJob.refreshConfiguration(context, media)
        val configuration = requests.getValue("DiscoveryUpdateConfiguration:novel")
        runRequest(configuration)
        preferences.manualRefreshAt(media).get() shouldBe 1_000_000L
        preferences.manualRefreshCount(media).get() shouldBe 7
        preferences.manualRefreshWorkId(media).get() shouldBe "current-manual"

        DiscoveryUpdateJob.refreshNow(context, media)
        requests.keys shouldBe setOf("DiscoveryUpdateConfiguration:novel", "DiscoveryUpdateManual:novel")
        val manual = requests.getValue("DiscoveryUpdateManual:novel")
        manual.tags.contains(DiscoveryUpdateJob.TAG_MANUAL) shouldBe true
        runRequest(manual)
        preferences.manualRefreshCount(media).get() shouldBe 8
        preferences.manualRefreshAt(media).get() shouldBe 1_000_000L
    }

    private suspend fun runRequest(request: OneTimeWorkRequest) {
        val media = DiscoveryMediaType.fromKey(request.workSpec.input.getString("target_media_type")!!)!!
        val outcome = Injekt.get<DiscoveryRunner>().run(
            listOf(media),
            isManualRefresh = request.tags.contains(DiscoveryUpdateJob.TAG_MANUAL),
            isConfigurationRefresh = request.workSpec.input.getBoolean(
                DiscoveryUpdateJob.KEY_CONFIGURATION_REFRESH,
                false,
            ),
        ).getValue(media)
        outcome.status shouldBe DiscoveryRunStatus.SUCCESS
    }
}

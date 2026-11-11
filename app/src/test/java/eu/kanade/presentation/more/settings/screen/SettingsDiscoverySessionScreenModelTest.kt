package eu.kanade.presentation.more.settings.screen

import android.app.Application
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import eu.kanade.domain.discovery.service.DiscoveryPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.discovery.DiscoveryUpdateJob
import eu.kanade.tachiyomi.test.PersistingPreferenceStore
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.api.get

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SettingsDiscoverySessionScreenModelTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val scheduledWorks = mutableListOf<Pair<String, ExistingWorkPolicy>>()
    private lateinit var preferences: DiscoveryPreferences
    private lateinit var sourcePreferences: SourcePreferences

    @Before
    fun setUp() {
        preferences = DiscoveryPreferences(PersistingPreferenceStore())
        sourcePreferences = SourcePreferences(PersistingPreferenceStore())
        Injekt.addSingleton(preferences)
        Injekt.addSingleton(sourcePreferences)
        preferences = Injekt.get()
        sourcePreferences = Injekt.get()
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
        every {
            workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
        } answers {
            scheduledWorks += firstArg<String>() to secondArg<ExistingWorkPolicy>()
            mockk(relaxed = true)
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `no changes during session does not trigger refresh on dispose`() {
        val model = SettingsDiscoverySessionScreenModel(context, preferences, sourcePreferences)
        model.onDispose()
        scheduledWorks.isEmpty() shouldBe true
    }

    @Test
    fun `genre changes for Novel only trigger refresh for Novel on dispose`() {
        val model = SettingsDiscoverySessionScreenModel(context, preferences, sourcePreferences)
        preferences.requiredGenres(DiscoveryMediaType.NOVEL).set("Fantasy,Action")
        model.onDispose()

        scheduledWorks.size shouldBe 1
        scheduledWorks.single().first shouldBe DiscoveryUpdateJob.configurationWorkName(DiscoveryMediaType.NOVEL)
        scheduledWorks.single().second shouldBe ExistingWorkPolicy.REPLACE
    }

    @Test
    fun `reverting setting before dispose does not trigger refresh`() {
        val initialStatus = preferences.releaseStatusFilter().get()
        val model = SettingsDiscoverySessionScreenModel(context, preferences, sourcePreferences)

        preferences.releaseStatusFilter().set("COMPLETED")
        preferences.releaseStatusFilter().set(initialStatus)

        model.onDispose()
        scheduledWorks.isEmpty() shouldBe true
    }

    @Test
    fun `provider changes refresh affected media types on dispose`() {
        val model = SettingsDiscoverySessionScreenModel(context, preferences, sourcePreferences)
        sourcePreferences.suggestionsUseNovelUpdates().set(true)
        model.onDispose()

        scheduledWorks.size shouldBe 1
        scheduledWorks.single().first shouldBe DiscoveryUpdateJob.configurationWorkName(DiscoveryMediaType.NOVEL)
    }

    @Test
    fun `global external provider toggle refreshes all media types on dispose`() {
        val model = SettingsDiscoverySessionScreenModel(context, preferences, sourcePreferences)
        preferences.externalProvidersEnabled().set(false)
        model.onDispose()

        scheduledWorks.map { it.first }.toSet() shouldBe setOf(
            DiscoveryUpdateJob.configurationWorkName(DiscoveryMediaType.ANIME),
            DiscoveryUpdateJob.configurationWorkName(DiscoveryMediaType.MANGA),
            DiscoveryUpdateJob.configurationWorkName(DiscoveryMediaType.NOVEL),
        )
    }
}

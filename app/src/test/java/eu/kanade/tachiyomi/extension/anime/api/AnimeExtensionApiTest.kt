package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class AnimeExtensionApiTest {

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var lastCheckPreference: Preference<Long>
    private lateinit var updateExtensionRepo: UpdateAnimeExtensionRepo
    private lateinit var animeExtensionManager: AnimeExtensionManager
    private lateinit var context: Context
    private lateinit var api: AnimeExtensionApi

    private var nowMs = 0L

    @BeforeEach
    fun setup() {
        preferenceStore = mockk()
        lastCheckPreference = mockk()
        updateExtensionRepo = mockk()
        animeExtensionManager = mockk()
        context = mockk(relaxed = true)

        every { preferenceStore.getLong(any(), any()) } returns lastCheckPreference
        every { lastCheckPreference.set(any<Long>()) } answers { }
        every { animeExtensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        every { animeExtensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        coJustRun { updateExtensionRepo.awaitAll() }

        api = AnimeExtensionApi(
            preferenceStore = preferenceStore,
            getExtensionRepo = mockk<GetAnimeExtensionRepo>(relaxed = true),
            updateExtensionRepo = updateExtensionRepo,
            animeExtensionManager = animeExtensionManager,
            networkService = mockk(relaxed = true),
            json = Json.Default,
            timeProvider = { nowMs },
        )
    }

    @Test
    fun `if due check is not reached then update check is skipped`() {
        runTest {
            nowMs = 1_000_000L
            every { lastCheckPreference.get() } returns nowMs

            val result = api.checkForUpdatesIfDue(context)

            result shouldBe null
            coVerify(exactly = 0) { updateExtensionRepo.awaitAll() }
            verify(exactly = 0) { lastCheckPreference.set(any<Long>()) }
        }
    }

    @Test
    fun `if due check is reached then last check timestamp is updated`() {
        runTest {
            nowMs = 200_000_000L
            every { lastCheckPreference.get() } returns 0L

            api.checkForUpdatesIfDue(context)

            coVerify(exactly = 1) { updateExtensionRepo.awaitAll() }
            verify(exactly = 1) { lastCheckPreference.set(nowMs) }
        }
    }
}

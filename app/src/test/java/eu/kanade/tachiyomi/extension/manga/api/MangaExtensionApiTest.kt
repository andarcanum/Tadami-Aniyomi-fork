package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class MangaExtensionApiTest {

    private lateinit var preferenceStore: PreferenceStore
    private lateinit var lastCheckPreference: Preference<Long>
    private lateinit var updateExtensionRepo: UpdateMangaExtensionRepo
    private lateinit var mangaExtensionManager: MangaExtensionManager
    private lateinit var context: Context
    private lateinit var api: MangaExtensionApi

    private var nowMs = 0L

    @BeforeEach
    fun setup() {
        preferenceStore = mockk()
        lastCheckPreference = mockk()
        updateExtensionRepo = mockk()
        mangaExtensionManager = mockk()
        context = mockk(relaxed = true)

        every { preferenceStore.getLong(any(), any()) } returns lastCheckPreference
        every { lastCheckPreference.set(any<Long>()) } answers { }
        every { mangaExtensionManager.availableExtensionsFlow } returns MutableStateFlow(emptyList())
        every { mangaExtensionManager.installedExtensionsFlow } returns MutableStateFlow(emptyList())
        coJustRun { updateExtensionRepo.awaitAll() }

        api = MangaExtensionApi(
            preferenceStore = preferenceStore,
            getExtensionRepo = mockk<GetMangaExtensionRepo>(relaxed = true),
            updateExtensionRepo = updateExtensionRepo,
            extensionManager = mangaExtensionManager,
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

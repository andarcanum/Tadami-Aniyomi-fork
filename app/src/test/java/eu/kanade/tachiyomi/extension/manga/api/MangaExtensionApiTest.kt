package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mihon.data.extension.model.AvailableExtensionData
import mihon.data.extension.repository.ExtensionStoreFetchResult
import mihon.data.extension.repository.ExtensionStoreFetcher
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.legacyBaseUrl
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
            storeRepository = mockk<MangaExtensionStoreRepository>(relaxed = true),
            storeFetcher = mockk<ExtensionStoreFetcher>(relaxed = true),
            updateExtensionRepo = updateExtensionRepo,
            extensionManager = mangaExtensionManager,
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

    @Test
    fun `find extensions keeps store variants and store names`() {
        runTest {
            val storeRepository = mockk<MangaExtensionStoreRepository>()
            val storeFetcher = mockk<ExtensionStoreFetcher>()
            val alphaStore = legacyStore(
                baseUrl = "https://alpha.example",
                badgeLabel = "Alpha Store",
            )
            val betaStore = legacyStore(
                baseUrl = "https://beta.example",
                badgeLabel = "Beta Store",
            )
            coEvery { storeRepository.getAll() } returns listOf(alphaStore, betaStore)
            coEvery { storeFetcher.fetchExtensions(any()) } returns ExtensionStoreFetchResult(
                extensions = listOf(
                    availableExtension(alphaStore, versionCode = 10),
                    availableExtension(betaStore, versionCode = 11),
                ),
                failedStores = emptyList(),
            )

            val api = MangaExtensionApi(
                preferenceStore = preferenceStore,
                storeRepository = storeRepository,
                storeFetcher = storeFetcher,
                updateExtensionRepo = updateExtensionRepo,
                extensionManager = mangaExtensionManager,
                timeProvider = { nowMs },
            )

            val extensions = api.findExtensions().extensions

            extensions.size shouldBe 2
            extensions.map { it.pkgName }.distinct().size shouldBe 1
            extensions.map { it.repoName } shouldBe listOf(
                "Alpha Store",
                "Beta Store",
            )
        }
    }

    @Test
    fun `precision issues with float values are correctly rounded and matched`() {
        val rawLibVersion = 1.4000000357627869
        val roundedLibVersion = kotlin.math.round(rawLibVersion * 100.0) / 100.0
        roundedLibVersion shouldBe 1.4
        (roundedLibVersion in MangaExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
    }

    @Test
    fun `getApkUrl returns full http apk url unchanged`() {
        val extension = eu.kanade.tachiyomi.extension.manga.model.MangaExtension.Available(
            name = "Ext",
            pkgName = "pkg.example",
            versionName = "1.0",
            versionCode = 1,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            sources = emptyList(),
            apkName = "https://cdn.example/app.apk",
            iconUrl = "",
            repoUrl = "https://repo.example",
            repoName = "Repo",
        )

        api.getApkUrl(extension) shouldBe "https://cdn.example/app.apk"
    }

    private fun legacyStore(baseUrl: String, badgeLabel: String): ExtensionStore {
        return ExtensionStore(
            indexUrl = "$baseUrl/repo.json",
            name = badgeLabel,
            badgeLabel = badgeLabel,
            signingKey = "fp",
            contact = ExtensionStore.Contact(website = baseUrl, discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
    }

    private fun availableExtension(store: ExtensionStore, versionCode: Long): AvailableExtensionData {
        return AvailableExtensionData(
            name = "Source",
            pkgName = "pkg.example",
            apkUrl = "${store.legacyBaseUrl()}/apk/pkg.example.apk",
            iconUrl = "${store.legacyBaseUrl()}/icon/pkg.example.png",
            libVersion = 1.4,
            versionCode = versionCode,
            versionName = "1.4.0",
            lang = "en",
            isNsfw = false,
            sources = listOf(
                AvailableExtensionData.Source(
                    id = 1,
                    lang = "en",
                    name = "Source",
                    baseUrl = "https://example.org/source",
                ),
            ),
            store = store,
        )
    }
}

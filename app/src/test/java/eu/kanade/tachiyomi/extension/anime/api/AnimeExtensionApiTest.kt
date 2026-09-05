package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import mihon.data.extension.model.AvailableExtensionData
import mihon.data.extension.repository.ExtensionStoreFetchResult
import mihon.data.extension.repository.ExtensionStoreFetcher
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.legacyBaseUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

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
            storeRepository = mockk<AnimeExtensionStoreRepository>(relaxed = true),
            storeFetcher = mockk<ExtensionStoreFetcher>(relaxed = true),
            updateExtensionRepo = updateExtensionRepo,
            animeExtensionManager = animeExtensionManager,
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
    fun `supported library versions accept the full 12-19 range including minor versions`() {
        (12.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (14.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (14.4 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (16.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (17.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (18.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        // The reels feed-contract v18 work raised LIB_VERSION_MAX to 19.0; these assertions
        // still expected the old 18.x ceiling (pre-existing red, surfaced by an unrelated slice).
        (18.1 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (19.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe true
        (11.0 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
        (19.1 in AnimeExtensionLoader.SUPPORTED_LIB_VERSIONS) shouldBe false
    }

    @Test
    fun `find extensions keeps store variants and store names`() {
        runTest {
            val storeRepository = mockk<AnimeExtensionStoreRepository>()
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

            val api = AnimeExtensionApi(
                preferenceStore = preferenceStore,
                storeRepository = storeRepository,
                storeFetcher = storeFetcher,
                updateExtensionRepo = updateExtensionRepo,
                animeExtensionManager = animeExtensionManager,
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
            libVersion = AnimeExtensionLoader.LIB_VERSION_MIN,
            versionCode = versionCode,
            versionName = "${AnimeExtensionLoader.LIB_VERSION_MIN}.0.0",
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

    @Test
    fun `last check timestamp is stored under an anime specific app state key`() {
        // Sharing one key with the manga check meant whichever ran first starved the other.
        runTest {
            nowMs = 1_000_000L
            every { lastCheckPreference.get() } returns nowMs

            api.checkForUpdatesIfDue(context)

            verify {
                preferenceStore.getLong(
                    match<String> { it.contains("anime") && it.contains("last") },
                    any(),
                )
            }
        }
    }

    @Test
    fun `update check matches suffixed store pkgName to normalized installed name`() = runTest {
        nowMs = 200_000_000L
        every { lastCheckPreference.get() } returns 0L

        val installed = AnimeExtension.Installed(
            name = "Source",
            pkgName = "pkg.example", // normalized: the suffix was stripped at install time
            versionName = "1.4.0",
            versionCode = 10,
            libVersion = 1.4,
            lang = "en",
            isNsfw = false,
            pkgFactory = null,
            sources = emptyList(),
            icon = null,
            isShared = true,
        )
        every { animeExtensionManager.installedExtensionsFlow } returns
            MutableStateFlow(listOf(installed))

        // The store publishes the same package with a numeric suffix and a newer build.
        val storeRepository = mockk<AnimeExtensionStoreRepository>()
        val storeFetcher = mockk<ExtensionStoreFetcher>()
        val store = legacyStore(baseUrl = "https://alpha.example", badgeLabel = "Alpha Store")
        coEvery { storeRepository.getAll() } returns listOf(store)
        coEvery { storeFetcher.fetchExtensions(any()) } returns ExtensionStoreFetchResult(
            extensions = listOf(
                availableExtension(store, versionCode = 20).copy(pkgName = "pkg.example-123456789"),
            ),
            failedStores = emptyList(),
        )

        mockkConstructor(ExtensionUpdateNotifier::class)
        every { anyConstructed<ExtensionUpdateNotifier>().promptUpdates(any(), any()) } just runs
        // The notifier constructor resolves this from Injekt.
        Injekt.addSingleton(SecurityPreferences(InMemoryPreferenceStore()))

        val api = AnimeExtensionApi(
            preferenceStore = preferenceStore,
            storeRepository = storeRepository,
            storeFetcher = storeFetcher,
            updateExtensionRepo = updateExtensionRepo,
            animeExtensionManager = animeExtensionManager,
            timeProvider = { nowMs },
        )

        val result = api.checkForUpdatesIfDue(context)

        result!!.map { it.pkgName } shouldBe listOf("pkg.example")
    }

    @AfterEach
    fun tearDownMocks() {
        unmockkAll()
    }
}

package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoEntry
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class NovelExtensionApiTest {

    private lateinit var getExtensionRepo: GetNovelExtensionRepo
    private lateinit var updateExtensionRepo: UpdateNovelExtensionRepo
    private lateinit var repoUpdateInteractor: NovelPluginRepoUpdateInteractor
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var preferenceStore: PreferenceStore
    private lateinit var lastCheckPreference: Preference<Long>
    private lateinit var updatesCountPreference: Preference<Int>
    private lateinit var api: NovelExtensionApi

    private var nowMs = 0L

    @BeforeEach
    fun setup() {
        getExtensionRepo = mockk()
        updateExtensionRepo = mockk()
        repoUpdateInteractor = mockk()
        sourcePreferences = mockk()
        preferenceStore = mockk()
        lastCheckPreference = mockk()
        updatesCountPreference = mockk()

        every { preferenceStore.getLong(any(), any()) } returns lastCheckPreference
        every { sourcePreferences.novelExtensionUpdatesCount() } returns updatesCountPreference
        every { updatesCountPreference.set(any<Int>()) } answers { }
        every { lastCheckPreference.set(any<Long>()) } answers { }

        api = NovelExtensionApi(
            getExtensionRepo = getExtensionRepo,
            updateExtensionRepo = updateExtensionRepo,
            repoUpdateInteractor = repoUpdateInteractor,
            sourcePreferences = sourcePreferences,
            preferenceStore = preferenceStore,
            timeProvider = { nowMs },
        )
    }

    @Test
    fun `when checked too soon from available list expect no work`() {
        runTest {
            nowMs = 1_000_000L
            every { lastCheckPreference.get() } returns nowMs

            val result = api.checkForUpdates(fromAvailableExtensionList = true)

            result shouldBe null
            coVerify(exactly = 0) { updateExtensionRepo.awaitAll() }
            coVerify(exactly = 0) { repoUpdateInteractor.findUpdates(any<List<String>>()) }
            verify(exactly = 0) { updatesCountPreference.set(any<Int>()) }
        }
    }

    @Test
    fun `when checked normally expect update count set`() {
        runTest {
            nowMs = 2_000_000L
            every { lastCheckPreference.get() } returns 0L

            val repo = ExtensionRepo(
                baseUrl = "https://example.org",
                name = "Example",
                shortName = null,
                website = "https://example.org",
                signingKeyFingerprint = "ABC",
            )
            val entry = NovelPluginRepoEntry(
                id = "example-id",
                name = "Example Source",
                site = "Example",
                lang = "en",
                version = 2,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "deadbeef",
            )

            coEvery { getExtensionRepo.getAll() } returns listOf(repo)
            coEvery {
                repoUpdateInteractor.findUpdates(
                    listOf(
                        "https://example.org/index.min.json",
                        "https://example.org/plugins.min.json",
                    ),
                )
            } returns listOf(entry)
            coJustRun { updateExtensionRepo.awaitAll() }

            val result = api.checkForUpdates()

            result shouldBe listOf(entry)
            coVerify { updateExtensionRepo.awaitAll() }
            coVerify {
                repoUpdateInteractor.findUpdates(
                    listOf(
                        "https://example.org/index.min.json",
                        "https://example.org/plugins.min.json",
                    ),
                )
            }
            verify { lastCheckPreference.set(nowMs) }
            verify { updatesCountPreference.set(1) }
        }
    }

    @Test
    fun `when repo base url already points to index file expect no double suffix`() {
        runTest {
            nowMs = 3_000_000L
            every { lastCheckPreference.get() } returns 0L

            val repo = ExtensionRepo(
                baseUrl = "https://example.org/index.min.json",
                name = "Example",
                shortName = null,
                website = "https://example.org",
                signingKeyFingerprint = "ABC",
            )
            val entry = NovelPluginRepoEntry(
                id = "example-id",
                name = "Example Source",
                site = "Example",
                lang = "en",
                version = 2,
                url = "https://example.org/plugin.js",
                iconUrl = null,
                customJsUrl = null,
                customCssUrl = null,
                hasSettings = false,
                sha256 = "deadbeef",
            )

            coEvery { getExtensionRepo.getAll() } returns listOf(repo)
            coEvery {
                repoUpdateInteractor.findUpdates(listOf("https://example.org/index.min.json"))
            } returns listOf(entry)
            coJustRun { updateExtensionRepo.awaitAll() }

            val result = api.checkForUpdates()

            result shouldBe listOf(entry)
            coVerify {
                repoUpdateInteractor.findUpdates(listOf("https://example.org/index.min.json"))
            }
        }
    }
}

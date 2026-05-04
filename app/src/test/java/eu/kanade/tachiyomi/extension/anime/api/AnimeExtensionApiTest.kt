package eu.kanade.tachiyomi.extension.anime.api

import android.content.Context
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
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
import mihon.domain.extensionrepo.model.ExtensionRepo
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

    @Test
    fun `find extensions keeps repo variants and repo names`() {
        runTest {
            val getExtensionRepo = mockk<GetAnimeExtensionRepo>()
            val networkService = mockk<eu.kanade.tachiyomi.network.NetworkHelper>(relaxed = true)
            val server = MockWebServer()
            server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path) {
                        "/alpha/index.min.json" -> MockResponse().setResponseCode(200).setBody(
                            extensionPayload(
                                name = "Aniyomi: Alpha Source",
                                versionCode = 10,
                                version = "12.0",
                            ),
                        )
                        "/beta/index.min.json" -> MockResponse().setResponseCode(200).setBody(
                            extensionPayload(
                                name = "Aniyomi: Beta Source",
                                versionCode = 11,
                                version = "12.1",
                            ),
                        )
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()

            try {
                val repoUrlAlpha = server.url("/alpha").toString()
                val repoUrlBeta = server.url("/beta").toString()
                coEvery { getExtensionRepo.getAll() } returns listOf(
                    ExtensionRepo(
                        baseUrl = repoUrlAlpha,
                        name = "",
                        shortName = "Alpha Repo",
                        website = "https://alpha.example",
                        signingKeyFingerprint = "alpha",
                    ),
                    ExtensionRepo(
                        baseUrl = repoUrlBeta,
                        name = "",
                        shortName = null,
                        website = "https://beta.example",
                        signingKeyFingerprint = "beta",
                    ),
                )
                every { networkService.client } returns OkHttpClient.Builder().build()

                val api = AnimeExtensionApi(
                    preferenceStore = preferenceStore,
                    getExtensionRepo = getExtensionRepo,
                    updateExtensionRepo = updateExtensionRepo,
                    animeExtensionManager = animeExtensionManager,
                    networkService = networkService,
                    json = Json.Default,
                    timeProvider = { nowMs },
                )

                val extensions = api.findExtensions()

                extensions.size shouldBe 2
                extensions.map { it.pkgName }.distinct().size shouldBe 1
                extensions.map { it.repoName } shouldBe listOf(
                    "Alpha Repo",
                    repoUrlBeta,
                )
            } finally {
                server.shutdown()
            }
        }
    }

    private fun extensionPayload(
        name: String,
        versionCode: Long,
        version: String,
    ): String {
        return """
            [
              {
                "name": "$name",
                "pkg": "pkg.example",
                "apk": "pkg.example.apk",
                "lang": "en",
                "code": $versionCode,
                "version": "$version",
                "nsfw": 0,
                "sources": [
                  {
                    "id": 1,
                    "lang": "en",
                    "name": "Source",
                    "baseUrl": "https://example.org/source"
                  }
                ]
              }
            ]
        """.trimIndent()
    }
}

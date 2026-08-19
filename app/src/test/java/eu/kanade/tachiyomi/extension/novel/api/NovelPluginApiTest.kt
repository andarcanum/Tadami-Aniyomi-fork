package eu.kanade.tachiyomi.extension.novel.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.Test
import tachiyomi.domain.extension.novel.model.NovelPlugin

class NovelPluginApiTest {

    @Test
    fun `fetches plugins from all repos`() = runTest {
        val repos = listOf(
            ExtensionRepo(
                baseUrl = "https://repo.one",
                name = "Repo One",
                shortName = null,
                website = "https://repo.one",
                signingKeyFingerprint = "fingerprint-1",
            ),
            ExtensionRepo(
                baseUrl = "https://repo.two",
                name = "Repo Two",
                shortName = null,
                website = "https://repo.two",
                signingKeyFingerprint = "fingerprint-2",
            ),
        )

        val payloads = mapOf(
            "https://repo.one" to """
                [
                  {
                    "id": "one.plugin",
                    "name": "One",
                    "site": "https://one.example",
                    "lang": "en",
                    "version": 1,
                    "url": "https://one.example/plugin.js",
                    "hasSettings": false,
                    "sha256": "aaa"
                  }
                ]
            """.trimIndent(),
            "https://repo.two" to """
                [
                  {
                    "id": "two.plugin",
                    "name": "Two",
                    "site": "https://two.example",
                    "lang": "ru",
                    "version": 2,
                    "url": "https://two.example/plugin.js",
                    "hasSettings": true,
                    "sha256": "bbb"
                  }
                ]
            """.trimIndent(),
        )

        val api = NovelPluginApi(
            repoProvider = FakeRepoProvider(repos),
            fetcher = FakeFetcher(payloads),
            parser = NovelPluginIndexParser(Json { ignoreUnknownKeys = true }),
        )

        val plugins = api.fetchAvailablePlugins()

        plugins shouldBe listOf(
            NovelPlugin.Available(
                id = "one.plugin",
                name = "One",
                site = "https://one.example",
                lang = "en",
                versionCode = 1,
                versionName = "1",
                url = "https://one.example/plugin.js",
                iconUrl = null,
                customJs = null,
                customCss = null,
                hasSettings = false,
                sha256 = "aaa",
                repoUrl = "https://repo.one",
                repoName = "Repo One",
            ),
            NovelPlugin.Available(
                id = "two.plugin",
                name = "Two",
                site = "https://two.example",
                lang = "ru",
                versionCode = 2,
                versionName = "2",
                url = "https://two.example/plugin.js",
                iconUrl = null,
                customJs = null,
                customCss = null,
                hasSettings = true,
                sha256 = "bbb",
                repoUrl = "https://repo.two",
                repoName = "Repo Two",
            ),
        )
    }

    @Test
    fun `canonical indexUrl wins over base url probing`() = runTest {
        val repo = ExtensionRepo(
            baseUrl = "https://repo.one",
            name = "Repo One",
            shortName = null,
            website = "https://repo.one",
            signingKeyFingerprint = "fingerprint-1",
            indexUrl = "https://repo.one/repo/index.pb",
        )
        val requested = mutableListOf<String>()
        val api = NovelPluginApi(
            repoProvider = FakeRepoProvider(listOf(repo)),
            fetcher = FakeFetcher(
                payloads = mapOf(
                    "https://repo.one/repo/index.pb" to """
                        [
                          {
                            "isNovel": true,
                            "pkg": "one.extension",
                            "name": "One Ext",
                            "lang": "all",
                            "apk": "https://cdn.example/one.apk",
                            "version": 3,
                            "sources": [ { "id": "1", "lang": "all", "name": "One", "baseUrl": "https://one.example" } ]
                          }
                        ]
                    """.trimIndent(),
                ),
                requested = requested,
            ),
            parser = NovelPluginIndexParser(Json { ignoreUnknownKeys = true }),
        )

        val plugins = api.fetchAvailablePlugins()

        requested shouldBe listOf("https://repo.one/repo/index.pb")
        plugins.single().id shouldBe "one.extension"
        plugins.single().isKotlinExtension shouldBe true
        plugins.single().apkUrl shouldBe "https://cdn.example/one.apk"
    }

    private class FakeRepoProvider(
        private val repos: List<ExtensionRepo>,
    ) : NovelPluginRepoProvider {
        override suspend fun getAll(): List<ExtensionRepo> = repos
    }

    private class FakeFetcher(
        private val payloads: Map<String, String>,
        private val requested: MutableList<String> = mutableListOf(),
    ) : NovelPluginIndexFetcher {
        override suspend fun fetch(repoUrl: String): String {
            requested.add(repoUrl)
            return payloads[repoUrl] ?: error("Missing payload for $repoUrl")
        }
    }
}

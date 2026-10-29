package mihon.data.extension.service

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkAnimeExtensionStore
import mihon.domain.extensionstore.model.ExtensionStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AnimeExtensionStoreServiceTest {

    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true }
    private val protoBuf = ProtoBuf
    private lateinit var service: ExtensionStoreService

    @BeforeEach
    fun setup() {
        server.start()
        service = ExtensionStoreService(OkHttpClient(), json, protoBuf)
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    /**
     * Regression: anime repos published for Aniyomi (e.g. mojuru/anime-repo) encode
     * Extension.isTorrent at proto 8 and sources at proto 9. Decoding them with the manga
     * model crashed with "Expected wire type SIZE_DELIMITED(2), but found VARINT(0)" at the
     * first isTorrent=true entry, so the repo could not be added.
     */
    @Test
    fun `anime store via repo json index_v2 to gzipped pb decodes isTorrent and proto9 sources`() = runTest {
        val v2Url = server.url("/raw/repo/index.pb").toString()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.path) {
                    // A legacy JSON array body is what triggers the repo.json → index_v2 chain.
                    "/repo/index.min.json" -> bodyResponse("[]", gzip = false)
                    "/repo/repo.json" -> bodyResponse(repoJson(v2Url), gzip = false)
                    "/raw/repo/index.pb" -> bodyResponse(animeStorePbBytes(), gzip = true)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }

        val fetched = service.fetch(server.url("/repo/index.min.json").toString(), ExtensionStoreMedia.ANIME)
        fetched.isSuccess.shouldBeTrue()
        val store = fetched.getOrThrow()
        store.name shouldBe "Yūzōnō"
        store.isLegacy shouldBe false
        store.indexUrl shouldBe v2Url

        val listing = service.getExtensions(store, ExtensionStoreMedia.ANIME)
        listing.isSuccess.shouldBeTrue()
        val extensions = listing.getOrThrow()
        extensions.size shouldBe 2
        val torrentio = extensions.first { it.pkgName == "eu.kanade.tachiyomi.animeextension.all.torrentio" }
        torrentio.isTorrent shouldBe true
        torrentio.sources.single().id shouldBe 8542735178285060053L
        extensions.first { it.pkgName == "eu.kanade.tachiyomi.animeextension.all.animeonsen" }.isTorrent shouldBe false
    }

    @Test
    fun `anime json store index embedded extension list decodes isTorrent and string int64`() = runTest {
        server.enqueue(bodyResponse(animeStoreJson(), gzip = false))
        val store = nonLegacyStore(indexUrl = server.url("/index.json").toString())

        val listing = service.getExtensions(store, ExtensionStoreMedia.ANIME)

        listing.isSuccess.shouldBeTrue()
        val torrentio = listing.getOrThrow().single()
        torrentio.pkgName shouldBe "eu.kanade.tachiyomi.animeextension.all.torrentio"
        torrentio.versionCode shouldBe 10L
        torrentio.isTorrent shouldBe true
        torrentio.sources.single().id shouldBe 8542735178285060053L
    }

    @Test
    fun `anime separate pb extension list url decodes isTorrent`() = runTest {
        server.enqueue(bodyResponse(animeExtensionListPbBytes(), gzip = true))
        val store = nonLegacyStore(indexUrl = server.url("/index.pb").toString())
            .copy(extensionListUrl = server.url("/extensions.pb").toString())

        val listing = service.getExtensions(store, ExtensionStoreMedia.ANIME)

        listing.isSuccess.shouldBeTrue()
        listing.getOrThrow().single().isTorrent shouldBe true
    }

    /**
     * Upstream Aniyomi parity: the legacy index.min.json listing ignores the torrent key
     * (NetworkLegacyAnimeExtension hardcodes isTorrent = false), so we do the same.
     */
    @Test
    fun `anime legacy index ignores torrent key like upstream aniyomi`() = runTest {
        val legacyIndex = """
            [
              {
                "name": "Aniyomi: Subsplease",
                "pkg": "eu.kanade.tachiyomi.animeextension.en.subsplease",
                "apk": "eu.kanade.tachiyomi.animeextension.en.subsplease.apk",
                "lang": "en",
                "code": 14,
                "version": "14.1",
                "nsfw": 0,
                "torrent": true,
                "sources": [
                  {
                    "id": 8627569908756080147,
                    "name": "Subsplease",
                    "lang": "en",
                    "baseUrl": "https://subsplease.example"
                  }
                ]
              }
            ]
        """.trimIndent()
        server.enqueue(bodyResponse(legacyIndex, gzip = false))
        val store = legacyStore()

        val listing = service.getExtensions(store, ExtensionStoreMedia.ANIME)

        listing.isSuccess.shouldBeTrue()
        val subsplease = listing.getOrThrow().single()
        subsplease.pkgName shouldBe "eu.kanade.tachiyomi.animeextension.en.subsplease"
        subsplease.isTorrent shouldBe false
        subsplease.isNsfw shouldBe false
    }

    private fun repoJson(indexV2Url: String) = """
        {
          "index_v2": "$indexV2Url",
          "meta": {
            "name": "Yūzōnō",
            "shortName": "Yūzōnō",
            "website": "https://yuzono.example",
            "signingKeyFingerprint": "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905"
          }
        }
    """.trimIndent()

    private fun animeStoreJson(isTorrent: Boolean = true) = """
        {
          "name": "Yūzōnō",
          "badgeLabel": "Yū",
          "signingKey": "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905",
          "contact": { "website": "https://yuzono.example", "discord": null },
          "extensionList": {
            "extensions": [
              {
                "name": "Torrentio (Torrent / Debrid)",
                "packageName": "eu.kanade.tachiyomi.animeextension.all.torrentio",
                "resources": {
                  "apkUrl": "https://cdn.example/torrentio.apk",
                  "iconUrl": "https://cdn.example/torrentio.png"
                },
                "extensionLib": "14",
                "versionCode": "10",
                "versionName": "14.10",
                "contentWarning": "CONTENT_WARNING_SAFE",
                "isTorrent": $isTorrent,
                "sources": [
                  {
                    "id": "8542735178285060053",
                    "name": "Torrentio",
                    "language": "all",
                    "homeUrl": "https://torrentio.example"
                  }
                ]
              }
            ]
          },
          "extensionListUrl": null
        }
    """.trimIndent().trim()

    private fun animeStorePbBytes(): ByteArray {
        return protoBuf.encodeToByteArray(
            NetworkAnimeExtensionStore.serializer(),
            NetworkAnimeExtensionStore(
                name = "Yūzōnō",
                badgeLabel = "Yū",
                signingKey = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905",
                contact = NetworkAnimeExtensionStore.Contact(website = "https://yuzono.example", discord = null),
                extensionList = NetworkAnimeExtensionStore.ExtensionList(
                    extensions = listOf(
                        animeExtension(isTorrent = true, pkgName = "eu.kanade.tachiyomi.animeextension.all.torrentio"),
                        animeExtension(
                            isTorrent = false,
                            pkgName = "eu.kanade.tachiyomi.animeextension.all.animeonsen",
                        ),
                    ),
                ),
                extensionListUrl = null,
            ),
        )
    }

    private fun animeExtensionListPbBytes(): ByteArray {
        return protoBuf.encodeToByteArray(
            NetworkAnimeExtensionStore.ExtensionList.serializer(),
            NetworkAnimeExtensionStore.ExtensionList(
                extensions = listOf(
                    animeExtension(isTorrent = true, pkgName = "eu.kanade.tachiyomi.animeextension.all.torrentio"),
                ),
            ),
        )
    }

    private fun animeExtension(isTorrent: Boolean, pkgName: String) = NetworkAnimeExtensionStore.Extension(
        name = "Torrentio (Torrent / Debrid)",
        packageName = pkgName,
        resources = NetworkAnimeExtensionStore.Resources(
            apkUrl = "https://cdn.example/torrentio.apk",
            iconUrl = "https://cdn.example/torrentio.png",
        ),
        extensionLib = "14",
        versionCode = 10L,
        versionName = "14.10",
        contentWarning = NetworkAnimeExtensionStore.ContentWarning.SAFE,
        isTorrent = isTorrent,
        sources = listOf(
            NetworkAnimeExtensionStore.Source(
                id = 8542735178285060053L,
                name = "Torrentio",
                language = "all",
                homeUrl = "https://torrentio.example",
            ),
        ),
    )

    private fun nonLegacyStore(indexUrl: String) = ExtensionStore(
        indexUrl = indexUrl,
        name = "Yūzōnō",
        badgeLabel = "Yū",
        signingKey = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905",
        contact = ExtensionStore.Contact(website = "https://yuzono.example", discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private fun legacyStore() = ExtensionStore(
        indexUrl = server.url("/repo/repo.json").toString(),
        name = "Yūzōnō",
        badgeLabel = "Yū",
        signingKey = "fp",
        contact = ExtensionStore.Contact(website = "https://yuzono.example", discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )

    private fun bodyResponse(body: String, gzip: Boolean): MockResponse {
        return if (gzip) {
            MockResponse().setBody(Buffer().apply { GzipSink(this).buffer().use { it.writeUtf8(body) } })
        } else {
            MockResponse().setBody(body)
        }
    }

    private fun bodyResponse(bytes: ByteArray, gzip: Boolean): MockResponse {
        return if (gzip) {
            MockResponse().setBody(Buffer().apply { GzipSink(this).buffer().use { it.write(bytes) } })
        } else {
            MockResponse().setBody(Buffer().write(bytes))
        }
    }
}

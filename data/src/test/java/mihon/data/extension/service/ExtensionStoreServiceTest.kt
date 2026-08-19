package mihon.data.extension.service

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import mihon.domain.extensionstore.model.ExtensionStore
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ExtensionStoreServiceTest {

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

    @ParameterizedTest(name = "{0} {1} gzip={2}")
    @MethodSource("decodeMatrix")
    fun `decode matrix covers fetch and getExtensions with plain and gzip bodies`(
        operation: String,
        format: String,
        gzip: Boolean,
    ) = runTest {
        when (operation) {
            "fetch" -> runFetchMatrix(format, gzip)
            "getExtensions" -> runGetExtensionsMatrix(format, gzip)
            else -> error("Unknown operation: $operation")
        }
    }

    @Test
    fun `fetch repo json with indexV2 follows redirect`() = runTest {
        val v2Url = server.url("/v2/store.json").toString()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "index_v2": "$v2Url",
                  "meta": {
                    "name": "Old",
                    "shortName": null,
                    "website": "https://old.example",
                    "signingKeyFingerprint": "old"
                  }
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "name": "V2 Store",
                  "badgeLabel": "v2",
                  "signingKey": "v2-fp",
                  "contact": { "website": "https://v2.example", "discord": null },
                  "extensionList": null,
                  "extensionListUrl": null
                }
                """.trimIndent(),
            ),
        )

        val result = service.fetch(server.url("/repo/repo.json").toString())

        result.isSuccess.shouldBeTrue()
        result.getOrThrow().name shouldBe "V2 Store"
        result.getOrThrow().isLegacy shouldBe false
    }

    private suspend fun runFetchMatrix(format: String, gzip: Boolean) {
        val legacyRepoJson = """
            {
              "index_v2": null,
              "meta": {
                "name": "Matrix Legacy",
                "shortName": "matrix",
                "website": "https://matrix.example",
                "signingKeyFingerprint": "matrix-fp"
              }
            }
        """.trimIndent()
        val jsonStore = """
            {
              "name": "Matrix JSON",
              "badgeLabel": "matrix-json",
              "signingKey": "matrix-json-fp",
              "contact": { "website": "https://matrix-json.example", "discord": null },
              "extensionList": null,
              "extensionListUrl": null
            }
        """.trimIndent()
        val protoStore = NetworkExtensionStore(
            name = "Matrix Proto",
            badgeLabel = "matrix-proto",
            signingKey = "matrix-proto-fp",
            contact = NetworkExtensionStore.Contact(website = "https://matrix-proto.example", discord = null),
            extensionList = null,
            extensionListUrl = null,
        )
        val protoBytes = protoBuf.encodeToByteArray(NetworkExtensionStore.serializer(), protoStore)

        when (format) {
            "legacy_index" -> {
                server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                    override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                        return when (request.path) {
                            "/repo/index.min.json" -> bodyResponse("[]", gzip)
                            "/repo/repo.json" -> bodyResponse(legacyRepoJson, gzip)
                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                }
                val result = service.fetch(server.url("/repo/index.min.json").toString())
                result.isSuccess.shouldBeTrue()
                result.getOrThrow().name shouldBe "Matrix Legacy"
                result.getOrThrow().isLegacy shouldBe true
                result.getOrThrow().indexUrl shouldBe server.url("/repo/repo.json").toString()
            }
            "legacy_index_inner_gzip" -> {
                server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                    override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                        return when (request.path) {
                            "/repo/index.min.json" -> bodyResponse("[]", gzipBody = false)
                            "/repo/repo.json" -> bodyResponse(legacyRepoJson, gzipBody = true)
                            else -> MockResponse().setResponseCode(404)
                        }
                    }
                }
                val result = service.fetch(server.url("/repo/index.min.json").toString())
                result.isSuccess.shouldBeTrue()
                result.getOrThrow().name shouldBe "Matrix Legacy"
            }
            "legacy_repo" -> {
                server.enqueue(bodyResponse(legacyRepoJson, gzip))
                val result = service.fetch(server.url("/repo/repo.json").toString())
                result.isSuccess.shouldBeTrue()
                result.getOrThrow().name shouldBe "Matrix Legacy"
                result.getOrThrow().isLegacy shouldBe true
            }
            "json" -> {
                server.enqueue(bodyResponse(jsonStore, gzip))
                val result = service.fetch(server.url("/store.json").toString())
                result.isSuccess.shouldBeTrue()
                result.getOrThrow().name shouldBe "Matrix JSON"
            }
            "proto" -> {
                server.enqueue(bodyResponse(protoBytes, gzip))
                val result = service.fetch(server.url("/store.pb").toString())
                result.isSuccess.shouldBeTrue()
                result.getOrThrow().name shouldBe "Matrix Proto"
            }
            else -> error("Unknown fetch format: $format")
        }
    }

    private suspend fun runGetExtensionsMatrix(format: String, gzip: Boolean) {
        val legacyIndex = """
            [
              {
                "name": "Tachiyomi: Matrix Legacy",
                "pkg": "matrix.legacy.pkg",
                "apk": "matrix.legacy.pkg.apk",
                "lang": "en",
                "code": 20,
                "version": "2.0.0",
                "nsfw": 1,
                "sources": []
              }
            ]
        """.trimIndent()
        val embeddedStore = """
            {
              "name": "Matrix Store",
              "badgeLabel": "matrix",
              "signingKey": "fp",
              "contact": { "website": "https://matrix.example", "discord": null },
              "extensionList": {
                "extensions": [
                  {
                    "name": "Safe Ext",
                    "packageName": "safe.pkg",
                    "resources": {
                      "apkUrl": "https://cdn.example/safe.apk",
                      "iconUrl": "https://cdn.example/safe.png"
                    },
                    "extensionLib": "1.4",
                    "versionCode": 1,
                    "versionName": "1.0.0",
                    "contentWarning": "CONTENT_WARNING_SAFE",
                    "sources": []
                  },
                  {
                    "name": "Mixed Ext",
                    "packageName": "mixed.pkg",
                    "resources": {
                      "apkUrl": "https://cdn.example/mixed.apk",
                      "iconUrl": "https://cdn.example/mixed.png"
                    },
                    "extensionLib": "1.4",
                    "versionCode": 2,
                    "versionName": "2.0.0",
                    "contentWarning": "CONTENT_WARNING_MIXED",
                    "sources": []
                  }
                ]
              },
              "extensionListUrl": null
            }
        """.trimIndent()
        val separateList = """
            {
              "extensions": [
                {
                  "name": "Listed Matrix Ext",
                  "packageName": "matrix.listed.pkg",
                  "resources": {
                    "apkUrl": "https://cdn.example/listed.apk",
                    "iconUrl": "https://cdn.example/listed.png"
                  },
                  "extensionLib": "1.4",
                  "versionCode": 2,
                  "versionName": "2.0.0",
                  "contentWarning": "CONTENT_WARNING_NSFW",
                  "sources": []
                }
              ]
            }
        """.trimIndent()

        when (format) {
            "legacy_index" -> {
                server.enqueue(bodyResponse(legacyIndex, gzip))
                val store = legacyStore()
                val result = service.getExtensions(store)
                result.isSuccess.shouldBeTrue()
                val extension = result.getOrThrow().single()
                extension.pkgName shouldBe "matrix.legacy.pkg"
                extension.isNsfw shouldBe true
            }
            "embedded_json" -> {
                server.enqueue(bodyResponse(embeddedStore, gzip))
                val store = ExtensionStore(
                    indexUrl = server.url("/store.json").toString(),
                    name = "Store",
                    badgeLabel = "store",
                    signingKey = "fp",
                    contact = ExtensionStore.Contact(website = "https://store.example", discord = null),
                    isLegacy = false,
                    extensionListUrl = null,
                )
                val result = service.getExtensions(store)
                result.isSuccess.shouldBeTrue()
                val extensions = result.getOrThrow()
                extensions.size shouldBe 2
                extensions.first { it.pkgName == "safe.pkg" }.isNsfw shouldBe false
                extensions.first { it.pkgName == "mixed.pkg" }.isNsfw shouldBe true
            }
            "separate_list" -> {
                server.enqueue(bodyResponse(separateList, gzip))
                val store = ExtensionStore(
                    indexUrl = server.url("/store.json").toString(),
                    name = "Store",
                    badgeLabel = "store",
                    signingKey = "fp",
                    contact = ExtensionStore.Contact(website = "https://store.example", discord = null),
                    isLegacy = false,
                    extensionListUrl = server.url("/extensions.json").toString(),
                )
                val result = service.getExtensions(store)
                result.isSuccess.shouldBeTrue()
                val extension = result.getOrThrow().single()
                extension.pkgName shouldBe "matrix.listed.pkg"
                extension.isNsfw shouldBe true
            }
            "proto3_json_string_int64" -> {
                val keiyoushiStyle = """
                    {
                      "name": "Kei",
                      "badgeLabel": "KEI",
                      "signingKey": "fp",
                      "contact": { "website": "https://kei.example", "discord": null },
                      "extensionList": {
                        "extensions": [
                          {
                            "name": "Str Id Ext",
                            "packageName": "str.id.pkg",
                            "resources": {
                              "apkUrl": "https://cdn.example/str.apk",
                              "iconUrl": "https://cdn.example/str.png",
                              "jarUrl": "https://cdn.example/str.jar"
                            },
                            "extensionLib": "1.6",
                            "versionCode": "4",
                            "versionName": "1.6.4",
                            "contentWarning": "CONTENT_WARNING_NSFW",
                            "sources": [
                              {
                                "id": "6289731484943315811",
                                "name": "Str Id",
                                "language": "all",
                                "homeUrl": "https://str.example"
                              }
                            ]
                          }
                        ]
                      },
                      "extensionListUrl": null
                    }
                """.trimIndent()
                val store = ExtensionStore(
                    indexUrl = server.url("/store.json").toString(),
                    name = "Kei",
                    badgeLabel = "KEI",
                    signingKey = "fp",
                    contact = ExtensionStore.Contact(website = "https://kei.example", discord = null),
                    isLegacy = false,
                    extensionListUrl = null,
                )
                server.enqueue(bodyResponse(keiyoushiStyle, gzip))
                // fetch() must also decode the embedded extension list
                val fetched = service.fetch(server.url("/store.json").toString())
                fetched.isSuccess.shouldBeTrue()
                fetched.getOrThrow().name shouldBe "Kei"
                server.enqueue(bodyResponse(keiyoushiStyle, gzip))
                val result = service.getExtensions(store)
                result.isSuccess.shouldBeTrue()
                val extension = result.getOrThrow().single()
                extension.pkgName shouldBe "str.id.pkg"
                extension.versionCode shouldBe 4
                extension.libVersion shouldBe 1.6
                extension.isNsfw shouldBe true
                extension.sources.single().id shouldBe 6289731484943315811
            }
            else -> error("Unknown getExtensions format: $format")
        }
    }

    private fun legacyStore(): ExtensionStore {
        return ExtensionStore(
            indexUrl = server.url("/repo/repo.json").toString(),
            name = "Legacy",
            badgeLabel = "legacy",
            signingKey = "fp",
            contact = ExtensionStore.Contact(website = "https://legacy.example", discord = null),
            isLegacy = true,
            extensionListUrl = null,
        )
    }

    private fun bodyResponse(body: String, gzipBody: Boolean): MockResponse {
        return if (gzipBody) {
            MockResponse().setBody(Buffer().write(gzip(body)))
        } else {
            MockResponse().setBody(body)
        }
    }

    private fun bodyResponse(bytes: ByteArray, gzipBody: Boolean): MockResponse {
        return if (gzipBody) {
            MockResponse().setBody(Buffer().write(gzip(bytes)))
        } else {
            MockResponse().setBody(Buffer().write(bytes))
        }
    }

    private fun gzip(body: String): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.writeUtf8(body) }
        return buffer.readByteArray()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(bytes) }
        return buffer.readByteArray()
    }

    companion object {
        @JvmStatic
        fun decodeMatrix(): Stream<Arguments> = Stream.of(
            Arguments.of("fetch", "legacy_index", false),
            Arguments.of("fetch", "legacy_index", true),
            Arguments.of("fetch", "legacy_index_inner_gzip", false),
            Arguments.of("fetch", "legacy_repo", false),
            Arguments.of("fetch", "legacy_repo", true),
            Arguments.of("fetch", "json", false),
            Arguments.of("fetch", "json", true),
            Arguments.of("fetch", "proto", false),
            Arguments.of("getExtensions", "legacy_index", false),
            Arguments.of("getExtensions", "legacy_index", true),
            Arguments.of("getExtensions", "embedded_json", false),
            Arguments.of("getExtensions", "embedded_json", true),
            Arguments.of("getExtensions", "separate_list", false),
            Arguments.of("getExtensions", "separate_list", true),
            Arguments.of("getExtensions", "proto3_json_string_int64", false),
            Arguments.of("getExtensions", "proto3_json_string_int64", true),
        )
    }
}

package eu.kanade.tachiyomi.extension.novel

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.data.extension.model.NetworkExtensionStore
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.jupiter.api.Test

class NovelIndexFormatDecoderTest {

    private val proto = ProtoBuf
    private val json = Json { ignoreUnknownKeys = true }

    private fun store(extensions: List<NetworkExtensionStore.Extension>) = NetworkExtensionStore(
        name = "PB",
        badgeLabel = "PB",
        signingKey = "fp",
        contact = NetworkExtensionStore.Contact(website = "https://pb.example", discord = null),
        extensionList = NetworkExtensionStore.ExtensionList(extensions),
        extensionListUrl = null,
    )

    private fun extension(contentWarning: NetworkExtensionStore.ContentWarning) =
        NetworkExtensionStore.Extension(
            name = "PB Ext",
            packageName = "pb.ext",
            resources = NetworkExtensionStore.Resources(
                apkUrl = "https://cdn.example/pb.apk",
                iconUrl = "https://cdn.example/pb.png",
                jarUrl = "https://cdn.example/pb.jar",
            ),
            extensionLib = "1.4",
            versionCode = 7,
            versionName = "1.0.7",
            contentWarning = contentWarning,
            sources = listOf(
                NetworkExtensionStore.Source(
                    id = 1234567890123,
                    name = "PB",
                    language = "en",
                    homeUrl = "https://pb.example",
                ),
            ),
        )

    @Test
    fun `plain protobuf index maps to hybrid isNovel plugin json`() {
        val bytes = proto.encodeToByteArray(
            NetworkExtensionStore.serializer(),
            store(listOf(extension(NetworkExtensionStore.ContentWarning.NSFW))),
        )
        val decoded = NovelIndexFormatDecoder.decodeToPluginJson(bytes)
        decoded shouldNotBe null
        val entry = json.parseToJsonElement(decoded!!).jsonArray.single().jsonObject
        assertHybridEntry(entry, nsfw = true)
    }

    @Test
    fun `gzip compressed protobuf index maps to hybrid isNovel plugin json`() {
        val bytes = proto.encodeToByteArray(
            NetworkExtensionStore.serializer(),
            store(listOf(extension(NetworkExtensionStore.ContentWarning.SAFE))),
        )
        val decoded = NovelIndexFormatDecoder.decodeToPluginJson(gzip(bytes))
        decoded shouldNotBe null
        val entry = json.parseToJsonElement(decoded!!).jsonArray.single().jsonObject
        assertHybridEntry(entry, nsfw = false)
    }

    @Test
    fun `plain json array passes through`() {
        val json = NovelIndexFormatDecoder.decodeToPluginJson("[]".toByteArray())
        json shouldBe "[]"
    }

    @Test
    fun `new-format json store with string int64 maps to hybrid isNovel plugin json`() {
        val payload = """
            {
              "name": "Kei",
              "badgeLabel": "KEI",
              "signingKey": "fp",
              "contact": { "website": "https://kei.example", "discord": null },
              "extensionList": {
                "extensions": [
                  {
                    "name": "PB Ext",
                    "packageName": "pb.ext",
                    "resources": {
                      "apkUrl": "https://cdn.example/pb.apk",
                      "iconUrl": "https://cdn.example/pb.png",
                      "jarUrl": "https://cdn.example/pb.jar"
                    },
                    "extensionLib": "1.4",
                    "versionCode": "7",
                    "versionName": "1.0.7",
                    "contentWarning": "CONTENT_WARNING_NSFW",
                    "sources": [ { "id": "1234567890123", "name": "PB", "language": "all", "homeUrl": "https://pb.example" } ]
                  }
                ]
              },
              "extensionListUrl": null
            }
        """.trimIndent()
        val decoded = NovelIndexFormatDecoder.decodeToPluginJson(payload.toByteArray())
        decoded shouldNotBe null
        val entry = json.parseToJsonElement(decoded!!).jsonArray.single().jsonObject
        assertHybridEntry(entry, nsfw = true, lang = "all")
    }

    @Test
    fun `unsupported payload returns null`() {
        NovelIndexFormatDecoder.decodeToPluginJson(byteArrayOf(1, 2, 3)) shouldBe null
        NovelIndexFormatDecoder.decodeToPluginJson("not json at all".toByteArray()) shouldBe null
    }

    private fun assertHybridEntry(entry: kotlinx.serialization.json.JsonObject, nsfw: Boolean, lang: String = "en") {
        entry["isNovel"]?.jsonPrimitive?.booleanOrNull shouldBe true
        entry["id"]?.jsonPrimitive?.contentOrNull shouldBe "pb.ext"
        entry["pkg"]?.jsonPrimitive?.contentOrNull shouldBe "pb.ext"
        entry["name"]?.jsonPrimitive?.contentOrNull shouldBe "PB Ext"
        entry["lang"]?.jsonPrimitive?.contentOrNull shouldBe lang
        entry["version"]?.jsonPrimitive?.intOrNull shouldBe 7
        entry["url"]?.jsonPrimitive?.contentOrNull shouldBe "https://cdn.example/pb.apk"
        entry["apk"]?.jsonPrimitive?.contentOrNull shouldBe "https://cdn.example/pb.apk"
        entry["nsfw"]?.jsonPrimitive?.intOrNull shouldBe if (nsfw) 1 else 0
        entry["sources"]?.jsonArray?.single()?.jsonObject?.get("baseUrl")?.jsonPrimitive?.contentOrNull shouldBe
            "https://pb.example"
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(bytes) }
        return buffer.readByteArray()
    }
}

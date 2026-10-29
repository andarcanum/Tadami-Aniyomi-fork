package mihon.data.extension.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class NetworkAnimeExtensionStoreTest {

    private val protoBuf = ProtoBuf
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `pb round trip preserves isTorrent at proto 8 and sources at proto 9`() {
        val store = animeStore()

        val bytes = protoBuf.encodeToByteArray(NetworkAnimeExtensionStore.serializer(), store)
        val decoded = protoBuf.decodeFromByteArray<NetworkAnimeExtensionStore>(bytes)

        val torrentio = decoded.extensionList!!.extensions.single()
        torrentio.isTorrent shouldBe true
        torrentio.versionCode shouldBe 10L
        torrentio.sources.single().id shouldBe 8542735178285060053L
    }

    @Test
    fun `proto3 json string int64 and isTorrent decode from json store index`() {
        val body = """
            {
              "name": "Yūzōnō",
              "badgeLabel": "Yū",
              "signingKey": "fp",
              "contact": { "website": "https://yuzono.example", "discord": null },
              "extensionList": {
                "extensions": [
                  {
                    "name": "AnimeOnsen",
                    "packageName": "eu.kanade.tachiyomi.animeextension.all.animeonsen",
                    "resources": {
                      "apkUrl": "https://cdn.example/onsen.apk",
                      "iconUrl": "https://cdn.example/onsen.png"
                    },
                    "extensionLib": "14",
                    "versionCode": "10",
                    "versionName": "14.10",
                    "contentWarning": "CONTENT_WARNING_SAFE",
                    "isTorrent": false,
                    "sources": [
                      {
                        "id": "8542735178285060053",
                        "name": "AnimeOnsen",
                        "language": "all",
                        "homeUrl": "https://www.animeonsen.xyz"
                      }
                    ]
                  }
                ]
              },
              "extensionListUrl": null
            }
        """.trimIndent()

        val decoded = json.decodeFromString<NetworkAnimeExtensionStore>(body)

        val onsen = decoded.extensionList!!.extensions.single()
        onsen.versionCode shouldBe 10L
        onsen.isTorrent shouldBe false
        onsen.sources.single().id shouldBe 8542735178285060053L
    }

    @Test
    fun `extension list mapper maps isTorrent and contentWarning into AvailableExtensionData`() {
        val list = NetworkAnimeExtensionStore.ExtensionList(
            extensions = listOf(
                torrentExtension(isTorrent = true, contentWarning = NetworkAnimeExtensionStore.ContentWarning.SAFE),
                torrentExtension(isTorrent = false, contentWarning = NetworkAnimeExtensionStore.ContentWarning.NSFW),
            ),
        )

        val mapped = list.toAvailableExtensionData(store())

        mapped.first { it.pkgName == "eu.kanade.tachiyomi.animeextension.all.torrentio" }.let {
            it.isTorrent shouldBe true
            it.isNsfw shouldBe false
        }
        mapped.last { it.pkgName == "eu.kanade.tachiyomi.animeextension.all.torrentio" }.let {
            it.isTorrent shouldBe false
            it.isNsfw shouldBe true
        }
    }

    private fun animeStore() = NetworkAnimeExtensionStore(
        name = "Yūzōnō",
        badgeLabel = "Yū",
        signingKey = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905",
        contact = NetworkAnimeExtensionStore.Contact(website = "https://yuzono.example", discord = null),
        extensionList = NetworkAnimeExtensionStore.ExtensionList(extensions = listOf(torrentExtension())),
        extensionListUrl = null,
    )

    private fun torrentExtension(
        isTorrent: Boolean = true,
        contentWarning: NetworkAnimeExtensionStore.ContentWarning = NetworkAnimeExtensionStore.ContentWarning.SAFE,
    ) = NetworkAnimeExtensionStore.Extension(
        name = "Torrentio (Torrent / Debrid)",
        packageName = "eu.kanade.tachiyomi.animeextension.all.torrentio",
        resources = NetworkAnimeExtensionStore.Resources(
            apkUrl = "https://cdn.example/torrentio.apk",
            iconUrl = "https://cdn.example/torrentio.png",
        ),
        extensionLib = "14",
        versionCode = 10L,
        versionName = "14.10",
        contentWarning = contentWarning,
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

    private fun store() = ExtensionStore(
        indexUrl = "https://repo.example/repo.json",
        name = "Store",
        badgeLabel = "store",
        signingKey = "fp",
        contact = ExtensionStore.Contact(website = "https://repo.example", discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )
}

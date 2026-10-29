package mihon.data.extension.mapper

import io.kotest.matchers.shouldBe
import mihon.data.extension.model.AvailableExtensionData
import mihon.domain.extensionstore.model.ExtensionStore
import org.junit.jupiter.api.Test

class AnimeExtensionStoreMapperTest {

    @Test
    fun `isTorrent passes through to available anime extension`() {
        val data = AvailableExtensionData(
            name = "Torrentio (Torrent / Debrid)",
            pkgName = "eu.kanade.tachiyomi.animeextension.all.torrentio",
            apkUrl = "https://cdn.example/torrentio.apk",
            iconUrl = "https://cdn.example/torrentio.png",
            libVersion = 14.0,
            versionCode = 10L,
            versionName = "14.10",
            lang = "all",
            isNsfw = false,
            isTorrent = true,
            sources = listOf(
                AvailableExtensionData.Source(
                    id = 8542735178285060053L,
                    lang = "all",
                    name = "Torrentio",
                    baseUrl = "https://torrentio.example",
                ),
            ),
            store = store(),
        )

        val available = data.toAnimeExtensionAvailable()!!

        available.isTorrent shouldBe true
        available.sources.single().id shouldBe 8542735178285060053L
    }

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

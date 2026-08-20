package mihon.domain.extensionstore

import io.kotest.matchers.shouldBe
import mihon.domain.extensionstore.model.ExtensionStore
import mihon.domain.extensionstore.model.isExtensionStoreIndexUrl
import mihon.domain.extensionstore.model.repoDisplayNameFallback
import mihon.domain.extensionstore.model.toExtensionStoreBaseUrl
import org.junit.jupiter.api.Test

class ExtensionStoreIndexUrlTest {

    private fun store(indexUrl: String) = ExtensionStore(
        indexUrl = indexUrl,
        name = "Example",
        badgeLabel = "Example",
        signingKey = "abc",
        contact = ExtensionStore.Contact(website = "https://example.org", discord = null),
        isLegacy = true,
        extensionListUrl = null,
    )

    @Test
    fun `the ui model keeps the url the store is indexed by`() {
        // The copy-url action pastes this back, so a fabricated index.min.json would 404 for novel
        // plugin repos and for non-legacy store indexes.
        store("https://example.org/repo/plugins.min.json").toExtensionRepo().indexUrl shouldBe
            "https://example.org/repo/plugins.min.json"
        store("https://example.org/repo/repo.json").toExtensionRepo().indexUrl shouldBe
            "https://example.org/repo/repo.json"
    }

    @Test
    fun `the base url still strips the index file`() {
        store("https://example.org/repo/index.min.json").toExtensionRepo().baseUrl shouldBe
            "https://example.org/repo"
    }

    @Test
    fun `index pb is recognized as an index url and stripped from the base url`() {
        store("https://example.org/repo/index.pb").toExtensionRepo().baseUrl shouldBe
            "https://example.org/repo"
        "https://example.org/repo/index.pb".isExtensionStoreIndexUrl() shouldBe true
        "https://example.org/repo/index.pb".toExtensionStoreBaseUrl() shouldBe
            "https://example.org/repo"
    }

    @Test
    fun `repo json pointing at index pb keeps its own url as the index`() {
        store("https://example.org/repo/repo.json").toExtensionRepo().indexUrl shouldBe
            "https://example.org/repo/repo.json"
    }

    @Test
    fun `repoDisplayNameFallback collapses github urls to owner repo`() {
        "https://github.com/novelsourcery/extensions/raw/repo".repoDisplayNameFallback() shouldBe
            "novelsourcery/extensions"
        "https://github.com/NovelSourcery".repoDisplayNameFallback() shouldBe "NovelSourcery"
        "https://raw.githubusercontent.com/novelsourcery/extensions/repo/index.pb".repoDisplayNameFallback() shouldBe
            "novelsourcery/extensions"
        "https://github.com".repoDisplayNameFallback() shouldBe "github.com"
    }

    @Test
    fun `repoDisplayNameFallback keeps plain names and generic hosts readable`() {
        "NovelSourcery".repoDisplayNameFallback() shouldBe "NovelSourcery"
        "https://gitlab.com/group/repo".repoDisplayNameFallback() shouldBe "group/repo"
        "https://example.com".repoDisplayNameFallback() shouldBe "example.com"
        "https://www.example.org/foo/bar".repoDisplayNameFallback() shouldBe "foo/bar"
    }
}

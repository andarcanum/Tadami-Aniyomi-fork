package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelUrlUtilsTest {

    @Test
    fun `resolveUrl keeps absolute urls`() {
        resolveUrl("https://example.org/a/b", "https://ignored.com/") shouldBe
            "https://example.org/a/b"
    }

    @Test
    fun `resolveUrl resolves relative urls against base`() {
        resolveUrl("path/item", "https://example.org/base/") shouldBe
            "https://example.org/base/path/item"
    }

    @Test
    fun `resolveUrl resolves relative urls against base without trailing slash`() {
        resolveUrl("path/item", "https://example.org") shouldBe
            "https://example.org/path/item"
    }

    @Test
    fun `resolveUrl keeps relative urls when base is blank`() {
        resolveUrl("path/item", "") shouldBe "path/item"
    }

    @Test
    fun `getPathname extracts path`() {
        getPathname("https://example.org/a/b?c=1#d") shouldBe "/a/b"
    }
}

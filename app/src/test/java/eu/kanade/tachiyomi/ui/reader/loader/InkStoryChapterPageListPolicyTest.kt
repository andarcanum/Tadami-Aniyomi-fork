package eu.kanade.tachiyomi.ui.reader.loader

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InkStoryChapterPageListPolicyTest {

    @Test
    fun `bypasses chapter page list cache for inkstory hosts`() {
        shouldBypassChapterPageListCache("https://inkstory.net") shouldBe true
        shouldBypassChapterPageListCache("https://api.inkstory.net") shouldBe true
    }

    @Test
    fun `bypasses chapter page list cache for nartag hosts`() {
        shouldBypassChapterPageListCache("https://nartag.com") shouldBe true
    }

    @Test
    fun `does not bypass chapter page list cache for other hosts`() {
        shouldBypassChapterPageListCache("https://example.org") shouldBe false
        shouldBypassChapterPageListCache(null) shouldBe false
    }
}

package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.source.model.Page
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ChapterPageListPolicyTest {

    @Test
    fun `dedupes pages by url and keeps first occurrence`() {
        val pages = listOf(
            Page(index = 0, url = "https://example.org/page-1", imageUrl = "https://example.org/image-a"),
            Page(index = 1, url = "https://example.org/page-1", imageUrl = "https://example.org/image-b"),
            Page(index = 2, url = "https://example.org/page-2", imageUrl = "https://example.org/image-c"),
        )

        val deduped = pages.dedupeByStableIdentity()

        deduped.size shouldBe 2
        deduped[0].imageUrl shouldBe "https://example.org/image-a"
        deduped[1].url shouldBe "https://example.org/page-2"
    }

    @Test
    fun `dedupes pages by image url when page url is blank`() {
        val pages = listOf(
            Page(index = 0, url = "", imageUrl = "https://example.org/image-a"),
            Page(index = 1, url = "", imageUrl = "https://example.org/image-a"),
            Page(index = 2, url = "", imageUrl = "https://example.org/image-b"),
        )

        val deduped = pages.dedupeByStableIdentity()

        deduped.size shouldBe 2
        deduped[0].index shouldBe 0
        deduped[1].imageUrl shouldBe "https://example.org/image-b"
    }
}

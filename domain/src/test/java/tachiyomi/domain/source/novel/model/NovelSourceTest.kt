package tachiyomi.domain.source.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelSourceTest {

    @Test
    fun `visualName includes language when present`() {
        val source = NovelSource(
            id = 1L,
            lang = "en",
            name = "Test Source",
            supportsLatest = false,
            isStub = false,
        )

        source.visualName shouldBe "Test Source (EN)"
    }

    @Test
    fun `visualName omits language when empty`() {
        val source = NovelSource(
            id = 1L,
            lang = "",
            name = "Test Source",
            supportsLatest = false,
            isStub = false,
        )

        source.visualName shouldBe "Test Source"
    }

    @Test
    fun `key includes last used suffix when used last`() {
        val source = NovelSource(
            id = 42L,
            lang = "en",
            name = "Test Source",
            supportsLatest = false,
            isStub = false,
            isUsedLast = true,
        )

        source.key() shouldBe "42-lastused"
    }
}

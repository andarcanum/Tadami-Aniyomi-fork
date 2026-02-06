package tachiyomi.domain.entries.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class NovelTest {

    @Test
    fun `create returns default novel`() {
        val novel = Novel.create()

        novel.id shouldBe -1L
        novel.source shouldBe -1L
        novel.url shouldBe ""
        novel.title shouldBe ""
        novel.favorite shouldBe false
        novel.initialized shouldBe false
    }
}

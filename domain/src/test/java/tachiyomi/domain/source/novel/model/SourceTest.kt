package tachiyomi.domain.source.novel.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SourceTest {

    @Test
    fun `visualName uses name when lang empty`() {
        val source = Source(
            id = 1L,
            lang = "",
            name = "Novel",
            supportsLatest = true,
            isStub = false,
        )

        source.visualName shouldBe "Novel"
    }

    @Test
    fun `visualName appends lang when provided`() {
        val source = Source(
            id = 2L,
            lang = "en",
            name = "Novel",
            supportsLatest = true,
            isStub = false,
        )

        source.visualName shouldBe "Novel (EN)"
    }
}

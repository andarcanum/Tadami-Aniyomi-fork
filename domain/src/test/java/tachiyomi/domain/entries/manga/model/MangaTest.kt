package tachiyomi.domain.entries.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class MangaTest {

    @Test
    fun `create exposes default pinned state`() {
        val manga = Manga.create()

        manga.pinned shouldBe false
        manga.copy(pinned = true).pinned shouldBe true
    }
}

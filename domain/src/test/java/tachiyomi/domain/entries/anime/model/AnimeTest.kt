package tachiyomi.domain.entries.anime.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class AnimeTest {

    @Test
    fun `create exposes default pinned state`() {
        val anime = Anime.create()

        anime.pinned shouldBe false
        anime.copy(pinned = true).pinned shouldBe true
    }
}

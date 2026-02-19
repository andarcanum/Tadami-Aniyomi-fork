package eu.kanade.tachiyomi.novelsource.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class SNovelTest {

    @Test
    fun `getGenres splits by multiple delimiters and trims values`() {
        val novel = SNovel.create().apply {
            genre = " Action;Drama|Fantasy/School,Romance\nMystery  "
        }

        novel.getGenres() shouldBe listOf(
            "Action",
            "Drama",
            "Fantasy",
            "School",
            "Romance",
            "Mystery",
        )
    }

    @Test
    fun `getGenres removes duplicates case-insensitively and empty values`() {
        val novel = SNovel.create().apply {
            genre = "Action, action,  ;, ACTION |  "
        }

        novel.getGenres() shouldBe listOf("Action")
    }
}

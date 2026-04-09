package eu.kanade.domain.entries.manga.model

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceMangaRatingResolverTest {

    @Test
    fun `resolve returns groupLe rating from description`() {
        SourceMangaRatingResolver.resolve(
            sourceName = "ReadManga",
            description = "***** 9.3[4.65] (votes: 23)\nОписание",
        ) shouldBe 9.3f
    }

    @Test
    fun `resolve ignores unrelated sources`() {
        SourceMangaRatingResolver.resolve(
            sourceName = "Shikimori",
            description = "***** 9.3[4.65] (votes: 23)\nОписание",
        ).shouldBeNull()
    }
}

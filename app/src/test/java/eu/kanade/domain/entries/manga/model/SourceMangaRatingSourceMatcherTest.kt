package eu.kanade.domain.entries.manga.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SourceMangaRatingSourceMatcherTest {

    @Test
    fun `resolveFamily returns groupLe for groupLe source names`() {
        SourceMangaRatingSourceMatcher.resolveFamily("ReadManga", null) shouldBe SourceMangaRatingFamily.GROUP_LE
    }

    @Test
    fun `resolveFamily returns inkStory for inkstory hosts`() {
        SourceMangaRatingSourceMatcher.resolveFamily("AnySource", "https://api.inkstory.net/v2") shouldBe SourceMangaRatingFamily.INK_STORY
    }

    @Test
    fun `resolveFamily returns madara for madara classes`() {
        SourceMangaRatingSourceMatcher.resolveFamily("Golden Manga", "https://goldenmanga.net", "Madara") shouldBe SourceMangaRatingFamily.MADARA
    }

    @Test
    fun `resolveFamily returns null for unrelated sources`() {
        SourceMangaRatingSourceMatcher.resolveFamily("Shikimori", "https://shikimori.one") shouldBe null
    }
}

package eu.kanade.tachiyomi.ui.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga

class MangaScreenModelDefaultChapterFlagsTest {

    @Test
    fun `untouched manga applies default chapter flags`() {
        shouldApplyDefaultChapterFlags(Manga.create()) shouldBe true
    }

    @Test
    fun `customized manga skips default chapter flags`() {
        shouldApplyDefaultChapterFlags(
            Manga.create().copy(
                chapterFlags = Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_ASC,
            ),
        ) shouldBe false
    }

    @Test
    fun `favorited manga skips default chapter flags`() {
        shouldApplyDefaultChapterFlags(
            Manga.create().copy(favorite = true),
        ) shouldBe false
    }
}

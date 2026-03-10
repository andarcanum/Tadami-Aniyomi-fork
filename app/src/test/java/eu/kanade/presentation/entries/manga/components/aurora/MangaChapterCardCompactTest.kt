package eu.kanade.presentation.entries.manga.components.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaChapterCardCompactTest {

    @Test
    fun `bookmark status badge hides text label`() {
        shouldShowAuroraChapterStatusLabel(AuroraChapterStatus.Bookmark) shouldBe false
    }
}

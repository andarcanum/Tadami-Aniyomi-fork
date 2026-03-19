package eu.kanade.presentation.entries.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelScreenAuroraSelectionModeTest {

    @Test
    fun `selection start auto expands collapsed novel chapters list when preview is truncated`() {
        shouldAutoExpandAuroraNovelChaptersList(
            chaptersExpanded = false,
            totalChapters = 10,
        ) shouldBe true
    }

    @Test
    fun `selection start does not auto expand when novel list is already expanded or short`() {
        shouldAutoExpandAuroraNovelChaptersList(
            chaptersExpanded = true,
            totalChapters = 10,
        ) shouldBe false

        shouldAutoExpandAuroraNovelChaptersList(
            chaptersExpanded = false,
            totalChapters = 5,
        ) shouldBe false
    }

    @Test
    fun `fast scroll auto expands collapsed novel chapters list when preview is truncated`() {
        shouldAutoExpandAuroraNovelChaptersListForFastScroll(
            chaptersExpanded = false,
            totalChapters = 10,
        ) shouldBe true
    }

    @Test
    fun `fast scroll does not auto expand when novel list is already expanded or short`() {
        shouldAutoExpandAuroraNovelChaptersListForFastScroll(
            chaptersExpanded = true,
            totalChapters = 10,
        ) shouldBe false

        shouldAutoExpandAuroraNovelChaptersListForFastScroll(
            chaptersExpanded = false,
            totalChapters = 5,
        ) shouldBe false
    }
}

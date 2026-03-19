package eu.kanade.presentation.entries.manga

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class MangaScreenAuroraSelectionModeTest {

    @Test
    fun `chapter click opens chapter when nothing is selected`() {
        resolveAuroraChapterClickAction(
            isChapterSelected = false,
            isAnyChapterSelected = false,
        ) shouldBe AuroraChapterClickAction.OpenChapter
    }

    @Test
    fun `chapter click selects chapter when another chapter is already selected`() {
        resolveAuroraChapterClickAction(
            isChapterSelected = false,
            isAnyChapterSelected = true,
        ) shouldBe AuroraChapterClickAction.SelectChapter
    }

    @Test
    fun `chapter click unselects already selected chapter`() {
        resolveAuroraChapterClickAction(
            isChapterSelected = true,
            isAnyChapterSelected = true,
        ) shouldBe AuroraChapterClickAction.UnselectChapter
    }

    @Test
    fun `selection start auto-expands collapsed chapters list when more than five items`() {
        shouldAutoExpandAuroraChaptersList(
            chaptersExpanded = false,
            totalChapters = 10,
        ) shouldBe true
    }

    @Test
    fun `selection start does not auto-expand when list is already expanded or short`() {
        shouldAutoExpandAuroraChaptersList(
            chaptersExpanded = true,
            totalChapters = 10,
        ) shouldBe false

        shouldAutoExpandAuroraChaptersList(
            chaptersExpanded = false,
            totalChapters = 5,
        ) shouldBe false
    }

    @Test
    fun `fast scroll expands collapsed chapters list when more than five items`() {
        shouldAutoExpandAuroraChaptersListForFastScroll(
            chaptersExpanded = false,
            totalChapters = 10,
        ) shouldBe true
    }

    @Test
    fun `fast scroll does not expand when chapters list is already expanded or short`() {
        shouldAutoExpandAuroraChaptersListForFastScroll(
            chaptersExpanded = true,
            totalChapters = 10,
        ) shouldBe false

        shouldAutoExpandAuroraChaptersListForFastScroll(
            chaptersExpanded = false,
            totalChapters = 5,
        ) shouldBe false
    }
}

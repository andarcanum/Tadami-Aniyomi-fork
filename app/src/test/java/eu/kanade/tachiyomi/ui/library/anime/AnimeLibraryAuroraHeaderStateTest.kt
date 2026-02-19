package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeLibraryAuroraHeaderStateTest {

    @Test
    fun `resolveAuroraLibrarySection returns matching section for page`() {
        val sections = listOf(
            AnimeLibraryTab.Section.Anime,
            AnimeLibraryTab.Section.Manga,
            AnimeLibraryTab.Section.Novel,
        )

        resolveAuroraLibrarySection(sections, page = 1) shouldBe AnimeLibraryTab.Section.Manga
    }

    @Test
    fun `resolveAuroraLibrarySection returns null for out of range page`() {
        val sections = listOf(AnimeLibraryTab.Section.Anime)

        resolveAuroraLibrarySection(sections, page = 3) shouldBe null
    }

    @Test
    fun `shouldShowAuroraLibraryCategoryTabs is true for anime and manga sections`() {
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Anime) shouldBe true
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Manga) shouldBe true
    }

    @Test
    fun `shouldShowAuroraLibraryCategoryTabs is false for novel and null section`() {
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Novel) shouldBe false
        shouldShowAuroraLibraryCategoryTabs(null) shouldBe false
    }

    @Test
    fun `coerceAuroraLibraryCategoryIndex clamps values inside valid range`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = 7, categoryCount = 3) shouldBe 2
        coerceAuroraLibraryCategoryIndex(requestedIndex = -2, categoryCount = 3) shouldBe 0
    }

    @Test
    fun `coerceAuroraLibraryCategoryIndex returns zero when list is empty`() {
        coerceAuroraLibraryCategoryIndex(requestedIndex = 4, categoryCount = 0) shouldBe 0
    }

    @Test
    fun `shouldShowAuroraLibraryCategoryTabsRow matches legacy tab visibility rules`() {
        shouldShowAuroraLibraryCategoryTabsRow(
            section = AnimeLibraryTab.Section.Anime,
            categoryCount = 1,
            showCategoryTabs = false,
            searchQuery = null,
        ) shouldBe false

        shouldShowAuroraLibraryCategoryTabsRow(
            section = AnimeLibraryTab.Section.Anime,
            categoryCount = 2,
            showCategoryTabs = true,
            searchQuery = null,
        ) shouldBe true

        shouldShowAuroraLibraryCategoryTabsRow(
            section = AnimeLibraryTab.Section.Manga,
            categoryCount = 2,
            showCategoryTabs = false,
            searchQuery = "test",
        ) shouldBe true

        shouldShowAuroraLibraryCategoryTabsRow(
            section = AnimeLibraryTab.Section.Novel,
            categoryCount = 3,
            showCategoryTabs = true,
            searchQuery = "test",
        ) shouldBe false
    }

    @Test
    fun `shouldShowAuroraSearchField keeps search visible when manually expanded`() {
        shouldShowAuroraSearchField(
            isSearchExpanded = false,
            searchQuery = null,
        ) shouldBe false

        shouldShowAuroraSearchField(
            isSearchExpanded = false,
            searchQuery = "query",
        ) shouldBe true

        shouldShowAuroraSearchField(
            isSearchExpanded = true,
            searchQuery = null,
        ) shouldBe true
    }
}

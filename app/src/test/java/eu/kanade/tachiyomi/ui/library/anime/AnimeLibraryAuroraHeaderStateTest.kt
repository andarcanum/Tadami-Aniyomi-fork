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
    fun `shouldShowAuroraLibraryCategoryTabs is true for anime manga and novel sections`() {
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Anime) shouldBe true
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Manga) shouldBe true
        shouldShowAuroraLibraryCategoryTabs(AnimeLibraryTab.Section.Novel) shouldBe true
    }

    @Test
    fun `shouldShowAuroraLibraryCategoryTabs is false for null section`() {
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
    fun `shouldSyncAuroraLibraryCategoryIndex skips sync while categories are not loaded`() {
        shouldSyncAuroraLibraryCategoryIndex(
            categoryCount = 0,
            currentIndex = 2,
            targetIndex = 0,
        ) shouldBe false

        shouldSyncAuroraLibraryCategoryIndex(
            categoryCount = -1,
            currentIndex = 0,
            targetIndex = 0,
        ) shouldBe false
    }

    @Test
    fun `shouldSyncAuroraLibraryCategoryIndex syncs when current and target diverge`() {
        shouldSyncAuroraLibraryCategoryIndex(
            categoryCount = 3,
            currentIndex = 5,
            targetIndex = 2,
        ) shouldBe true
    }

    @Test
    fun `shouldSyncAuroraLibraryCategoryIndex skips sync when already aligned`() {
        shouldSyncAuroraLibraryCategoryIndex(
            categoryCount = 3,
            currentIndex = 1,
            targetIndex = 1,
        ) shouldBe false
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
        ) shouldBe true
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

    @Test
    fun `aurora pinned header menu includes import action when requested`() {
        auroraLibraryPinnedHeaderMenuItems(includeImportEpub = true) shouldBe listOf(
            AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent,
            AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal,
            AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry,
            AuroraLibraryPinnedHeaderMenuItem.ImportEpub,
        )
    }

    @Test
    fun `aurora pinned header menu omits import action by default`() {
        auroraLibraryPinnedHeaderMenuItems(includeImportEpub = false) shouldBe listOf(
            AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent,
            AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal,
            AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry,
        )
    }
}

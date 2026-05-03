package eu.kanade.tachiyomi.ui.library.anime

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class AnimeLibraryAuroraHeaderStateTest {

    private val testAccent = Color(0xFF3366FF)
    private val testAccentVariant = Color(0xFFD0D9FF)
    private val testGlassDark = Color.White.copy(alpha = 0.22f)
    private val testGlassLight = Color(0xE6FFFFFF)
    private val testCardBackgroundDark = Color.White.copy(alpha = 0.12f)
    private val testCardBackgroundLight = Color(0xFFF0F4F8)
    private val testTextPrimaryDark = Color.White
    private val testTextPrimaryLight = Color(0xFF0F172A)
    private val testTextSecondaryDark = Color.White.copy(alpha = 0.7f)
    private val testTextSecondaryLight = Color(0xFF475569)

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
    fun `auroraLibraryCategoryTabColors selected dark tab carries accent tint over glass`() {
        val colors = auroraLibraryCategoryTabColors(
            isSelected = true,
            isDark = true,
            accent = testAccent,
            accentVariant = testAccentVariant,
            glass = testGlassDark,
            cardBackground = testCardBackgroundDark,
            textPrimary = testTextPrimaryDark,
            textSecondary = testTextSecondaryDark,
        )

        colors.tabBackground shouldBe testAccent.copy(alpha = 0.22f).compositeOver(testGlassDark)
        colors.badgeBackground shouldBe Color.White.copy(alpha = 0.20f)
        colors.tabTextColor shouldBe testTextPrimaryDark
        colors.badgeTextColor shouldBe testTextPrimaryDark
    }

    @Test
    fun `auroraLibraryCategoryTabColors selected light tab uses accent variant with neutral badge`() {
        val colors = auroraLibraryCategoryTabColors(
            isSelected = true,
            isDark = false,
            accent = testAccent,
            accentVariant = testAccentVariant,
            glass = testGlassLight,
            cardBackground = testCardBackgroundLight,
            textPrimary = testTextPrimaryLight,
            textSecondary = testTextSecondaryLight,
        )

        colors.tabBackground shouldBe testAccentVariant
        colors.badgeBackground shouldBe Color.White
        colors.tabTextColor shouldBe testTextPrimaryLight
        colors.badgeTextColor shouldBe testTextPrimaryLight
    }

    @Test
    fun `auroraLibraryCategoryTabColors unselected dark tab is transparent with subtle badge`() {
        val colors = auroraLibraryCategoryTabColors(
            isSelected = false,
            isDark = true,
            accent = testAccent,
            accentVariant = testAccentVariant,
            glass = testGlassDark,
            cardBackground = testCardBackgroundDark,
            textPrimary = testTextPrimaryDark,
            textSecondary = testTextSecondaryDark,
        )

        colors.tabBackground shouldBe Color.Transparent
        colors.badgeBackground shouldBe testCardBackgroundDark
        colors.tabTextColor shouldBe testTextSecondaryDark
        colors.badgeTextColor shouldBe testTextSecondaryDark
    }

    @Test
    fun `auroraLibraryCategoryTabColors unselected light tab uses neutral surface but white badge`() {
        val colors = auroraLibraryCategoryTabColors(
            isSelected = false,
            isDark = false,
            accent = testAccent,
            accentVariant = testAccentVariant,
            glass = testGlassLight,
            cardBackground = testCardBackgroundLight,
            textPrimary = testTextPrimaryLight,
            textSecondary = testTextSecondaryLight,
        )

        colors.tabBackground shouldBe testCardBackgroundLight
        // White chip on the very-light cardBackground keeps the badge readable.
        colors.badgeBackground shouldBe Color.White
        colors.badgeBackground shouldNotBe colors.tabBackground
        colors.tabTextColor shouldBe testTextSecondaryLight
        colors.badgeTextColor shouldBe testTextSecondaryLight
    }

    @Test
    fun `auroraLibraryCategoryTabColors selected light badge no longer stacks accent on accent`() {
        val colors = auroraLibraryCategoryTabColors(
            isSelected = true,
            isDark = false,
            accent = testAccent,
            accentVariant = testAccentVariant,
            glass = testGlassLight,
            cardBackground = testCardBackgroundLight,
            textPrimary = testTextPrimaryLight,
            textSecondary = testTextSecondaryLight,
        )

        // Regression guard for issue: selected light badge must NOT be the accent itself,
        // otherwise it visually merges with the accentVariant tab background.
        colors.badgeBackground shouldNotBe testAccent
        colors.badgeBackground shouldNotBe testAccentVariant
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

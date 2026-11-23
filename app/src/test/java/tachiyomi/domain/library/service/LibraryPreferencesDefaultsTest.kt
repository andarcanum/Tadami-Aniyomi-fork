package tachiyomi.domain.library.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.AuroraLibraryCardStyle
import tachiyomi.domain.library.model.AuroraLibraryListStyle
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.novel.model.NovelLibrarySort

class LibraryPreferencesDefaultsTest {

    @Test
    fun `library defaults match aurora first launch expectations`() {
        val prefs = LibraryPreferences(InMemoryPreferenceStore())

        prefs.animeSortingMode().get() shouldBe AnimeLibrarySort(
            type = AnimeLibrarySort.Type.LastSeen,
            direction = AnimeLibrarySort.Direction.Descending,
        )
        prefs.sortEpisodeBySourceOrNumber().get() shouldBe Anime.EPISODE_SORTING_SOURCE
        prefs.mangaSortingMode().get() shouldBe MangaLibrarySort(
            type = MangaLibrarySort.Type.LastRead,
            direction = MangaLibrarySort.Direction.Descending,
        )
        prefs.sortChapterBySourceOrNumber().get() shouldBe Manga.CHAPTER_SORTING_SOURCE
        prefs.novelSortingMode().get() shouldBe NovelLibrarySort(
            type = NovelLibrarySort.Type.LastRead,
            direction = NovelLibrarySort.Direction.Descending,
        )
        prefs.sortNovelChapterBySourceOrNumber().get() shouldBe Novel.CHAPTER_SORTING_SOURCE

        prefs.displayMode().get() shouldBe LibraryDisplayMode.ComfortableGrid
        prefs.animeDisplayMode().get() shouldBe LibraryDisplayMode.ComfortableGrid
        prefs.mangaDisplayMode().get() shouldBe LibraryDisplayMode.ComfortableGrid
        prefs.novelDisplayMode().get() shouldBe LibraryDisplayMode.ComfortableGrid

        prefs.animePortraitColumns().get() shouldBe 3
        prefs.mangaPortraitColumns().get() shouldBe 3
        prefs.novelPortraitColumns().get() shouldBe 3
        prefs.animeLandscapeColumns().get() shouldBe 3
        prefs.mangaLandscapeColumns().get() shouldBe 3
        prefs.novelLandscapeColumns().get() shouldBe 3

        prefs.showContinueViewingButton().get() shouldBe true
        prefs.auroraLibraryCardStyle().get() shouldBe AuroraLibraryCardStyle.GlowContour
        prefs.auroraLibraryListStyle().get() shouldBe AuroraLibraryListStyle.Banner
        prefs.auroraLibraryListSize().get() shouldBe 0

        prefs.autoUpdateItemRestrictions().get() shouldBe emptySet()
        prefs.autoUpdateWifiAndChargingOnly().get() shouldBe false
    }

    @Test
    fun `language filter prefs default to empty set`() {
        val prefs = LibraryPreferences(InMemoryPreferenceStore())

        prefs.filterMangaLanguages().get() shouldBe emptySet()
        prefs.filterNovelLanguages().get() shouldBe emptySet()
        prefs.filterAnimeLanguages().get() shouldBe emptySet()
    }

    @Test
    fun `aurora library card style preference persists`() {
        val prefs = LibraryPreferences(InMemoryPreferenceStore())
        val cardStylePref = prefs.auroraLibraryCardStyle()

        cardStylePref.set(AuroraLibraryCardStyle.GlowContour)

        cardStylePref.get() shouldBe AuroraLibraryCardStyle.GlowContour
    }

    @Test
    fun `aurora library list style and size preferences persist`() {
        val prefs = LibraryPreferences(InMemoryPreferenceStore())
        val listStylePref = prefs.auroraLibraryListStyle()
        val listSizePref = prefs.auroraLibraryListSize()

        listStylePref.set(AuroraLibraryListStyle.Compact)
        listSizePref.set(3)

        listStylePref.get() shouldBe AuroraLibraryListStyle.Compact
        listSizePref.get() shouldBe 3

        listStylePref.set(AuroraLibraryListStyle.Comfortable)
        listStylePref.get() shouldBe AuroraLibraryListStyle.Comfortable
    }
}

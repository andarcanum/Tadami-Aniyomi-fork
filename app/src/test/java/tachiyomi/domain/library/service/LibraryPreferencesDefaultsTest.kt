package tachiyomi.domain.library.service

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.library.anime.model.AnimeLibrarySort
import tachiyomi.domain.library.manga.model.MangaLibrarySort
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
        prefs.mangaSortingMode().get() shouldBe MangaLibrarySort(
            type = MangaLibrarySort.Type.LastRead,
            direction = MangaLibrarySort.Direction.Descending,
        )
        prefs.novelSortingMode().get() shouldBe NovelLibrarySort(
            type = NovelLibrarySort.Type.LastRead,
            direction = NovelLibrarySort.Direction.Descending,
        )

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
    }
}

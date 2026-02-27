package eu.kanade.tachiyomi.ui.library.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeLibraryAuroraSelectionStateTest {

    @Test
    fun `resolveAuroraLibrarySelectionMode returns anime selection when aurora is disabled`() {
        resolveAuroraLibrarySelectionMode(
            isAurora = false,
            section = AnimeLibraryTab.Section.Manga,
            animeSelectionMode = true,
            mangaSelectionMode = false,
            novelSelectionMode = false,
        ) shouldBe true
    }

    @Test
    fun `resolveAuroraLibrarySelectionMode returns manga selection for manga section`() {
        resolveAuroraLibrarySelectionMode(
            isAurora = true,
            section = AnimeLibraryTab.Section.Manga,
            animeSelectionMode = false,
            mangaSelectionMode = true,
            novelSelectionMode = false,
        ) shouldBe true
    }

    @Test
    fun `resolveAuroraLibrarySelectionMode returns anime selection for anime section`() {
        resolveAuroraLibrarySelectionMode(
            isAurora = true,
            section = AnimeLibraryTab.Section.Anime,
            animeSelectionMode = true,
            mangaSelectionMode = false,
            novelSelectionMode = false,
        ) shouldBe true
    }

    @Test
    fun `resolveAuroraLibrarySelectionMode returns novel selection for novel section`() {
        resolveAuroraLibrarySelectionMode(
            isAurora = true,
            section = AnimeLibraryTab.Section.Novel,
            animeSelectionMode = false,
            mangaSelectionMode = false,
            novelSelectionMode = true,
        ) shouldBe true
    }
}

package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeHubHeroReloadTest {

    @Test
    fun `anime hero episode reloads when episode changes inside the same anime`() {
        shouldReloadAnimeHomeHeroEpisode(
            previousHeroAnimeId = 1L,
            previousHeroEpisodeId = 10L,
            currentHeroAnimeId = 1L,
            currentHeroEpisodeId = 11L,
        ) shouldBe true

        shouldReloadAnimeHomeHeroEpisode(
            previousHeroAnimeId = 1L,
            previousHeroEpisodeId = 10L,
            currentHeroAnimeId = 1L,
            currentHeroEpisodeId = 10L,
        ) shouldBe false
    }

    @Test
    fun `manga hero chapter reloads when chapter changes inside the same manga`() {
        shouldReloadMangaHomeHeroChapter(
            previousHeroMangaId = 2L,
            previousHeroChapterId = 20L,
            currentHeroMangaId = 2L,
            currentHeroChapterId = 21L,
        ) shouldBe true

        shouldReloadMangaHomeHeroChapter(
            previousHeroMangaId = 2L,
            previousHeroChapterId = 20L,
            currentHeroMangaId = 2L,
            currentHeroChapterId = 20L,
        ) shouldBe false
    }
}

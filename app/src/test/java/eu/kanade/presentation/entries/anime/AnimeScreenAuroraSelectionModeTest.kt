package eu.kanade.presentation.entries.anime

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeScreenAuroraSelectionModeTest {

    @Test
    fun `episode click opens episode when nothing is selected`() {
        resolveAuroraEpisodeClickAction(
            isEpisodeSelected = false,
            isAnyEpisodeSelected = false,
        ) shouldBe AuroraEpisodeClickAction.OpenEpisode
    }

    @Test
    fun `episode click selects episode when another episode is already selected`() {
        resolveAuroraEpisodeClickAction(
            isEpisodeSelected = false,
            isAnyEpisodeSelected = true,
        ) shouldBe AuroraEpisodeClickAction.SelectEpisode
    }

    @Test
    fun `episode click unselects already selected episode`() {
        resolveAuroraEpisodeClickAction(
            isEpisodeSelected = true,
            isAnyEpisodeSelected = true,
        ) shouldBe AuroraEpisodeClickAction.UnselectEpisode
    }

    @Test
    fun `selection start auto-expands collapsed episodes list when more than five items`() {
        shouldAutoExpandAuroraEpisodesList(
            episodesExpanded = false,
            totalEpisodes = 10,
        ) shouldBe true
    }

    @Test
    fun `selection start does not auto-expand when list is already expanded or short`() {
        shouldAutoExpandAuroraEpisodesList(
            episodesExpanded = true,
            totalEpisodes = 10,
        ) shouldBe false

        shouldAutoExpandAuroraEpisodesList(
            episodesExpanded = false,
            totalEpisodes = 5,
        ) shouldBe false
    }
}

package eu.kanade.presentation.entries.anime

import eu.kanade.presentation.entries.anime.components.aurora.AuroraEpisodeStatus
import eu.kanade.presentation.entries.anime.components.aurora.shouldShowAuroraEpisodeStatusLabel
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeScreenAuroraSelectionModeTest {

    @Test
    fun `hero content hides while episode selection mode is active`() {
        shouldShowAnimeAuroraHeroContent(
            useTwoPaneLayout = false,
            firstVisibleItemIndex = 0,
            scrollOffset = 0,
            heroThreshold = 100,
            isSelectionMode = true,
        ) shouldBe false
    }

    @Test
    fun `aurora selection controls are rendered in bottom stack`() {
        auroraSelectionControlsPlacement() shouldBe AuroraSelectionControlsPlacement.BottomStack
    }

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

    @Test
    fun `fast scroll expands collapsed episodes list when more than five items`() {
        shouldAutoExpandAuroraEpisodesListForFastScroll(
            episodesExpanded = false,
            totalEpisodes = 10,
        ) shouldBe true
    }

    @Test
    fun `fast scroll does not expand when episodes list is already expanded or short`() {
        shouldAutoExpandAuroraEpisodesListForFastScroll(
            episodesExpanded = true,
            totalEpisodes = 10,
        ) shouldBe false

        shouldAutoExpandAuroraEpisodesListForFastScroll(
            episodesExpanded = false,
            totalEpisodes = 5,
        ) shouldBe false
    }

    @Test
    fun `aurora selection summary uses compact icon actions`() {
        shouldUseCompactAuroraSelectionActions() shouldBe true
    }

    @Test
    fun `bookmark status badge hides text label while filler and seen keep it`() {
        shouldShowAuroraEpisodeStatusLabel(AuroraEpisodeStatus.Bookmark) shouldBe false
        shouldShowAuroraEpisodeStatusLabel(AuroraEpisodeStatus.Fillermark) shouldBe true
        shouldShowAuroraEpisodeStatusLabel(AuroraEpisodeStatus.Seen) shouldBe true
    }
}

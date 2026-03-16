package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.HomeHeaderLayoutElement
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.domain.ui.model.HomeStreakCounterStyle
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

class HomeHubTabLayoutTest {

    @Test
    fun `home hub rim light stops match hero border style`() {
        homeHubRimLightAlphaStops() shouldBe listOf(
            0.00f to 0.15f,
            0.28f to 0.05f,
            0.62f to 0.00f,
            1.00f to 0.00f,
        )
    }

    @Test
    fun `wrapped homehub section layout is enabled only on tablet classes`() {
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.Phone) shouldBe false
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletCompact) shouldBe true
        shouldUseHomeHubWrappedSections(AuroraDeviceClass.TabletExpanded) shouldBe true
    }

    @Test
    fun `home header editor visible elements omit hidden greeting and streak`() {
        homeHeaderLayoutEditorVisibleElements(
            showGreeting = false,
            showStreak = false,
        ) shouldBe listOf(
            HomeHeaderLayoutElement.Nickname,
            HomeHeaderLayoutElement.Avatar,
        )
    }

    @Test
    fun `home streak style parser falls back to no badge`() {
        HomeStreakCounterStyle.fromKey("unknown") shouldBe HomeStreakCounterStyle.NoBadge
    }

    @Test
    fun `home streak style picker options keep stable visual order`() {
        resolveHomeStreakStylePickerOptions() shouldBe listOf(
            HomeStreakCounterStyle.ClassicBadge,
            HomeStreakCounterStyle.NumberBadgeOnly,
            HomeStreakCounterStyle.NoBadge,
        )
    }

    @Test
    fun `reserve hero slot while loading prevents top item jump`() {
        shouldReserveHomeHubHeroSlot(
            hasHero = false,
            isLoading = true,
            showWelcome = false,
            isFiltering = false,
        ) shouldBe true
    }

    @Test
    fun `do not reserve hero slot once loading is finished`() {
        shouldReserveHomeHubHeroSlot(
            hasHero = false,
            isLoading = false,
            showWelcome = false,
            isFiltering = false,
        ) shouldBe false
    }

    @Test
    fun `aurora anime hero cta uses resume with play icon when progress exists`() {
        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Anime,
            progressNumber = 7.0,
            mode = HomeHeroCtaMode.Aurora,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = MR.strings.action_resume,
            progressLabelRes = AYMR.strings.aurora_episode_progress,
            icon = HomeHubHeroActionIcon.Play,
        )
    }

    @Test
    fun `aurora manga hero cta uses resume with read icon when progress exists`() {
        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Manga,
            progressNumber = 14.0,
            mode = HomeHeroCtaMode.Aurora,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = MR.strings.action_resume,
            progressLabelRes = AYMR.strings.aurora_chapter_progress,
            icon = HomeHubHeroActionIcon.Play,
        )
    }

    @Test
    fun `aurora novel hero cta uses resume with read icon when progress exists`() {
        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Novel,
            progressNumber = 4.0,
            mode = HomeHeroCtaMode.Aurora,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = MR.strings.action_resume,
            progressLabelRes = AYMR.strings.aurora_chapter_progress,
            icon = HomeHubHeroActionIcon.Play,
        )
    }

    @Test
    fun `aurora hero cta falls back to media-specific start labels without progress`() {
        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Anime,
            progressNumber = 0.0,
            mode = HomeHeroCtaMode.Aurora,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = AYMR.strings.aurora_play,
            progressLabelRes = AYMR.strings.aurora_episode_progress,
            icon = HomeHubHeroActionIcon.Play,
        )

        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Manga,
            progressNumber = 0.0,
            mode = HomeHeroCtaMode.Aurora,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = AYMR.strings.aurora_read,
            progressLabelRes = AYMR.strings.aurora_chapter_progress,
            icon = HomeHubHeroActionIcon.Play,
        )
    }

    @Test
    fun `classic hero cta keeps legacy labels and play icon`() {
        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Anime,
            progressNumber = 9.0,
            mode = HomeHeroCtaMode.Classic,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = AYMR.strings.aurora_play,
            progressLabelRes = AYMR.strings.aurora_episode_progress,
            icon = HomeHubHeroActionIcon.Play,
        )

        resolveHomeHubHeroActionSpec(
            section = HomeHubSection.Manga,
            progressNumber = 9.0,
            mode = HomeHeroCtaMode.Classic,
        ) shouldBe HomeHubHeroActionSpec(
            labelRes = AYMR.strings.aurora_read,
            progressLabelRes = AYMR.strings.aurora_chapter_progress,
            icon = HomeHubHeroActionIcon.Play,
        )
    }

    @Test
    fun `hero cta visual mode switches between aurora glass and classic solid`() {
        resolveHomeHubHeroButtonVisualMode(HomeHeroCtaMode.Aurora) shouldBe
            HomeHubHeroButtonVisualMode.AuroraGlass
        resolveHomeHubHeroButtonVisualMode(HomeHeroCtaMode.Classic) shouldBe
            HomeHubHeroButtonVisualMode.ClassicSolid
    }

    @Test
    fun `home recent card mode switches between poster aurora and classic aurora card`() {
        resolveHomeHubRecentCardRenderMode(HomeHubRecentCardMode.Aurora) shouldBe
            HomeHubRecentCardRenderMode.AuroraPoster
        resolveHomeHubRecentCardRenderMode(HomeHubRecentCardMode.Classic) shouldBe
            HomeHubRecentCardRenderMode.ClassicAuroraCard
    }

    @Test
    fun `home recent aurora poster spec preserves vertical poster priority`() {
        resolveHomeHubRecentPosterCardSpec(AuroraDeviceClass.Phone) shouldBe
            HomeHubRecentPosterCardSpec(
                posterAspectRatio = 0.9f,
                titleMaxLines = 2,
                textHorizontalPaddingDp = 2,
                textTopSpacingDp = 8,
                textBlockMinHeightDp = 58,
            )
    }

    @Test
    fun `home recent aurora poster surface uses subtle translucent container`() {
        resolveHomeHubRecentPosterSurfaceSpec(isDark = true) shouldBe
            HomeHubRecentPosterSurfaceSpec(
                containerAlpha = 0.06f,
                posterAlpha = 0.10f,
            )
    }
}

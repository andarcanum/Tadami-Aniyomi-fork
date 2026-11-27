package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.ui.player.controls.components.sheets.HosterState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HosterStartupPlannerTest {

    @Test
    fun `prioritizes preferred player and dubbing before other hosters`() {
        val hosters = listOf(
            Hoster(hosterUrl = "u0", hosterName = "AniDUB", playerId = "kodik", dubbingLabel = "AniDUB"),
            Hoster(hosterUrl = "u1", hosterName = "AniLibria.TV", playerId = "kodik", dubbingLabel = "AniLibria.TV"),
            Hoster(hosterUrl = "u2", hosterName = "AniLibria.TV", playerId = "cdn", dubbingLabel = "AniLibria.TV"),
            Hoster(hosterUrl = "u3", hosterName = "JAM", playerId = "cdn", dubbingLabel = "JAM"),
        )
        val prefs = PlaybackSelectionPreferences(
            preferredPlayer = PlaybackPlayerPreference.CDN,
            preferredDubbingCdn = "AniLibria.TV",
        )

        val order = HosterStartupPlanner.orderIndicesForStartup(hosters, prefs)

        order shouldBe listOf(2, 3, 0, 1)
    }

    @Test
    fun `prioritizes legacy dubbing match when hosters lack playerId`() {
        val hosters = listOf(
            Hoster(hosterUrl = "u0", hosterName = "AniDUB"),
            Hoster(hosterUrl = "u1", hosterName = "AniLibria.TV"),
            Hoster(hosterUrl = "u2", hosterName = "Dream Cast"),
        )
        val prefs = PlaybackSelectionPreferences(
            preferredDubbingKodik = "Dream Cast",
        )

        val order = HosterStartupPlanner.orderIndicesForStartup(hosters, prefs)

        order shouldBe listOf(2, 0, 1)
    }

    @Test
    fun `skips lazy hosters in startup order`() {
        val hosters = listOf(
            Hoster(hosterUrl = "u0", hosterName = "LazyOne", lazy = true),
            Hoster(hosterUrl = "u1", hosterName = "AniDUB", lazy = false),
            Hoster(hosterUrl = "u2", hosterName = "AniLibria", lazy = false),
        )

        val order = HosterStartupPlanner.orderIndicesForStartup(hosters, PlaybackSelectionPreferences())

        order shouldBe listOf(1, 2)
    }

    @Test
    fun `selectVideoIndexForHoster respects preferred quality and skips failed videos`() {
        val v1080 = Video(videoUrl = "https://cdn/1080.m3u8", videoTitle = "1080p", initialized = true)
        val v720 = Video(videoUrl = "https://cdn/720.m3u8", videoTitle = "720p", initialized = true)
        val v480 = Video(videoUrl = "https://cdn/480.m3u8", videoTitle = "480p", initialized = true)
        val ready = HosterState.Ready(
            name = "AniDUB",
            videoList = listOf(v1080, v720, v480),
            videoState = listOf(Video.State.ERROR, Video.State.QUEUE, Video.State.QUEUE),
            playerId = "cdn",
        )

        HosterStartupPlanner.selectVideoIndexForHoster(
            hoster = ready,
            preferences = PlaybackSelectionPreferences(preferredQualityCdn = "1080p"),
        ) shouldBe 1

        HosterStartupPlanner.selectVideoIndexForHoster(
            hoster = ready,
            preferences = PlaybackSelectionPreferences(preferredQualityCdn = "480p"),
        ) shouldBe 2
    }

    @Test
    fun `selectNextIdleHosterIndex skips excluded and non-idle hosters`() {
        val states = listOf(
            HosterState.Ready(name = "A", videoList = emptyList(), videoState = emptyList()),
            HosterState.Error(name = "B"),
            HosterState.Idle(name = "C"),
            HosterState.Idle(name = "D"),
        )

        HosterStartupPlanner.selectNextIdleHosterIndex(states, excludedHosterIndices = setOf(0, 2)) shouldBe 3
        HosterStartupPlanner.selectNextIdleHosterIndex(states, excludedHosterIndices = setOf(0, 2, 3)) shouldBe null
    }
}

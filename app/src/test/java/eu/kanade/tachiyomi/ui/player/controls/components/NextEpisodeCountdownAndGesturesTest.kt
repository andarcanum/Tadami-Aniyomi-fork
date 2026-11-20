package eu.kanade.tachiyomi.ui.player.controls.components

import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.VideoAspect
import eu.kanade.tachiyomi.ui.player.controls.components.dialogs.computeEpisodeProgressFraction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NextEpisodeCountdownAndGesturesTest {
    @Test
    fun `computeNextEpisodeCountdownSeconds shows countdown only in final 15 seconds when eligible`() {
        computeNextEpisodeCountdownSeconds(
            positionSec = 1430f,
            durationSec = 1440f,
            hasNextEpisode = true,
            autoPlayEnabled = true,
            repeatEpisode = false,
            dismissedEpisodeId = null,
            currentEpisodeId = 7L,
        ) shouldBe 10

        // Dismissed for current episode
        computeNextEpisodeCountdownSeconds(
            positionSec = 1430f,
            durationSec = 1440f,
            hasNextEpisode = true,
            autoPlayEnabled = true,
            repeatEpisode = false,
            dismissedEpisodeId = 7L,
            currentEpisodeId = 7L,
        ) shouldBe null

        // Repeat episode suppresses next-episode countdown
        computeNextEpisodeCountdownSeconds(
            positionSec = 1430f,
            durationSec = 1440f,
            hasNextEpisode = true,
            autoPlayEnabled = true,
            repeatEpisode = true,
            dismissedEpisodeId = null,
            currentEpisodeId = 7L,
        ) shouldBe null
    }

    @Test
    fun `resolvePinchAspectTransition toggles Crop and Fit cleanly with hysteresis`() {
        resolvePinchAspectTransition(1.22f, VideoAspect.Fit) shouldBe VideoAspect.Crop
        resolvePinchAspectTransition(1.22f, VideoAspect.Crop) shouldBe null
        resolvePinchAspectTransition(0.80f, VideoAspect.Crop) shouldBe VideoAspect.Fit
        resolvePinchAspectTransition(1.05f, VideoAspect.Fit) shouldBe null
    }

    @Test
    fun `resolveChapterTitleAtPosition returns active chapter and ignores blank names`() {
        val chapters = listOf(
            Segment("", 0f),
            Segment("Opening", 90f),
            Segment("Part A", 180f),
        )
        resolveChapterTitleAtPosition(chapters, 45f) shouldBe null
        resolveChapterTitleAtPosition(chapters, 120f) shouldBe "Opening"
        resolveChapterTitleAtPosition(chapters, 240f) shouldBe "Part A"
    }

    @Test
    fun `computeEpisodeProgressFraction computes ratio only for partially watched episodes`() {
        computeEpisodeProgressFraction(lastSecondSeenMs = 600_000L, totalSecondsMs = 1_200_000L, seen = false) shouldBe 0.5f
        computeEpisodeProgressFraction(lastSecondSeenMs = 600_000L, totalSecondsMs = 1_200_000L, seen = true) shouldBe null
        computeEpisodeProgressFraction(lastSecondSeenMs = 0L, totalSecondsMs = 1_200_000L, seen = false) shouldBe null
    }
}

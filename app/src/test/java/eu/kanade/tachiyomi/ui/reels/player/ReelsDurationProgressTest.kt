package eu.kanade.tachiyomi.ui.reels.player

import androidx.media3.common.C
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsDurationProgressTest {

    @Test
    fun `player duration wins over metadata fallback when positive`() {
        resolveReelDurationMs(playerDurationMs = 185_000L, fallbackDurationSec = 180f) shouldBe 185_000L
        resolveReelDurationMs(playerDurationMs = 12_000L, fallbackDurationSec = 0f) shouldBe 12_000L
    }

    @Test
    fun `metadata fallback is used when player reports TIME_UNSET or non-positive duration`() {
        resolveReelDurationMs(playerDurationMs = C.TIME_UNSET, fallbackDurationSec = 600f) shouldBe 600_000L
        resolveReelDurationMs(playerDurationMs = 0L, fallbackDurationSec = 45.5f) shouldBe 45_500L
        resolveReelDurationMs(playerDurationMs = -1L, fallbackDurationSec = 120f) shouldBe 120_000L
    }

    @Test
    fun `unknown duration returns zero when both player and metadata are missing or invalid`() {
        resolveReelDurationMs(playerDurationMs = C.TIME_UNSET, fallbackDurationSec = 0f) shouldBe 0L
        resolveReelDurationMs(playerDurationMs = C.TIME_UNSET, fallbackDurationSec = -10f) shouldBe 0L
        resolveReelDurationMs(playerDurationMs = C.TIME_UNSET, fallbackDurationSec = Float.NaN) shouldBe 0L
        resolveReelDurationMs(playerDurationMs = C.TIME_UNSET, fallbackDurationSec = Float.POSITIVE_INFINITY) shouldBe
            0L
    }

    @Test
    fun `long video with TIME_UNSET uses metadata duration instead of racing 5s dwell bar`() {
        val effectiveDurationMs = resolveReelDurationMs(
            playerDurationMs = C.TIME_UNSET,
            fallbackDurationSec = 600f,
        )
        val progressAt5s = resolveReelPlaybackProgress(
            currentPositionMs = 5_000L,
            effectiveDurationMs = effectiveDurationMs,
            isFirstFrameRendered = true,
            isEndedOrHolding = false,
            dwellMs = 5_000L,
        )
        progressAt5s shouldBe (5_000f / 600_000f)
    }

    @Test
    fun `playing video with completely unknown duration stays at zero instead of racing to end`() {
        resolveReelPlaybackProgress(
            currentPositionMs = 4_000L,
            effectiveDurationMs = 0L,
            isFirstFrameRendered = true,
            isEndedOrHolding = false,
            dwellMs = 4_000L,
        ) shouldBe 0f

        resolveReelPlaybackProgress(
            currentPositionMs = 15_000L,
            effectiveDurationMs = 0L,
            isFirstFrameRendered = true,
            isEndedOrHolding = false,
            dwellMs = 15_000L,
        ) shouldBe 0f
    }

    @Test
    fun `zero-duration clip holding minimum dwell after ending still reports dwell progress`() {
        resolveReelPlaybackProgress(
            currentPositionMs = 0L,
            effectiveDurationMs = 0L,
            isFirstFrameRendered = true,
            isEndedOrHolding = true,
            dwellMs = 2_500L,
            minDwellMs = 5_000L,
        ) shouldBe 0.5f

        resolveReelPlaybackProgress(
            currentPositionMs = 0L,
            effectiveDurationMs = 0L,
            isFirstFrameRendered = true,
            isEndedOrHolding = true,
            dwellMs = 5_000L,
            minDwellMs = 5_000L,
        ) shouldBe 1f
    }

    @Test
    fun `returns null before first frame when duration is unknown`() {
        resolveReelPlaybackProgress(
            currentPositionMs = 0L,
            effectiveDurationMs = 0L,
            isFirstFrameRendered = false,
            isEndedOrHolding = false,
            dwellMs = 0L,
        ).shouldBeNull()
    }

    @Test
    fun `loopback reel urls are detected for local streaming proxies`() {
        isLoopbackReelUrl("http://127.0.0.1:42921/video?fileId=7450&token=abc") shouldBe true
        isLoopbackReelUrl("http://localhost:8080/stream.mp4") shouldBe true
        isLoopbackReelUrl("http://[::1]:42921/video") shouldBe true
        isLoopbackReelUrl("https://cdn.example.com/video.mp4") shouldBe false
        isLoopbackReelUrl("not a url") shouldBe false
    }
}

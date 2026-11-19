package eu.kanade.tachiyomi.ui.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DisplayRefreshRateMatcherTest {

    @Test
    fun `switches 23_976fps anime from 60hz to 120hz on 60_90_120hz panel`() {
        val modes = listOf(
            DisplayModeInfo(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 90f),
            DisplayModeInfo(modeId = 3, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
        )

        DisplayRefreshRateMatcher.selectBestModeId(
            videoFps = 23.976,
            availableModes = modes,
            currentModeId = 1,
        ) shouldBe 3
    }

    @Test
    fun `picks 48hz over 60hz for 24fps when 120hz is unavailable`() {
        val modes = listOf(
            DisplayModeInfo(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 48f),
        )

        DisplayRefreshRateMatcher.selectBestModeId(
            videoFps = 24.0,
            availableModes = modes,
            currentModeId = 1,
        ) shouldBe 2
    }

    @Test
    fun `never switches to a different physical resolution`() {
        val modes = listOf(
            DisplayModeInfo(modeId = 1, physicalWidth = 1440, physicalHeight = 3200, refreshRate = 60f),
            DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
        )

        DisplayRefreshRateMatcher.selectBestModeId(
            videoFps = 23.976,
            availableModes = modes,
            currentModeId = 1,
        ) shouldBe null
    }

    @Test
    fun `returns null when already on optimal refresh rate or fps is invalid`() {
        val modes = listOf(
            DisplayModeInfo(modeId = 1, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 60f),
            DisplayModeInfo(modeId = 2, physicalWidth = 1080, physicalHeight = 2400, refreshRate = 120f),
        )

        DisplayRefreshRateMatcher.selectBestModeId(23.976, modes, currentModeId = 2) shouldBe null
        DisplayRefreshRateMatcher.selectBestModeId(0.0, modes, currentModeId = 1) shouldBe null
    }
}

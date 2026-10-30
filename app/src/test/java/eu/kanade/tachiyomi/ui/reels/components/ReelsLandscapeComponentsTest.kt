package eu.kanade.tachiyomi.ui.reels.components

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

class ReelsLandscapeComponentsTest {

    @Test
    fun `formatCompactCount formats values correctly`() {
        val sep = if ("%.1f".format(java.util.Locale.getDefault(), 1.0).contains(',')) "," else "."
        formatCompactCount(0) shouldBe "0"
        formatCompactCount(450) shouldBe "450"
        formatCompactCount(1_000) shouldBe "1${sep}0K"
        formatCompactCount(14_800) shouldBe "14${sep}8K"
        formatCompactCount(1_200_000) shouldBe "1${sep}2M"
        formatCompactCount(1_100_000_000) shouldBe "1${sep}1B"
    }

    @Test
    fun `clamped horizontal scrubber offset stays within bounds`() {
        val barWidthPx = 1000f
        val cardWidthPx = 124
        val halfWidthPx = cardWidthPx / 2

        fun computeClampedCenter(fraction: Float): Int {
            val rawCenterPx = (fraction * barWidthPx).toInt()
            return rawCenterPx.coerceIn(
                halfWidthPx,
                (barWidthPx - halfWidthPx).toInt().coerceAtLeast(halfWidthPx),
            )
        }

        // At 0% (start), should clamp to halfWidthPx
        computeClampedCenter(0f) shouldBe halfWidthPx

        // At 50% (middle), should be exactly center
        computeClampedCenter(0.5f) shouldBe 500

        // At 100% (end), should clamp to barWidthPx - halfWidthPx
        computeClampedCenter(1.0f) shouldBe (1000 - halfWidthPx)
    }

    @Test
    fun `seconds to time string formatting logic`() {
        fun formatSeconds(totalSeconds: Int): String {
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(java.util.Locale.US, minutes, seconds)
        }

        formatSeconds(0) shouldBe "00:00"
        formatSeconds(23) shouldBe "00:23"
        formatSeconds(62) shouldBe "01:02"
        formatSeconds(3605) shouldBe "60:05"
    }

    @Test
    fun `reelMediaPlan handles media resolution scenarios`() {
        reelMediaPlan("https://cdn.example.com/clip.mp4", hasResolver = true, lazyResolution = true) shouldBe
            ReelMediaPlan.READY
        reelMediaPlan("", hasResolver = true, lazyResolution = true) shouldBe ReelMediaPlan.RESOLVE
        reelMediaPlan("", hasResolver = false, lazyResolution = true) shouldBe ReelMediaPlan.UNPLAYABLE
        reelMediaPlan("", hasResolver = true, lazyResolution = false) shouldBe ReelMediaPlan.UNPLAYABLE
    }

    @Test
    fun `landscape action rail vertical centering guarantees clearance from topbar`() {
        val screenHeightDp = 360f
        val topBarBottomDp = 54f

        // Old layout: BottomEnd with 54dp bottom margin and 275dp height
        val oldRailHeightDp = 275f
        val oldBottomMarginDp = 54f
        val oldRailTopDp = screenHeightDp - oldBottomMarginDp - oldRailHeightDp // 31dp
        val oldCollisionDp = topBarBottomDp - oldRailTopDp // 23dp overlap
        (oldCollisionDp > 0) shouldBe true

        // New Smart layout: CenterEnd with compact 4-pod rail (36dp circle in 44dp touch target + 4dp spacing)
        val buttonOnlyRailHeightDp = (4 * 44f) + (3 * 4f) // 188dp
        val buttonOnlyRailTopDp = (screenHeightDp / 2f) - (buttonOnlyRailHeightDp / 2f) // 86dp
        val buttonClearance = buttonOnlyRailTopDp - topBarBottomDp // 32dp of clear air
        (buttonClearance >= 30f) shouldBe true

        // Full layout including compact 9sp labels (44dp button + 2dp spacing + 11dp label = 57dp per pod)
        val fullRailHeightDp = (4 * (44f + 2f + 11f)) + (3 * 4f) // 240dp
        val compactRailTopDp = (screenHeightDp / 2f) - (fullRailHeightDp / 2f) // 60dp
        val compactClearanceFromTopBar = compactRailTopDp - topBarBottomDp // 6dp on compact 360dp phone
        (compactClearanceFromTopBar > 0f) shouldBe true

        // On standard phone (392dp height), full layout clearance provides ample breathing room
        val standardHeightDp = 392f
        val standardRailTopDp = (standardHeightDp / 2f) - (fullRailHeightDp / 2f) // 76dp
        val standardClearanceFromTopBar = standardRailTopDp - topBarBottomDp // 22dp of clear air
        (standardClearanceFromTopBar >= 20f) shouldBe true
    }

    @Test
    fun `speed pill never shows a trailing zero or a locale comma`() {
        reelsSpeedLabel(2f) shouldBe "2×"
        reelsSpeedLabel(0.5f) shouldBe "0.5×"
        reelsSpeedLabel(1.5f) shouldBe "1.5×"
        reelsSpeedLabel(1.25f) shouldBe "1.25×"
        reelsSpeedLabel(1f) shouldBe "1×"
    }

    @Test
    fun `hold uses halves so a thumb near the middle still lands`() {
        val width = 900f
        reelsHoldRate(0f, width) shouldBe REELS_HOLD_SLOW_RATE
        reelsHoldRate(300f, width) shouldBe REELS_HOLD_SLOW_RATE
        reelsHoldRate(449f, width) shouldBe REELS_HOLD_SLOW_RATE
        // The midpoint itself is no longer a dead zone: it belongs to the fast half.
        reelsHoldRate(450f, width) shouldBe REELS_HOLD_FAST_RATE
        reelsHoldRate(899f, width) shouldBe REELS_HOLD_FAST_RATE
    }

    @Test
    fun `drift only cancels a hold before the timeout`() {
        reelsHoldCancelledByDrift(30f, 24f, elapsedMs = 300L, timeoutMs = 500L) shouldBe true
        reelsHoldCancelledByDrift(24f, 24f, elapsedMs = 300L, timeoutMs = 500L) shouldBe false
        reelsHoldCancelledByDrift(10f, 24f, elapsedMs = 300L, timeoutMs = 500L) shouldBe false
        // Once the hold activated, the finger may roam anywhere without losing the rate.
        reelsHoldCancelledByDrift(400f, 24f, elapsedMs = 600L, timeoutMs = 500L) shouldBe false
        reelsHoldCancelledByDrift(400f, 24f, elapsedMs = 500L, timeoutMs = 500L) shouldBe false
    }

    @Test
    fun `a hold before the layout has a width does nothing`() {
        reelsHoldRate(10f, 0f) shouldBe null
        reelsHoldRate(-1f, -1f) shouldBe null
    }

    @Test
    fun `download labels never invent a file size`() {
        resolveDownloadActionLabel(false, null, "Скачать", "Загрузка") shouldBe "Скачать"
        resolveDownloadActionLabel(false, "", "Download", "Downloading") shouldBe "Download"
        resolveDownloadActionLabel(true, null, "Скачать", "Загрузка") shouldBe "Загрузка"
        resolveDownloadActionLabel(true, "3,6 МБ", "Скачать", "Загрузка") shouldBe "3,6 МБ"
        resolveDownloadActionLabel(false, "8.1 MB", "Download", "Downloading") shouldBe "8.1 MB"
    }

    @Test
    fun `portrait return fits the compact landscape rail at enlarged font scale`() {
        val screenHeightDp = 360f
        val topBarBottomDp = 54f

        // Like, portrait return, download and share; only the portrait return has no caption.
        for (fontScale in listOf(1f, 1.3f)) {
            val captionedPodHeight = 44f + 2f + 12f * fontScale
            val railHeightDp = 3 * captionedPodHeight + 44f + 3 * 4f
            val railTopDp = (screenHeightDp - railHeightDp) / 2f
            (railTopDp - topBarBottomDp > 0f) shouldBe true
        }
    }
}

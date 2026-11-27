package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reels.player.calculateReelTextureScale
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.floats.shouldBeWithinPercentageOf
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.max

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
    fun `v1 landscape controls meet 48dp touch target minimums`() {
        (REELS_LANDSCAPE_TOUCH_TARGET_DP >= 48f).shouldBeTrue()
        (REELS_LANDSCAPE_DOCK_HEIGHT_DP >= 48f).shouldBeTrue()
        REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP shouldBe 36f
    }

    @Test
    fun `v2 unified cinema console and compact 2-line meta clear center transport across font scales`() {
        val screenHeightDp = 360f
        val centerTransportBottomDp = (screenHeightDp / 2f) + 32f // 212dp (64dp Play circle centered)
        val dockHeightDp = 24f + 2f + REELS_LANDSCAPE_DOCK_HEIGHT_DP + 4f // 78dp total bottom console

        for (fontScale in listOf(1f, 1.3f)) {
            // Compact 2-line landscape meta: Row 1 (max 20dp avatar / 12.5sp * fontScale) + 3dp gap + Row 2 (16sp * fontScale)
            val row1HeightDp = max(20f, 14f * fontScale)
            val row2HeightDp = 16.5f * fontScale
            val compactMetaHeightDp = row1HeightDp + 3f + row2HeightDp
            val metaTopDp = screenHeightDp - dockHeightDp - compactMetaHeightDp
            (metaTopDp - centerTransportBottomDp >= 20f).shouldBeTrue()
        }
    }

    @Test
    fun `v1 bottom-end landscape rail clears topbar and scrubber dock across font scales`() {
        val screenHeightDp = 360f
        val topBarBottomDp = 56f
        val railBottomMarginDp = 60f

        for (fontScale in listOf(1f, 1.3f)) {
            // 36dp circle + 2dp gap + 11sp * fontScale label + 2dp item vertical padding, floored at 48dp hit height
            val podHeightDp = max(REELS_LANDSCAPE_TOUCH_TARGET_DP, 36f + 2f + 11f * fontScale + 2f)
            // 4 pods + 3 * 1dp Arrangement.spacedBy + 2dp top/bottom container padding (4dp total)
            val railHeightDp = (4 * podHeightDp) + (3 * 1f) + 4f
            val railTopDp = screenHeightDp - railBottomMarginDp - railHeightDp
            val clearanceDp = railTopDp - topBarBottomDp
            (clearanceDp >= 16f).shouldBeTrue()
        }
    }

    @Test
    fun `v1 landscape seek notice padding clears both action rail and center transport`() {
        reelsSeekNoticeHorizontalPadding(isLandscape = false) shouldBe REELS_SEEK_NOTICE_PORTRAIT_INSET_DP.dp
        reelsSeekNoticeHorizontalPadding(isLandscape = true) shouldBe REELS_SEEK_NOTICE_LANDSCAPE_INSET_DP.dp

        val usableWidthDp = 592f
        val centerHalfWidthDp = (48f + 28f + 64f + 28f + 48f) / 2f // 108dp
        val centerRightEdgeDp = (usableWidthDp / 2f) + centerHalfWidthDp // 404dp
        val railLeftEdgeDp = usableWidthDp - 12f - 58f // 522dp
        val noticeWidthDp = 76f
        val noticeRightEdgeDp = usableWidthDp - REELS_SEEK_NOTICE_LANDSCAPE_INSET_DP
        val noticeLeftEdgeDp = noticeRightEdgeDp - noticeWidthDp

        (noticeLeftEdgeDp - centerRightEdgeDp >= 20f).shouldBeTrue()
        (railLeftEdgeDp - noticeRightEdgeDp >= 15f).shouldBeTrue()
    }

    @Test
    fun `nextReelsLandscapeSpeed cycles expected rates cleanly`() {
        nextReelsLandscapeSpeed(1f) shouldBe 1.25f
        nextReelsLandscapeSpeed(1.25f) shouldBe 1.5f
        nextReelsLandscapeSpeed(1.5f) shouldBe 2f
        nextReelsLandscapeSpeed(2f) shouldBe 0.75f
        nextReelsLandscapeSpeed(0.75f) shouldBe 1f
        nextReelsLandscapeSpeed(1.251f) shouldBe 1.5f
        nextReelsLandscapeSpeed(0.5f) shouldBe 1f
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
    fun `v1 landscape topbar and transitional exit rail fit 360dp height at enlarged font scale`() {
        val screenHeightDp = 360f
        val topBarBottomDp = 56f
        val railBottomMarginDp = 60f

        // Transitional Wave 1 mode (showExitButton = true, onOpenHide = null):
        // 3 captioned pods (Like, Download, Share) + 1 uncaptioned 48dp exit pod.
        for (fontScale in listOf(1f, 1.3f)) {
            val captionedPodHeight = max(REELS_LANDSCAPE_TOUCH_TARGET_DP, 36f + 2f + 11f * fontScale + 2f)
            val railHeightDp = (3 * captionedPodHeight) + REELS_LANDSCAPE_TOUCH_TARGET_DP + (3 * 2f) + 8f
            val railTopDp = screenHeightDp - railBottomMarginDp - railHeightDp
            (railTopDp - topBarBottomDp >= 16f).shouldBeTrue()
        }
    }

    @Test
    fun `calculateReelTextureScale switches cleanly between Fit and Fill in landscape`() {
        // 9:16 vertical clip (1080x1920) on a 20:9 landscape viewport (2400x1080)
        val verticalFit = calculateReelTextureScale(1080, 1920, 2400f, 1080f, crop = false)!!
        verticalFit.first.shouldBeWithinPercentageOf(0.253125f, 0.1)
        verticalFit.second shouldBe 1f

        val verticalFill = calculateReelTextureScale(1080, 1920, 2400f, 1080f, crop = true)!!
        verticalFill.first shouldBe 1f
        verticalFill.second.shouldBeWithinPercentageOf(3.9506f, 0.1)

        // 16:9 horizontal clip (1920x1080) on a 20:9 landscape viewport (2400x1080)
        val horizontalFit = calculateReelTextureScale(1920, 1080, 2400f, 1080f, crop = false)!!
        horizontalFit.first.shouldBeWithinPercentageOf(0.8f, 0.1)
        horizontalFit.second shouldBe 1f

        val horizontalFill = calculateReelTextureScale(1920, 1080, 2400f, 1080f, crop = true)!!
        horizontalFill.first shouldBe 1f
        horizontalFill.second.shouldBeWithinPercentageOf(1.25f, 0.1)

        // Invalid dimensions return null
        calculateReelTextureScale(0, 1080, 2400f, 1080f, crop = false) shouldBe null
        calculateReelTextureScale(1920, 1080, 0f, 1080f, crop = true) shouldBe null
    }
}

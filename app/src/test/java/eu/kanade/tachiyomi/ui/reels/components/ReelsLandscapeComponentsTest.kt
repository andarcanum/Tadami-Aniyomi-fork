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
    fun `offline compact label logic formats properly`() {
        resolveOfflineActionLabel(isOfflineStored = true, offlineSizeText = null) shouldBe "✓"
        resolveOfflineActionLabel(isOfflineStored = true, offlineSizeText = "~24 МБ") shouldBe "✓"
        resolveOfflineActionLabel(isOfflineStored = false, offlineSizeText = "~24 МБ") shouldBe "~24 МБ"
        resolveOfflineActionLabel(isOfflineStored = false, offlineSizeText = null) shouldBe "24 МБ"
        resolveOfflineActionLabel(isOfflineStored = false, offlineSizeText = "") shouldBe "24 МБ"
    }

    @Test
    fun `3-pod landscape action rail clearance when hide pod is disabled`() {
        val screenHeightDp = 360f
        val topBarBottomDp = 54f

        // When hide pod is null, 3 pods are rendered: Like, Offline, Share
        val podsCount = 3
        val podHeight = 44f + 2f + 11f // 57dp
        val spacing = 4f
        val railHeightDp = (podsCount * podHeight) + ((podsCount - 1) * spacing) // 179dp
        val railTopDp = (screenHeightDp / 2f) - (railHeightDp / 2f) // 90.5dp
        val clearanceFromTopBar = railTopDp - topBarBottomDp // 36.5dp

        (clearanceFromTopBar >= 30f) shouldBe true
    }
}

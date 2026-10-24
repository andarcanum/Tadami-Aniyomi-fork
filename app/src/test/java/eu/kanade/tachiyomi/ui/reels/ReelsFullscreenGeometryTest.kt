package eu.kanade.tachiyomi.ui.reels

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsFullscreenGeometryTest {

    @Test
    fun `both physical landscape sides map to the matching rotation`() {
        // Gravity +x when the natural top edge points left, -x when it points right.
        reelsGravityRotation(9.5f, 0.2f) shouldBe 90
        reelsGravityRotation(-9.5f, 0.2f) shouldBe 270
    }

    @Test
    fun `ambiguous holds do not report a side`() {
        reelsGravityRotation(0.4f, 9.6f) shouldBe null // upright portrait
        reelsGravityRotation(5.0f, 5.0f) shouldBe null // 45° diagonal
        reelsGravityRotation(0.2f, 0.1f) shouldBe null // flat, below the threshold
    }

    @Test
    fun `landscape window and pip never rotate the subtree`() {
        reelsSoftwareRotationDegrees(90, windowIsLandscape = true, inPictureInPicture = false) shouldBe 0
        reelsSoftwareRotationDegrees(270, windowIsLandscape = false, inPictureInPicture = true) shouldBe 0
        reelsSoftwareRotationDegrees(90, windowIsLandscape = true, inPictureInPicture = true) shouldBe 0
    }

    @Test
    fun `portrait window keeps the detected landscape rotation`() {
        reelsSoftwareRotationDegrees(90, windowIsLandscape = false, inPictureInPicture = false) shouldBe 90
        reelsSoftwareRotationDegrees(270, windowIsLandscape = false, inPictureInPicture = false) shouldBe 270
    }

    @Test
    fun `window orientation follows actual dimensions`() {
        reelsWindowIsLandscape(800, 400) shouldBe true
        reelsWindowIsLandscape(400, 800) shouldBe false
        reelsWindowIsLandscape(400, 400) shouldBe false
    }

    @Test
    fun `safe padding follows the rotated content edges`() {
        assertPadding(rotation = 0, left = 1.dp, top = 2.dp, right = 3.dp, bottom = 4.dp)
        assertPadding(rotation = 90, left = 2.dp, top = 3.dp, right = 4.dp, bottom = 1.dp)
        assertPadding(rotation = 180, left = 3.dp, top = 4.dp, right = 1.dp, bottom = 2.dp)
        assertPadding(rotation = 270, left = 4.dp, top = 1.dp, right = 2.dp, bottom = 3.dp)
    }

    @Test
    fun `safe padding stays absolute in rtl`() {
        val padding = reelsRotatedSafePadding(
            left = 1.dp,
            top = 2.dp,
            right = 3.dp,
            bottom = 4.dp,
            rotationZ = 90,
        )
        padding.calculateLeftPadding(LayoutDirection.Rtl) shouldBe 2.dp
        padding.calculateRightPadding(LayoutDirection.Rtl) shouldBe 4.dp
    }

    @Test
    fun `pip aspect follows the active reel orientation`() {
        reelsPipAspectRatio(isLandscapeVideo = true) shouldBe (16 to 9)
        reelsPipAspectRatio(isLandscapeVideo = false) shouldBe (9 to 16)
        reelsPipAspectRatio(isLandscapeVideo = null) shouldBe null
    }

    private fun assertPadding(
        rotation: Int,
        left: androidx.compose.ui.unit.Dp,
        top: androidx.compose.ui.unit.Dp,
        right: androidx.compose.ui.unit.Dp,
        bottom: androidx.compose.ui.unit.Dp,
    ) {
        val padding = reelsRotatedSafePadding(
            left = 1.dp,
            top = 2.dp,
            right = 3.dp,
            bottom = 4.dp,
            rotationZ = rotation,
        )
        padding.calculateLeftPadding(LayoutDirection.Ltr) shouldBe left
        padding.calculateTopPadding() shouldBe top
        padding.calculateRightPadding(LayoutDirection.Ltr) shouldBe right
        padding.calculateBottomPadding() shouldBe bottom
    }
}

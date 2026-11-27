package eu.kanade.tachiyomi.ui.reader.viewer.panel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaPanelDetectorTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF101010.toInt()
    private val midGray = 0xFF707070.toInt()

    private fun createCanvas(width: Int, height: Int, bg: Int): IntArray {
        return IntArray(width * height) { bg }
    }

    private fun fillRect(
        pixels: IntArray,
        stride: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int,
    ) {
        for (y in top until bottom) {
            val row = y * stride
            for (x in left until right) {
                pixels[row + x] = color
            }
        }
    }

    @Test
    fun `detects 2x2 manga grid in RTL reading order`() {
        val w = 200
        val h = 300
        val pixels = createCanvas(w, h, white)
        fillRect(pixels, w, 12, 14, 92, 138, midGray)
        fillRect(pixels, w, 108, 14, 188, 138, midGray)
        fillRect(pixels, w, 12, 160, 92, 286, midGray)
        fillRect(pixels, w, 108, 160, 188, 286, midGray)

        val panels = MangaPanelDetector.detectFromPixels(pixels, w, h, isRtl = true)
        assertEquals(4, panels.size)

        // RTL order: Top-Right -> Top-Left -> Bottom-Right -> Bottom-Left
        assertTrue(panels[0].centerX > 0.5f && panels[0].centerY < 0.5f)
        assertTrue(panels[1].centerX < 0.5f && panels[1].centerY < 0.5f)
        assertTrue(panels[2].centerX > 0.5f && panels[2].centerY > 0.5f)
        assertTrue(panels[3].centerX < 0.5f && panels[3].centerY > 0.5f)
    }

    @Test
    fun `detects 2x2 comic grid in LTR reading order`() {
        val w = 200
        val h = 300
        val pixels = createCanvas(w, h, white)
        fillRect(pixels, w, 12, 14, 92, 138, midGray)
        fillRect(pixels, w, 108, 14, 188, 138, midGray)
        fillRect(pixels, w, 12, 160, 92, 286, midGray)
        fillRect(pixels, w, 108, 160, 188, 286, midGray)

        val panels = MangaPanelDetector.detectFromPixels(pixels, w, h, isRtl = false)
        assertEquals(4, panels.size)

        // LTR order: Top-Left -> Top-Right -> Bottom-Left -> Bottom-Right
        assertTrue(panels[0].centerX < 0.5f && panels[0].centerY < 0.5f)
        assertTrue(panels[1].centerX > 0.5f && panels[1].centerY < 0.5f)
        assertTrue(panels[2].centerX < 0.5f && panels[2].centerY > 0.5f)
        assertTrue(panels[3].centerX > 0.5f && panels[3].centerY > 0.5f)
    }

    @Test
    fun `detects asymmetric layout with 2 top panels and 1 wide bottom panel`() {
        val w = 200
        val h = 300
        val pixels = createCanvas(w, h, white)
        fillRect(pixels, w, 10, 12, 92, 135, midGray)
        fillRect(pixels, w, 108, 12, 190, 135, midGray)
        fillRect(pixels, w, 10, 155, 190, 288, midGray)

        val panels = MangaPanelDetector.detectFromPixels(pixels, w, h, isRtl = true)
        assertEquals(3, panels.size)
        assertTrue(panels[0].centerX > 0.5f && panels[0].centerY < 0.5f)
        assertTrue(panels[1].centerX < 0.5f && panels[1].centerY < 0.5f)
        assertTrue(panels[2].width > 0.8f && panels[2].centerY > 0.5f)
    }

    @Test
    fun `detects panels on dark-gutter flashback pages`() {
        val w = 200
        val h = 300
        val pixels = createCanvas(w, h, black)
        fillRect(pixels, w, 12, 12, 188, 92, white)
        fillRect(pixels, w, 12, 108, 188, 188, white)
        fillRect(pixels, w, 12, 204, 188, 288, white)

        val panels = MangaPanelDetector.detectFromPixels(pixels, w, h, isRtl = true)
        assertEquals(3, panels.size)
        assertTrue(panels[0].centerY < panels[1].centerY)
        assertTrue(panels[1].centerY < panels[2].centerY)
    }

    @Test
    fun `falls back to empty list on full-bleed splash page`() {
        val w = 200
        val h = 300
        val pixels = createCanvas(w, h, midGray)

        val panels = MangaPanelDetector.detectFromPixels(pixels, w, h, isRtl = true)
        assertTrue(panels.isEmpty())
    }

    @Test
    fun `computes safe camera target fitting panel within viewport`() {
        val panel = PanelRect(left = 0.5f, top = 0.0f, right = 1.0f, bottom = 0.5f)
        val target = MangaPanelDetector.computeCameraTarget(
            panel = panel,
            sourceWidth = 1000,
            sourceHeight = 1500,
            viewWidth = 1080,
            viewHeight = 1920,
            minScale = 1.0f,
            maxScale = 5.0f,
            paddingFraction = 0.05f,
        )
        assertNotNull(target)
        assertEquals(750f, target!!.centerX, 0.5f)
        assertEquals(375f, target.centerY, 0.5f)
        // Panel is 500x750; usable viewport is 972x1728 -> min(972/500, 1728/750) = 1.944
        assertEquals(1.944f, target.scale, 0.01f)
    }

    @Test
    fun `guided panel controller steps forward and backward across panels`() {
        val panels = listOf(
            PanelRect(0.5f, 0.0f, 1.0f, 0.5f),
            PanelRect(0.0f, 0.0f, 0.5f, 0.5f),
            PanelRect(0.0f, 0.5f, 1.0f, 1.0f),
        )
        val controller = GuidedPanelController()

        val entry = controller.enterPage(panels, fromEnd = false)
        assertNotNull(entry)
        assertEquals(0, entry!!.index)

        val step1 = controller.stepForward(panels)
        assertTrue(step1 is GuidedPanelController.StepResult.Focus && step1.index == 1)

        val step2 = controller.stepForward(panels)
        assertTrue(step2 is GuidedPanelController.StepResult.Focus && step2.index == 2)

        val step3 = controller.stepForward(panels)
        assertEquals(GuidedPanelController.StepResult.NextPage, step3)

        val back1 = controller.stepBackward(panels)
        assertTrue(back1 is GuidedPanelController.StepResult.Focus && back1.index == 1)

        val backFromEnd = controller.enterPage(panels, fromEnd = true)
        assertEquals(2, backFromEnd!!.index)

        assertNull(controller.enterPage(emptyList(), fromEnd = false))
    }
}

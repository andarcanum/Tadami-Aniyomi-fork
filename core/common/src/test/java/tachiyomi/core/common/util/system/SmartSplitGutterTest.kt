package tachiyomi.core.common.util.system

import android.app.Application
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SmartSplitGutterTest {

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val MID_GRAY = 0xFF808080.toInt()
    }

    @Test
    fun prefersWhiteGutterOverSolidMidGrayScreentone() {
        val sw = 40
        val sh = 64
        val pixels = IntArray(sw * sh) { idx ->
            val col = idx % sw
            val row = idx / sw
            when {
                // Solid mid-gray screentone near center (col 18..20) has zero variance, must NOT beat white gutter
                col in 18..20 -> MID_GRAY
                // Clean white book gutter slightly off-center at col 26
                col == 26 -> WHITE
                // Busy artwork everywhere else
                else -> if ((col + row) % 2 == 0) BLACK else WHITE
            }
        }

        val splitCol = ImageUtil.findSmartSplitColumn(pixels, sw, sh)
        assertEquals(26, splitCol)
    }

    @Test
    fun findsOffCenterBlackGutterCleanly() {
        val sw = 40
        val sh = 64
        val pixels = IntArray(sw * sh) { idx ->
            val col = idx % sw
            val row = idx / sw
            when (col) {
                12 -> BLACK
                else -> if ((col + row) % 2 == 0) BLACK else WHITE
            }
        }

        val splitCol = ImageUtil.findSmartSplitColumn(pixels, sw, sh)
        assertEquals(12, splitCol)
    }

    @Test
    fun returnsNullForContinuousFullBleedSpreadWithoutGutter() {
        val sw = 40
        val sh = 64
        // Every column has alternating high-contrast artwork (high variance > 400)
        val pixels = IntArray(sw * sh) { idx ->
            val col = idx % sw
            val row = idx / sw
            if ((col + row) % 2 == 0) BLACK else WHITE
        }

        assertNull(ImageUtil.findSmartSplitColumn(pixels, sw, sh))
    }
}

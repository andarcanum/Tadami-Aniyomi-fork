package eu.kanade.tachiyomi.ui.reader.setting

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderVisualPresetsTest {

    private fun applyColorMatrix(matrix: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        val outR = (matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[4]).coerceIn(0f, 255f)
        val outG = (matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[9]).coerceIn(0f, 255f)
        val outB = (matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[14]).coerceIn(0f, 255f)
        return Triple(outR, outG, outB)
    }

    @Test
    fun smartInvertMapsWhiteToBlackAndBlackToWhite() {
        val m = ReaderVisualPreset.invertColorMatrixValues(smartInvert = true)

        val (wr, wg, wb) = applyColorMatrix(m, 255f, 255f, 255f)
        assertTrue(abs(wr) < 1e-2f && abs(wg) < 1e-2f && abs(wb) < 1e-2f, "White must map to black: ($wr,$wg,$wb)")

        val (br, bg, bb) = applyColorMatrix(m, 0f, 0f, 0f)
        assertTrue(
            abs(br - 255f) < 1e-2f && abs(bg - 255f) < 1e-2f && abs(bb - 255f) < 1e-2f,
            "Black must map to white",
        )
    }

    @Test
    fun smartInvertPreservesDominantColorHueWhileInvertingBrightness() {
        val smart = ReaderVisualPreset.invertColorMatrixValues(smartInvert = true)
        val classic = ReaderVisualPreset.invertColorMatrixValues(smartInvert = false)

        // Bright warm red stays red-dominant under smart invert (classic turns it cyan).
        val (sr, sg, sb) = applyColorMatrix(smart, 220f, 60f, 50f)
        assertTrue(sr > sg && sr > sb, "Smart invert must keep red dominant: ($sr,$sg,$sb)")

        val (cr, cg, cb) = applyColorMatrix(classic, 220f, 60f, 50f)
        assertTrue(cg > cr && cb > cr, "Classic invert flips red into cyan")

        // Light sky blue stays blue-dominant under smart invert (classic turns it warm orange).
        val (sbr, sbg, sbb) = applyColorMatrix(smart, 140f, 195f, 250f)
        assertTrue(sbb > sbr && sbb > sbg, "Smart invert must keep blue dominant: ($sbr,$sbg,$sbb)")
    }

    @Test
    fun detectActiveMatchesEveryPresetAndReturnsNullWhenCustomized() {
        for (preset in ReaderVisualPreset.entries) {
            val detected = ReaderVisualPreset.detectActive(
                colorFilter = preset.colorFilter,
                colorFilterValue = preset.colorFilterValue,
                colorFilterMode = preset.colorFilterMode,
                customBrightness = preset.customBrightness,
                customBrightnessValue = preset.customBrightnessValue,
                grayscale = preset.grayscale,
                invertedColors = preset.invertedColors,
                smartInvertColors = preset.smartInvertColors,
                paperWhitePoint = preset.paperWhitePoint,
                paperGammaContrast = preset.paperGammaContrast,
                paperBlackPoint = preset.paperBlackPoint,
                sharpening = preset.sharpening,
                denoise = preset.denoise,
                binarization = preset.binarization,
            )
            assertEquals(preset, detected, "Expected exact match for $preset")
        }

        val custom = ReaderVisualPreset.detectActive(
            colorFilter = false,
            colorFilterValue = 0,
            colorFilterMode = 0,
            customBrightness = false,
            customBrightnessValue = 0,
            grayscale = false,
            invertedColors = false,
            smartInvertColors = true,
            paperWhitePoint = 25,
            paperGammaContrast = 0,
            paperBlackPoint = 0,
            sharpening = 50,
            denoise = 0,
            binarization = 0,
        )
        assertNull(custom)
    }
}

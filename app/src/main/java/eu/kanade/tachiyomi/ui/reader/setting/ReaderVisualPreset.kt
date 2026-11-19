package eu.kanade.tachiyomi.ui.reader.setting

/**
 * Curated one-tap visual presets and shared color-inversion matrix helpers for the manga reader.
 */
enum class ReaderVisualPreset(
    val colorFilter: Boolean,
    val colorFilterValue: Int,
    val colorFilterMode: Int,
    val customBrightness: Boolean,
    val customBrightnessValue: Int,
    val grayscale: Boolean,
    val invertedColors: Boolean,
    val smartInvertColors: Boolean,
    val paperWhitePoint: Int,
    val paperGammaContrast: Int,
    val paperBlackPoint: Int,
    val sharpening: Int,
    val denoise: Int,
    val binarization: Int,
) {
    ORIGINAL(
        colorFilter = false,
        colorFilterValue = 0,
        colorFilterMode = 0,
        customBrightness = false,
        customBrightnessValue = 0,
        grayscale = false,
        invertedColors = false,
        smartInvertColors = true,
        paperWhitePoint = 0,
        paperGammaContrast = 0,
        paperBlackPoint = 0,
        sharpening = 0,
        denoise = 0,
        binarization = 0,
    ),
    CRISP_SCAN(
        colorFilter = false,
        colorFilterValue = 0,
        colorFilterMode = 0,
        customBrightness = false,
        customBrightnessValue = 0,
        grayscale = false,
        invertedColors = false,
        smartInvertColors = true,
        paperWhitePoint = 18,
        paperGammaContrast = 8,
        paperBlackPoint = 10,
        sharpening = 35,
        denoise = 15,
        binarization = 0,
    ),
    WARM_PAPER(
        colorFilter = true,
        // Full-alpha warm cream tint in Multiply/Modulate mode preserves black ink while warming white paper.
        colorFilterValue = 0xFFF5E8CE.toInt(),
        colorFilterMode = 1,
        customBrightness = false,
        customBrightnessValue = 0,
        grayscale = false,
        invertedColors = false,
        smartInvertColors = true,
        paperWhitePoint = 8,
        paperGammaContrast = 4,
        paperBlackPoint = 4,
        sharpening = 0,
        denoise = 0,
        binarization = 0,
    ),
    OLED_NIGHT(
        colorFilter = false,
        colorFilterValue = 0,
        colorFilterMode = 0,
        customBrightness = true,
        customBrightnessValue = -12,
        grayscale = false,
        invertedColors = true,
        smartInvertColors = true,
        paperWhitePoint = 12,
        paperGammaContrast = 0,
        paperBlackPoint = 8,
        sharpening = 0,
        denoise = 0,
        binarization = 0,
    ),
    AGED_SCAN(
        colorFilter = false,
        colorFilterValue = 0,
        colorFilterMode = 0,
        customBrightness = false,
        customBrightnessValue = 0,
        grayscale = true,
        invertedColors = false,
        smartInvertColors = true,
        paperWhitePoint = 32,
        paperGammaContrast = 12,
        paperBlackPoint = 14,
        sharpening = 30,
        denoise = 30,
        binarization = 0,
    ),
    ;

    fun applyTo(preferences: ReaderPreferences) {
        preferences.colorFilter().set(colorFilter)
        if (colorFilter) {
            preferences.colorFilterValue().set(colorFilterValue)
            preferences.colorFilterMode().set(colorFilterMode)
        }
        preferences.customBrightness().set(customBrightness)
        preferences.customBrightnessValue().set(if (customBrightness) customBrightnessValue else 0)
        preferences.grayscale().set(grayscale)
        preferences.invertedColors().set(invertedColors)
        preferences.smartInvertColors().set(smartInvertColors)
        preferences.paperWhitePoint().set(paperWhitePoint)
        preferences.paperGammaContrast().set(paperGammaContrast)
        preferences.paperBlackPoint().set(paperBlackPoint)
        preferences.sharpening().set(sharpening)
        preferences.denoise().set(denoise)
        preferences.binarization().set(binarization)
    }

    companion object {
        private const val LUMA_R = 0.2126f
        private const val LUMA_G = 0.7152f
        private const val LUMA_B = 0.0722f

        /**
         * Builds a 4x5 [android.graphics.ColorMatrix] array for page inversion.
         *
         * When [smartInvert] is true, applies luminance-only inversion (`c' = 1 + c - 2 * luma(c)`):
         * monochrome pages invert cleanly (`white <-> black`), while colored panels and covers
         * invert brightness without flipping hues into unnatural cyan/orange negatives.
         */
        fun invertColorMatrixValues(smartInvert: Boolean): FloatArray {
            return if (smartInvert) {
                floatArrayOf(
                    1f - 2f * LUMA_R, -2f * LUMA_G, -2f * LUMA_B, 0f, 255f,
                    -2f * LUMA_R, 1f - 2f * LUMA_G, -2f * LUMA_B, 0f, 255f,
                    -2f * LUMA_R, -2f * LUMA_G, 1f - 2f * LUMA_B, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )
            } else {
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                )
            }
        }

        fun detectActive(
            colorFilter: Boolean,
            colorFilterValue: Int,
            colorFilterMode: Int,
            customBrightness: Boolean,
            customBrightnessValue: Int,
            grayscale: Boolean,
            invertedColors: Boolean,
            smartInvertColors: Boolean,
            paperWhitePoint: Int,
            paperGammaContrast: Int,
            paperBlackPoint: Int,
            sharpening: Int,
            denoise: Int,
            binarization: Int,
        ): ReaderVisualPreset? {
            return entries.firstOrNull { preset ->
                preset.colorFilter == colorFilter &&
                    (
                        !preset.colorFilter ||
                            (preset.colorFilterValue == colorFilterValue && preset.colorFilterMode == colorFilterMode)
                        ) &&
                    preset.customBrightness == customBrightness &&
                    (!preset.customBrightness || preset.customBrightnessValue == customBrightnessValue) &&
                    preset.grayscale == grayscale &&
                    preset.invertedColors == invertedColors &&
                    (!preset.invertedColors || preset.smartInvertColors == smartInvertColors) &&
                    preset.paperWhitePoint == paperWhitePoint &&
                    preset.paperGammaContrast == paperGammaContrast &&
                    preset.paperBlackPoint == paperBlackPoint &&
                    preset.sharpening == sharpening &&
                    preset.denoise == denoise &&
                    preset.binarization == binarization
            }
        }
    }
}

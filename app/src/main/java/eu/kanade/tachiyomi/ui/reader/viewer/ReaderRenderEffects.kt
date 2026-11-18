package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi

/**
 * Shared holder for the active reader AGSL [RenderEffect] on Android 13+.
 * Built once when sharpening / denoise / binarization sliders change and applied directly
 * to each page's inner image view so margins, transitions, and overlays are not shaded.
 */
internal object ReaderRenderEffects {
    @Volatile
    private var configuredEffect: Any? = null

    @Volatile
    private var flingPaused: Boolean = false

    val currentEffect: Any?
        get() = if (flingPaused) null else configuredEffect

    fun update(
        sharpeningVal: Float,
        denoiseVal: Float,
        binarizationVal: Float,
        whitePointVal: Float = 0f,
        gammaContrastVal: Float = 0f,
        blackPointVal: Float = 0f,
    ) {
        configuredEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Api33.build(
                sharpeningVal = sharpeningVal,
                denoiseVal = denoiseVal,
                binarizationVal = binarizationVal,
                whitePointVal = whitePointVal,
                gammaContrastVal = gammaContrastVal,
                blackPointVal = blackPointVal,
            )
        } else {
            null
        }
    }

    fun setFlingPaused(paused: Boolean, root: View) {
        if (flingPaused == paused) return
        flingPaused = paused
        applyToHierarchy(root)
    }

    fun clear() {
        configuredEffect = null
        flingPaused = false
    }

    fun applyToHierarchy(root: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val effect = currentEffect as? RenderEffect
        if (root is ReaderPageImageView) {
            root.applyPageRenderEffect(effect)
            return
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                applyToHierarchy(root.getChildAt(i))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33 {
        private const val SHARPEN_SHADER = """
            uniform shader inputShader;
            uniform float sharpness;

            half4 main(float2 coords) {
                half4 center = inputShader.eval(coords);
                half4 left = inputShader.eval(coords + float2(-1.0, 0.0));
                half4 right = inputShader.eval(coords + float2(1.0, 0.0));
                half4 top = inputShader.eval(coords + float2(0.0, -1.0));
                half4 bottom = inputShader.eval(coords + float2(0.0, 1.0));

                half4 sharp = center * 5.0 - (left + right + top + bottom);
                return center + (sharp - center) * sharpness;
            }
        """

        private const val DENOISE_SHADER = """
            uniform shader inputShader;
            uniform float denoise;

            half4 main(float2 coords) {
                half4 color = inputShader.eval(coords);
                half4 sum = color;
                float count = 1.0;

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        if (x == 0 && y == 0) continue;
                        half4 neighbor = inputShader.eval(coords + float2(x, y));
                        if (distance(neighbor.rgb, color.rgb) < 0.15) {
                            sum += neighbor;
                            count += 1.0;
                        }
                    }
                }
                half4 smoothColor = sum / count;
                return color + (smoothColor - color) * denoise;
            }
        """

        private const val BINARIZATION_SHADER = """
            uniform shader inputShader;
            uniform float binarization;

            half4 main(float2 coords) {
                half4 center = inputShader.eval(coords);
                float centerLuma = (center.r + center.g + center.b) * 0.3333;

                float sum = centerLuma;
                float minVal = centerLuma;
                float maxVal = centerLuma;

                float2 offsets[8];
                offsets[0] = float2(-2.0, -2.0);
                offsets[1] = float2( 0.0, -2.0);
                offsets[2] = float2( 2.0, -2.0);
                offsets[3] = float2(-2.0,  0.0);
                offsets[4] = float2( 2.0,  0.0);
                offsets[5] = float2(-2.0,  2.0);
                offsets[6] = float2( 0.0,  2.0);
                offsets[7] = float2( 2.0,  2.0);

                for (int i = 0; i < 8; i++) {
                    half4 col = inputShader.eval(coords + offsets[i]);
                    float luma = (col.r + col.g + col.b) * 0.3333;
                    sum += luma;
                    minVal = min(minVal, luma);
                    maxVal = max(maxVal, luma);
                }

                float mean = sum * 0.1111;
                float contrast = maxVal - minVal;

                float binaryLuma = step(mean, centerLuma);
                float lowContrastLuma = step(0.5, centerLuma);
                float isLowContrast = step(contrast, 0.15);
                float finalLuma = mix(binaryLuma, lowContrastLuma, isLowContrast);

                float mixedLuma = mix(centerLuma, finalLuma, binarization);
                return half4(mixedLuma, mixedLuma, mixedLuma, center.a);
            }
        """

        private const val LEVELS_SHADER = """
            uniform shader inputShader;
            uniform float blackPoint;
            uniform float whitePoint;
            uniform float gammaExp;

            half4 main(float2 coords) {
                half4 c = inputShader.eval(coords);
                float span = max(0.05, whitePoint - blackPoint);
                float3 mapped = clamp((c.rgb - blackPoint) / span, 0.0, 1.0);
                mapped = pow(mapped, float3(gammaExp));
                return half4(mapped, c.a);
            }
        """

        fun build(
            sharpeningVal: Float,
            denoiseVal: Float,
            binarizationVal: Float,
            whitePointVal: Float,
            gammaContrastVal: Float,
            blackPointVal: Float,
        ): RenderEffect? {
            var effect: RenderEffect? = null

            if (whitePointVal > 0f || blackPointVal > 0f || gammaContrastVal != 0f) {
                val levelsShader = RuntimeShader(LEVELS_SHADER)
                val bp = (blackPointVal * 0.35f).coerceIn(0f, 0.40f)
                val wp = (1f - whitePointVal * 0.45f).coerceIn(bp + 0.10f, 1f)
                // Positive gammaContrastVal increases midtone contrast (darker midtones -> exponent > 1)
                val gammaExp = (1f + gammaContrastVal * 0.9f).coerceIn(0.45f, 2.2f)
                levelsShader.setFloatUniform("blackPoint", bp)
                levelsShader.setFloatUniform("whitePoint", wp)
                levelsShader.setFloatUniform("gammaExp", gammaExp)
                effect = RenderEffect.createRuntimeShaderEffect(levelsShader, "inputShader")
            }

            if (sharpeningVal > 0f) {
                val sharpShader = RuntimeShader(SHARPEN_SHADER)
                sharpShader.setFloatUniform("sharpness", sharpeningVal)
                val sharpEffect = RenderEffect.createRuntimeShaderEffect(sharpShader, "inputShader")
                effect = if (effect != null) {
                    RenderEffect.createChainEffect(sharpEffect, effect)
                } else {
                    sharpEffect
                }
            }

            if (denoiseVal > 0f) {
                val denoiseShader = RuntimeShader(DENOISE_SHADER)
                denoiseShader.setFloatUniform("denoise", denoiseVal)
                val denoiseEffect = RenderEffect.createRuntimeShaderEffect(denoiseShader, "inputShader")
                effect = if (effect != null) {
                    RenderEffect.createChainEffect(denoiseEffect, effect)
                } else {
                    denoiseEffect
                }
            }

            if (binarizationVal > 0f) {
                val binarizationShader = RuntimeShader(BINARIZATION_SHADER)
                binarizationShader.setFloatUniform("binarization", binarizationVal)
                val binarizationEffect = RenderEffect.createRuntimeShaderEffect(binarizationShader, "inputShader")
                effect = if (effect != null) {
                    RenderEffect.createChainEffect(binarizationEffect, effect)
                } else {
                    binarizationEffect
                }
            }

            return effect
        }
    }
}

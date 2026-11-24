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
        if (configuredEffect == null) return
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
        // Contrast-Adaptive Luma Sharpening (CAS-inspired): sharpens scanned text & soft lineart
        // while suppressing ringing halos on high-contrast panel borders and preserving chroma hues.
        private const val SHARPEN_SHADER = """
            uniform shader inputShader;
            uniform float sharpness;

            half4 main(float2 coords) {
                half4 center = inputShader.eval(coords);
                half3 left = inputShader.eval(coords + float2(-1.0, 0.0)).rgb;
                half3 right = inputShader.eval(coords + float2(1.0, 0.0)).rgb;
                half3 top = inputShader.eval(coords + float2(0.0, -1.0)).rgb;
                half3 bottom = inputShader.eval(coords + float2(0.0, 1.0)).rgb;

                const half3 lumaW = half3(0.2126, 0.7152, 0.0722);
                float lC = dot(center.rgb, lumaW);
                float lL = dot(left, lumaW);
                float lR = dot(right, lumaW);
                float lT = dot(top, lumaW);
                float lB = dot(bottom, lumaW);

                float minL = min(lC, min(min(lL, lR), min(lT, lB)));
                float maxL = max(lC, max(max(lL, lR), max(lT, lB)));
                float contrast = maxL - minL;

                float amp = sqrt(clamp(min(minL, 1.0 - maxL) / max(maxL, 0.001), 0.0, 1.0));
                float edgeGate = smoothstep(0.02, 0.08, contrast);
                float weight = -amp * edgeGate * (sharpness * 0.22);

                float sharpenedL = (lC + (lL + lR + lT + lB) * weight) / (1.0 + 4.0 * weight);
                float margin = 0.06 * sharpness;
                float delta = clamp(sharpenedL, minL - margin, maxL + margin) - lC;
                half3 outRgb = clamp(center.rgb + half3(delta), half3(0.0), half3(1.0));
                return half4(outRgb, center.a);
            }
        """

        // Edge-preserving Gaussian bilateral filter: removes JPEG mosquito noise and paper grain
        // without smearing black manga ink outlines or introducing hard-threshold banding.
        private const val DENOISE_SHADER = """
            uniform shader inputShader;
            uniform float denoise;

            half4 main(float2 coords) {
                half4 center = inputShader.eval(coords);
                float sigmaRange = mix(0.06, 0.18, denoise);
                float invTwoSigmaR2 = 1.0 / (2.0 * sigmaRange * sigmaRange);
                half3 sum = center.rgb;
                float weightSum = 1.0;

                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        if (x == 0 && y == 0) continue;
                        half3 neighbor = inputShader.eval(coords + float2(float(x), float(y))).rgb;
                        half3 diff = neighbor - center.rgb;
                        float dist2 = dot(diff, diff);
                        float spatialW = (x == 0 || y == 0) ? 0.6065 : 0.3679;
                        float rangeW = exp(-dist2 * invTwoSigmaR2);
                        float w = spatialW * rangeW;
                        sum += neighbor * w;
                        weightSum += w;
                    }
                }
                half3 smoothRgb = sum / weightSum;
                return half4(mix(center.rgb, smoothRgb, denoise), center.a);
            }
        """

        // Sauvola-inspired local adaptive thresholding with smoothstep anti-aliasing so diagonal
        // ink strokes and small text stay crisp instead of breaking into 1-bit pixel staircases.
        private const val BINARIZATION_SHADER = """
            uniform shader inputShader;
            uniform float binarization;

            half4 main(float2 coords) {
                half4 center = inputShader.eval(coords);
                const half3 lumaW = half3(0.2126, 0.7152, 0.0722);
                float centerLuma = dot(center.rgb, lumaW);

                float sum = centerLuma;
                float sumSq = centerLuma * centerLuma;
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
                    half3 col = inputShader.eval(coords + offsets[i]).rgb;
                    float luma = dot(col, lumaW);
                    sum += luma;
                    sumSq += luma * luma;
                    minVal = min(minVal, luma);
                    maxVal = max(maxVal, luma);
                }

                float mean = sum * 0.111111;
                float variance = max(0.0, sumSq * 0.111111 - mean * mean);
                float stdDev = sqrt(variance);
                float contrast = maxVal - minVal;

                float sauvolaT = mean * (1.0 + 0.18 * (stdDev / 0.5 - 1.0));
                float aaWidth = mix(0.08, 0.03, binarization);
                float adaptiveLuma = smoothstep(sauvolaT - aaWidth, sauvolaT + aaWidth, centerLuma);
                float flatLuma = smoothstep(0.5 - aaWidth, 0.5 + aaWidth, centerLuma);
                float flatWeight = 1.0 - smoothstep(0.08, 0.16, contrast);
                float finalLuma = mix(adaptiveLuma, flatLuma, flatWeight);

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

            // Pipeline order: Denoise -> Levels -> Sharpen -> Binarization.
            // Denoising first cleans scan/JPEG grain before levels stretch contrast and before
            // sharpening enhances line art (previously Sharpen ran before Denoise, amplifying
            // noise and then blurring the sharpened edges).
            if (denoiseVal > 0f) {
                val denoiseShader = RuntimeShader(DENOISE_SHADER)
                denoiseShader.setFloatUniform("denoise", denoiseVal)
                effect = RenderEffect.createRuntimeShaderEffect(denoiseShader, "inputShader")
            }

            if (whitePointVal > 0f || blackPointVal > 0f || gammaContrastVal != 0f) {
                val levelsShader = RuntimeShader(LEVELS_SHADER)
                val bp = (blackPointVal * 0.35f).coerceIn(0f, 0.40f)
                val wp = (1f - whitePointVal * 0.45f).coerceIn(bp + 0.10f, 1f)
                // Positive gammaContrastVal increases midtone contrast (darker midtones -> exponent > 1)
                val gammaExp = (1f + gammaContrastVal * 0.9f).coerceIn(0.45f, 2.2f)
                levelsShader.setFloatUniform("blackPoint", bp)
                levelsShader.setFloatUniform("whitePoint", wp)
                levelsShader.setFloatUniform("gammaExp", gammaExp)
                val levelsEffect = RenderEffect.createRuntimeShaderEffect(levelsShader, "inputShader")
                effect = if (effect != null) {
                    RenderEffect.createChainEffect(levelsEffect, effect)
                } else {
                    levelsEffect
                }
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

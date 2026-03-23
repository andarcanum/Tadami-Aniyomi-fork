package eu.kanade.presentation.entries.components.aurora

import coil3.request.ImageRequest
import coil3.size.Precision
import eu.kanade.tachiyomi.data.coil.staticBlur
import kotlin.math.max

private const val AURORA_BACKGROUND_BLUR_DOWNSAMPLE_DIVISOR = 3

internal data class AuroraPosterBackgroundSpec(
    val sharpMemoryCacheKey: String,
    val blurMemoryCacheKey: String,
    val blurWidthPx: Int,
    val blurHeightPx: Int,
)

internal fun auroraPosterBackgroundSpec(
    baseCacheKey: String,
    containerWidthPx: Int,
    containerHeightPx: Int,
    blurRadiusPx: Int,
): AuroraPosterBackgroundSpec {
    val blurWidthPx = max(1, containerWidthPx / AURORA_BACKGROUND_BLUR_DOWNSAMPLE_DIVISOR)
    val blurHeightPx = max(1, containerHeightPx / AURORA_BACKGROUND_BLUR_DOWNSAMPLE_DIVISOR)

    return AuroraPosterBackgroundSpec(
        sharpMemoryCacheKey = "$baseCacheKey;sharp",
        blurMemoryCacheKey = "$baseCacheKey;blur;${blurWidthPx}x$blurHeightPx;r$blurRadiusPx",
        blurWidthPx = blurWidthPx,
        blurHeightPx = blurHeightPx,
    )
}

internal fun ImageRequest.Builder.applyAuroraBlurBackground(
    spec: AuroraPosterBackgroundSpec,
    blurRadiusPx: Int,
): ImageRequest.Builder {
    return memoryCacheKey(spec.blurMemoryCacheKey)
        .size(spec.blurWidthPx, spec.blurHeightPx)
        .precision(Precision.INEXACT)
        .staticBlur(blurRadiusPx, intensityFactor = 0.6f)
}

package eu.kanade.presentation.entries.components.aurora

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import eu.kanade.presentation.components.resolveAuroraCoverPlaceholderMemoryCacheKey
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverValue

internal data class AuroraPosterBackgroundSpec(
    val memoryCacheKey: String,
)

internal fun auroraPosterBackgroundSpec(
    baseCacheKey: String,
    containerWidthPx: Int,
    containerHeightPx: Int,
): AuroraPosterBackgroundSpec {
    return AuroraPosterBackgroundSpec(
        memoryCacheKey = "$baseCacheKey;${containerWidthPx}x$containerHeightPx",
    )
}

internal fun buildAuroraPosterBackgroundRequest(
    context: Context,
    data: Any?,
    spec: AuroraPosterBackgroundSpec,
    containerWidthPx: Int,
    containerHeightPx: Int,
    placeholderData: Any? = null,
    configure: ImageRequest.Builder.() -> Unit = {},
): ImageRequest {
    val placeholderKey = resolveAuroraPosterPlaceholderMemoryCacheKey(placeholderData)
    debugTitleCoverFlow(
        scope = "poster-request",
        message = "build data=${previewTitleCoverValue(
            data,
        )} memoryKey=${spec.memoryCacheKey} placeholderKey=$placeholderKey size=${containerWidthPx}x$containerHeightPx",
    )
    return ImageRequest.Builder(context)
        .data(data)
        .memoryCacheKey(spec.memoryCacheKey)
        .placeholderMemoryCacheKey(placeholderKey)
        .size(containerWidthPx, containerHeightPx)
        .apply(configure)
        .build()
}

private fun resolveAuroraPosterPlaceholderMemoryCacheKey(placeholderData: Any?): String? {
    return when (placeholderData) {
        is AuroraPosterBackgroundSpec -> placeholderData.memoryCacheKey
        else -> resolveAuroraCoverPlaceholderMemoryCacheKey(placeholderData)
    }
}

@Composable
internal fun rememberAuroraPosterBackgroundPainter(
    request: ImageRequest,
    placeholderPainter: Painter,
): AsyncImagePainter {
    return rememberAsyncImagePainter(
        model = request,
        error = placeholderPainter,
        fallback = placeholderPainter,
        contentScale = ContentScale.Crop,
    )
}

internal fun Modifier.auroraPosterBlur(blurRadius: Dp): Modifier {
    return if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(blurRadius)
    } else {
        this
    }
}

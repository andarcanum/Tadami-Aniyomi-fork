package eu.kanade.presentation.entries.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverValue
import kotlinx.coroutines.flow.collectLatest

/**
 * Two-layer hero cover that mirrors the fullscreen poster logic used by
 * [FullscreenPosterBackground]: an instant thumbnail preview layer with the
 * full-res poster (metadata-resolved URL or user custom cover) fading in on
 * top. The last successfully drawn frame is kept as the placeholder, so cover
 * updates (custom cover set/deleted, reconnect reloads, cover edits) never
 * flash the card blank.
 *
 * Coil requests are only built once the card has been measured (onSizeChanged),
 * because ImageRequest.Builder.size rejects zero dimensions; before the first
 * layout pass nothing is drawn yet anyway.
 *
 * [reloadTick] is a monotonically increasing signal (e.g. network reconnect)
 * that forces a fresh request while the previous frame stays visible.
 */
@Composable
internal fun AuroraHeroCoverImage(
    entryId: Long,
    posterRequest: AuroraPosterRequest,
    previewCoverModel: Any?,
    placeholderPainter: Painter,
    reloadTick: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val hasContainerSize = containerSize.width > 0 && containerSize.height > 0

    // Last successful frame + ready flag are keyed per entry: cover edits keep
    // showing the old frame until the new one is ready, then fade across.
    var previousSuccessfulSpec by remember(entryId) {
        mutableStateOf<AuroraPosterBackgroundSpec?>(null)
    }
    var isPosterReady by remember(entryId) {
        mutableStateOf(false)
    }

    val backgroundSpec = remember(
        entryId,
        posterRequest.primaryUrl,
        posterRequest.fallbackUrl,
        posterRequest.coverLastModified,
        posterRequest.customCoverFile?.exists() == true,
        reloadTick,
        containerSize,
    ) {
        auroraPosterBackgroundSpec(
            baseCacheKey = "hero-cover;$entryId;${posterRequest.coverLastModified};" +
                "${posterRequest.primaryUrl.orEmpty()};${posterRequest.fallbackUrl.orEmpty()};" +
                "${posterRequest.customCoverFile?.exists() == true};$reloadTick",
            containerWidthPx = containerSize.width,
            containerHeightPx = containerSize.height,
        )
    }

    val backgroundRequest = remember(
        posterRequest,
        previewCoverModel,
        previousSuccessfulSpec?.memoryCacheKey,
        backgroundSpec.memoryCacheKey,
        containerSize,
    ) {
        if (hasContainerSize) {
            buildAuroraPosterBackgroundRequest(
                context = context,
                data = posterRequest,
                spec = backgroundSpec,
                containerWidthPx = containerSize.width,
                containerHeightPx = containerSize.height,
                placeholderData = previousSuccessfulSpec
                    ?.takeIf { it.memoryCacheKey != backgroundSpec.memoryCacheKey }
                    ?: previewCoverModel,
            )
        } else {
            null
        }
    }

    val previewRequest = remember(previewCoverModel, containerSize) {
        if (hasContainerSize) {
            // Reuse the grid request so the thumbnail resolves from the memory
            // cache instantly (never the "no poster" placeholder).
            buildAuroraCoverImageRequest(context, previewCoverModel)
        } else {
            null
        }
    }

    if (previewRequest != null && backgroundRequest != null) {
        val backgroundPainter = rememberAuroraPosterBackgroundPainter(
            request = backgroundRequest,
            placeholderPainter = placeholderPainter,
        )
        val previewPainter = rememberAsyncImagePainter(
            model = previewRequest,
            error = placeholderPainter,
            fallback = placeholderPainter,
            contentScale = ContentScale.Crop,
        )

        LaunchedEffect(backgroundPainter, backgroundSpec) {
            backgroundPainter.state.collectLatest { state ->
                if (state is AsyncImagePainter.State.Success) {
                    previousSuccessfulSpec = backgroundSpec
                    isPosterReady = true
                } else if (state is AsyncImagePainter.State.Error) {
                    // Poster failed: keep the preview thumbnail instead of the placeholder.
                    isPosterReady = false
                }
                debugTitleCoverFlow(
                    scope = "hero-cover",
                    message = "painterState=${state::class.simpleName} " +
                        "poster=${previewTitleCoverValue(posterRequest)} " +
                        "memoryKey=${backgroundSpec.memoryCacheKey}",
                )
            }
        }

        val posterAlpha by animateFloatAsState(
            targetValue = if (isPosterReady) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "heroPosterAlpha",
        )

        Box(
            modifier = modifier.onSizeChanged { containerSize = it },
        ) {
            // Base preview layer (list thumbnail) - instant, never blank on enter/reload.
            Image(
                painter = previewPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier.fillMaxSize(),
            )

            // Full poster (resolved or custom) fades in on top (smooth overlay, no black).
            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = posterAlpha },
            )
        }
    } else {
        // First composition happens before layout: the card has no measured size
        // yet, so Coil requests cannot be built (zero px is rejected). Just hold
        // the box; onSizeChanged fires on the first layout pass and the layers
        // compose right after.
        Box(
            modifier = modifier.onSizeChanged { containerSize = it },
        )
    }
}

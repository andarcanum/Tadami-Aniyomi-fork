package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.tachiyomi.data.coil.staticBlur
import tachiyomi.domain.entries.anime.model.Anime

/**
 * Fixed fullscreen poster background with scroll-based dimming and blur effects.
 *
 * @param anime Anime object containing cover information
 * @param scrollOffset Current scroll offset from LazyListState
 * @param firstVisibleItemIndex Current first visible item index from LazyListState
 * @param resolvedCoverUrl Resolved cover URL to display (null to skip loading)
 */
@Composable
fun FullscreenPosterBackground(
    anime: Anime,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    modifier: Modifier = Modifier,
    resolvedCoverUrl: String?,
    resolvedCoverUrlFallback: String? = null,
) {
    val context = LocalContext.current

    // Once user scrolled away from first screen, keep blur/dim at maximum permanently
    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

    // Calculate dim alpha - permanent after scrolling away
    val dimAlpha by animateFloatAsState(
        targetValue = if (hasScrolledAway) 0.7f else (scrollOffset / 100f).coerceIn(0f, 0.7f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "dimAlpha",
    )
    val blurOverlayAlpha by animateFloatAsState(
        targetValue = if (hasScrolledAway) 1f else (scrollOffset / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "blurOverlayAlpha",
    )
    val blurRadiusPx = with(LocalDensity.current) { 20.dp.roundToPx() }

    Box(modifier = modifier.fillMaxSize()) {
        if (resolvedCoverUrl != null) {
            var model by remember(resolvedCoverUrl) { mutableStateOf(resolvedCoverUrl) }

            AsyncImage(
                model = remember(model, anime.id, anime.coverLastModified) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .build()
                },
                onError = {
                    if (model == resolvedCoverUrl && resolvedCoverUrlFallback != null) {
                        model = resolvedCoverUrlFallback
                    }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            AsyncImage(
                model = remember(model, anime.id, anime.coverLastModified, blurRadiusPx) {
                    ImageRequest.Builder(context)
                        .data(model)
                        .staticBlur(blurRadiusPx, intensityFactor = 0.6f)
                        .build()
                },
                onError = {
                    if (model == resolvedCoverUrl && resolvedCoverUrlFallback != null) {
                        model = resolvedCoverUrlFallback
                    }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = blurOverlayAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Base gradient overlay (always present)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.1f),
                        0.5f to Color.Black.copy(alpha = 0.4f),
                        0.7f to Color.Black.copy(alpha = 0.7f),
                        1.0f to Color.Black.copy(alpha = 0.9f),
                    ),
                ),
        )

        // Scroll-based dimming overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimAlpha)),
        )
    }
}

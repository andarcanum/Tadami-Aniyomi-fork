package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.asMangaCover

/**
 * Fixed fullscreen poster background with scroll-based dimming and blur effects.
 *
 * @param manga Manga object containing cover information
 * @param scrollOffset Current scroll offset from LazyListState
 * @param firstVisibleItemIndex Current first visible item index from LazyListState
 */
@Composable
fun FullscreenPosterBackground(
    manga: Manga,
    scrollOffset: Int,
    firstVisibleItemIndex: Int,
    modifier: Modifier = Modifier,
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
        // Base poster.
        AsyncImage(
            model = remember(manga.id, manga.coverLastModified) {
                ImageRequest.Builder(context)
                    .data(manga.asMangaCover())
                    // Avoid reusing low-res cached covers from list thumbnails.
                    .memoryCacheKey("manga-bg;${manga.id};${manga.coverLastModified}")
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Pre-blurred overlay.
        AsyncImage(
            model = remember(manga.id, manga.coverLastModified, blurRadiusPx) {
                ImageRequest.Builder(context)
                    .data(manga.asMangaCover())
                    .memoryCacheKey("manga-bg;${manga.id};${manga.coverLastModified}")
                    .staticBlur(blurRadiusPx, intensityFactor = 0.6f)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = blurOverlayAlpha,
            modifier = Modifier.fillMaxSize(),
        )

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

package eu.kanade.presentation.entries.novel.components.aurora

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import tachiyomi.domain.entries.novel.model.Novel

/**
 * Fixed fullscreen poster background with scroll-based dimming and blur effects.
 *
 * @param novel Novel object containing cover information
 * @param scrollOffset Current scroll offset from LazyListState
 * @param firstVisibleItemIndex Current first visible item index from LazyListState
 */
@Composable
fun FullscreenPosterBackground(
    novel: Novel,
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

    // Calculate blur amount - permanent after scrolling away
    val blurAmount = if (hasScrolledAway) {
        20.dp
    } else {
        (scrollOffset / 100f * 20f).coerceIn(0f, 20f).dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full quality poster
        AsyncImage(
            model = remember(novel.id, novel.coverLastModified) {
                ImageRequest.Builder(context)
                    .data(novel.thumbnailUrl)
                    // Avoid reusing low-res cached covers from list thumbnails.
                    .memoryCacheKey("novel-bg;${novel.id};${novel.coverLastModified}")
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurAmount),
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

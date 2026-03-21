package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCoverModel
import eu.kanade.presentation.entries.components.aurora.applyAuroraBlurBackground
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val posterModel = resolveAuroraCoverModel(manga.asMangaCover())

    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

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
    val blurRadiusPx = with(density) { 20.dp.roundToPx() }
    val containerWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val containerHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val backgroundSpec = remember(
        manga.id,
        manga.coverLastModified,
        containerWidthPx,
        containerHeightPx,
        blurRadiusPx,
    ) {
        auroraPosterBackgroundSpec(
            baseCacheKey = "manga-bg;${manga.id};${manga.coverLastModified}",
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            blurRadiusPx = blurRadiusPx,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (posterModel != null) {
            AsyncImage(
                model = remember(posterModel, backgroundSpec.sharpMemoryCacheKey) {
                    ImageRequest.Builder(context)
                        .data(posterModel)
                        .memoryCacheKey(backgroundSpec.sharpMemoryCacheKey)
                        .build()
                },
                error = placeholderPainter,
                fallback = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            AsyncImage(
                model = remember(posterModel, backgroundSpec, blurRadiusPx) {
                    ImageRequest.Builder(context)
                        .data(posterModel)
                        .applyAuroraBlurBackground(
                            spec = backgroundSpec,
                            blurRadiusPx = blurRadiusPx,
                        )
                        .build()
                },
                error = placeholderPainter,
                fallback = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = blurOverlayAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dimAlpha)),
        )
    }
}

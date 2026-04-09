package eu.kanade.presentation.entries.anime.components.aurora

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
import eu.kanade.presentation.components.resolveAuroraPosterModelPair
import eu.kanade.presentation.entries.components.aurora.applyAuroraBlurBackground
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraPosterScrimBrush
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val posterModelPair = remember(resolvedCoverUrl, resolvedCoverUrlFallback) {
        resolveAuroraPosterModelPair(resolvedCoverUrl, resolvedCoverUrlFallback)
    }
    val posterRequest = remember(posterModelPair) {
        AuroraPosterRequest(
            primaryUrl = posterModelPair.primary as? String,
            fallbackUrl = posterModelPair.fallback as? String,
        )
    }
    val posterModel = posterRequest.primaryUrl ?: posterRequest.fallbackUrl

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

    Box(modifier = modifier.fillMaxSize()) {
        val colors = AuroraTheme.colors

        if (posterModel != null) {
            val backgroundSpec = remember(
                anime.id,
                anime.coverLastModified,
                posterRequest.hashCode(),
                containerWidthPx,
                containerHeightPx,
                blurRadiusPx,
            ) {
                auroraPosterBackgroundSpec(
                    baseCacheKey = "anime-bg;${anime.id};${anime.coverLastModified};${posterRequest.hashCode()}",
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    blurRadiusPx = blurRadiusPx,
                )
            }
            AsyncImage(
                model = remember(posterRequest, backgroundSpec.sharpMemoryCacheKey) {
                    ImageRequest.Builder(context)
                        .data(posterRequest)
                        .memoryCacheKey(backgroundSpec.sharpMemoryCacheKey)
                        .build()
                },
                error = placeholderPainter,
                fallback = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = rememberAuroraPosterColorFilter(),
                modifier = Modifier.fillMaxSize(),
            )

            AsyncImage(
                model = remember(posterRequest, backgroundSpec, blurRadiusPx) {
                    ImageRequest.Builder(context)
                        .data(posterRequest)
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
                colorFilter = rememberAuroraPosterColorFilter(),
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
                .background(resolveAuroraPosterScrimBrush(colors)),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (colors.isDark) {
                        Color.Black.copy(alpha = dimAlpha)
                    } else {
                        colors.background.copy(alpha = dimAlpha * 0.18f)
                    },
                ),
        )
    }
}

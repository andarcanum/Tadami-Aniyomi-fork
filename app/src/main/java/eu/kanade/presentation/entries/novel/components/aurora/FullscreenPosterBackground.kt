package eu.kanade.presentation.entries.novel.components.aurora

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
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBlur
import eu.kanade.presentation.entries.components.aurora.buildAuroraPosterBackgroundRequest
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterBackgroundPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraPosterScrimBrush
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import eu.kanade.presentation.theme.AuroraTheme
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
    minimumBlurOverlayAlpha: Float = 0f,
    posterScrimAlpha: Float? = null,
    modifier: Modifier = Modifier,
    resolvedCoverUrl: String? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val posterModel = resolvedCoverUrl?.takeIf { it.isNotBlank() } ?: sourceAwareNovelCoverModel(novel)
    val isPosterLoadable = when (posterModel) {
        is String -> posterModel.isNotBlank()
        null -> false
        else -> true
    }
    val posterColorFilter = rememberAuroraPosterColorFilter()

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
        targetValue = if (hasScrolledAway) {
            1f
        } else {
            (scrollOffset / 100f).coerceIn(minimumBlurOverlayAlpha, 1f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "blurOverlayAlpha",
    )
    val containerWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val containerHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val backgroundSpec = remember(
        novel.id,
        novel.coverLastModified,
        resolvedCoverUrl,
        containerWidthPx,
        containerHeightPx,
    ) {
        auroraPosterBackgroundSpec(
            baseCacheKey = "novel-bg;${novel.id};${novel.coverLastModified};${resolvedCoverUrl.orEmpty()}",
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        val colors = AuroraTheme.colors
        val scrimColor = if (colors.isDark) Color.Black else colors.background

        if (isPosterLoadable) {
            val backgroundRequest = remember(
                posterModel,
                backgroundSpec.memoryCacheKey,
                containerWidthPx,
                containerHeightPx,
            ) {
                buildAuroraPosterBackgroundRequest(
                    context = context,
                    data = posterModel,
                    spec = backgroundSpec,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                ) {
                    placeholderMemoryCacheKey(novel.thumbnailUrl)
                }
            }
            val backgroundPainter = rememberAuroraPosterBackgroundPainter(
                request = backgroundRequest,
                placeholderPainter = placeholderPainter,
            )

            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                alpha = blurOverlayAlpha,
                modifier = Modifier
                    .fillMaxSize()
                    .auroraPosterBlur(20.dp),
            )
        } else {
            Image(
                painter = placeholderPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (posterScrimAlpha != null) {
            val posterScrimBottomAlpha = (posterScrimAlpha + 0.15f).coerceAtMost(1f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to scrimColor.copy(alpha = posterScrimAlpha),
                                0.6f to scrimColor.copy(alpha = posterScrimAlpha),
                                1.0f to scrimColor.copy(alpha = posterScrimBottomAlpha),
                            ),
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(resolveAuroraPosterScrimBrush(colors)),
            )
        }

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

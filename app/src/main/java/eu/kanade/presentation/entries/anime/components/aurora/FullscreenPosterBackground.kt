package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.AuroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBlur
import eu.kanade.presentation.entries.components.aurora.buildAuroraPosterBackgroundRequest
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterBackgroundPainter
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterColorFilter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraPosterScrimBrush
import eu.kanade.presentation.entries.components.aurora.shouldDrawAuroraPosterBlurOverlay
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.asAnimeCover

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
    refererUrl: String? = null,
    onPosterLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val posterRequest = remember(resolvedCoverUrl, resolvedCoverUrlFallback, refererUrl, anime.thumbnailUrl) {
        AuroraPosterRequest(
            primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
            fallbackUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: anime.thumbnailUrl,
            refererUrl = refererUrl?.takeIf { it.isNotBlank() },
        )
    }
    val posterModel = posterRequest.primaryUrl ?: posterRequest.fallbackUrl
    val posterColorFilter = rememberAuroraPosterColorFilter()

    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

    // PERF (backported from novel Aurora): direct values on initial to avoid anim cost on every open.
    val rawDim = if (hasScrolledAway) 0.7f else (scrollOffset / 100f).coerceIn(0f, 0.7f)
    val rawBlur = if (hasScrolledAway) {
        1f
    } else {
        (scrollOffset / 100f).coerceIn(0f, 1f)
    }

    val dimAlpha by animateFloatAsState(
        targetValue = rawDim,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (hasScrolledAway) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "dimAlpha",
    )
    val blurOverlayAlpha by animateFloatAsState(
        targetValue = rawBlur,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (hasScrolledAway) Spring.StiffnessLow else Spring.StiffnessMedium,
        ),
        label = "blurOverlayAlpha",
    )
    val containerWidthPx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val containerHeightPx = with(density) { configuration.screenHeightDp.dp.roundToPx() }
    val placeholderPosterUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: anime.thumbnailUrl
    val placeholderCover = remember(
        anime.id,
        anime.source,
        anime.favorite,
        anime.coverLastModified,
        placeholderPosterUrl,
    ) {
        anime.asAnimeCover().copy(url = placeholderPosterUrl)
    }
    var previousSuccessfulBackgroundSpec by remember(anime.id) {
        mutableStateOf<AuroraPosterBackgroundSpec?>(null)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onPosterLongPress != null) {
                    Modifier.pointerInput(onPosterLongPress) {
                        detectTapGestures(
                            onLongPress = { onPosterLongPress() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val colors = AuroraTheme.colors

        if (posterModel != null) {
            val backgroundSpec = remember(
                anime.id,
                anime.coverLastModified,
                posterRequest,
                containerWidthPx,
            ) {
                val baseCacheKey = "anime-bg;${anime.id};${anime.coverLastModified};" +
                    posterRequest.primaryUrl.orEmpty()
                auroraPosterBackgroundSpec(
                    baseCacheKey = baseCacheKey,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                )
            }
            val backgroundRequest = remember(
                posterRequest,
                placeholderCover,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
                backgroundSpec.memoryCacheKey,
                containerWidthPx,
                containerHeightPx,
            ) {
                buildAuroraPosterBackgroundRequest(
                    context = context,
                    data = posterRequest,
                    spec = backgroundSpec,
                    containerWidthPx = containerWidthPx,
                    containerHeightPx = containerHeightPx,
                    placeholderData = previousSuccessfulBackgroundSpec
                        ?.takeIf { it.memoryCacheKey != backgroundSpec.memoryCacheKey }
                        ?: placeholderCover,
                )
            }
            val backgroundPainter = rememberAuroraPosterBackgroundPainter(
                request = backgroundRequest,
                placeholderPainter = placeholderPainter,
            )
            LaunchedEffect(
                posterRequest.primaryUrl,
                placeholderPosterUrl,
                backgroundSpec.memoryCacheKey,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
            ) {
                val fallbackKey = "anime;${anime.id};$placeholderPosterUrl;${anime.coverLastModified}"
                val debugMessage = "request poster=${previewTitleCoverUrl(posterRequest.primaryUrl)} " +
                    "placeholder=${previewTitleCoverUrl(placeholderPosterUrl)} " +
                    "memoryKey=${backgroundSpec.memoryCacheKey} " +
                    "placeholderKey=${previousSuccessfulBackgroundSpec?.memoryCacheKey ?: fallbackKey}"
                debugTitleCoverFlow(
                    scope = "anime-bg",
                    message = debugMessage,
                )
            }
            LaunchedEffect(backgroundPainter, backgroundSpec) {
                backgroundPainter.state.collectLatest { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        previousSuccessfulBackgroundSpec = backgroundSpec
                    }
                    debugTitleCoverFlow(
                        scope = "anime-bg",
                        message = "painterState=${state::class.simpleName} poster=${previewTitleCoverUrl(
                            posterRequest.primaryUrl,
                        )} memoryKey=${backgroundSpec.memoryCacheKey}",
                    )
                }
            }

            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            // PERF (backported from novel): guard the blur layer cost on initial Aurora title open.
            val shouldApplyBlurLayer by remember {
                derivedStateOf {
                    blurOverlayAlpha > 0.08f &&
                        shouldDrawAuroraPosterBlurOverlay(blurOverlayAlpha)
                }
            }
            if (shouldApplyBlurLayer) {
                Image(
                    painter = backgroundPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = posterColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = blurOverlayAlpha
                        }
                        .auroraPosterBlur(20.dp),
                )
            }
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
                .drawWithCache {
                    val color = if (colors.isDark) Color.Black else colors.background
                    val factor = if (colors.isDark) 1f else 0.35f
                    onDrawBehind {
                        drawRect(color = color, alpha = dimAlpha * factor)
                    }
                },
        )
    }
}

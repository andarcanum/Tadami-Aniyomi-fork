package eu.kanade.presentation.entries.manga.components.aurora

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.components.resolveAuroraCoverPlaceholderMemoryCacheKey
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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover

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
    minimumBlurOverlayAlpha: Float = 0f,
    posterScrimAlpha: Float? = null,
    modifier: Modifier = Modifier,
    resolvedCoverUrl: String? = null,
    resolvedCoverUrlFallback: String? = null,
    refererUrl: String? = null,
    onPosterLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val posterRequest = remember(resolvedCoverUrl, resolvedCoverUrlFallback, refererUrl, manga.thumbnailUrl) {
        AuroraPosterRequest(
            primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
            fallbackUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: manga.thumbnailUrl,
            refererUrl = refererUrl?.takeIf { it.isNotBlank() },
        )
    }
    val posterModel = posterRequest.primaryUrl ?: posterRequest.fallbackUrl
    val posterColorFilter = rememberAuroraPosterColorFilter()

    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

    // PERF (backported from novel Aurora): on initial load use direct values to avoid
    // animation cost until scroll. Saves work on every title open for Aurora screen.
    val rawDim = if (hasScrolledAway) 0.7f else (scrollOffset / 100f).coerceIn(0f, 0.7f)
    val rawBlur = if (hasScrolledAway) {
        1f
    } else {
        (scrollOffset / 100f).coerceIn(minimumBlurOverlayAlpha, 1f)
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
    val placeholderPosterUrl = resolvedCoverUrlFallback?.takeIf { it.isNotBlank() } ?: manga.thumbnailUrl
    val placeholderCover = remember(
        manga.id,
        manga.source,
        manga.favorite,
        manga.coverLastModified,
        placeholderPosterUrl,
    ) {
        MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = placeholderPosterUrl,
            lastModified = manga.coverLastModified,
        )
    }
    // Stable preview from manga's thumbnail (the one visible in library/browse grid).
    // Full/resolves poster will fade in over it.
    val previewCoverModel = remember(manga.id) {
        MangaCover(
            mangaId = manga.id,
            sourceId = manga.source,
            isMangaFavorite = manga.favorite,
            url = manga.thumbnailUrl,
            lastModified = manga.coverLastModified,
        )
    }
    var previousSuccessfulBackgroundSpec by remember(manga.id) {
        mutableStateOf<AuroraPosterBackgroundSpec?>(null)
    }
    // Drives overlay fade for the detailed/full poster.
    var isHighResPosterReady by remember(manga.id) {
        mutableStateOf<Boolean>(false)
    }
    val backgroundSpec = remember(
        manga.id,
        manga.coverLastModified,
        posterModel,
        containerWidthPx,
        containerHeightPx,
    ) {
        auroraPosterBackgroundSpec(
            baseCacheKey = "manga-bg;${manga.id};${manga.coverLastModified};${posterModel.orEmpty()}",
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
        )
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

            // Preview layer from original thumbnail for instant non-black on enter.
            // Full poster (with resolved if any) overlays via fade animation.
            val previewRequest = remember(manga.id) {
                ImageRequest.Builder(context)
                    .data(previewCoverModel)
                    .size(containerWidthPx, containerHeightPx)
                    .placeholderMemoryCacheKey(
                        resolveAuroraCoverPlaceholderMemoryCacheKey(previewCoverModel),
                    )
                    .build()
            }
            val previewLayerPainter = rememberAsyncImagePainter(
                model = previewRequest,
                error = placeholderPainter,
                fallback = placeholderPainter,
                contentScale = ContentScale.Crop,
            )

            LaunchedEffect(
                posterRequest.primaryUrl,
                placeholderPosterUrl,
                backgroundSpec.memoryCacheKey,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
            ) {
                val fallbackKey = "manga;${manga.id};$placeholderPosterUrl;${manga.coverLastModified}"
                val debugMessage = "request poster=${previewTitleCoverUrl(posterRequest.primaryUrl)} " +
                    "placeholder=${previewTitleCoverUrl(placeholderPosterUrl)} " +
                    "memoryKey=${backgroundSpec.memoryCacheKey} " +
                    "placeholderKey=${previousSuccessfulBackgroundSpec?.memoryCacheKey ?: fallbackKey}"
                debugTitleCoverFlow(
                    scope = "manga-bg",
                    message = debugMessage,
                )
            }
            LaunchedEffect(backgroundPainter, backgroundSpec) {
                backgroundPainter.state.collectLatest { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        previousSuccessfulBackgroundSpec = backgroundSpec
                        isHighResPosterReady = true
                    }
                    debugTitleCoverFlow(
                        scope = "manga-bg",
                        message = "painterState=${state::class.simpleName} poster=${previewTitleCoverUrl(
                            posterRequest.primaryUrl,
                        )} memoryKey=${backgroundSpec.memoryCacheKey}",
                    )
                }
            }

            val highResAlpha by animateFloatAsState(
                targetValue = if (isHighResPosterReady) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "highResPosterAlpha",
            )

            // Base preview layer (list thumbnail) - instant.
            Image(
                painter = previewLayerPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            // Full poster fades in on top (smooth overlay, no black).
            Image(
                painter = backgroundPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = highResAlpha
                    },
            )

            // PERF (backported from novel): only pay expensive blur layer on scroll or significant blur.
            // Avoids double full-res + blur modifier on initial Aurora title launch.
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
                            alpha = blurOverlayAlpha * highResAlpha
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

        if (posterScrimAlpha != null) {
            val scrimColor = if (colors.isDark) Color.Black else colors.background
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

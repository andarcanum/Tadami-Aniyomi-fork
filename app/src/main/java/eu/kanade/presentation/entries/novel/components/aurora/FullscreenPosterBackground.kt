package eu.kanade.presentation.entries.novel.components.aurora

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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import eu.kanade.domain.ui.model.EInkProfile
import eu.kanade.presentation.components.AuroraCoverPlaceholderVariant
import eu.kanade.presentation.components.buildAuroraCoverImageRequest
import eu.kanade.presentation.components.rememberAuroraCoverPlaceholderPainter
import eu.kanade.presentation.entries.components.aurora.AuroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBackgroundSpec
import eu.kanade.presentation.entries.components.aurora.auroraPosterBlur
import eu.kanade.presentation.entries.components.aurora.buildAuroraPosterBackgroundRequest
import eu.kanade.presentation.entries.components.aurora.rememberAuroraPosterBackgroundPainter
import eu.kanade.presentation.entries.components.aurora.resolveAuroraPosterScrimBrush
import eu.kanade.presentation.entries.components.aurora.shouldDrawAuroraPosterBlurOverlay
import eu.kanade.presentation.novel.sourceAwareNovelCoverModel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.coil.AuroraPosterRequest
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import eu.kanade.tachiyomi.util.previewTitleCoverValue
import kotlinx.coroutines.flow.collectLatest
import okhttp3.Call
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
    sourceHeaders: Map<String, String>? = null,
    sourceClient: Call.Factory? = null,
    onPosterLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val placeholderPainter = rememberAuroraCoverPlaceholderPainter(AuroraCoverPlaceholderVariant.Wide)
    val coverCache = remember { Injekt.get<NovelCoverCache>() }
    // Issue #154: surface the user-set custom cover on the details poster,
    // matching what the library grid already shows.
    val customCoverFile = remember(novel.id, novel.coverLastModified) {
        coverCache.getCustomCoverFile(novel.id).takeIf { it.exists() }
    }
    val posterRequest =
        remember(
            resolvedCoverUrl,
            novel.thumbnailUrl,
            sourceHeaders,
            sourceClient,
            customCoverFile,
            novel.coverLastModified,
        ) {
            AuroraPosterRequest(
                primaryUrl = resolvedCoverUrl?.takeIf { it.isNotBlank() },
                fallbackUrl = novel.thumbnailUrl,
                headers = sourceHeaders,
                client = sourceClient,
                customCoverFile = customCoverFile,
                coverLastModified = novel.coverLastModified,
            )
        }
    // Stable preview from the thumbnail shown in list/grid before open.
    // We keep this layer always visible initially so enter never shows black,
    // then overlay the (possibly higher-quality or full-screen-sized) poster.
    val previewCoverModel = remember(novel.id) {
        sourceAwareNovelCoverModel(novel)
    }
    val isPosterLoadable = !posterRequest.primaryUrl.isNullOrBlank() ||
        !posterRequest.fallbackUrl.isNullOrBlank() ||
        posterRequest.customCoverFile != null
    val colors = AuroraTheme.colors
    val hasScrolledAway = firstVisibleItemIndex > 0 || scrollOffset > 100

    // PERF: on initial load (no scroll) we can use direct values to avoid animation cost
    // until user actually interacts. Springs are nice but add work on every open.
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

    val posterColorFilter = remember(colors.isDark, colors.eInkProfile, blurOverlayAlpha) {
        if (colors.eInkProfile == EInkProfile.MONOCHROME) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else if (!colors.isDark) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(1f - (blurOverlayAlpha * 0.35f))
                },
            )
        } else {
            null
        }
    }

    var previousSuccessfulBackgroundSpec by remember(novel.id) {
        mutableStateOf<AuroraPosterBackgroundSpec?>(null)
    }
    // Drives the overlay animation for the full poster once its target data succeeds.
    // Preview layer (above) ensures instant non-black content from list thumbnail.
    var isHighResPosterReady by remember(novel.id) {
        mutableStateOf<Boolean>(false)
    }

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
        val scrimColor = if (colors.isDark) Color.Black else colors.background

        if (isPosterLoadable) {
            val backgroundRequest = remember(
                posterRequest,
                previewCoverModel,
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
                        ?: previewCoverModel,
                )
            }
            val backgroundPainter = rememberAuroraPosterBackgroundPainter(
                request = backgroundRequest,
                placeholderPainter = placeholderPainter,
            )

            // Preview layer (thumbnail from before navigation) is always rendered first.
            // This matches old behavior: show preview poster immediately, full version
            // replaces via background overlay animation (not crude black swap).
            // Preview layer: reuse the exact grid request so the thumbnail resolves
            // from the memory cache instantly — never the "no poster" placeholder.
            val previewRequest = remember(novel.id, previewCoverModel) {
                buildAuroraCoverImageRequest(context, previewCoverModel)
            }
            val previewLayerPainter = rememberAsyncImagePainter(
                model = previewRequest,
                error = placeholderPainter,
                fallback = placeholderPainter,
                contentScale = ContentScale.Crop,
            )

            LaunchedEffect(
                posterRequest,
                resolvedCoverUrl,
                backgroundSpec.memoryCacheKey,
                previousSuccessfulBackgroundSpec?.memoryCacheKey,
            ) {
                val fallbackKey = "novel;${novel.id};${novel.thumbnailUrl};${novel.coverLastModified}"
                val debugMessage = "request poster=${previewTitleCoverValue(posterRequest)} " +
                    "resolved=${previewTitleCoverUrl(resolvedCoverUrl)} " +
                    "memoryKey=${backgroundSpec.memoryCacheKey} " +
                    "placeholderKey=${previousSuccessfulBackgroundSpec?.memoryCacheKey ?: fallbackKey}"
                debugTitleCoverFlow(
                    scope = "novel-bg",
                    message = debugMessage,
                )
            }
            LaunchedEffect(backgroundPainter, backgroundSpec) {
                backgroundPainter.state.collectLatest { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        previousSuccessfulBackgroundSpec = backgroundSpec
                        isHighResPosterReady = true
                    } else if (state is AsyncImagePainter.State.Error) {
                        // Full poster failed (e.g. Cloudflare on the generic poster
                        // client): hide the overlay so the preview thumbnail stays
                        // visible instead of showing the "no poster" placeholder.
                        isHighResPosterReady = false
                    }
                    debugTitleCoverFlow(
                        scope = "novel-bg",
                        message = "painterState=${state::class.simpleName} poster=${previewTitleCoverValue(
                            posterRequest,
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

            // Base preview layer (from list thumbnail) - guarantees instant visible poster on title enter.
            Image(
                painter = previewLayerPainter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = posterColorFilter,
                modifier = Modifier.fillMaxSize(),
            )

            // High-res / full poster (resolved or screen-sized Aurora request) fades in on top.
            // This is the smooth background overlay replacement instead of black swap.
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

            val activePosterPainter = if (isHighResPosterReady) backgroundPainter else previewLayerPainter

            // PERF: only pay the expensive blur layer cost when user has scrolled or blur is significant.
            // Avoids double full-res decode + heavy blur modifier on initial screen launch.
            val shouldApplyBlurLayer by remember {
                derivedStateOf {
                    blurOverlayAlpha > 0.08f &&
                        shouldDrawAuroraPosterBlurOverlay(blurOverlayAlpha)
                }
            }
            if (shouldApplyBlurLayer) {
                Image(
                    painter = activePosterPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = posterColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = blurOverlayAlpha
                        }
                        .auroraPosterBlur(if (colors.isDark) 20.dp else 32.dp),
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
                    val factor = if (colors.isDark) 1f else 0.60f
                    onDrawBehind {
                        drawRect(color = color, alpha = dimAlpha * factor)
                    }
                },
        )
    }
}

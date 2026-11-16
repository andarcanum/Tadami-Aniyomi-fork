package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.ui.home.DiscoveryAllPicksPill
import eu.kanade.tachiyomi.ui.home.ForYouHeaderRow
import eu.kanade.tachiyomi.ui.home.HybridDiscoveryHeaderRow
import eu.kanade.tachiyomi.ui.home.resolveHomeHubRecentPosterCardSpec
import eu.kanade.tachiyomi.ui.home.resolveHomeHubRecentPosterSurfaceSpec
import eu.kanade.tachiyomi.ui.home.resolveHybridDiscoveryStripLayoutSpec
import eu.kanade.tachiyomi.ui.home.resolveStageSlotPose
import kotlin.math.abs

@Composable
internal fun rememberDiscoveryShimmerAlpha(): Float {
    val colors = AuroraTheme.colors
    if (colors.isEInk) return 0.2f
    val transition = rememberInfiniteTransition(label = "discovery_shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "discovery_shimmer_alpha",
    )
    return alpha
}

@Composable
internal fun rememberDiscoverySkeletonPlaceholderColor(): Color {
    val colors = AuroraTheme.colors
    val shimmerAlpha = rememberDiscoveryShimmerAlpha()
    return if (colors.isDark) {
        Color.White.copy(alpha = shimmerAlpha * 0.15f)
    } else {
        Color.Black.copy(alpha = shimmerAlpha * 0.10f)
    }
}

@Composable
internal fun DiscoveryFeedSkeletonGrid(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val placeholderColor = rememberDiscoverySkeletonPlaceholderColor()
    val cardShape = RoundedCornerShape(16.dp)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(130.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        items(count = 12) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(if (colors.isDark) colors.cardBackground else Color.White)
                    .then(
                        if (colors.isDark || colors.isEInk) {
                            Modifier.border(1.dp, colors.divider.copy(alpha = 0.4f), cardShape)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(placeholderColor),
                )
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                }
            }
        }
    }
}

/**
 * Скелет постера-карточки, повторяющий геометрию DiscoveryPosterCard (аспект 0.9 + текстовый блок снизу).
 */
@Composable
internal fun DiscoveryPosterSkeletonCard(
    cardWidth: Dp,
    deviceClass: AuroraDeviceClass,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val posterSpec = remember(deviceClass) { resolveHomeHubRecentPosterCardSpec(deviceClass) }
    val surfaceSpec = remember(colors.isDark) { resolveHomeHubRecentPosterSurfaceSpec(colors.isDark) }
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)
    val outerSurface = when {
        colors.isDark -> colors.glass.copy(alpha = surfaceSpec.containerAlpha)
        colors.isEInk -> resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass)
        else -> Color.White.copy(alpha = 0.68f)
    }
    val posterSurface = if (colors.isDark) {
        colors.cardBackground.copy(alpha = surfaceSpec.posterAlpha)
    } else {
        resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle)
    }

    Box(
        modifier = modifier
            .width(cardWidth)
            .clip(cardShape)
            .background(outerSurface)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(
                        width = 1.dp,
                        color = if (colors.isDark) {
                            Color.White.copy(alpha = 0.06f)
                        } else {
                            Color.Black.copy(alpha = 0.05f)
                        },
                        shape = cardShape,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterSpec.posterAspectRatio)
                    .clip(posterShape)
                    .background(posterSurface),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(placeholderColor),
                )
            }
            Spacer(Modifier.height(posterSpec.textTopSpacingDp.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = posterSpec.textBlockMinHeightDp.dp)
                    .padding(horizontal = posterSpec.textHorizontalPaddingDp.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
                )
            }
        }
    }
}

@Composable
internal fun DiscoveryTeaserSkeletonRow(
    cardWidth: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val placeholderColor = rememberDiscoverySkeletonPlaceholderColor()
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = adaptiveSpec.updatesMaxWidthDp ?: adaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(adaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(adaptiveSpec.deviceClass)
    }
    val resolvedCardWidth = cardWidth ?: layoutSpec.cardWidth.dp
    val sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp
    val rowSpacing = layoutSpec.rowSpacing.dp

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp),
        contentPadding = PaddingValues(horizontal = sectionHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(rowSpacing),
        userScrollEnabled = false,
    ) {
        items(count = 5) {
            DiscoveryPosterSkeletonCard(
                cardWidth = resolvedCardWidth,
                deviceClass = adaptiveSpec.deviceClass,
                placeholderColor = placeholderColor,
            )
        }
    }
}

/** Скелет секции «Для тебя» (режим «Продолжить»): заголовок + рельс карточек в единых пропорциях. */
@Composable
internal fun ForYouSectionSkeleton(
    isRefreshing: Boolean,
    onRefreshClick: (() -> Unit)?,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = adaptiveSpec.updatesMaxWidthDp ?: adaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(adaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(adaptiveSpec.deviceClass)
    }

    Column(modifier = modifier.padding(top = 32.dp)) {
        ForYouHeaderRow(
            contentMaxWidthDp = contentMaxWidthDp,
            sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp,
            isRefreshing = isRefreshing,
            onRefreshClick = onRefreshClick,
            onMoreClick = onMoreClick,
        )
        Spacer(Modifier.height(16.dp))
        DiscoveryTeaserSkeletonRow()
    }
}

/** Скелет гибридной полосы под compact hero в стиле HybridDiscoveryStrip. */
@Composable
internal fun HybridDiscoveryStripSkeleton(
    onMoreClick: () -> Unit,
    isRefreshing: Boolean = true,
    onRefreshClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val placeholderColor = rememberDiscoverySkeletonPlaceholderColor()
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    val stripMaxWidthDp = adaptiveSpec.updatesMaxWidthDp ?: adaptiveSpec.entryMaxWidthDp
    val layoutSpec = remember(adaptiveSpec.deviceClass) {
        resolveHybridDiscoveryStripLayoutSpec(adaptiveSpec.deviceClass)
    }

    Column(
        modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(stripMaxWidthDp)
            .padding(top = 12.dp, bottom = 16.dp),
    ) {
        HybridDiscoveryHeaderRow(
            sectionHorizontalPadding = layoutSpec.sectionHorizontalPadding.dp,
            isRefreshing = isRefreshing,
            onRefreshClick = onRefreshClick,
            onMoreClick = onMoreClick,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = layoutSpec.sectionHorizontalPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(layoutSpec.rowSpacing.dp),
            userScrollEnabled = false,
        ) {
            items(count = 4) {
                DiscoveryPosterSkeletonCard(
                    cardWidth = layoutSpec.cardWidth.dp,
                    deviceClass = adaptiveSpec.deviceClass,
                    placeholderColor = placeholderColor,
                )
            }
        }
    }
}

/**
 * Скелет «Кинематографичного фокуса» (Stage): точно повторяет высоту 440dp, 3D-позы слотов (-2..2)
 * и кнопку «Все подборки», исключая визуальный скачок при появлении постеров.
 */
@Composable
internal fun DiscoveryHeroStageSkeleton(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val placeholderColor = rememberDiscoverySkeletonPlaceholderColor()
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = adaptiveSpec.updatesMaxWidthDp ?: adaptiveSpec.entryMaxWidthDp
    val outerShape = RoundedCornerShape(20.dp)
    val tileShape = RoundedCornerShape(18.dp)

    Box(
        modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 10.dp),
        ) {
            val slotOrder = listOf(-2, 2, -1, 1, 0)
            slotOrder.forEach { rel ->
                val pose = resolveStageSlotPose(rel)
                val isCenter = rel == 0
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(0.92f)
                        .fillMaxWidth(0.6f)
                        .zIndex(10f - abs(rel))
                        .graphicsLayer {
                            translationX = size.width * pose.translationXPercent / 100f
                            scaleX = pose.scale
                            scaleY = pose.scale
                            rotationY = pose.rotationYDeg
                            cameraDistance = 12f * density
                            alpha = pose.alpha
                        }
                        .clip(tileShape)
                        .background(colors.cardBackground)
                        .then(
                            if (colors.isDark || colors.isEInk) {
                                Modifier.border(
                                    1.dp,
                                    colors.divider.copy(alpha = if (colors.isEInk) 0.7f else 0.4f),
                                    tileShape,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(placeholderColor),
                    )
                    if (pose.dimAlpha > 0f) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    (if (colors.isEInk) Color.White else Color.Black).copy(alpha = pose.dimAlpha),
                                ),
                        )
                    }
                    if (isCenter) {
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0.55f to Color.Transparent,
                                    1.0f to if (colors.isEInk) Color.White.copy(alpha = 0.85f) else Color(0x9904060A),
                                ),
                            ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 14.dp, end = 14.dp, bottom = 70.dp),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.78f)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(placeholderColor),
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth(0.52f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(placeholderColor),
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            DiscoveryAllPicksPill(onMoreClick = onMoreClick)
        }
    }
}

/**
 * Скелет «Коллажа «Для тебя»» (Collage): мозаика 440dp (1 большая левая плитка + 2 колонки по 2 малых)
 * с кнопкой «Все подборки».
 */
@Composable
internal fun DiscoveryHeroCollageSkeleton(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val placeholderColor = rememberDiscoverySkeletonPlaceholderColor()
    val adaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = adaptiveSpec.updatesMaxWidthDp ?: adaptiveSpec.entryMaxWidthDp
    val outerShape = RoundedCornerShape(20.dp)
    val tileShape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .fillMaxWidth()
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(outerShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider, outerShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(Modifier.fillMaxSize().padding(5.dp)) {
            CollageSkeletonTile(
                big = true,
                tileShape = tileShape,
                placeholderColor = placeholderColor,
                modifier = Modifier.weight(1.55f).fillMaxHeight(),
            )
            repeat(2) {
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    repeat(2) { index ->
                        if (index > 0) Spacer(Modifier.height(5.dp))
                        CollageSkeletonTile(
                            big = false,
                            tileShape = tileShape,
                            placeholderColor = placeholderColor,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp)) {
            DiscoveryAllPicksPill(onMoreClick = onMoreClick)
        }
    }
}

@Composable
private fun CollageSkeletonTile(
    big: Boolean,
    tileShape: RoundedCornerShape,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier
            .clip(tileShape)
            .background(colors.cardBackground)
            .then(
                if (colors.isDark || colors.isEInk) {
                    Modifier.border(1.dp, colors.divider.copy(alpha = 0.5f), tileShape)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(placeholderColor),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = if (big) 12.dp else 8.dp,
                    end = if (big) 12.dp else 8.dp,
                    bottom = if (big) 58.dp else 10.dp,
                ),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (big) 0.72f else 0.75f)
                    .height(if (big) 15.dp else 11.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderColor),
            )
            if (big) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.45f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
                )
            }
        }
    }
}

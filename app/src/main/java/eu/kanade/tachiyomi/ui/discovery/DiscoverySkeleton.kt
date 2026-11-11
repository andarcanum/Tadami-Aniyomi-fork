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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme

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
internal fun DiscoveryFeedSkeletonGrid(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shimmerAlpha = rememberDiscoveryShimmerAlpha()
    val placeholderColor = if (colors.isDark) {
        Color.White.copy(alpha = shimmerAlpha * 0.15f)
    } else {
        Color.Black.copy(alpha = shimmerAlpha * 0.10f)
    }
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

@Composable
internal fun DiscoveryTeaserSkeletonRow(
    cardWidth: Dp = 140.dp,
    cardHeight: Dp = 210.dp,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val shimmerAlpha = rememberDiscoveryShimmerAlpha()
    val placeholderColor = if (colors.isDark) {
        Color.White.copy(alpha = shimmerAlpha * 0.15f)
    } else {
        Color.Black.copy(alpha = shimmerAlpha * 0.10f)
    }
    val cardShape = RoundedCornerShape(18.dp)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        items(count = 5) {
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
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
                        .fillMaxSize()
                        .background(placeholderColor),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(placeholderColor),
                    )
                }
            }
        }
    }
}

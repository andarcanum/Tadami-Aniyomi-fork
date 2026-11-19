package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Cold-start Reels skeleton shimmer: shows realistic silhouettes of the right action rail, author
 * avatar/handle, description lines, tag chips and progress track instead of a bare spinner over a
 * blank black canvas. Respects e-ink mode with a static high-contrast placeholder.
 */
@Composable
fun ReelsFeedSkeletonShimmer(
    modifier: Modifier = Modifier,
) {
    val isEInk = AuroraTheme.colors.isEInk
    val shimmerBrush = if (isEInk) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.14f),
            ),
        )
    } else {
        val transition = rememberInfiniteTransition(label = "reels_skeleton_shimmer")
        val shift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 900f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "reels_skeleton_offset",
        )
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.08f),
            ),
            start = Offset(shift - 300f, shift - 300f),
            end = Offset(shift, shift),
        )
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val safeBottom = maxOf(navBottom, 26.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Right action rail placeholders
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = safeBottom + 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(4) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush),
                    )
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush),
                    )
                }
            }
        }

        // Bottom meta silhouette (avatar + author handle + caption + tags)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.74f)
                .padding(start = 16.dp, bottom = safeBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(shimmerBrush),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(64.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(shimmerBrush),
                )
                Box(
                    modifier = Modifier
                        .width(82.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(shimmerBrush),
                )
            }
        }

        // Bottom scrubber track silhouette
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = safeBottom + 10.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(shimmerBrush),
        )
    }
}

/**
 * Small loading indicator shown at the bottom of the feed while the next page is fetched.
 */
@Composable
fun ReelsNextPageLoader(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = AuroraTheme.colors.accent,
            trackColor = Color.White.copy(alpha = 0.18f),
            strokeWidth = 3.dp,
        )
        Text(
            text = stringResource(MR.strings.reels_loading_more),
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f),
        )
    }
}

/**
 * Empty state for a search that returned no videos, with a hint and a reset action.
 */
@Composable
fun ReelsEmptySearchState(
    onResetSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.SearchOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(MR.strings.reels_empty_search_title),
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(MR.strings.reels_empty_search_hint),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onResetSearch) {
            Text(
                text = stringResource(MR.strings.action_reset),
                color = AuroraTheme.colors.accent,
            )
        }
    }
}

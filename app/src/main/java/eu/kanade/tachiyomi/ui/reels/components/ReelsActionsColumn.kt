package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReelsActionsColumn(
    isLiked: Boolean,
    isMuted: Boolean,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    // Creator follow slot (contract v18): shown by the caller only when the source is
    // AnimeCreatorFeedSource and the item has an author.
    showFollow: Boolean = false,
    isFollowing: Boolean = false,
    onToggleFollow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val heartColor = MaterialTheme.colorScheme.error
    val scaleHeart by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_scale",
    )
    val scalePerson by animateFloatAsState(
        targetValue = if (isFollowing) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "follow_scale",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Like Action
        ActionIconItem(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = if (isLiked) {
                stringResource(
                    MR.strings.reels_feed_action_liked,
                )
            } else {
                stringResource(MR.strings.reels_feed_action_like)
            },
            tint = if (isLiked) heartColor else Color.White,
            modifier = Modifier.scale(scaleHeart),
            onClick = onToggleLike,
        )

        // Creator Follow (v18)
        if (showFollow) {
            ActionIconItem(
                icon = if (isFollowing) Icons.Filled.Person else Icons.Filled.PersonAdd,
                label = if (isFollowing) {
                    stringResource(MR.strings.reels_following)
                } else {
                    stringResource(MR.strings.reels_follow)
                },
                tint = if (isFollowing) AuroraTheme.colors.accent else Color.White,
                modifier = Modifier.scale(scalePerson),
                onClick = onToggleFollow,
            )
        }

        // Audio Mute/Unmute
        ActionIconItem(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = stringResource(MR.strings.reels_feed_action_sound),
            tint = Color.White,
            onClick = onToggleMute,
        )

        // Share button
        ActionIconItem(
            icon = Icons.Filled.Share,
            label = stringResource(MR.strings.reels_feed_action_share),
            tint = Color.White,
            onClick = onShare,
        )
    }
}

@Composable
private fun ActionIconItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

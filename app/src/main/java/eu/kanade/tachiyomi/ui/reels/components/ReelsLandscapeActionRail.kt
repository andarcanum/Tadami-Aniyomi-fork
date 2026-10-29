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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val HeartColor = Color(0xFFFF3366)

/**
 * 4-Pod Action Rail for native landscape reels (Prototype v2).
 *
 * Compact ergonomic cluster designed to fit comfortably within the right thumb's reach
 * zone in landscape (compact 210-230dp total height).
 *
 * 1. Like (animated heart counter)
 * 2. Offline (ReelsOfflineStore status toggle with MB indication)
 * 3. Hide / More (AuroraFrostDialog choice)
 * 4. Share (system intent)
 */
@Composable
fun ReelsLandscapeActionRail(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    likesCount: Long? = null,
    isOfflineStored: Boolean = false,
    onToggleOffline: () -> Unit,
    offlineSizeText: String? = null,
    onOpenHide: (() -> Unit)? = null,
    onShare: () -> Unit,
    shareCount: Long? = null,
    modifier: Modifier = Modifier,
) {
    val scaleHeart by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "landscape_rail_heart_scale",
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Like Pod
        val likeLabel = likesCount?.let { formatCompactCount(it) }
            ?: stringResource(if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like)
        LandscapeRailItem(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = likeLabel,
            tint = if (isLiked) HeartColor else Color.White,
            isActive = isLiked,
            activeColor = HeartColor,
            buttonModifier = Modifier.scale(scaleHeart),
            stateDescription = stringResource(
                if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
            ),
            onClick = onToggleLike,
        )

        // 2. Offline Pod
        val offlineLabel = resolveOfflineActionLabel(isOfflineStored, offlineSizeText)
        LandscapeRailItem(
            icon = if (isOfflineStored) Icons.Filled.OfflinePin else Icons.Outlined.Download,
            label = offlineLabel,
            tint = if (isOfflineStored) AuroraTheme.colors.accent else Color.White,
            isActive = isOfflineStored,
            activeColor = AuroraTheme.colors.accent,
            stateDescription = stringResource(
                if (isOfflineStored) MR.strings.reels_offline_saved else MR.strings.reels_offline_save,
            ),
            onClick = onToggleOffline,
        )

        // 3. More / Hide Pod (Not Interested / More)
        if (onOpenHide != null) {
            LandscapeRailItem(
                icon = Icons.Filled.MoreVert,
                label = stringResource(MR.strings.reels_hide_menu),
                tint = Color.White,
                onClick = onOpenHide,
            )
        }

        // 4. Share Pod
        val shareLabel = shareCount?.let { formatCompactCount(it) }
            ?: stringResource(MR.strings.reels_feed_action_share)
        LandscapeRailItem(
            icon = Icons.Filled.Share,
            label = shareLabel,
            tint = Color.White,
            onClick = onShare,
        )
    }
}

@Composable
private fun LandscapeRailItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeColor: Color = Color.Transparent,
    stateDescription: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    if (stateDescription != null) this.stateDescription = stateDescription
                }
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .then(buttonModifier),
            contentAlignment = Alignment.Center,
        ) {
            val bgColor = if (isActive) {
                activeColor.copy(alpha = 0.22f)
            } else {
                Color(0xFF0F141E).copy(alpha = 0.78f)
            }
            val borderColor = if (isActive) {
                activeColor.copy(alpha = 0.55f)
            } else {
                Color.White.copy(alpha = 0.18f)
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bgColor, CircleShape)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.95f),
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

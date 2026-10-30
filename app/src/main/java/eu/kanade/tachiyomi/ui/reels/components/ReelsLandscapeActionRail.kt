package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
 * Compact action rail for native landscape reels.
 *
 * Compact ergonomic cluster designed to fit comfortably within the right thumb's reach
 * zone in landscape (compact 210-230dp total height).
 *
 * 1. Like (animated heart counter)
 * 2. Return to portrait
 * 3. Download (real file size and transfer progress)
 * 4. Hide / More (AuroraFrostDialog choice)
 * 5. Share (system intent)
 */
@Composable
fun ReelsLandscapeActionRail(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    likesCount: Long? = null,
    onToggleLandscape: () -> Unit,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    onDownload: (() -> Unit)? = null,
    downloadSizeText: String? = null,
    downloadProgress: Float? = null,
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
            contentDescription = stringResource(MR.strings.reels_feed_action_like),
            tint = if (isLiked) HeartColor else Color.White,
            isActive = isLiked,
            buttonModifier = Modifier.scale(scaleHeart),
            stateDescription = stringResource(
                if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
            ),
            onClick = onToggleLike,
        )

        LandscapeRailItem(
            icon = Icons.Filled.StayCurrentPortrait,
            label = stringResource(MR.strings.reels_landscape_exit),
            showLabel = false,
            tint = Color.White,
            onClick = onToggleLandscape,
        )

        if (onDownload != null) {
            val downloadLabel = stringResource(MR.strings.reels_download_video)
            // One control, four meanings: idle starts, running cancels, partial continues,
            // finished opens. The caller owns the decision, so the pod stays tappable.
            val runningLabel = stringResource(MR.strings.reels_download_cancel)
            val resumableLabel = stringResource(MR.strings.reels_download_resume)
            LandscapeRailItem(
                icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                label = resolveDownloadActionLabel(
                    isDownloading,
                    downloadSizeText,
                    downloadLabel,
                    stringResource(MR.strings.reels_download_in_progress),
                ),
                contentDescription = when {
                    isDownloading -> runningLabel
                    isPartial -> resumableLabel
                    isDownloaded -> stringResource(MR.strings.reels_download_open)
                    else -> downloadLabel
                },
                tint = if (isDownloaded || isPartial) AuroraTheme.colors.accent else Color.White,
                isActive = isDownloaded || isPartial,
                stateDescription = when {
                    isDownloading -> runningLabel
                    isPartial -> resumableLabel
                    isDownloaded -> stringResource(MR.strings.reels_download_saved)
                    else -> downloadLabel
                },
                isLoading = isDownloading,
                progress = downloadProgress,
                onClick = onDownload,
            )
        }

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
            contentDescription = stringResource(MR.strings.reels_feed_action_share),
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
    stateDescription: String? = null,
    contentDescription: String = label,
    showLabel: Boolean = true,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    progress: Float? = null,
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
                    this.contentDescription = contentDescription
                    if (stateDescription != null) this.stateDescription = stateDescription
                }
                .clip(CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
                .then(buttonModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .reelsGlassSurface(
                        active = isActive,
                        eInk = AuroraTheme.colors.isEInk,
                        eInkColor = AuroraTheme.colors.cardBackground,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    if (progress == null) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(18.dp),
                            color = tint,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f),
                ),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

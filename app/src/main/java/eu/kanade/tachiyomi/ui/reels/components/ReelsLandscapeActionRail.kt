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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
internal const val REELS_LANDSCAPE_TOUCH_TARGET_DP = 48f

/**
 * Ergonomic thumb-arc action rail for native landscape reels (Variant V1).
 *
 * Uses a compact frosted pill (`>= 48dp` touch targets wrapping 36dp glass pods) positioned in
 * the bottom-end quadrant above the scrubber dock:
 * 1. Like (animated heart counter)
 * 2. Optional legacy Exit Portrait slot (`showExitButton = false` once TopBar owns it in Wave 2)
 * 3. Download (real file size and transfer progress)
 * 4. Share (system intent)
 * 5. More / Hide (AuroraFrostDialog choice)
 */
@Composable
fun ReelsLandscapeActionRail(
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    likesCount: Long? = null,
    onToggleLandscape: () -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    onDownload: (() -> Unit)? = null,
    downloadSizeText: String? = null,
    downloadProgress: Float? = null,
    onOpenHide: (() -> Unit)? = null,
    onShare: () -> Unit,
    shareCount: Long? = null,
    showExitButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val contentTint = if (auroraColors.isEInk) MaterialTheme.colorScheme.onSurface else Color.White
    val scaleHeart by animateFloatAsState(
        targetValue = if (isLiked) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "landscape_rail_heart_scale",
    )
    val railShape = RoundedCornerShape(26.dp)
    val railContainerColor = if (auroraColors.isEInk) {
        auroraColors.cardBackground
    } else {
        Color(0xFF0E0C13).copy(alpha = 0.44f)
    }
    val railBorderColor = if (auroraColors.isEInk) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    } else {
        Color.White.copy(alpha = 0.14f)
    }

    Column(
        modifier = modifier
            .clip(railShape)
            .background(railContainerColor, railShape)
            .border(1.dp, railBorderColor, railShape)
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 1. Like Pod
        val likeLabel = likesCount?.let { formatCompactCount(it) }
            ?: stringResource(if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like)
        LandscapeRailItem(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = likeLabel,
            contentDescription = stringResource(MR.strings.reels_feed_action_like),
            tint = if (isLiked) HeartColor else contentTint,
            labelColor = contentTint,
            isActive = isLiked,
            buttonModifier = Modifier.scale(scaleHeart),
            stateDescription = stringResource(
                if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
            ),
            onClick = onToggleLike,
        )

        if (showExitButton) {
            LandscapeRailItem(
                icon = Icons.Filled.StayCurrentPortrait,
                label = stringResource(MR.strings.reels_landscape_exit),
                showLabel = false,
                tint = contentTint,
                labelColor = contentTint,
                onClick = onToggleLandscape,
            )
        }

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
                tint = if (isDownloaded || isPartial) auroraColors.accent else contentTint,
                labelColor = contentTint,
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

        // 3. Share Pod
        val shareLabel = shareCount?.let { formatCompactCount(it) }
            ?: stringResource(MR.strings.reels_feed_action_share)
        LandscapeRailItem(
            icon = Icons.Filled.Share,
            label = shareLabel,
            contentDescription = stringResource(MR.strings.reels_feed_action_share),
            tint = contentTint,
            labelColor = contentTint,
            onClick = onShare,
        )

        // 4. More / Hide Pod (Not Interested / More)
        if (onOpenHide != null) {
            LandscapeRailItem(
                icon = Icons.Filled.MoreVert,
                label = stringResource(MR.strings.reels_hide_menu),
                tint = contentTint,
                labelColor = contentTint,
                onClick = onOpenHide,
            )
        }
    }
}

/**
 * YouTube/Netflix-style center transport cluster (`-10s` / `Play|Pause` / `+10s`) for
 * landscape Reels playback.
 */
@Composable
fun ReelsLandscapeCenterTransport(
    isPlaying: Boolean,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val contentTint = if (auroraColors.isEInk) MaterialTheme.colorScheme.onSurface else Color.White

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        CenterSeekButton(
            icon = Icons.Filled.Replay10,
            contentDescription = stringResource(MR.strings.reels_a11y_seek_back),
            tint = contentTint,
            onClick = onSeekBack,
        )

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (auroraColors.isEInk) {
                        auroraColors.cardBackground
                    } else {
                        Color.Black.copy(alpha = 0.60f)
                    },
                    CircleShape,
                )
                .border(
                    1.dp,
                    if (auroraColors.isEInk) {
                        contentTint.copy(alpha = 0.35f)
                    } else {
                        Color.White.copy(alpha = 0.28f)
                    },
                    CircleShape,
                )
                .clickable(role = Role.Button, onClick = onTogglePlayPause),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (!isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(
                    if (!isPlaying) MR.strings.action_play else MR.strings.action_pause,
                ),
                tint = contentTint,
                modifier = Modifier.size(34.dp),
            )
        }

        CenterSeekButton(
            icon = Icons.Filled.Forward10,
            contentDescription = stringResource(MR.strings.reels_a11y_seek_forward),
            tint = contentTint,
            onClick = onSeekForward,
        )
    }
}

@Composable
private fun CenterSeekButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val auroraColors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .size(REELS_LANDSCAPE_TOUCH_TARGET_DP.dp)
            .clip(CircleShape)
            .background(
                if (auroraColors.isEInk) {
                    auroraColors.cardBackground
                } else {
                    Color.Black.copy(alpha = 0.55f)
                },
                CircleShape,
            )
            .border(
                1.dp,
                if (auroraColors.isEInk) {
                    tint.copy(alpha = 0.3f)
                } else {
                    Color.White.copy(alpha = 0.22f)
                },
                CircleShape,
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LandscapeRailItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    labelColor: Color,
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
    val auroraColors = AuroraTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .defaultMinSize(
                minWidth = REELS_LANDSCAPE_TOUCH_TARGET_DP.dp,
                minHeight = REELS_LANDSCAPE_TOUCH_TARGET_DP.dp,
            )
            .clip(RoundedCornerShape(20.dp))
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (stateDescription != null) this.stateDescription = stateDescription
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(buttonModifier)
                .reelsGlassSurface(
                    active = isActive,
                    eInk = auroraColors.isEInk,
                    eInkColor = auroraColors.cardBackground,
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
        if (showLabel) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor.copy(alpha = 0.92f),
                maxLines = 1,
                style = if (auroraColors.isEInk) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f),
                    )
                },
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

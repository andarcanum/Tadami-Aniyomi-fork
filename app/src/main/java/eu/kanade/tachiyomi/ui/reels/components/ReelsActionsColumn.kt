package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun ReelsActionsColumn(
    isLiked: Boolean,
    isMuted: Boolean,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    // Deprecated duplicate creator follow params kept only for source compatibility;
    // follow now lives solely next to the author/channel chip in ReelsBottomMeta.
    @Suppress("UNUSED_PARAMETER")
    showFollow: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    isFollowing: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    onToggleFollow: () -> Unit = {},
    likesCount: Long? = null,
    // B3.2 "more / hide" slot kept for call-site source compatibility until Wave 2 cleanup.
    @Suppress("UNUSED_PARAMETER")
    onOpenHide: (() -> Unit)? = null,
    // B3.3 offline-copy slot (offline playlist only): null hides the slot.
    onToggleOffline: (() -> Unit)? = null,
    isOfflineStored: Boolean = false,
    offlineSizeText: String? = null,
    // Native window orientation toggle (U01): the thumb reaches the action rail, not the top
    // bar, and it sits right after Like so it reads as a view mode, not as a media action.
    landscape: Boolean = false,
    onToggleLandscape: () -> Unit = {},
    // Export-to-Downloads slot (portrait): same four meanings as the landscape rail pod —
    // idle starts, running cancels, partial continues, finished opens. Null hides the slot.
    onDownload: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    downloadSizeText: String? = null,
    downloadProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val scaleHeart by animateFloatAsState(
        targetValue = if (isLiked) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart_scale",
    )

    val showLabels = true

    @Composable
    fun LikeItem() {
        val likeLabel = stringResource(
            if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
        )
        val visualLabel = likesCount?.takeIf { it > 0L }?.let(::formatCompactCount) ?: likeLabel
        ActionIconItem(
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            label = visualLabel,
            contentDescription = likeLabel,
            tint = if (isLiked) HeartColor else Color.White,
            modifier = Modifier.scale(scaleHeart),
            stateDescription = likeLabel,
            showLabel = showLabels,
            onClick = onToggleLike,
        )
    }

    @Composable
    fun LandscapeItem() {
        ActionIconItem(
            icon = if (landscape) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            label = stringResource(if (landscape) MR.strings.reels_action_portrait else MR.strings.reels_action_rotate),
            stateDescription = stringResource(
                if (landscape) MR.strings.reels_landscape_exit else MR.strings.reels_landscape_fullscreen,
            ),
            tint = if (landscape) AuroraTheme.colors.accent else Color.White,
            showLabel = showLabels,
            onClick = onToggleLandscape,
        )
    }

    @Composable
    fun DownloadItem() {
        val download = onDownload ?: return
        val downloadLabel = stringResource(MR.strings.reels_download_video)
        val runningLabel = stringResource(MR.strings.reels_download_cancel)
        val resumableLabel = stringResource(MR.strings.reels_download_resume)
        ActionIconItem(
            icon = if (isDownloaded || isPartial) Icons.Filled.DownloadDone else Icons.Outlined.Download,
            label = resolveDownloadActionLabel(
                isDownloading,
                downloadSizeText,
                downloadLabel,
                stringResource(MR.strings.reels_download_in_progress),
            ),
            stateDescription = when {
                isDownloading -> runningLabel
                isPartial -> resumableLabel
                isDownloaded -> stringResource(MR.strings.reels_download_saved)
                else -> downloadLabel
            },
            tint = if (isDownloaded || isPartial) AuroraTheme.colors.accent else Color.White,
            showLabel = showLabels,
            isLoading = isDownloading,
            progress = downloadProgress,
            onClick = download,
        )
    }

    @Composable
    fun SoundItem() {
        ActionIconItem(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = stringResource(
                if (isMuted) MR.strings.reels_feed_action_muted else MR.strings.reels_feed_action_sound,
            ),
            // TalkBack needs the CURRENT state, not only a static label.
            stateDescription = stringResource(
                if (isMuted) MR.strings.reels_sound_off else MR.strings.reels_sound_on,
            ),
            tint = if (isMuted) Color(0xFFF87171) else Color.White,
            showLabel = showLabels,
            onClick = onToggleMute,
        )
    }

    @Composable
    fun ShareItem() {
        ActionIconItem(
            icon = Icons.Filled.Share,
            label = stringResource(MR.strings.reels_feed_action_share),
            tint = Color.White,
            showLabel = showLabels,
            onClick = onShare,
        )
    }

    @Composable
    fun OfflineItem() {
        // B3.3 offline copy: download / remove the real local file.
        if (onToggleOffline != null) {
            val offlineLabel = offlineSizeText?.takeIf { it.isNotBlank() }
                ?: stringResource(
                    if (isOfflineStored) MR.strings.reels_download_saved_short else MR.strings.reels_download_video,
                )
            ActionIconItem(
                icon = if (isOfflineStored) Icons.Filled.OfflinePin else Icons.Outlined.Download,
                label = offlineLabel,
                tint = if (isOfflineStored) AuroraTheme.colors.accent else Color.White,
                stateDescription = stringResource(
                    if (isOfflineStored) MR.strings.reels_offline_saved else MR.strings.reels_offline_save,
                ),
                showLabel = showLabels,
                onClick = onToggleOffline,
            )
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LikeItem()
        LandscapeItem()
        DownloadItem()
        ShareItem()
        OfflineItem()
        SoundItem()
    }
}

@Composable
private fun ActionIconItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    stateDescription: String? = null,
    showLabel: Boolean = true,
    isLoading: Boolean = false,
    progress: Float? = null,
) {
    val auroraColors = AuroraTheme.colors
    val isEInk = auroraColors.isEInk
    val resolvedTint = if (isEInk && tint == Color.White) {
        MaterialTheme.colorScheme.onSurface
    } else {
        tint
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier,
    ) {
        // Bare 27dp glyph over the right-edge scrim (no glass circle wrapper) inside a 48dp
        // minimum touch target. E-ink keeps a solid high-contrast circular backplate.
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    role = Role.Button
                    if (stateDescription != null) this.stateDescription = stateDescription
                }
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val content: @Composable () -> Unit = {
                if (isLoading) {
                    val spinnerSize = if (isEInk) 20.dp else 24.dp
                    if (progress == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(spinnerSize),
                            color = resolvedTint,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(spinnerSize),
                            color = resolvedTint,
                            strokeWidth = 2.5.dp,
                        )
                    }
                } else {
                    ReelsChromeIcon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = resolvedTint,
                        size = if (isEInk) 20.dp else 27.dp,
                        eInk = isEInk,
                    )
                }
            }

            if (isEInk) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .reelsGlassSurface(
                            eInk = true,
                            eInkColor = auroraColors.cardBackground,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            } else {
                content()
            }
        }
        if (showLabel) {
            Text(
                text = label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isEInk) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.94f),
                style = if (isEInk) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.labelSmall.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.85f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f,
                        ),
                    )
                },
                modifier = if (isEInk) {
                    Modifier
                        .background(auroraColors.cardBackground.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                } else {
                    Modifier
                },
                maxLines = 1,
            )
        }
    }
}

internal fun resolveDownloadActionLabel(
    isDownloading: Boolean,
    sizeText: String?,
    downloadLabel: String,
    downloadingLabel: String,
): String = sizeText?.takeIf { it.isNotBlank() } ?: if (isDownloading) downloadingLabel else downloadLabel

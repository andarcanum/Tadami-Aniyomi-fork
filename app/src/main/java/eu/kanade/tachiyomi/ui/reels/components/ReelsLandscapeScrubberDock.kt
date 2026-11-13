package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val REELS_LANDSCAPE_DOCK_HEIGHT_DP = 48f
internal const val REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP = 36f
internal val REELS_LANDSCAPE_SPEED_STEPS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
private val DockLikeTint = Color(0xFFFF4D6D)
private val DockSavedTint = Color(0xFF34D399)

/** Cycles through standard landscape playback speeds (`1× -> 1.25× -> 1.5× -> 2× -> 0.75× -> 1×`). */
internal fun nextReelsLandscapeSpeed(current: Float): Float {
    val index = REELS_LANDSCAPE_SPEED_STEPS.indexOfFirst { abs(it - current) < 0.01f }
    return if (index >= 0) {
        REELS_LANDSCAPE_SPEED_STEPS[(index + 1) % REELS_LANDSCAPE_SPEED_STEPS.size]
    } else {
        1f
    }
}

/**
 * Bottom Unified Cinema Console for native landscape reels (Variant A v2).
 *
 * Unifies all playback, aspect, speed, quality, and social actions into a 2-row bottom console
 * with a strict `36dp` visual height (`CircleShape` pills and circles) inside `48dp` minimum
 * touch targets, keeping the right screen edge completely clear of vertical rails.
 */
@Composable
fun ReelsLandscapeScrubberDock(
    progressState: State<Float>,
    durationSec: Float,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onLockScreen: () -> Unit,
    qualityLabel: String? = null,
    onQualityClick: (() -> Unit)? = null,
    isCropMode: Boolean = false,
    onToggleCropMode: (() -> Unit)? = null,
    playbackSpeed: Float = 1f,
    onCycleSpeed: (() -> Unit)? = null,
    isLiked: Boolean = false,
    likesCount: Long? = null,
    onToggleLike: (() -> Unit)? = null,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    downloadProgress: Float? = null,
    onDownload: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onOpenMore: (() -> Unit)? = null,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit = {},
    bufferFraction: Float = 0f,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val contentTint = if (auroraColors.isEInk) MaterialTheme.colorScheme.onSurface else Color.White
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val currentFraction = if (isDragging) dragProgress else progressState.value.coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        label = "landscape_scrub_height",
    )
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "landscape_dock_like_scale",
    )

    val totalSec = durationSec.coerceAtLeast(0f).roundToInt()
    val currentSec = (currentFraction * durationSec).coerceAtLeast(0f).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Row 1: Full-width Slim Seekbar Track + Floating Preview Card
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(currentFraction, 0f..1f)
                        setProgress { target: Float ->
                            onSeek(target.coerceIn(0f, 1f))
                            true
                        }
                    }
                    .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        val gesture = ReelsScrubGesture(touchSlop = viewConfiguration.touchSlop)
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            gesture.start(down.position.x, barWidthPx)
                            isDragging = true
                            dragProgress = gesture.progress
                            onScrubStart()
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                val outcome = gesture.move(delta.x, delta.y, consumedByOther = change.isConsumed)
                                if (outcome == ReelsScrubOutcome.Cancelled) break
                                if (outcome == ReelsScrubOutcome.Owned) change.consume()
                                dragProgress = gesture.progress
                                isDragging = gesture.isActive
                            }
                            gesture.end()?.let(onSeek)
                            isDragging = false
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                )

                if (bufferFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = bufferFraction.coerceIn(0f, 1f))
                            .height(barHeight)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.38f)),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = currentFraction)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(auroraColors.accent),
                )

                Box(
                    modifier = Modifier.fillMaxWidth(fraction = currentFraction),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 14.dp else 11.dp)
                            .clip(CircleShape)
                            .background(Color.White, CircleShape)
                            .border(2.dp, auroraColors.accent, CircleShape),
                    )
                }
            }

            // Floating Precision Scrubber Preview Card
            androidx.compose.animation.AnimatedVisibility(
                visible = isDragging,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut() + scaleOut(targetScale = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val cardWidthPx = 124.dp.roundToPx()
                        val rawCenterPx = (currentFraction * barWidthPx).toInt()
                        val halfWidthPx = cardWidthPx / 2
                        val clampedX = rawCenterPx.coerceIn(
                            halfWidthPx,
                            (barWidthPx - halfWidthPx).toInt().coerceAtLeast(halfWidthPx),
                        )
                        IntOffset(x = clampedX - halfWidthPx, y = -104.dp.roundToPx())
                    },
            ) {
                Column(
                    modifier = Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (auroraColors.isEInk) {
                                auroraColors.cardBackground
                            } else {
                                Color(0xFF0F141E).copy(alpha = 0.96f)
                            },
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = "Tadami Reel",
                                color = auroraColors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatSeconds(currentSec),
                            color = contentTint,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                        Text(
                            text = " / ${formatSeconds(totalSec)}",
                            color = contentTint.copy(alpha = 0.6f),
                            fontSize = 10.5.sp,
                            style = TextStyle(fontFeatureSettings = "tnum"),
                        )
                    }

                    Text(
                        text = stringResource(MR.strings.reels_landscape_preview_frame),
                        color = auroraColors.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Row 2: Unified Control Deck (36dp visual pills/circles inside 48dp touch targets)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = REELS_LANDSCAPE_DOCK_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left Cluster: Tabular Time + Fit/Fill + Speed + Quality
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatSeconds(currentSec),
                        color = contentTint,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Text(
                        text = " / ${formatSeconds(totalSec)}",
                        color = contentTint.copy(alpha = 0.62f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }

                if (onToggleCropMode != null) {
                    val aspectLabel = stringResource(
                        if (isCropMode) MR.strings.reels_aspect_fill else MR.strings.reels_aspect_fit,
                    )
                    DockUnifiedPill(
                        label = aspectLabel,
                        icon = Icons.Outlined.AspectRatio,
                        isActive = isCropMode,
                        contentTint = if (isCropMode) auroraColors.accent else contentTint,
                        contentDescription = "${stringResource(MR.strings.reels_aspect)}: $aspectLabel",
                        onClick = onToggleCropMode,
                    )
                }

                if (onCycleSpeed != null) {
                    val speedText = reelsSpeedLabel(playbackSpeed)
                    val isCustomSpeed = abs(playbackSpeed - 1f) >= 0.01f
                    DockUnifiedPill(
                        label = speedText,
                        isActive = isCustomSpeed,
                        contentTint = if (isCustomSpeed) auroraColors.accent else contentTint,
                        contentDescription = "${stringResource(MR.strings.reels_a11y_speed_2x)}: $speedText",
                        onClick = onCycleSpeed,
                    )
                }

                if (qualityLabel != null && onQualityClick != null) {
                    DockUnifiedPill(
                        label = qualityLabel,
                        contentTint = contentTint,
                        contentDescription = "${stringResource(MR.strings.reels_quality)}: $qualityLabel",
                        onClick = onQualityClick,
                    )
                }
            }

            // Right Cluster: Like + Download + Share + Mute + Lock + More (all 36dp visual CircleShape)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (onToggleLike != null) {
                    val likeA11y = stringResource(
                        if (isLiked) MR.strings.reels_feed_action_liked else MR.strings.reels_feed_action_like,
                    )
                    val likeTint = if (isLiked) DockLikeTint else contentTint
                    val likeIcon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                    if (likesCount != null && likesCount > 0L) {
                        DockUnifiedPill(
                            label = formatCompactCount(likesCount),
                            icon = likeIcon,
                            isActive = isLiked,
                            contentTint = likeTint,
                            contentDescription = "$likeA11y: ${formatCompactCount(likesCount)}",
                            visualModifier = Modifier.scale(likeScale),
                            onClick = onToggleLike,
                        )
                    } else {
                        DockUnifiedIconButton(
                            icon = likeIcon,
                            contentDescription = likeA11y,
                            tint = likeTint,
                            isActive = isLiked,
                            visualModifier = Modifier.scale(likeScale),
                            onClick = onToggleLike,
                        )
                    }
                }

                if (onDownload != null) {
                    val downloadLabel = stringResource(MR.strings.reels_download_video)
                    val runningLabel = stringResource(MR.strings.reels_download_cancel)
                    val resumableLabel = stringResource(MR.strings.reels_download_resume)
                    val downloadA11y = when {
                        isDownloading -> runningLabel
                        isPartial -> resumableLabel
                        isDownloaded -> stringResource(MR.strings.reels_download_open)
                        else -> downloadLabel
                    }
                    DockUnifiedIconButton(
                        icon = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Outlined.Download,
                        contentDescription = downloadA11y,
                        tint = when {
                            isDownloaded -> DockSavedTint
                            isPartial -> auroraColors.accent
                            else -> contentTint
                        },
                        isActive = isDownloaded || isPartial,
                        isLoading = isDownloading,
                        progress = downloadProgress,
                        onClick = onDownload,
                    )
                }

                if (onShare != null) {
                    DockUnifiedIconButton(
                        icon = Icons.Filled.Share,
                        contentDescription = stringResource(MR.strings.reels_feed_action_share),
                        tint = contentTint,
                        onClick = onShare,
                    )
                }

                DockUnifiedIconButton(
                    icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = stringResource(MR.strings.reels_feed_action_sound),
                    tint = if (isMuted) Color(0xFFF87171) else contentTint,
                    isActive = isMuted,
                    onClick = onToggleMute,
                )

                DockUnifiedIconButton(
                    icon = Icons.Filled.Lock,
                    contentDescription = stringResource(MR.strings.reels_touch_lock),
                    tint = contentTint,
                    onClick = onLockScreen,
                )

                if (onOpenMore != null) {
                    DockUnifiedIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(MR.strings.reels_hide_menu),
                        tint = contentTint,
                        onClick = onOpenMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun DockUnifiedPill(
    label: String,
    contentTint: Color,
    contentDescription: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    visualModifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .defaultMinSize(
                minWidth = REELS_LANDSCAPE_DOCK_HEIGHT_DP.dp,
                minHeight = REELS_LANDSCAPE_DOCK_HEIGHT_DP.dp,
            )
            .clip(CircleShape)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                if (isActive) this.stateDescription = label
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = visualModifier
                .height(REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP.dp)
                .reelsGlassSurface(
                    shape = CircleShape,
                    active = isActive,
                    eInk = auroraColors.isEInk,
                    eInkColor = auroraColors.cardBackground,
                )
                .padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = label,
                color = contentTint,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(fontFeatureSettings = "tnum"),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DockUnifiedIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    isActive: Boolean = false,
    isLoading: Boolean = false,
    progress: Float? = null,
    visualModifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    Box(
        modifier = Modifier
            .size(REELS_LANDSCAPE_DOCK_HEIGHT_DP.dp)
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
            }
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = visualModifier
                .size(REELS_LANDSCAPE_CONTROL_VISUAL_HEIGHT_DP.dp)
                .reelsGlassSurface(
                    shape = CircleShape,
                    active = isActive,
                    eInk = auroraColors.isEInk,
                    eInkColor = auroraColors.cardBackground,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                val safeProgress = progress?.takeIf { it.isFinite() && it in 0f..1f }
                if (safeProgress != null) {
                    CircularProgressIndicator(
                        progress = { safeProgress },
                        modifier = Modifier.size(17.dp),
                        color = auroraColors.accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = auroraColors.accent,
                        strokeWidth = 2.dp,
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
}

package eu.kanade.tachiyomi.ui.reels.components

import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onScrubPreview: ((Float) -> Unit)? = null,
    onScrubStart: () -> Unit = {},
    onScrubStateChange: ((ReelsScrubUiState) -> Unit)? = null,
    externalScrubFraction: Float? = null,
    fineScrubEnabled: Boolean = true,
    bufferFraction: Float = 0f,
    bufferFractionState: androidx.compose.runtime.State<Float>? = null,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    val auroraColors = AuroraTheme.colors
    val contentTint = if (auroraColors.isEInk) MaterialTheme.colorScheme.onSurface else Color.White
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val fineEnterLiftPx = with(density) { 32.dp.toPx() }
    val fineExitLiftPx = with(density) { 10.dp.toPx() }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubPreview by rememberUpdatedState(onScrubPreview)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubStateChange by rememberUpdatedState(onScrubStateChange)
    val currentDurationSec by rememberUpdatedState(durationSec)
    val currentFineScrubEnabled by rememberUpdatedState(fineScrubEnabled)

    val effectiveDragging = isDragging || externalScrubFraction != null
    val semanticProgress by remember(isDragging, externalScrubFraction) {
        derivedStateOf {
            val raw = when {
                isDragging -> dragProgress
                externalScrubFraction != null -> externalScrubFraction.coerceIn(0f, 1f)
                else -> progressState.value.coerceIn(0f, 1f)
            }
            (raw * 100f).roundToInt() / 100f
        }
    }
    val barHeight by animateDpAsState(
        targetValue = if (effectiveDragging) 6.dp else 4.dp,
        label = "landscape_scrub_height",
    )
    val likeScale by animateFloatAsState(
        targetValue = if (isLiked) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "landscape_dock_like_scale",
    )

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
                        progressBarRangeInfo = ProgressBarRangeInfo(semanticProgress, 0f..1f)
                        setProgress { target: Float ->
                            onSeek(target.coerceIn(0f, 1f))
                            true
                        }
                    }
                    .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(fineEnterLiftPx, fineExitLiftPx) {
                        val fineController = ReelsFineScrubController(
                            fineEnterLiftPx = fineEnterLiftPx,
                            fineExitLiftPx = fineExitLiftPx,
                        )
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val startProgress = progressState.value.coerceIn(0f, 1f)
                            val initialTapProgress = (down.position.x / barWidthPx).coerceIn(0f, 1f)
                            fineController.start(
                                initialProgress = initialTapProgress,
                                barWidthPx = barWidthPx,
                                durationSec = currentDurationSec,
                                startProgress = startProgress,
                            )
                            isDragging = true
                            dragProgress = fineController.progress
                            currentOnScrubStart()
                            currentOnScrubStateChange?.invoke(
                                ReelsScrubUiState(
                                    isActive = true,
                                    progress = fineController.progress,
                                    startProgress = startProgress,
                                    isFineMode = false,
                                    fromScreenGesture = false,
                                    barWidthPx = barWidthPx,
                                ),
                            )
                            currentOnScrubPreview?.invoke(fineController.progress)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var lastLiveSeekAtMs = SystemClock.elapsedRealtime()
                            var lastFineTickSec = (fineController.progress * currentDurationSec).roundToInt()
                            try {
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val delta = change.positionChange()
                                    change.consume()
                                    val modeToggled = fineController.move(
                                        dx = delta.x,
                                        dy = delta.y,
                                        allowFineMode = currentFineScrubEnabled,
                                    )
                                    if (modeToggled) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    dragProgress = fineController.progress
                                    if (fineController.isFineMode && currentDurationSec > 0f) {
                                        val tickSec = (fineController.progress * currentDurationSec).roundToInt()
                                        if (tickSec != lastFineTickSec) {
                                            lastFineTickSec = tickSec
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    currentOnScrubStateChange?.invoke(
                                        ReelsScrubUiState(
                                            isActive = true,
                                            progress = fineController.progress,
                                            startProgress = startProgress,
                                            isFineMode = fineController.isFineMode,
                                            fromScreenGesture = false,
                                            barWidthPx = barWidthPx,
                                        ),
                                    )
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastLiveSeekAtMs >= 75L) {
                                        lastLiveSeekAtMs = now
                                        currentOnScrubPreview?.invoke(fineController.progress)
                                    }
                                }
                                fineController.end()?.let { target ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentOnSeek(target)
                                }
                            } finally {
                                fineController.cancel()
                                isDragging = false
                                currentOnScrubStateChange?.invoke(ReelsScrubUiState())
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight),
                ) {
                    val corner = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.22f),
                        cornerRadius = corner,
                    )

                    val effectiveBufferFraction = (bufferFractionState?.value ?: bufferFraction).coerceIn(0f, 1f)
                    if (effectiveBufferFraction > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.38f),
                            size = Size(size.width * effectiveBufferFraction, size.height),
                            cornerRadius = corner,
                        )
                    }

                    val currentFraction = when {
                        isDragging -> dragProgress
                        externalScrubFraction != null -> externalScrubFraction.coerceIn(0f, 1f)
                        else -> progressState.value.coerceIn(0f, 1f)
                    }
                    if (currentFraction > 0f) {
                        drawRoundRect(
                            color = auroraColors.accent,
                            size = Size(size.width * currentFraction, size.height),
                            cornerRadius = corner,
                        )
                    }

                    val thumbRadius = (if (effectiveDragging) 7.5.dp else 5.5.dp).toPx()
                    val strokeWidth = 2.dp.toPx()
                    val centerX = (size.width * currentFraction)
                        .coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
                    val center = Offset(centerX, size.height / 2f)
                    drawCircle(
                        color = Color.White,
                        radius = thumbRadius,
                        center = center,
                    )
                    drawCircle(
                        color = auroraColors.accent,
                        radius = (thumbRadius - strokeWidth / 2f).coerceAtLeast(0f),
                        center = center,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }

            // Preview card and fine-scrub filmstrip are rendered unclipped by ReelsScrubPreviewOverlay.
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
                LandscapeDockTimeDisplay(
                    progressState = progressState,
                    isDragging = isDragging,
                    dragProgress = dragProgress,
                    externalScrubFraction = externalScrubFraction,
                    durationSec = durationSec,
                    contentTint = contentTint,
                )

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

@Composable
private fun LandscapeDockTimeDisplay(
    progressState: State<Float>,
    isDragging: Boolean,
    dragProgress: Float,
    externalScrubFraction: Float?,
    durationSec: Float,
    contentTint: Color,
) {
    val totalSec = durationSec.coerceAtLeast(0f).roundToInt()
    val currentSec by remember(durationSec, isDragging, externalScrubFraction) {
        derivedStateOf {
            val frac = when {
                isDragging -> dragProgress
                externalScrubFraction != null -> externalScrubFraction.coerceIn(0f, 1f)
                else -> progressState.value.coerceIn(0f, 1f)
            }
            (frac * durationSec).coerceAtLeast(0f).roundToInt()
        }
    }
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
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
}

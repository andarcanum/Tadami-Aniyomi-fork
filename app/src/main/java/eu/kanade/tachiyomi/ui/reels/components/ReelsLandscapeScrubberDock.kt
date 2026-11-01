package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.theme.AuroraTheme
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Bottom Scrubber Dock for native landscape reels (Prototype v2).
 *
 * Current time badge, buffered + played progress track with precision preview card,
 * total time badge, and right thumb quick dock actions (Mute & Lock).
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
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit = {},
    bufferFraction: Float = 0f,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val currentFraction = if (isDragging) dragProgress else progressState.value.coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        label = "landscape_scrub_height",
    )

    val totalSec = durationSec.coerceAtLeast(0f).roundToInt()
    val currentSec = (currentFraction * durationSec).coerceAtLeast(0f).roundToInt()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Current Time Badge
        TimeBadge(timeText = formatSeconds(currentSec))

        // Center Track Area
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
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
                // Background Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                )

                // Buffered Progress
                if (bufferFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = bufferFraction.coerceIn(0f, 1f))
                            .height(barHeight)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.38f)),
                    )
                }

                // Active Played Progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = currentFraction)
                        .height(barHeight)
                        .clip(CircleShape)
                        .background(AuroraTheme.colors.accent),
                )

                // Scrubber Handle (Visible always, slightly larger on drag)
                Box(
                    modifier = Modifier.fillMaxWidth(fraction = currentFraction),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isDragging) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(Color.White, CircleShape)
                            .border(2.dp, AuroraTheme.colors.accent, CircleShape),
                    )
                }
            }

            // Floating Precision Scrubber Preview Card (Clamped within borders, matching Prototype v2)
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
                        IntOffset(x = clampedX - halfWidthPx, y = -105.dp.roundToPx())
                    },
            ) {
                Column(
                    modifier = Modifier
                        .width(124.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0F141E).copy(alpha = 0.96f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Preview Thumbnail Box
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
                                color = AuroraTheme.colors.accent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Preview Time
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatSeconds(currentSec),
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = " / ${formatSeconds(totalSec)}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 10.5.sp,
                        )
                    }

                    // Chapter hint
                    Text(
                        text = "Кадр видео • Tadami Reel",
                        color = AuroraTheme.colors.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Total Time Badge
        TimeBadge(timeText = formatSeconds(totalSec))

        // Quick Thumb Dock Actions (Quality, Mute & Lock)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (qualityLabel != null && onQualityClick != null) {
                QualityDockButton(label = qualityLabel, onClick = onQualityClick)
            }

            // Mute Button
            DockCircularButton(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Звук",
                tint = if (isMuted) Color(0xFFF87171) else Color.White,
                onClick = onToggleMute,
            )

            // Lock Button
            DockCircularButton(
                icon = Icons.Filled.Lock,
                contentDescription = "Заблокировать касания",
                tint = Color.White,
                onClick = onLockScreen,
            )
        }
    }
}

@Composable
private fun QualityDockButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TimeBadge(timeText: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = timeText,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DockCircularButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF0F141E).copy(alpha = 0.72f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
}

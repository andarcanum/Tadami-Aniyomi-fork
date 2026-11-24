package eu.kanade.tachiyomi.ui.reels.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReelsProgressBar(
    progressState: State<Float>,
    durationSec: Float,
    onSeek: (Float) -> Unit,
    onScrubPreview: ((Float) -> Unit)? = null,
    onScrubStart: () -> Unit = {},
    onScrubStateChange: ((ReelsScrubUiState) -> Unit)? = null,
    externalScrubFraction: Float? = null,
    chromeVisible: Boolean = true,
    bufferFraction: Float = 0f,
    bufferFractionState: State<Float>? = null,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubPreview by rememberUpdatedState(onScrubPreview)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubStateChange by rememberUpdatedState(onScrubStateChange)

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
        targetValue = if (effectiveDragging) {
            6.dp
        } else if (chromeVisible) {
            4.dp
        } else {
            3.dp
        },
        label = "progress_bar_height",
    )
    val accentColor = AuroraTheme.colors.accent

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 48dp interactive height (invisible — the track anchors to the bottom edge);
            // keeps the gesture area above the a11y minimum without changing the visuals.
            .height(48.dp)
            // TalkBack: expose the scrubber as an adjustable progress control.
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(semanticProgress, 0f..1f)
                setProgress { target: Float ->
                    onSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                // One recognizer arbitrates tap-seek, horizontal scrub and the vertical pager:
                // competing detectors used to commit a seek even after the scroll consumed the
                // press (tryAwaitRelease() returned false and its result was ignored).
                val gesture = ReelsScrubGesture(touchSlop = viewConfiguration.touchSlop)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Own the down so the page-level tap does not also toggle playback; the
                    // pager still tracks it and takes over once the movement turns vertical.
                    down.consume()
                    val startProgress = progressState.value.coerceIn(0f, 1f)
                    gesture.start(down.position.x, barWidthPx)
                    isDragging = true
                    dragProgress = gesture.progress
                    currentOnScrubStart()
                    currentOnScrubStateChange?.invoke(
                        ReelsScrubUiState(
                            isActive = true,
                            progress = gesture.progress,
                            startProgress = startProgress,
                            isFineMode = false,
                            fromScreenGesture = false,
                            barWidthPx = barWidthPx,
                        ),
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    var lastLiveSeekAtMs = 0L
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.positionChange()
                            val outcome = gesture.move(delta.x, delta.y, consumedByOther = change.isConsumed)
                            if (outcome == ReelsScrubOutcome.Cancelled) break
                            if (outcome == ReelsScrubOutcome.Owned) change.consume()
                            dragProgress = gesture.progress
                            isDragging = gesture.isActive
                            if (gesture.isActive) {
                                currentOnScrubStateChange?.invoke(
                                    ReelsScrubUiState(
                                        isActive = true,
                                        progress = gesture.progress,
                                        startProgress = startProgress,
                                        isFineMode = false,
                                        fromScreenGesture = false,
                                        barWidthPx = barWidthPx,
                                    ),
                                )
                            }
                            if (outcome == ReelsScrubOutcome.Owned) {
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastLiveSeekAtMs >= LIVE_SCRUB_THROTTLE_MS) {
                                    lastLiveSeekAtMs = now
                                    currentOnScrubPreview?.invoke(gesture.progress)
                                }
                            }
                        }
                        gesture.end()?.let { target ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnSeek(target)
                        }
                    } finally {
                        isDragging = false
                        currentOnScrubStateChange?.invoke(ReelsScrubUiState())
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Compact Time Badge when chrome is visible and user is not actively scrubbing.
        // Recomposes at most once per second (integer-second derivedStateOf) and unmounts when hidden.
        AnimatedVisibility(
            visible = chromeVisible && !effectiveDragging && durationSec > 0f,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp),
        ) {
            ReelsProgressTimeBadge(
                progressState = progressState,
                isDragging = isDragging,
                dragProgress = dragProgress,
                externalScrubFraction = externalScrubFraction,
                durationSec = durationSec,
            )
        }

        // Preview card is rendered unclipped by ReelsScrubPreviewOverlay in ReelsVideoPage.
        // Draw-phase track: reads progressState and bufferFractionState strictly in DrawScope
        // so playback progress updates cause zero Compose recompositions or layout passes.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
        ) {
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            // Base Track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                cornerRadius = corner,
            )

            // Buffered Progress Fill
            val effectiveBufferFraction = (bufferFractionState?.value ?: bufferFraction).coerceIn(0f, 1f)
            if (effectiveBufferFraction > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.35f),
                    size = Size(size.width * effectiveBufferFraction, size.height),
                    cornerRadius = corner,
                )
            }

            // Active Progress Fill (using dynamic Aurora Theme Accent!)
            val currentFraction = when {
                isDragging -> dragProgress
                externalScrubFraction != null -> externalScrubFraction.coerceIn(0f, 1f)
                else -> progressState.value.coerceIn(0f, 1f)
            }
            if (currentFraction > 0f) {
                drawRoundRect(
                    color = accentColor,
                    size = Size(size.width * currentFraction, size.height),
                    cornerRadius = corner,
                )
            }

            // Scrubber Thumb on Drag
            if (effectiveDragging) {
                val thumbRadius = 7.dp.toPx()
                val strokeWidth = 2.5.dp.toPx()
                val centerX = (size.width * currentFraction)
                    .coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius))
                val center = Offset(centerX, size.height / 2f)
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = center,
                )
                drawCircle(
                    color = accentColor,
                    radius = (thumbRadius - strokeWidth / 2f).coerceAtLeast(0f),
                    center = center,
                    style = Stroke(width = strokeWidth),
                )
            }
        }
    }
}

@Composable
private fun ReelsProgressTimeBadge(
    progressState: State<Float>,
    isDragging: Boolean,
    dragProgress: Float,
    externalScrubFraction: Float?,
    durationSec: Float,
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${formatSeconds(currentSec)} / ${formatSeconds(totalSec)}",
            color = Color.White.copy(alpha = 0.95f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private const val LIVE_SCRUB_THROTTLE_MS = 90L

private fun formatSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.getDefault(), minutes, seconds)
}

internal enum class ReelsScrubOutcome { Pending, Owned, Cancelled }

/**
 * Axis arbitration for the scrubber. A press seeks only when it ends on the bar (tap) or
 * turns into a horizontal drag; a vertical move hands the gesture to the pager and must
 * never commit the progress sampled at the down point.
 */
internal class ReelsScrubGesture(private val touchSlop: Float) {
    private var widthPx = 1f
    private var totalDx = 0f
    private var totalDy = 0f
    private var horizontal = false
    private var cancelled = false

    var isActive: Boolean = false
        private set
    var progress: Float = 0f
        private set

    fun start(x: Float, width: Float) {
        widthPx = width.coerceAtLeast(1f)
        progress = (x / widthPx).coerceIn(0f, 1f)
        totalDx = 0f
        totalDy = 0f
        horizontal = false
        cancelled = false
        isActive = true
    }

    fun move(dx: Float, dy: Float, consumedByOther: Boolean): ReelsScrubOutcome {
        if (!isActive) return ReelsScrubOutcome.Cancelled
        if (!horizontal && consumedByOther) return cancel()
        if (!horizontal) {
            totalDx += dx
            totalDy += dy
            if (abs(totalDy) > touchSlop && abs(totalDy) > abs(totalDx)) return cancel()
            if (abs(totalDx) <= touchSlop || abs(totalDx) < abs(totalDy)) {
                return ReelsScrubOutcome.Pending
            }
            horizontal = true
            // Include the travel accumulated before the axis was known.
            progress = (progress + totalDx / widthPx).coerceIn(0f, 1f)
            return ReelsScrubOutcome.Owned
        }
        progress = (progress + dx / widthPx).coerceIn(0f, 1f)
        return ReelsScrubOutcome.Owned
    }

    /** Returns the fraction to commit, or null when the gesture was cancelled. */
    fun end(): Float? {
        if (!isActive || cancelled) return null
        isActive = false
        return progress
    }

    private fun cancel(): ReelsScrubOutcome {
        cancelled = true
        isActive = false
        return ReelsScrubOutcome.Cancelled
    }
}

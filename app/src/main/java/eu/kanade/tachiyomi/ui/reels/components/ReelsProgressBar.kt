package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
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
    onScrubStart: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    // Only this composable reads progressState.value, so the 10Hz updates recompose just the
    // bar, not the whole video page.
    val currentFraction = if (isDragging) dragProgress else progressState.value.coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 3.dp,
        label = "progress_bar_height",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 48dp interactive height (invisible — the track anchors to the bottom edge);
            // keeps the gesture area above the a11y minimum without changing the visuals.
            .height(48.dp)
            // TalkBack: expose the scrubber as an adjustable progress control.
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(currentFraction, 0f..1f)
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
                    // Exactly one commit per gesture — and none when a competing gesture
                    // (vertical scroll) took it over.
                    gesture.end()?.let(onSeek)
                    isDragging = false
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Floating Time Indicator when dragging (bottom-anchored so the taller touch box
        // does not shift it relative to the bar).
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            val totalSec = durationSec.coerceAtLeast(0f).roundToInt()
            val currentSec = (currentFraction * durationSec).coerceAtLeast(0f).roundToInt()

            val currentFormatted = formatSeconds(currentSec)
            val totalFormatted = formatSeconds(totalSec)

            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, AuroraTheme.colors.accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "$currentFormatted / $totalFormatted",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Base Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Active Progress Fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = currentFraction)
                    .height(barHeight)
                    .background(AuroraTheme.colors.accent),
            )

            // Scrubber Thumb on Drag
            if (isDragging) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = currentFraction),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, AuroraTheme.colors.accent, CircleShape),
                    )
                }
            }
        }
    }
}

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
        if (consumedByOther) return cancel()
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

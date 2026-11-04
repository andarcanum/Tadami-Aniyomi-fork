package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReelsProgressBar(
    progressState: State<Float>,
    durationSec: Float,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit = {},
    chromeVisible: Boolean = true,
    bufferFraction: Float = 0f,
    posterUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    // Only this composable reads progressState.value, so the 10Hz updates recompose just the
    // bar, not the whole video page.
    val currentFraction = if (isDragging) dragProgress else progressState.value.coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (isDragging) {
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
        val totalSec = durationSec.coerceAtLeast(0f).roundToInt()
        val currentSec = (currentFraction * durationSec).coerceAtLeast(0f).roundToInt()

        // Compact Time Badge when chrome is visible and user is not actively scrubbing
        AnimatedVisibility(
            visible = chromeVisible && !isDragging && durationSec > 0f,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp),
        ) {
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

        // Floating Precision Preview Card when scrubbing (drag)
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset {
                    val cardWidthPx = 116.dp.roundToPx()
                    val rawCenterPx = (currentFraction * barWidthPx).toInt()
                    val halfWidthPx = cardWidthPx / 2
                    val clampedX = rawCenterPx.coerceIn(
                        halfWidthPx,
                        (barWidthPx - halfWidthPx).toInt().coerceAtLeast(halfWidthPx),
                    )
                    IntOffset(x = clampedX - halfWidthPx, y = -74.dp.roundToPx())
                },
        ) {
            Column(
                modifier = Modifier
                    .width(116.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F141E).copy(alpha = 0.95f))
                    .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(posterUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = "${formatSeconds(currentSec)} / ${formatSeconds(totalSec)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Base Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(barHeight / 2))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Buffered Progress Fill
            if (bufferFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = bufferFraction.coerceIn(0f, 1f))
                        .height(barHeight)
                        .clip(RoundedCornerShape(barHeight / 2))
                        .background(Color.White.copy(alpha = 0.35f)),
                )
            }

            // Active Progress Fill (using dynamic Aurora Theme Accent!)
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = currentFraction)
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(accentColor),
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
                            .size(14.dp)
                            .background(Color.White, CircleShape)
                            .border(2.5.dp, accentColor, CircleShape),
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

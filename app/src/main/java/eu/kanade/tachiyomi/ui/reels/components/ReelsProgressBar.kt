package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.theme.AuroraTheme
import kotlin.math.roundToInt

@Composable
fun ReelsProgressBar(
    progressState: State<Float>,
    durationSec: Float,
    onSeek: (Float) -> Unit,
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
            .height(32.dp)
            .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        tryAwaitRelease()
                        onSeek(dragProgress)
                        isDragging = false
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / barWidthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragProgress)
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val delta = dragAmount / barWidthPx
                        dragProgress = (dragProgress + delta).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Floating Time Indicator when dragging
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 8.dp),
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
    return "%02d:%02d".format(minutes, seconds)
}

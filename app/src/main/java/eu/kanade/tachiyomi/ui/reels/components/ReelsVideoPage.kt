package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReelsVideoPage(
    item: ShortVideoItem,
    isActive: Boolean,
    isPreload: Boolean = false,
    isPlaying: Boolean,
    isMuted: Boolean,
    isLiked: Boolean,
    isHdQuality: Boolean,
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onTagClick: (String) -> Unit,
    onVideoCompleted: () -> Unit,
    onPlaybackError: (String) -> Unit = {},
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // Progress lives in a State that only ReelsProgressBar reads; writing it 10Hz must NOT
    // invalidate the whole page subtree (recomposition isolation).
    val progressState = remember { mutableFloatStateOf(0f) }
    var durationSec by remember(item) { mutableFloatStateOf(item.durationSec ?: 0f) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var showHeartPop by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val heartScale = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current

    val videoUrl = remember(item, isHdQuality) {
        if (isHdQuality) (item.videoUrlHd ?: item.videoUrl) else item.videoUrl
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // 2x speed from a long-press must reset when the finger lifts. detectTapGestures
                // cancels its press scope the moment the long-press fires (tryAwaitRelease
                // returns false before the finger is up), so reset from a passive watcher instead.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    waitForUpOrCancellation()
                    if (playbackSpeed != 1f) playbackSpeed = 1f
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        playbackSpeed = 2f
                    },
                    onTap = {
                        onTogglePlayPause()
                    },
                    onDoubleTap = { offset ->
                        // Left / right thirds seek ±10s, center double-tap likes.
                        val seekDeltaSec = when {
                            durationSec > 0f && offset.x < size.width / 3f -> -10f
                            durationSec > 0f && offset.x > size.width * 2f / 3f -> 10f
                            else -> 0f
                        }
                        if (seekDeltaSec != 0f) {
                            val currentSec = progressState.floatValue * durationSec
                            val target = ((currentSec + seekDeltaSec) / durationSec).coerceIn(0f, 1f)
                            seekFraction = target
                            progressState.floatValue = target
                            coroutineScope.launch {
                                delay(50)
                                seekFraction = null
                            }
                        } else {
                            if (!isLiked) onToggleLike()
                            coroutineScope.launch {
                                showHeartPop = true
                                heartScale.snapTo(0f)
                                heartScale.animateTo(
                                    targetValue = 1.3f,
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                )
                                delay(200)
                                showHeartPop = false
                            }
                        }
                    },
                )
            },
    ) {
        // 1. Video Player & Poster layer
        ReelsPlayerView(
            videoUrl = videoUrl,
            posterUrl = item.posterUrlVertical ?: item.posterUrl,
            isActive = isActive,
            isPreload = isPreload,
            isMuted = isMuted,
            isPlaying = isPlaying,
            isAutoAdvance = isAutoAdvance,
            isCropMode = isCropMode,
            seekToFraction = seekFraction,
            onProgressUpdate = { progressState.floatValue = it },
            onVideoCompleted = onVideoCompleted,
            onDurationKnown = { if (durationSec <= 0f) durationSec = it },
            onPlaybackError = onPlaybackError,
            onBufferingChanged = { isBuffering = it },
            playbackSpeed = playbackSpeed,
            headers = headers,
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering spinner on the active page.
        if (isBuffering && isActive) {
            androidx.compose.material3.CircularProgressIndicator(
                color = AuroraTheme.colors.accent,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            )
        }

        // 2. Gradient overlays for readable text & controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.45f),
                        0.2f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )

        // 3. Play / Pause indicator overlay (flashes briefly on state change)
        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (!isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // 4. Double tap Heart burst animation
        if (showHeartPop) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = AuroraTheme.colors.accent,
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center)
                    // Read the anim value in the draw phase so the 60Hz burst does not
                    // recompose the whole page (graphicsLayer avoids recomposition).
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                    },
            )
        }

        // 5. Right Action Bar (Like, Mute, Share)
        ReelsActionsColumn(
            isLiked = isLiked,
            isMuted = isMuted,
            onToggleLike = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggleLike()
            },
            onToggleMute = onToggleMute,
            onShare = onShare,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 48.dp),
        )

        // 6. Bottom Meta info (Author, Title, Clickable Tags)
        ReelsBottomMeta(
            item = item,
            onTagClick = onTagClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 76.dp, bottom = 24.dp),
        )

        // 7. Interactive Scrubber / Seek Bar (Aurora style)
        ReelsProgressBar(
            progressState = progressState,
            durationSec = durationSec,
            onSeek = { frac ->
                seekFraction = frac
                progressState.floatValue = frac
                coroutineScope.launch {
                    delay(50)
                    seekFraction = null
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

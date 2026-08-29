package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
    isLastPage: Boolean = false,
    // Feed-level immersive chrome flag: the side action bar hides together with the top bar.
    chromeVisible: Boolean = true,
    // Landscape fullscreen: the page content is rendered rotated 90° inside the locked
    // portrait activity (software rotation — no orientation request, no player rebuild).
    isLandscapeFullscreen: Boolean = false,
    onLandscapeFullscreenChange: (Boolean) -> Unit = {},
    // Stable per-source prefix; the page appends item id + the PINNED quality to build the
    // progressive cache key.
    cachePrefix: String? = null,
    // Creator follow affordances (contract v18): the caller gates both on
    // `source is AnimeCreatorFeedSource && item.author != null`.
    showFollowAction: Boolean = false,
    isFollowingCreator: Boolean = false,
    onToggleFollowCreator: () -> Unit = {},
    onAuthorClick: (() -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onTagClick: (String) -> Unit,
    onVideoCompleted: () -> Unit,
    // Second parameter retries the current video (snackbar "Retry" action).
    onPlaybackError: (String, retry: () -> Unit) -> Unit = { _, _ -> },
    onScrubStart: () -> Unit = {},
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
    var retrySignal by remember { mutableIntStateOf(0) }
    val heartScale = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    // Real orientation of the media, reported by the player; only landscape reels get
    // the fullscreen affordance.
    var isLandscapeVideo by remember(item) { mutableStateOf(false) }

    // The effective (data-saver aware) quality is re-read only when the page ACTIVATES:
    // mid-playback network changes must not rebuild the player and interrupt the clip.
    var pinnedHd by remember(item.id) { mutableStateOf(isHdQuality) }
    LaunchedEffect(isActive) {
        if (isActive) pinnedHd = isHdQuality
    }

    val videoUrl = remember(item, pinnedHd) {
        if (pinnedHd) (item.videoUrlHd ?: item.videoUrl) else item.videoUrl
    }
    val cacheKey = cachePrefix?.let { prefix -> "$prefix:${item.id}:${if (pinnedHd) "hd" else "sd"}" }

    Box(
        modifier = (if (isLandscapeFullscreen) modifier.rotatedLandscapeFill() else modifier)
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
            isLastPage = isLastPage,
            cacheKey = cacheKey,
            videoDescription = listOfNotNull(item.author, item.title).joinToString(" — ").ifBlank { null },
            seekToFraction = seekFraction,
            onProgressUpdate = { progressState.floatValue = it },
            onVideoCompleted = onVideoCompleted,
            // The player-reported duration is authoritative; the server value is only a
            // placeholder until STATE_READY (wrong server durations must not stick).
            onDurationKnown = { durationSec = it },
            onVideoLandscapeKnown = { isLandscapeVideo = it },
            onPlaybackError = { msg -> onPlaybackError(msg) { retrySignal++ } },
            onBufferingChanged = { isBuffering = it },
            playbackSpeed = playbackSpeed,
            retrySignal = retrySignal,
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
                    contentDescription = stringResource(
                        if (!isPlaying) MR.strings.action_play else MR.strings.action_pause,
                    ),
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

        // 5. Right Action Bar (Like, Follow?, Mute, Share) — hides with the immersive
        // chrome, sliding out to the trailing edge like the top bar slides up.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            ReelsActionsColumn(
                isLiked = isLiked,
                isMuted = isMuted,
                onToggleLike = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggleLike()
                },
                onToggleMute = onToggleMute,
                onShare = onShare,
                showFollow = showFollowAction,
                isFollowing = isFollowingCreator,
                onToggleFollow = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                    onToggleFollowCreator()
                },
                modifier = Modifier.padding(end = 12.dp, bottom = 48.dp),
            )
        }

        // Landscape fullscreen affordance: only landscape reels can expand, and the
        // buttons ride the same chrome visibility as the rest of the overlay UI.
        AnimatedVisibility(
            visible = chromeVisible && isLandscapeVideo && !isLandscapeFullscreen,
            enter = fadeIn() + slideInHorizontally { it },
            exit = fadeOut() + slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ReelsLandscapeButton(
                icon = Icons.Filled.Fullscreen,
                contentDescription = stringResource(MR.strings.reels_landscape_fullscreen),
                modifier = Modifier.padding(end = 12.dp),
            ) { onLandscapeFullscreenChange(true) }
        }
        AnimatedVisibility(
            visible = isLandscapeFullscreen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            // Lives inside the rotated frame so it appears at the video's own top-right
            // corner once the phone is turned.
            ReelsLandscapeButton(
                icon = Icons.Filled.FullscreenExit,
                contentDescription = stringResource(MR.strings.reels_landscape_exit),
                modifier = Modifier.padding(end = 12.dp, top = 12.dp),
            ) { onLandscapeFullscreenChange(false) }
        }

        // 6. Bottom Meta info (Author, Title, Clickable Tags)
        ReelsBottomMeta(
            item = item,
            onTagClick = onTagClick,
            onAuthorClick = onAuthorClick,
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
            onScrubStart = onScrubStart,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Software landscape fill for the locked-portrait reels screen: measures the content
 * with width/height swapped and rotates it 90° about the center, so a landscape video
 * covers the physical screen exactly. No orientation request is made — the activity
 * stays portrait, the player is never rebuilt, and touch input follows the rotation.
 */
private fun Modifier.rotatedLandscapeFill(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = 0,
            maxWidth = constraints.maxHeight,
            minHeight = 0,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.placeRelative(
            x = (constraints.maxWidth - placeable.width) / 2,
            y = (constraints.maxHeight - placeable.height) / 2,
        )
    }
}.graphicsLayer { rotationZ = 90f }

@Composable
private fun ReelsLandscapeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

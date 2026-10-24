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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import eu.kanade.tachiyomi.ui.reels.rememberReelsFullscreenSafePadding
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
    // Feed-level immersive chrome flag: the side action bar and the bottom meta hide
    // together with the top bar.
    chromeVisible: Boolean = true,
    // Landscape fullscreen: the page content is rendered rotated 90° inside the locked
    // portrait activity (software rotation — no orientation request, no player rebuild).
    isLandscapeFullscreen: Boolean = false,
    // U02: 90/270 follows the physical hold (gravity — the portrait lock pins the display
    // rotation); 0 means the window is already landscape (split-screen/tablet/foldable) or
    // the system owns the PiP frame.
    softwareRotationDegrees: Int = 90,
    isInPictureInPicture: Boolean = false,
    // Reports the media orientation to the host for the PiP aspect ratio; re-emitted when
    // the page becomes active so a preloaded report cannot be lost.
    onVideoLandscapeKnown: (Boolean) -> Unit = {},
    onLandscapeFullscreenChange: (Boolean) -> Unit = {},
    // Sticky landscape intent (see ReelsFeedScreen): while armed, a settled landscape
    // reel re-enters fullscreen automatically; a portrait reel only auto-exits.
    landscapeAutoRestore: Boolean = false,
    onLandscapeAutoExit: () -> Unit = {},
    // Stable per-source prefix; the page appends item id + the PINNED quality to build the
    // progressive cache key.
    cachePrefix: String? = null,
    // Lazy stream signing (balbums v6+): items may carry a blank videoUrl; the page resolves
    // just-in-time through the source's v22 resolver before building the player.
    resolveUrl: (suspend (ShortVideoItem) -> String?)? = null,
    // Creator follow affordances (contract v18): the caller gates both on
    // `source is AnimeCreatorFeedSource && item.author != null`.
    showFollowAction: Boolean = false,
    isFollowingCreator: Boolean = false,
    onToggleFollowCreator: () -> Unit = {},
    onAuthorClick: (() -> Unit)? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: (secondsPlayed: Float) -> Unit = {},
    // B3.2 "not interested": gated by the caller; the author variant needs a non-null author.
    showHideAction: Boolean = false,
    onHideVideo: () -> Unit = {},
    onHideAuthor: (() -> Unit)? = null,
    // B3.3 offline copy slot (offline playlist only).
    showOfflineAction: Boolean = false,
    isOfflineStored: Boolean = false,
    onToggleOffline: () -> Unit = {},
    onTagClick: (String) -> Unit,
    onVideoCompleted: () -> Unit,
    // Second parameter retries the current video (snackbar "Retry" action).
    onPlaybackError: (String, retry: () -> Unit) -> Unit = { _, _ -> },
    onScrubStart: () -> Unit = {},
    // Reported once when the clip is left (swipe/completion): actual watched seconds, the full
    // duration and the playback fraction — drives remote personalization and local history.
    onViewReported: (secondsWatched: Float, duration: Float, positionFraction: Float) -> Unit = { _, _, _ -> },
    // B3.1 resume-seek: when set, the page seeks here once on its first activation.
    initialSeekFraction: Float? = null,
    // Called once when the resume seek above was applied (the model clears the stored target).
    onInitialSeekConsumed: () -> Unit = {},
    // Actual playWhenReady of THIS page's player (audio-focus/lifecycle pauses stop the
    // player without changing the feed-level play intent); forwarded only while active.
    onPlayingStateChanged: (Boolean) -> Unit = {},
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // Progress lives in a State that only ReelsProgressBar reads; writing it 10Hz must NOT
    // invalidate the whole page subtree (recomposition isolation).
    val progressState = remember { mutableFloatStateOf(0f) }
    var durationSec by remember(item) { mutableFloatStateOf(item.durationSec ?: 0f) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var initialSeekApplied by remember(item.id) { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    val currentIsActive by rememberUpdatedState(isActive)
    // Real playback state of this page's player (audio-focus and lifecycle pauses stop the
    // player without changing the feed-level play intent); gates watched-time accrual.
    var actuallyPlaying by remember(item.id) { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var retrySignal by remember { mutableIntStateOf(0) }
    val heartScale = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    // Real orientation of the media, reported by the player; null until known. Only
    // landscape reels get the fullscreen affordance.
    var isLandscapeVideo by remember(item) { mutableStateOf<Boolean?>(null) }
    val rotateLandscape = isLandscapeFullscreen && softwareRotationDegrees != 0
    // Safe drawing insets are expressed in window coordinates; inside the rotated subtree
    // they must move to the content edge that actually faces the cutout.
    val fullscreenSafePadding = if (isLandscapeFullscreen && !isInPictureInPicture) {
        rememberReelsFullscreenSafePadding(softwareRotationDegrees)
    } else {
        PaddingValues(0.dp)
    }

    // Landscape fullscreen survives auto-advance only while the reels stay wide: a
    // portrait reel settling in the rotated frame drops back to the normal feed (the
    // sticky intent stays armed).
    LaunchedEffect(isActive, isLandscapeFullscreen, isLandscapeVideo) {
        if (isActive && isLandscapeFullscreen && isLandscapeVideo == false) {
            onLandscapeAutoExit()
        }
    }
    // Sticky re-entry: the next landscape reel that settles while the intent is armed
    // goes fullscreen without a tap.
    LaunchedEffect(isActive, landscapeAutoRestore, isLandscapeFullscreen, isLandscapeVideo) {
        if (isActive && landscapeAutoRestore && !isLandscapeFullscreen && isLandscapeVideo == true) {
            onLandscapeFullscreenChange(true)
        }
    }
    // A preloaded page can discover its orientation before it settles; the host needs the
    // value for the PiP aspect ratio even though no player event will fire again.
    LaunchedEffect(isActive, isLandscapeVideo) {
        if (isActive) isLandscapeVideo?.let(onVideoLandscapeKnown)
    }

    // Accumulated playback seconds, reported once when the clip is left (drives the feed
    // source's personalization). Re-counts from zero on every (re)activation.
    var watchedSeconds by remember(item.id) { mutableFloatStateOf(0f) }
    var wasActivated by remember(item.id) { mutableStateOf(false) }
    var viewReported by remember(item.id) { mutableStateOf(false) }
    // The counter restarts on every key change, including the actual play state, so an
    // audio-focus pause stops the accrual at once and a resume picks it up immediately.
    LaunchedEffect(item.id, isActive, isPlaying, isBuffering, actuallyPlaying) {
        while (isActive && isPlaying && actuallyPlaying && !isBuffering) {
            delay(1000)
            watchedSeconds += 1f
        }
    }
    LaunchedEffect(isActive) {
        if (isActive) {
            wasActivated = true
            viewReported = false
        } else if (wasActivated && !viewReported) {
            viewReported = true
            onViewReported(watchedSeconds, durationSec, progressState.floatValue)
            watchedSeconds = 0f
        }
    }
    // Leaving the SCREEN (back / dispose) never runs the deactivation branch above: report the
    // watched clip on dispose too, so the last reel doesn't escape the watch history / feedback.
    DisposableEffect(item.id) {
        onDispose {
            if (wasActivated && !viewReported) {
                viewReported = true
                onViewReported(watchedSeconds, durationSec, progressState.floatValue)
            }
        }
    }

    // B3.1 resume-seek: apply the stored history position exactly once, on first activation.
    LaunchedEffect(isActive, initialSeekFraction) {
        if (isActive && !initialSeekApplied && initialSeekFraction != null) {
            initialSeekApplied = true
            seekFraction = initialSeekFraction
            onInitialSeekConsumed()
        }
    }

    // The effective (data-saver aware) quality is pinned ONCE per item appearance, as soon as
    // the page starts buffering for real (preload or active): a mid-preload network change
    // must not buffer HD that the activation would re-pin to SD (double download), and an
    // active clip never rebuilds its player mid-playback.
    var pinnedHd by remember(item.id) { mutableStateOf(isHdQuality) }
    var qualityPinned by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(isActive, isPreload, isHdQuality) {
        if (isActive || isPreload) {
            if (!qualityPinned) {
                qualityPinned = true
                pinnedHd = isHdQuality
            } else if (!isHdQuality && pinnedHd) {
                // Data saver / SD toggle outranks the pin: a clip preloaded on Wi-Fi must
                // never play HD after the device moved to a metered network. Downgrading
                // rebuilds the player with position restore; upgrades wait for the next
                // appearance (never mid-playback).
                pinnedHd = false
            }
        }
    }

    val rawUrl = remember(item, pinnedHd) {
        if (pinnedHd) (item.videoUrlHd ?: item.videoUrl) else item.videoUrl
    }
    // Lazy signing: a blank url resolves just-in-time; the player is only built once a real
    // url exists (a blank MediaItem would surface a playback error before signing finishes).
    var videoUrl by remember(rawUrl, item.id) { mutableStateOf(rawUrl) }
    LaunchedEffect(rawUrl, item.id, retrySignal) {
        if (rawUrl.isBlank()) {
            val resolved = runCatching { resolveUrl?.invoke(item) }.getOrNull()
            if (!resolved.isNullOrBlank()) {
                videoUrl = resolved
            } else if (resolveUrl != null) {
                onPlaybackError("Failed to load video stream") {
                    retrySignal++
                }
            }
        } else {
            videoUrl = rawUrl
        }
    }
    val cacheKey = cachePrefix?.let { prefix -> "$prefix:${item.id}:${if (pinnedHd) "hd" else "sd"}" }

    Box(
        modifier = (if (rotateLandscape) modifier.rotatedLandscapeFill(softwareRotationDegrees) else modifier)
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
            onVideoLandscapeKnown = { landscape ->
                isLandscapeVideo = landscape
                onVideoLandscapeKnown(landscape)
            },
            onPlaybackError = { msg -> onPlaybackError(msg) { retrySignal++ } },
            onBufferingChanged = { isBuffering = it },
            onPlaybackRunningChanged = { playing ->
                actuallyPlaying = playing
                if (currentIsActive) onPlayingStateChanged(playing)
            },
            playbackSpeed = playbackSpeed,
            retrySignal = retrySignal,
            headers = headers,
            webUrl = item.webUrl,
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering spinner on the active page.
        if (isBuffering && isActive) {
            androidx.compose.material3.CircularProgressIndicator(
                color = AuroraTheme.colors.accent,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
            )
        }

        // 2. Gradient overlays for readable text & controls. The scrim is part of the
        // chrome: in clean fullscreen / auto-hidden state the video must stay untouched
        // (the progress bar and fullscreen button carry their own backplates).
        AnimatedVisibility(
            visible = chromeVisible && !isInPictureInPicture,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
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
        }

        // 3. Play / Pause indicator overlay (flashes briefly on state change)
        AnimatedVisibility(
            visible = !isPlaying && !isInPictureInPicture,
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
        if (showHeartPop && !isInPictureInPicture) {
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

        // 5-7. Controls sit inside the rotated frame and are inset from the physical safe
        // edges, so cutouts and rounded corners never cover them. In PiP the system owns the
        // frame and chrome would only crowd the tiny window.
        if (!isInPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(fullscreenSafePadding),
            ) {
                // Right Action Bar (Like, Follow?, Mute, Share) — hides with the immersive
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
                        // B3.6: share carries the current playback second for a timeline URL.
                        onShare = { onShare(progressState.floatValue * durationSec.coerceAtLeast(0f)) },
                        showFollow = showFollowAction,
                        isFollowing = isFollowingCreator,
                        onToggleFollow = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            onToggleFollowCreator()
                        },
                        onOpenHide = if (showHideAction) {
                            { showHideDialog = true }
                        } else {
                            null
                        },
                        onToggleOffline = if (showOfflineAction) {
                            onToggleOffline
                        } else {
                            null
                        },
                        isOfflineStored = isOfflineStored,
                        modifier = Modifier.padding(end = 12.dp, bottom = 48.dp),
                    )
                }

                // Landscape fullscreen affordance: only landscape reels can expand, and the
                // buttons ride the same chrome visibility as the rest of the overlay UI.
                AnimatedVisibility(
                    visible = chromeVisible && isLandscapeVideo == true && !isLandscapeFullscreen,
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
                    // Lives inside the rotated frame so it appears at the video's own
                    // top-right corner once the phone is turned.
                    ReelsLandscapeButton(
                        icon = Icons.Filled.FullscreenExit,
                        contentDescription = stringResource(MR.strings.reels_landscape_exit),
                        modifier = Modifier.padding(end = 12.dp, top = 12.dp),
                    ) { onLandscapeFullscreenChange(false) }
                }

                // Bottom Meta info (Author, Title, Clickable Tags) — hides with the immersive
                // chrome, sliding down out of the frame like the top bar slides up.
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomStart),
                ) {
                    ReelsBottomMeta(
                        item = item,
                        onTagClick = onTagClick,
                        onAuthorClick = onAuthorClick,
                        onCreatorClick = onCreatorClick,
                        modifier = Modifier.padding(start = 16.dp, end = 76.dp, bottom = 24.dp),
                    )
                }

                // Interactive Scrubber / Seek Bar (Aurora style)
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
    }

    // B3.2 hide choice: reel only, or the author too when one is known.
    if (showHideDialog) {
        AlertDialog(
            onDismissRequest = { showHideDialog = false },
            title = { Text(stringResource(MR.strings.reels_hide_menu)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showHideDialog = false
                            onHideVideo()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(MR.strings.reels_hide_video))
                    }
                    if (onHideAuthor != null) {
                        TextButton(
                            onClick = {
                                showHideDialog = false
                                onHideAuthor()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(MR.strings.reels_hide_author))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

/**
 * Software landscape fill for the locked-portrait reels screen: measures the content
 * with width/height swapped and rotates it by [degrees] about the center, so a landscape
 * video covers the physical screen exactly. No orientation request is made — the activity
 * stays portrait, the player is never rebuilt, and touch input follows the rotation.
 */
private fun Modifier.rotatedLandscapeFill(degrees: Int): Modifier = layout { measurable, constraints ->
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
}.graphicsLayer { rotationZ = degrees.toFloat() }

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

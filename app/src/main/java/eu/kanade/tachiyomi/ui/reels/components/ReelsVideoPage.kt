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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.FeedMediaDescriptor
import eu.kanade.tachiyomi.animesource.model.FeedMediaKind
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.ReelsForYou
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerView
import eu.kanade.tachiyomi.ui.reels.rememberReelsFullscreenSafePadding
import kotlinx.coroutines.CancellationException
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
    // Measured frame dimensions for the PiP content rect/aspect (audit C03); re-emitted on
    // activation like the orientation.
    onVideoSizeKnown: (Int, Int) -> Unit = { _, _ -> },
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
    // Contract v25 media verdict for THIS item (null = legacy URL-based rules): authoritative
    // still/video kind, whether a blank URL may be resolved, and offline support.
    mediaDescriptor: FeedMediaDescriptor? = null,
    // Creator follow affordances (contract v18): the caller gates both on
    // `source is AnimeCreatorFeedSource && item.author != null`.
    showFollowAction: Boolean = false,
    isFollowingCreator: Boolean = false,
    onToggleFollowCreator: () -> Unit = {},
    onAuthorClick: (() -> Unit)? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    // Double-tap likes idempotently: the gesture coroutine captures this once, so it must
    // never hold a stale toggle (that would unlike on the second double-tap of the same reel).
    onLike: () -> Unit = onToggleLike,
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
    // Watch qualification signals. The page only reports what the player/UI really did; the
    // host's tracker decides whether a visit earned a qualified watch and commits it.
    onVisitStarted: (ShortVideoItem) -> Unit = {},
    onFirstFramePresented: (itemId: String) -> Unit = {},
    onPlaybackSample: (itemId: String, positionMs: Long, eligible: Boolean) -> Unit = { _, _, _ -> },
    // Reported once when the clip is left (swipe/completion/dispose): the real position, the
    // playback fraction and the player-reported duration.
    onVisitEnded: (ShortVideoItem, positionMs: Long, positionFraction: Float, durationSec: Float) -> Unit =
        { _, _, _, _ -> },
    // The host screen is in the foreground. Background playback never earns watch credit.
    isForeground: Boolean = true,
    // B3.1 resume-seek: when set, the page seeks here once on its first activation.
    initialSeekFraction: Float? = null,
    // Called once when the resume seek above was applied (the model clears the stored target).
    onInitialSeekConsumed: () -> Unit = {},
    // Absolute position seek in milliseconds (session continuity).
    initialSeekPositionMs: Long? = null,
    onInitialSeekPositionConsumed: () -> Unit = {},
    // Actual Player.isPlaying of THIS page's player (buffering, audio-focus/lifecycle pauses
    // and the media end stop it without changing the feed-level play intent); forwarded only
    // while active.
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
    var initialSeekPositionApplied by remember(item.id) { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    val currentIsActive by rememberUpdatedState(isActive)
    // The tap recognizer below is keyed by Unit and survives page rebinds (the pager reuses
    // a slot for a different item after a feed reset); values read inside the gesture must
    // not be captured from the composition that launched it.
    val currentOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val currentOnLike by rememberUpdatedState(onLike)
    val currentDurationSec by rememberUpdatedState(durationSec)
    // Real playback state of this page's player (audio-focus and lifecycle pauses stop the
    // player without changing the feed-level play intent); gates watched-time accrual.
    // Starts false: time must not accrue before the player confirms it is actually running
    // (a still reel reports its own eligibility).
    var actuallyPlaying by remember(item.id) { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var retrySignal by remember { mutableIntStateOf(0) }
    val heartScale = remember { Animatable(0f) }
    val hapticFeedback = LocalHapticFeedback.current
    // Real orientation of the media, reported by the player; null until known. Only
    // landscape reels get the fullscreen affordance.
    var isLandscapeVideo by remember(item) { mutableStateOf<Boolean?>(null) }
    var videoSize by remember(item) { mutableStateOf<Pair<Int, Int>?>(null) }
    val rotateLandscape = isLandscapeFullscreen && softwareRotationDegrees != 0
    // Safe drawing insets are expressed in window coordinates; inside the rotated subtree
    // they must move to the content edge that actually faces the cutout.
    val fullscreenSafePadding = if (isLandscapeFullscreen && !isInPictureInPicture) {
        rememberReelsFullscreenSafePadding(softwareRotationDegrees)
    } else {
        PaddingValues(0.dp)
    }

    // U06: playback, seeking, speed and liking are otherwise reachable only through raw
    // gestures (tap / double-tap thirds / long-press), which TalkBack never delivers.
    val playPauseLabel = stringResource(if (isPlaying) MR.strings.action_pause else MR.strings.action_play)
    val seekBackLabel = stringResource(MR.strings.reels_a11y_seek_back)
    val seekForwardLabel = stringResource(MR.strings.reels_a11y_seek_forward)
    val likeLabel = stringResource(MR.strings.reels_feed_action_like)
    val speedLabel = stringResource(
        if (playbackSpeed == 1f) MR.strings.reels_a11y_speed_2x else MR.strings.reels_a11y_speed_normal,
    )
    fun seekBy(deltaSec: Float) {
        if (durationSec <= 0f) return
        val target = ((progressState.floatValue * durationSec + deltaSec) / durationSec).coerceIn(0f, 1f)
        seekFraction = target
        progressState.floatValue = target
        coroutineScope.launch {
            delay(50)
            seekFraction = null
        }
    }
    fun toggleSpeed() {
        playbackSpeed = if (playbackSpeed == 1f) 2f else 1f
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
    LaunchedEffect(isActive, videoSize) {
        if (isActive) videoSize?.let { (width, height) -> onVideoSizeKnown(width, height) }
    }

    // P06: watched time accumulates from monotonic deltas instead of whole-second ticks, so
    // a 0.8 s watch, or a pause mid-tick, no longer loses its fraction.
    // Watch qualification lives in the host tracker; the page only reports signals, so the
    // same rules apply to every surface and the accumulator survives page rebinds.
    var wasActivated by remember(item.id) { mutableStateOf(false) }
    var visitEndReported by remember(item.id) { mutableStateOf(false) }
    val currentOnVisitEnded by rememberUpdatedState(onVisitEnded)
    // Same eligibility as the feed's "actually playing": active page, play intent, a player
    // that is really running, no buffering, and a foregrounded screen.
    val watchedEligible = isActive && isPlaying && actuallyPlaying && !isBuffering && isForeground
    val currentWatchedEligible by rememberUpdatedState(watchedEligible)

    // The first presented frame starts the clock: READY/buffering alone never counts.
    LaunchedEffect(item.id, actuallyPlaying) {
        if (actuallyPlaying) onFirstFramePresented(item.id)
    }

    // Sparse sampler (4 Hz): monotonic deltas, no per-frame work and never a DB write.
    LaunchedEffect(item.id, isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(ReelsForYou.SAMPLE_INTERVAL_MS)
            val duration = durationSec.coerceAtLeast(0f)
            val positionMs = (progressState.floatValue * duration * 1000f).toLong()
            onPlaybackSample(item.id, positionMs, currentWatchedEligible)
        }
    }

    fun reportVisitEnd() {
        if (!wasActivated || visitEndReported) return
        visitEndReported = true
        val duration = durationSec.coerceAtLeast(0f)
        val positionMs = (progressState.floatValue * duration * 1000f).toLong()
        currentOnVisitEnded(item, positionMs, progressState.floatValue, duration)
    }
    LaunchedEffect(isActive) {
        if (isActive) {
            wasActivated = true
            visitEndReported = false
            onVisitStarted(item)
        } else {
            reportVisitEnd()
        }
    }
    // Leaving the SCREEN (back / dispose) never runs the deactivation branch above: report the
    // watched clip on dispose too, so the last reel doesn't escape the history / feedback.
    DisposableEffect(item.id) {
        onDispose { reportVisitEnd() }
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
        // A blank HD variant is absent, not a stream: falling back to the base URL here keeps
        // the playable-base guarantee instead of asking the resolver for an empty URL.
        if (pinnedHd) {
            item.videoUrlHd?.takeIf { it.isNotBlank() } ?: item.videoUrl
        } else {
            item.videoUrl
        }
    }
    val mediaPlan = remember(rawUrl, item.id, mediaDescriptor?.lazyResolution, resolveUrl != null) {
        reelMediaPlan(rawUrl, resolveUrl != null, mediaDescriptor?.lazyResolution != false)
    }
    // Lazy signing: a blank url resolves just-in-time; the player is only built once a real
    // url exists (a blank MediaItem would surface a playback error before signing finishes).
    var videoUrl by remember(rawUrl, item.id) { mutableStateOf(rawUrl) }
    LaunchedEffect(rawUrl, item.id, retrySignal, mediaPlan) {
        when (mediaPlan) {
            ReelMediaPlan.READY -> videoUrl = rawUrl
            ReelMediaPlan.RESOLVE -> {
                val resolved = try {
                    resolveUrl?.invoke(item)
                } catch (e: CancellationException) {
                    // The page left composition / the feed generation changed: keep the
                    // resolution job cancelled instead of reporting a playback error.
                    throw e
                } catch (_: Throwable) {
                    null
                }
                if (!resolved.isNullOrBlank()) {
                    videoUrl = resolved
                } else {
                    onPlaybackError("Failed to load video stream") {
                        retrySignal++
                    }
                }
            }
            ReelMediaPlan.UNPLAYABLE -> {
                // A blank URL the host cannot sign would otherwise show the poster spinner
                // forever; fail terminally so the user gets the error affordance.
                onPlaybackError("Failed to load video stream") {
                    retrySignal++
                }
            }
        }
    }
    // Contract v25: a declared kind outranks the URL extension (opaque photo URLs have none,
    // and a video URL may happen to end in an image extension); null keeps legacy sniffing.
    val declaredStill = when (mediaDescriptor?.kind) {
        FeedMediaKind.STILL -> true
        FeedMediaKind.VIDEO -> false
        null -> null
    }
    val cacheKey = cachePrefix?.let { prefix -> "$prefix:${item.id}:${if (pinnedHd) "hd" else "sd"}" }

    Box(
        modifier = (if (rotateLandscape) modifier.rotatedLandscapeFill(softwareRotationDegrees) else modifier)
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                onClick(label = playPauseLabel) {
                    currentOnTogglePlayPause()
                    true
                }
                customActions = listOfNotNull(
                    CustomAccessibilityAction(seekBackLabel) {
                        seekBy(-10f)
                        true
                    },
                    CustomAccessibilityAction(seekForwardLabel) {
                        seekBy(10f)
                        true
                    },
                    CustomAccessibilityAction(speedLabel) {
                        toggleSpeed()
                        true
                    },
                    if (!isLiked) {
                        CustomAccessibilityAction(likeLabel) {
                            currentOnLike()
                            true
                        }
                    } else {
                        null
                    },
                )
            }
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
                        currentOnTogglePlayPause()
                    },
                    onDoubleTap = { offset ->
                        // Left / right thirds seek ±10s, center double-tap likes.
                        val activeDurationSec = currentDurationSec
                        val seekDeltaSec = when {
                            activeDurationSec > 0f && offset.x < size.width / 3f -> -10f
                            activeDurationSec > 0f && offset.x > size.width * 2f / 3f -> 10f
                            else -> 0f
                        }
                        if (seekDeltaSec != 0f) {
                            val currentSec = progressState.floatValue * activeDurationSec
                            val target = ((currentSec + seekDeltaSec) / activeDurationSec).coerceIn(0f, 1f)
                            seekFraction = target
                            progressState.floatValue = target
                            coroutineScope.launch {
                                delay(50)
                                seekFraction = null
                            }
                        } else {
                            // Set-like, not toggle: the second double-tap on an already liked
                            // reel must stay a like.
                            currentOnLike()
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
            isStill = declaredStill,
            isMediaUnplayable = mediaPlan == ReelMediaPlan.UNPLAYABLE,
            isLastPage = isLastPage,
            cacheKey = cacheKey,
            videoDescription = listOfNotNull(item.author, item.title).joinToString(" — ").ifBlank { null },
            seekToFraction = seekFraction,
            seekToPositionMs = if (isActive && !initialSeekPositionApplied) initialSeekPositionMs else null,
            onSeekPositionConsumed = {
                initialSeekPositionApplied = true
                onInitialSeekPositionConsumed()
            },
            onRenderedFirstFrame = {
                if (isActive) onFirstFramePresented(item.id)
            },
            onProgressUpdate = { progressState.floatValue = it },
            onVideoCompleted = onVideoCompleted,
            // The player-reported duration is authoritative; the server value is only a
            // placeholder until STATE_READY (wrong server durations must not stick).
            onDurationKnown = { durationSec = it },
            onVideoLandscapeKnown = { landscape ->
                isLandscapeVideo = landscape
                onVideoLandscapeKnown(landscape)
            },
            onVideoSizeKnown = { width, height ->
                videoSize = width to height
                onVideoSizeKnown(width, height)
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
                    .clickable(role = Role.Button, onClick = currentOnTogglePlayPause)
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

/** How a page turns its raw URL into a playable player input (contract v25). */
internal enum class ReelMediaPlan {
    READY,
    RESOLVE,
    UNPLAYABLE,
}

internal fun reelMediaPlan(
    rawUrl: String,
    hasResolver: Boolean,
    lazyResolution: Boolean,
): ReelMediaPlan = when {
    rawUrl.isNotBlank() -> ReelMediaPlan.READY
    // A blank URL is playable only when the source opted into lazy signing AND the host
    // actually has a resolver to sign it with; otherwise no future event can fill it.
    hasResolver && lazyResolution -> ReelMediaPlan.RESOLVE
    else -> ReelMediaPlan.UNPLAYABLE
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
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 48dp touch target (a11y minimum) around the 36dp visual circle.
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
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
}

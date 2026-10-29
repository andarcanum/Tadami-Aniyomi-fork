package eu.kanade.tachiyomi.ui.reels.components

import android.app.Activity
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.FeedMediaDescriptor
import eu.kanade.tachiyomi.animesource.model.FeedMediaKind
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.ReelsForYou
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerView
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
    // Window geometry only: landscape gets a horizontal actions row. Never a media-aspect hint.
    isLandscape: Boolean = false,
    // Orientation toggle lives in the action rail (thumb reach), not in the top bar.
    landscape: Boolean = false,
    onToggleLandscape: () -> Unit = {},
    isInPictureInPicture: Boolean = false,
    // Reports the media orientation to the host for the PiP aspect ratio; re-emitted when
    // the page becomes active so a preloaded report cannot be lost.
    onVideoLandscapeKnown: (Boolean) -> Unit = {},
    // Measured frame dimensions for the PiP content rect/aspect (audit C03); re-emitted on
    // activation like the orientation.
    onVideoSizeKnown: (Int, Int) -> Unit = { _, _ -> },
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
    // A configuration recreation rebuilds the player: park the position without ending the
    // visit, so a rotation never invents a watched/skipped event.
    onVisitSuspendedForConfiguration: (itemId: String, positionMs: Long) -> Unit = { _, _ -> },
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
    isTouchLocked: Boolean = false,
    onToggleTouchLock: () -> Unit = {},
    sourceName: String = "Tadami Feed",
    playlistItems: List<ShortVideoItem> = emptyList(),
    playlistIndex: Int = 0,
    onSelectPlaylistItem: (Int) -> Unit = {},
    onToggleCropMode: () -> Unit = {},
    onToggleQuality: () -> Unit = {},
    onToggleAutoAdvance: () -> Unit = {},
    preloadWifiOnly: Boolean = false,
    onTogglePreloadWifiOnly: () -> Unit = {},
    dataSaverEnabled: Boolean = false,
    onToggleDataSaver: () -> Unit = {},
    sleepTimerValue: String = "Выкл",
    onCycleSleepTimer: () -> Unit = {},
    offlineUsedText: String = "",
    onClearVideoCache: () -> Unit = {},
    isIncognito: Boolean = false,
    onToggleIncognito: () -> Unit = {},
    onClearOffline: () -> Unit = {},
    onBack: () -> Unit = {},
    onToggleChrome: () -> Unit = {},
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // Progress lives in a State that only ReelsProgressBar reads; writing it 10Hz must NOT
    // invalidate the whole page subtree (recomposition isolation).
    val progressState = remember { mutableFloatStateOf(0f) }
    var bufferFraction by remember { mutableFloatStateOf(0f) }
    var durationSec by remember(item) { mutableFloatStateOf(item.durationSec ?: 0f) }
    var seekFraction by remember { mutableStateOf<Float?>(null) }
    var initialSeekApplied by remember(item.id) { mutableStateOf(false) }
    var initialSeekPositionApplied by remember(item.id) { mutableStateOf(false) }
    var showHeartPop by remember { mutableStateOf(false) }
    var showHideDialog by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var drawerOpen by remember { mutableStateOf(false) }
    var drawerTab by remember { mutableIntStateOf(0) }
    var seekNoticeText by remember { mutableStateOf<String?>(null) }
    // Measured height of the portrait caption block: the action rail's bottom sits at the
    // author-name horizon (the block's top edge) so no icon drops into the description.
    var portraitMetaHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(seekNoticeText) {
        if (seekNoticeText != null) {
            delay(1000)
            seekNoticeText = null
        }
    }
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
    // Real orientation of the media, reported by the player; null until known. Used for the
    // PiP aspect ratio and media diagnostics, not for the window orientation.
    var isLandscapeVideo by remember(item) { mutableStateOf<Boolean?>(null) }
    var videoSize by remember(item) { mutableStateOf<Pair<Int, Int>?>(null) }

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
    // A configuration recreation is not a user leaving the clip — the visit stays open and
    // the position is restored on the new composition (see ReelsFeedScreenModel).
    val hostActivity = LocalContext.current as? Activity
    DisposableEffect(item.id) {
        onDispose {
            if (hostActivity?.isChangingConfigurations == true && wasActivated && !visitEndReported) {
                val duration = durationSec.coerceAtLeast(0f)
                onVisitSuspendedForConfiguration(
                    item.id,
                    (progressState.floatValue * duration * 1000f).toLong(),
                )
            } else {
                reportVisitEnd()
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
        modifier = modifier
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
                        // Device report: a reveal-only tap made pause unreachable — with the
                        // 3s auto-hide it was buried behind a second, timed tap (landscape
                        // had no pause path at all). Tap now reveals the chrome AND toggles
                        // playback in both orientations; clean viewing stays on the eye
                        // button (hides the chrome without pausing).
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
                            seekNoticeText = if (seekDeltaSec < 0f) "« -10 сек" else "+10 сек »"
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
            onBufferUpdate = { bufferFraction = it },
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

        // 5. 2x Speed HUD Pill overlay
        AnimatedVisibility(
            visible = playbackSpeed == 2f && !isInPictureInPicture,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, AuroraTheme.colors.accent, RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "⚡ 2.0x Ускорение",
                    color = AuroraTheme.colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 6. Seek Notice overlay (±10s)
        AnimatedVisibility(
            visible = seekNoticeText != null && !isInPictureInPicture,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seekNoticeText.orEmpty(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // 7. Controls sit inside the rotated frame and are inset from physical safe edges
        if (!isInPictureInPicture) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLandscape) {
                            Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (isLandscape) {
                    // Landscape TopBar
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        ReelsLandscapeTopBar(
                            sourceName = sourceName,
                            onBackClick = onBack,
                            onOpenPlaylist = {
                                drawerTab = 2
                                drawerOpen = true
                            },
                            onClearDisplay = onToggleChrome,
                        )
                    }

                    // Landscape Action Rail (Right Pod)
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn() + slideInHorizontally { it },
                        exit = fadeOut() + slideOutHorizontally { it },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        ReelsLandscapeActionRail(
                            isLiked = isLiked,
                            likesCount = item.likesCount,
                            onToggleLike = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onToggleLike()
                            },
                            isOfflineStored = isOfflineStored,
                            onToggleOffline = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                if (showOfflineAction) {
                                    onToggleOffline()
                                } else {
                                    drawerTab = 1
                                    drawerOpen = true
                                }
                            },
                            offlineSizeText = if (isOfflineStored) "✓" else "~24 МБ",
                            onOpenHide = null,
                            onShare = { onShare(progressState.floatValue * durationSec.coerceAtLeast(0f)) },
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }

                    // Landscape Bottom Meta
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
                            showFollowAction = showFollowAction,
                            isFollowingCreator = isFollowingCreator,
                            onToggleFollowCreator = onToggleFollowCreator,
                            onMetaStatsClick = {
                                drawerTab = 0
                                drawerOpen = true
                            },
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 120.dp,
                                bottom = 52.dp,
                            ),
                        )
                    }

                    // Landscape Bottom Scrubber Dock
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ReelsLandscapeScrubberDock(
                            progressState = progressState,
                            bufferFraction = bufferFraction,
                            durationSec = durationSec,
                            isMuted = isMuted,
                            onToggleMute = onToggleMute,
                            onLockScreen = onToggleTouchLock,
                            posterUrl = item.posterUrlVertical ?: item.posterUrl,
                            onSeek = { frac ->
                                seekFraction = frac
                                progressState.floatValue = frac
                                coroutineScope.launch {
                                    delay(50)
                                    seekFraction = null
                                }
                            },
                            onScrubStart = onScrubStart,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }

                    // Aurora Frost Drawer
                    if (drawerOpen) {
                        ReelsAuroraFrostDrawer(
                            isOpen = drawerOpen,
                            initialTab = drawerTab,
                            currentItem = item,
                            playlistItems = playlistItems,
                            currentPlaylistIndex = playlistIndex,
                            isHdQuality = isHdQuality,
                            onToggleQuality = onToggleQuality,
                            isCropMode = isCropMode,
                            onToggleCropMode = onToggleCropMode,
                            playbackSpeed = playbackSpeed,
                            onSetPlaybackSpeed = { playbackSpeed = it },
                            isAutoAdvance = isAutoAdvance,
                            onToggleAutoAdvance = onToggleAutoAdvance,
                            sleepTimerValue = sleepTimerValue,
                            onCycleSleepTimer = onCycleSleepTimer,
                            isIncognito = isIncognito,
                            onToggleIncognito = onToggleIncognito,
                            isOfflineStored = isOfflineStored,
                            onToggleOfflineCurrent = {
                                if (showOfflineAction) {
                                    onToggleOffline()
                                } else {
                                    onToggleOffline()
                                }
                            },
                            preloadWifiOnly = preloadWifiOnly,
                            onTogglePreloadWifiOnly = onTogglePreloadWifiOnly,
                            dataSaverEnabled = dataSaverEnabled,
                            onToggleDataSaver = onToggleDataSaver,
                            offlineUsedText = offlineUsedText.ifBlank { "148.6 МБ" },
                            onClearVideoCache = onClearVideoCache,
                            onSelectClip = { idx ->
                                drawerOpen = false
                                onSelectPlaylistItem(idx)
                            },
                            onDismiss = { drawerOpen = false },
                        )
                    }
                } else {
                    // Portrait Right Action Bar — raised so its bottom sits at the
                    // author-name horizon (top edge of the caption block): the stack
                    // clears the description instead of interleaving with it.
                    val density = LocalDensity.current
                    val actionsBottomPadding =
                        24.dp + 6.dp + with(density) { portraitMetaHeightPx.toDp() }
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
                            onOpenHide = null,
                            onToggleOffline = if (showOfflineAction) {
                                onToggleOffline
                            } else {
                                null
                            },
                            offlineSizeText = if (isOfflineStored) "✓" else "~24 МБ",
                            isOfflineStored = isOfflineStored,
                            landscape = landscape,
                            onToggleLandscape = onToggleLandscape,
                            isLandscapeLayout = false,
                            modifier = Modifier.padding(end = 8.dp, bottom = actionsBottomPadding),
                        )
                    }

                    // Portrait Bottom Meta info
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
                            showFollowAction = showFollowAction,
                            isFollowingCreator = isFollowingCreator,
                            onToggleFollowCreator = onToggleFollowCreator,
                            modifier = Modifier
                                .padding(
                                    start = 16.dp,
                                    end = 76.dp,
                                    bottom = 24.dp,
                                )
                                .onSizeChanged { portraitMetaHeightPx = it.height },
                        )
                    }

                    // Portrait Interactive Scrubber / Seek Bar (Aurora style)
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
    }

    // B3.2 hide choice: Aurora Frost Dialog with danger actions
    if (showHideDialog) {
        AuroraFrostDialog(
            onDismiss = { showHideDialog = false },
            title = stringResource(MR.strings.reels_hide_dialog_title),
            footer = {
                AuroraFrostCancel(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = { showHideDialog = false },
                )
            },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .clickable {
                            showHideDialog = false
                            onHideVideo()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(MR.strings.reels_hide_video),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (onHideAuthor != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable {
                                showHideDialog = false
                                onHideAuthor()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color(0xFFF87171),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.reels_hide_author),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
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

@Composable
internal fun ReelsLandscapeButton(
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

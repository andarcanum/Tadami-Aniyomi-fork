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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.FeedMediaDescriptor
import eu.kanade.tachiyomi.animesource.model.FeedMediaKind
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.reels.ReelsForYou
import eu.kanade.tachiyomi.ui.reels.ReelsQualityItemState
import eu.kanade.tachiyomi.ui.reels.ReelsQualityPlayback
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
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
    // Contract v29: this item is owned by the quality controller. While the actual binding
    // is null the page must not fall back to the item's Source hint on its own.
    qualityModeEnabled: Boolean = false,
    qualityPlayback: ReelsQualityPlayback? = null,
    qualityItemState: ReelsQualityItemState? = null,
    qualityGlobal: ReelsQualityPreference = ReelsQualityPreference.Source,
    onSetQualityGlobal: (ReelsQualityPreference) -> Unit = {},
    onSetQualityOverride: (String, ReelsQualityPreference) -> Unit = { _, _ -> },
    onClearQualityOverride: (String) -> Unit = {},
    onQualityDialogOpenChanged: (Boolean) -> Unit = {},
    onRetryQuality: () -> Unit = {},
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    isLastPage: Boolean = false,
    // Feed-level immersive chrome flag: the side action bar and the bottom meta hide
    // together with the top bar.
    chromeVisible: Boolean = true,
    // Landscape top bar had no search entry: the field is rendered by the host, but nothing in
    // the rotated chrome could open it.
    showSearch: Boolean = false,
    isSearchBarOpen: Boolean = false,
    onToggleSearch: () -> Unit = {},
    searchQuery: String = "",
    onSearchSubmit: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onOpenSourcePicker: (() -> Unit)? = null,
    // Window geometry only: landscape gets a horizontal actions row. Never a media-aspect hint.
    isLandscape: Boolean = false,
    // Orientation toggle lives in the action rail (thumb reach), not in the top bar.
    landscape: Boolean = false,
    onToggleLandscape: () -> Unit = {},
    onExitLandscape: () -> Unit = onToggleLandscape,
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
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    isPartial: Boolean = false,
    downloadSizeText: String? = null,
    downloadProgress: Float? = null,
    onDownload: (() -> Unit)? = null,
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
    feedSectionLabel: String? = null,
    sourceBadgeName: String? = null,
    sourceBadgeIcon: ImageBitmap? = null,
    onSourceBadgeClick: (() -> Unit)? = null,
    onExcludeSourceFromMix: (() -> Unit)? = null,
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
    playbackSpeed: Float = 1f,
    onPlaybackSpeedChange: ((Float) -> Unit)? = null,
    sleepTimerValue: String = "Выкл",
    onCycleSleepTimer: () -> Unit = {},
    offlineUsedText: String = "",
    offlineUsedFraction: Float = 0f,
    canClearOffline: Boolean = false,
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
    var qualityDialogMode by remember(item.id) { mutableStateOf<ReelsQualityDialogMode?>(null) }
    LaunchedEffect(isActive) {
        if (!isActive) {
            drawerOpen = false
            showHideDialog = false
        }
    }
    val pageOverlayOpen = qualityDialogMode != null || drawerOpen || showHideDialog
    LaunchedEffect(pageOverlayOpen) {
        onQualityDialogOpenChanged(pageOverlayOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onQualityDialogOpenChanged(false) }
    }
    val seekNotice = rememberReelsSeekNotice(item.id, isActive, isInPictureInPicture)
    val currentSeekNotice by rememberUpdatedState(seekNotice)
    // Measured height of the portrait caption block: the action rail's bottom sits at the
    // author-name horizon (the block's top edge) so no icon drops into the description.
    var portraitMetaHeightPx by remember { mutableIntStateOf(0) }

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
    var localPlaybackSpeed by remember { mutableFloatStateOf(1f) }
    val effectivePlaybackSpeed = if (onPlaybackSpeedChange != null) playbackSpeed else localPlaybackSpeed
    val updatePlaybackSpeed: (Float) -> Unit = { newSpeed ->
        if (onPlaybackSpeedChange != null) {
            onPlaybackSpeedChange(newSpeed)
        } else {
            localPlaybackSpeed = newSpeed
        }
    }
    // Rate while a finger is held down at an edge. Kept apart from playbackSpeed on purpose:
    // playbackSpeed is the user's *chosen* rate (button, drawer) and must survive the finger lift.
    var heldRate by remember { mutableStateOf<Float?>(null) }
    // The lift at the end of a hold also reaches the tap detector; without this the reel would
    // pause every time the user lets go of a speed hold.
    var heldToIgnoreTap by remember { mutableStateOf(false) }
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
        if (effectivePlaybackSpeed == 1f) MR.strings.reels_a11y_speed_2x else MR.strings.reels_a11y_speed_normal,
    )
    fun seekBy(deltaSec: Float, showNotice: Boolean = false) {
        val activeDurationSec = currentDurationSec
        if (activeDurationSec <= 0f) return
        val currentSec = progressState.floatValue * activeDurationSec
        val target = ((currentSec + deltaSec) / activeDurationSec).coerceIn(0f, 1f)
        seekFraction = target
        progressState.floatValue = target
        if (showNotice) {
            // The caption reports what the player really moved, so a tap near the ends
            // cannot promise +10 while the reel only had 3 seconds left.
            currentSeekNotice.show((target * activeDurationSec - currentSec).toInt())
        }
        coroutineScope.launch {
            delay(50)
            seekFraction = null
        }
    }
    fun toggleSpeed() {
        updatePlaybackSpeed(if (effectivePlaybackSpeed == 1f) 2f else 1f)
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
    LaunchedEffect(isActive, isPreload, isHdQuality, qualityModeEnabled) {
        if (qualityModeEnabled) return@LaunchedEffect
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

    val qualityUrl = qualityPlayback?.videoUrl.orEmpty()
    val rawUrl = remember(item, pinnedHd, qualityModeEnabled, qualityUrl) {
        if (qualityModeEnabled) {
            qualityUrl
        } else if (pinnedHd) {
            // A blank HD variant is absent, not a stream: falling back to the base URL here keeps
            // the playable-base guarantee instead of asking the resolver for an empty URL.
            item.videoUrlHd?.takeIf { it.isNotBlank() } ?: item.videoUrl
        } else {
            item.videoUrl
        }
    }
    val qualitySwitching = qualityItemState?.switching == true
    val qualityHasFailure = qualityItemState?.failure != null
    val mediaPlan = remember(
        rawUrl,
        item.id,
        mediaDescriptor?.lazyResolution,
        resolveUrl != null,
        qualityModeEnabled,
        qualitySwitching,
        qualityHasFailure,
    ) {
        reelMediaPlan(
            rawUrl = rawUrl,
            hasResolver = resolveUrl != null,
            lazyResolution = mediaDescriptor?.lazyResolution != false,
            qualityModeEnabled = qualityModeEnabled,
            qualitySwitching = qualitySwitching,
            qualityHasFailure = qualityHasFailure,
        )
    }
    // Lazy signing: a blank url resolves just-in-time; the player is only built once a real
    // url exists (a blank MediaItem would surface a playback error before signing finishes).
    var videoUrl by remember(rawUrl, item.id) { mutableStateOf(rawUrl) }
    LaunchedEffect(rawUrl, item.id, retrySignal, mediaPlan) {
        when (mediaPlan) {
            ReelMediaPlan.READY -> videoUrl = rawUrl
            ReelMediaPlan.WAIT -> Unit
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
                        if (qualityModeEnabled) onRetryQuality()
                        retrySignal++
                    }
                }
            }
            ReelMediaPlan.UNPLAYABLE -> {
                // A blank URL the host cannot sign would otherwise show the poster spinner
                // forever; fail terminally so the user gets the error affordance.
                onPlaybackError("Failed to load video stream") {
                    if (qualityModeEnabled) onRetryQuality()
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
    val cacheKey = if (qualityModeEnabled) {
        qualityPlayback?.cacheKey
    } else {
        cachePrefix?.let { prefix -> "$prefix:${item.id}:${if (pinnedHd) "hd" else "sd"}" }
    }
    val clipIdentity = if (qualityModeEnabled) {
        qualityPlayback?.let { "${it.owner.token}:${it.itemId}" }
    } else {
        null
    }
    val mediaIdentity = if (qualityModeEnabled) qualityPlayback?.cacheKey else null
    val qualityEntryLabel = qualityPlayback?.decision?.variant?.let { variant ->
        val shortEdge = listOfNotNull(variant.width, variant.height).minOrNull()?.takeIf { it > 0 }
        if (variant.isSource) {
            if (shortEdge != null) {
                stringResource(AYMR.strings.reels_quality_source_resolution, "${shortEdge}p")
            } else {
                stringResource(AYMR.strings.reels_quality_missing_source)
            }
        } else {
            shortEdge?.let { "${it}p" }
        }
    }

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
                // Hold-to-rate. detectTapGestures' onLongPress is unusable here: it abandons the
                // press the moment the finger drifts past touchSlop (~8dp) before the ~500ms
                // timeout, which a one-handed thumb does constantly. Here the press only has to
                // stay inside a wider slop until the timeout; after activation every movement is
                // ignored and the rate is held until the finger lifts.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val rate = reelsHoldRate(down.position.x, size.width.toFloat())
                        ?: return@awaitEachGesture
                    val timeout = viewConfiguration.longPressTimeoutMillis
                    val slop = viewConfiguration.touchSlop * 3f
                    // A perfectly still finger emits no pointer events, so activation has to be
                    // timed out rather than waited for.
                    val gaveUpEarly = withTimeoutOrNull(timeout) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull true
                            if (!change.pressed) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > slop) {
                                return@withTimeoutOrNull true
                            }
                        }
                    } == true
                    if (gaveUpEarly) return@awaitEachGesture
                    heldRate = rate
                    heldToIgnoreTap = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // Consume so the pager cannot flip the reel under a holding finger.
                        if (!change.isConsumed) change.consume()
                        if (!change.pressed) break
                    }
                    heldRate = null
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (heldToIgnoreTap) {
                            heldToIgnoreTap = false
                        } else {
                            // Device report: a reveal-only tap made pause unreachable — with the
                            // 3s auto-hide it was buried behind a second, timed tap (landscape
                            // had no pause path at all). Tap now reveals the chrome AND toggles
                            // playback in both orientations; clean viewing stays on the eye
                            // button (hides the chrome without pausing).
                            currentOnTogglePlayPause()
                        }
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
                            seekBy(seekDeltaSec, showNotice = true)
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
            clipIdentity = clipIdentity,
            mediaIdentity = mediaIdentity,
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
            onPlaybackError = { msg ->
                onPlaybackError(msg) {
                    if (qualityModeEnabled) onRetryQuality()
                    retrySignal++
                }
            },
            onBufferingChanged = { isBuffering = it },
            onPlaybackRunningChanged = { playing ->
                actuallyPlaying = playing
                if (currentIsActive) onPlayingStateChanged(playing)
            },
            playbackSpeed = heldRate ?: effectivePlaybackSpeed,
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

        // 3. Center transport / Play-Pause overlay
        AnimatedVisibility(
            visible = if (isLandscape) {
                (chromeVisible || !isPlaying) &&
                    heldRate == null &&
                    !drawerOpen &&
                    qualityDialogMode == null &&
                    !isInPictureInPicture
            } else {
                !isPlaying && !isInPictureInPicture
            },
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (isLandscape) {
                ReelsLandscapeCenterTransport(
                    isPlaying = isPlaying,
                    onSeekBack = {
                        onScrubStart()
                        seekBy(-10f, showNotice = true)
                    },
                    onTogglePlayPause = currentOnTogglePlayPause,
                    onSeekForward = {
                        onScrubStart()
                        seekBy(10f, showNotice = true)
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(role = Role.Button, onClick = currentOnTogglePlayPause)
                        .background(Color.Black.copy(alpha = 0.55f), shape = CircleShape),
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

        // 5. Speed pill: one slot, two meanings kept visibly apart. While a finger holds an edge
        // the pill shows that temporary rate with a direction chevron; a rate chosen from the
        // button or drawer stays on screen without the chevron.
        val holdingRate = heldRate
        val pillRate = holdingRate ?: effectivePlaybackSpeed
        val pillFast = (holdingRate ?: effectivePlaybackSpeed) > 1f
        val auroraColors = AuroraTheme.colors
        AnimatedVisibility(
            visible = pillRate != 1f && !isInPictureInPicture,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp),
        ) {
            // TalkBack needs the spoken meaning; the visible text stays a bare "2×" / "0.5×".
            val holdLabel = stringResource(
                if (pillFast) MR.strings.reels_hold_fast else MR.strings.reels_hold_slow,
            )
            Row(
                modifier = Modifier
                    .reelsFrostPill(
                        rim = auroraMenuRimLightBrush(auroraColors),
                        eInk = auroraColors.isEInk,
                        solid = auroraColors.cardBackground,
                        strong = true,
                    )
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (holdingRate != null) {
                    Text(
                        text = if (pillFast) "»" else "«",
                        color = if (pillFast) auroraColors.accent else Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = reelsSpeedLabel(pillRate),
                    color = if (pillFast && holdingRate != null) auroraColors.accent else Color.White,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = holdLabel
                    },
                )
            }
        }

        // 6. Seek caption: light frost plate hugging the edge of the seek direction. The type
        // scales with the window: a portrait screen has no room for a 18sp block over the caption,
        // while landscape has the whole width to spare.
        AnimatedVisibility(
            visible = seekNotice.seconds != null && heldRate == null && isActive && !isInPictureInPicture,
            enter = fadeIn() + slideInHorizontally { if (seekNotice.forward) it / 5 else -it / 5 },
            exit = fadeOut() + slideOutHorizontally { if (seekNotice.forward) it / 5 else -it / 5 },
            modifier = Modifier
                .align(if (seekNotice.forward) Alignment.CenterEnd else Alignment.CenterStart)
                .then(
                    if (isLandscape) {
                        Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = reelsSeekNoticeHorizontalPadding(isLandscape)),
        ) {
            Text(
                text = seekNotice.seconds?.let { seconds ->
                    stringResource(
                        if (seconds < 0) MR.strings.reels_seek_notice_back else MR.strings.reels_seek_notice_forward,
                        kotlin.math.abs(seconds),
                    )
                }.orEmpty(),
                style = TextStyle(
                    color = Color.White,
                    fontSize = if (isLandscape) 18.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    shadow = Shadow(color = Color.Black, offset = Offset(0f, 1f), blurRadius = 4f),
                ),
                maxLines = 1,
                modifier = Modifier
                    .reelsFrostPill(
                        rim = auroraMenuRimLightBrush(auroraColors),
                        eInk = auroraColors.isEInk,
                        solid = auroraColors.cardBackground,
                        strong = false,
                    )
                    .padding(horizontal = if (isLandscape) 12.dp else 9.dp, vertical = if (isLandscape) 5.dp else 3.dp),
            )
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
                qualityDialogMode?.let { mode ->
                    ReelsVideoQualityDialog(
                        mode = mode,
                        itemState = qualityItemState,
                        global = qualityGlobal,
                        onSelect = { preference ->
                            if (mode == ReelsQualityDialogMode.VIDEO) {
                                onSetQualityOverride(item.id, preference)
                            } else {
                                onSetQualityGlobal(preference)
                            }
                            qualityDialogMode = null
                        },
                        onUseDefault = {
                            onClearQualityOverride(item.id)
                            qualityDialogMode = null
                        },
                        onDismiss = { qualityDialogMode = null },
                    )
                }
                if (isLandscape) {
                    // Landscape TopBar
                    AnimatedVisibility(
                        visible = chromeVisible || isSearchBarOpen,
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
                            playbackSpeed = effectivePlaybackSpeed,
                            isCropMode = isCropMode,
                            onToggleCropMode = {
                                onScrubStart()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onToggleCropMode()
                            },
                            showSearch = showSearch,
                            isSearchOpen = isSearchBarOpen,
                            onToggleSearch = {
                                if (!isSearchBarOpen) onScrubStart()
                                onToggleSearch()
                            },
                            searchQuery = searchQuery,
                            onSearchSubmit = onSearchSubmit,
                            onClearSearch = onClearSearch,
                            onOpenSourcePicker = onOpenSourcePicker,
                            onOpenSettings = {
                                drawerTab = 0
                                drawerOpen = true
                            },
                            onExitLandscape = onExitLandscape,
                            feedSectionLabel = feedSectionLabel,
                        )
                    }

                    // Landscape Compact Bottom Meta (bottom-start, above the 2-row Unified Cinema Console)
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
                            sourceBadgeName = sourceBadgeName,
                            sourceBadgeIcon = sourceBadgeIcon,
                            onSourceBadgeClick = onSourceBadgeClick,
                            isLandscapeCompact = true,
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .padding(
                                    start = 16.dp,
                                    bottom = 78.dp,
                                ),
                        )
                    }

                    // Landscape Bottom Unified Cinema Console (Seekbar + 36dp Unified Control Deck)
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
                            qualityLabel = qualityEntryLabel?.let { "$it ▾" },
                            onQualityClick = {
                                if (qualityModeEnabled) {
                                    qualityDialogMode = ReelsQualityDialogMode.VIDEO
                                }
                            },
                            isCropMode = isCropMode,
                            onToggleCropMode = {
                                onScrubStart()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onToggleCropMode()
                            },
                            playbackSpeed = effectivePlaybackSpeed,
                            onCycleSpeed = {
                                onScrubStart()
                                updatePlaybackSpeed(nextReelsLandscapeSpeed(effectivePlaybackSpeed))
                            },
                            isLiked = isLiked,
                            likesCount = item.likesCount,
                            onToggleLike = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onToggleLike()
                            },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPartial = isPartial,
                            downloadProgress = downloadProgress,
                            onDownload = onDownload?.let { download ->
                                {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    download()
                                }
                            },
                            onShare = { onShare(progressState.floatValue * durationSec.coerceAtLeast(0f)) },
                            onOpenMore = if (showHideAction) {
                                { showHideDialog = true }
                            } else {
                                null
                            },
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
                            modifier = Modifier.padding(bottom = 4.dp),
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
                            qualityModeEnabled = qualityModeEnabled,
                            qualityLabel = qualityEntryLabel,
                            onOpenQualityVideo = { qualityDialogMode = ReelsQualityDialogMode.VIDEO },
                            onOpenQualityDefault = { qualityDialogMode = ReelsQualityDialogMode.GLOBAL },
                            isCropMode = isCropMode,
                            onToggleCropMode = {
                                onScrubStart()
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                onToggleCropMode()
                            },
                            playbackSpeed = effectivePlaybackSpeed,
                            onSetPlaybackSpeed = { updatePlaybackSpeed(it) },
                            isAutoAdvance = isAutoAdvance,
                            onToggleAutoAdvance = onToggleAutoAdvance,
                            sleepTimerValue = sleepTimerValue,
                            onCycleSleepTimer = onCycleSleepTimer,
                            isIncognito = isIncognito,
                            onToggleIncognito = onToggleIncognito,
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPartial = isPartial,
                            downloadSizeText = downloadSizeText,
                            onDownloadCurrent = onDownload,
                            preloadWifiOnly = preloadWifiOnly,
                            onTogglePreloadWifiOnly = onTogglePreloadWifiOnly,
                            dataSaverEnabled = dataSaverEnabled,
                            onToggleDataSaver = onToggleDataSaver,
                            offlineUsedText = offlineUsedText,
                            offlineUsedFraction = offlineUsedFraction,
                            canClearOffline = canClearOffline,
                            onClearOffline = onClearOffline,
                            onOpenHide = if (showHideAction) {
                                {
                                    drawerOpen = false
                                    showHideDialog = true
                                }
                            } else {
                                null
                            },
                            currentSourceName = sourceBadgeName,
                            onOpenSource = onSourceBadgeClick,
                            onExcludeSourceFromMix = onExcludeSourceFromMix,
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
                    val navBarsBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    // Guarantee a safe margin of at least 26.dp even when system gesture bar is hidden (navBars == 0dp)
                    val safeBottomInset = maxOf(navBarsBottomPadding, 26.dp)
                    val metaBottomPadding = safeBottomInset + 28.dp
                    val actionsBottomPadding =
                        metaBottomPadding + 6.dp + with(density) { portraitMetaHeightPx.toDp() }
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
                            landscape = landscape,
                            onToggleLandscape = onToggleLandscape,
                            onDownload = onDownload?.let { download ->
                                {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                    download()
                                }
                            },
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloading,
                            isPartial = isPartial,
                            downloadSizeText = downloadSizeText,
                            downloadProgress = downloadProgress,
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
                            sourceBadgeName = sourceBadgeName,
                            sourceBadgeIcon = sourceBadgeIcon,
                            onSourceBadgeClick = onSourceBadgeClick,
                            modifier = Modifier
                                .padding(
                                    start = 16.dp,
                                    end = 76.dp,
                                    bottom = metaBottomPadding,
                                )
                                .onSizeChanged { portraitMetaHeightPx = it.height },
                        )
                    }

                    // Portrait Interactive Scrubber / Seek Bar (Aurora style)
                    AnimatedVisibility(
                        visible = chromeVisible,
                        enter = fadeIn() + slideInVertically { it },
                        exit = fadeOut() + slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        ReelsProgressBar(
                            progressState = progressState,
                            durationSec = durationSec,
                            bufferFraction = bufferFraction,
                            posterUrl = item.posterUrlVertical ?: item.posterUrl,
                            chromeVisible = chromeVisible,
                            onSeek = { frac ->
                                seekFraction = frac
                                progressState.floatValue = frac
                                coroutineScope.launch {
                                    delay(50)
                                    seekFraction = null
                                }
                            },
                            onScrubStart = onScrubStart,
                            modifier = Modifier
                                .padding(bottom = safeBottomInset + 4.dp),
                        )
                    }
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

                if (!sourceBadgeName.isNullOrBlank() && onSourceBadgeClick != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AuroraTheme.colors.accent.copy(alpha = 0.14f))
                            .border(1.dp, AuroraTheme.colors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable {
                                showHideDialog = false
                                onSourceBadgeClick()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = AuroraTheme.colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.reels_mix_open_plugin, sourceBadgeName),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (!sourceBadgeName.isNullOrBlank() && onExcludeSourceFromMix != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .clickable {
                                showHideDialog = false
                                onExcludeSourceFromMix()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.reels_mix_exclude_plugin),
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

internal const val REELS_HOLD_SLOW_RATE = 0.5f
internal const val REELS_HOLD_FAST_RATE = 2f

/** "2×" / "0.5×" — locale independent, so the pill never shows "2,0×" by accident. */
internal fun reelsSpeedLabel(rate: Float): String {
    val digits = if (rate % 1f == 0f) rate.toInt().toString() else rate.toString().trimEnd('0').trimEnd('.')
    return "$digits×"
}

private val REELS_FROST_STRONG = listOf(
    0f to Color(0xFF2E2740).copy(alpha = 0.78f),
    0.45f to Color(0xFF171221).copy(alpha = 0.72f),
    1f to Color(0xFF0E0C13).copy(alpha = 0.74f),
)
private val REELS_FROST_LIGHT = listOf(
    0f to Color(0xFF2A2438).copy(alpha = 0.46f),
    0.45f to Color(0xFF15111F).copy(alpha = 0.40f),
    1f to Color(0xFF0E0C13).copy(alpha = 0.44f),
)

/**
 * The Aurora frost plate: rim light comes from the shared token so light/dark/AMOLED/e-ink all
 * stay correct, and e-ink gets a solid surface instead of translucency.
 */
internal fun Modifier.reelsFrostPill(
    rim: Brush,
    eInk: Boolean,
    solid: Color,
    strong: Boolean,
): Modifier {
    if (eInk) return clip(CircleShape).background(solid).border(1.dp, rim, CircleShape)
    val stops = (if (strong) REELS_FROST_STRONG else REELS_FROST_LIGHT).map { it.second }
    return clip(CircleShape)
        .background(Brush.verticalGradient(stops), CircleShape)
        .border(1.dp, rim, CircleShape)
}

/**
 * Hold-to-rate: the left half of the reel slows it down, the right half speeds it up. Halves and
 * not thirds because a hold has no competing meaning in the middle (unlike the double-tap, which
 * needs it for "like"), and a thumb landing at 34% of the width should not silently do nothing.
 */
internal fun reelsHoldRate(offsetX: Float, width: Float): Float? = when {
    width <= 0f -> null
    offsetX < width / 2f -> REELS_HOLD_SLOW_RATE
    else -> REELS_HOLD_FAST_RATE
}

/**
 * A finger can drift while the user is still deciding; only a drift *before* the long-press
 * timeout means "this was a swipe, not a hold". After activation the rate is kept no matter where
 * the thumb wanders, which is what makes the gesture tolerable on a phone held one-handed.
 */
internal fun reelsHoldCancelledByDrift(
    distancePx: Float,
    slopPx: Float,
    elapsedMs: Long,
    timeoutMs: Long,
): Boolean = elapsedMs < timeoutMs && distancePx > slopPx

/** How a page turns its raw URL into a playable player input (contract v25). */
internal enum class ReelMediaPlan {
    READY,
    RESOLVE,
    WAIT,
    UNPLAYABLE,
}

internal fun reelMediaPlan(
    rawUrl: String,
    hasResolver: Boolean,
    lazyResolution: Boolean,
    qualityModeEnabled: Boolean = false,
    qualitySwitching: Boolean = false,
    qualityHasFailure: Boolean = false,
): ReelMediaPlan = when {
    rawUrl.isNotBlank() -> ReelMediaPlan.READY
    qualityModeEnabled -> if (qualityHasFailure && !qualitySwitching) {
        ReelMediaPlan.UNPLAYABLE
    } else {
        ReelMediaPlan.WAIT
    }
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

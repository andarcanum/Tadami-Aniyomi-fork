package eu.kanade.tachiyomi.ui.reels.components

import android.app.Activity
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.unit.IntOffset
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
import eu.kanade.tachiyomi.ui.reels.player.ReelsSeekRequest
import eu.kanade.tachiyomi.ui.reels.player.ReelsZoom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

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
    onRefreshExpiredUrl: (() -> Boolean)? = null,
    onCodecFallback: (() -> Boolean)? = null,
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
    authorDisplayLabel: String? = null,
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
    isMeteredNetwork: Boolean = false,
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
    landscapeBrightnessGestureEnabled: Boolean = true,
    landscapeVolumeGestureEnabled: Boolean = true,
    landscapePullUpSeekEnabled: Boolean = true,
    onToggleLandscapeBrightnessGesture: () -> Unit = {},
    onToggleLandscapeVolumeGesture: () -> Unit = {},
    onToggleLandscapePullUpSeek: () -> Unit = {},
    onUnmuteIfMuted: () -> Unit = {},
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
    playbackHeadersProvider: (() -> Map<String, String>)? = null,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    // Key per-page state on item.id so pager slot reuse never leaks stale progress, seek, or
    // overlay state from a previous reel.
    val progressState = remember(item.id) { mutableFloatStateOf(0f) }
    val bufferFractionState = remember(item.id) { mutableFloatStateOf(0f) }
    var durationSec by remember(item) { mutableFloatStateOf(item.durationSec ?: 0f) }
    var seekRequest by remember(item.id) { mutableStateOf<ReelsSeekRequest?>(null) }
    var seekSequence by remember(item.id) { mutableLongStateOf(0L) }
    var scrubUiState by remember(item.id) { mutableStateOf(ReelsScrubUiState()) }
    val scrubThumbnailCache = remember(item.id) { ReelsScrubThumbnailCache<ImageBitmap>() }
    var scrubThumbVersion by remember(item.id) { mutableIntStateOf(0) }
    DisposableEffect(item.id) {
        onDispose { scrubThumbnailCache.clear() }
    }
    var initialSeekApplied by remember(item.id) { mutableStateOf(false) }
    var initialSeekPositionApplied by remember(item.id) { mutableStateOf(false) }
    val heartBursts = remember(item.id) { mutableStateListOf<HeartBurst>() }
    var heartBurstSequence by remember(item.id) { mutableLongStateOf(0L) }
    var showHideDialog by remember(item.id) { mutableStateOf(false) }
    var isBuffering by remember(item.id) { mutableStateOf(false) }
    var drawerOpen by remember(item.id) { mutableStateOf(false) }
    var drawerTab by remember(item.id) { mutableIntStateOf(0) }
    var qualityDialogMode by remember(item.id) { mutableStateOf<ReelsQualityDialogMode?>(null) }
    // Persistent Pinch-to-Zoom state (1x..4x) + pan offset; kept across finger lifts like a
    // pro video player and reset when switching reels or toggling Fit/Fill aspect mode.
    val zoomScale = remember(item.id) { Animatable(1f) }
    val zoomOffsetX = remember(item.id) { Animatable(0f) }
    val zoomOffsetY = remember(item.id) { Animatable(0f) }
    var isPinchZooming by remember(item.id) { mutableStateOf(false) }
    var pageViewportWidthPx by remember(item.id) { mutableFloatStateOf(0f) }
    var pageViewportHeightPx by remember(item.id) { mutableFloatStateOf(0f) }

    fun animateZoomTo(targetScale: Float, resetPan: Boolean = false) {
        val normalized = ReelsZoom.normalizeScale(targetScale, snapToBase = true)
        val (nextX, nextY) = if (resetPan || !ReelsZoom.isZoomed(normalized)) {
            0f to 0f
        } else {
            ReelsZoom.clampOffset(
                offsetX = zoomOffsetX.value,
                offsetY = zoomOffsetY.value,
                scale = normalized,
                viewWidth = pageViewportWidthPx,
                viewHeight = pageViewportHeightPx,
            )
        }
        coroutineScope.launch {
            launch { zoomScale.animateTo(normalized, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { zoomOffsetX.animateTo(nextX, spring(stiffness = Spring.StiffnessMediumLow)) }
            launch { zoomOffsetY.animateTo(nextY, spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }

    LaunchedEffect(isCropMode) {
        // Switching Fit <-> Fill resets any manual pinch zoom so the chosen aspect mode takes
        // full effect cleanly without staying stuck zoomed.
        if (ReelsZoom.isZoomed(zoomScale.value) || zoomOffsetX.value != 0f || zoomOffsetY.value != 0f) {
            zoomScale.snapTo(1f)
            zoomOffsetX.snapTo(0f)
            zoomOffsetY.snapTo(0f)
        }
    }
    LaunchedEffect(isActive) {
        if (!isActive) {
            drawerOpen = false
            showHideDialog = false
            isPinchZooming = false
            zoomScale.snapTo(1f)
            zoomOffsetX.snapTo(0f)
            zoomOffsetY.snapTo(0f)
        }
    }
    val pageOverlayOpen = qualityDialogMode != null || drawerOpen || showHideDialog
    val currentPageOverlayOpen by rememberUpdatedState(pageOverlayOpen)
    val currentOnQualityDialogOpenChanged by rememberUpdatedState(onQualityDialogOpenChanged)
    var reportedOverlayOpen by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(pageOverlayOpen) {
        if (pageOverlayOpen != reportedOverlayOpen) {
            reportedOverlayOpen = pageOverlayOpen
            currentOnQualityDialogOpenChanged(pageOverlayOpen)
        }
    }
    DisposableEffect(item.id) {
        onDispose {
            // Only notify close when THIS page actually opened an overlay — a neighbor page
            // leaving composition must never wipe out the active page's open sheet/dialog.
            if (reportedOverlayOpen || currentPageOverlayOpen) {
                reportedOverlayOpen = false
                currentOnQualityDialogOpenChanged(false)
            }
        }
    }
    val seekNotice = rememberReelsSeekNotice(item.id, isActive, isInPictureInPicture)
    val currentSeekNotice by rememberUpdatedState(seekNotice)

    val currentIsActive by rememberUpdatedState(isActive)
    // The tap recognizer below is keyed by Unit and survives page rebinds (the pager reuses
    // a slot for a different item after a feed reset); values read inside the gesture must
    // not be captured from the composition that launched it.
    val currentOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val currentOnLike by rememberUpdatedState(onLike)
    val currentOnFirstFramePresented by rememberUpdatedState(onFirstFramePresented)
    val currentOnPlayingStateChanged by rememberUpdatedState(onPlayingStateChanged)
    val currentDurationSec by rememberUpdatedState(durationSec)
    val currentItemId by rememberUpdatedState(item.id)
    val currentIsLandscape by rememberUpdatedState(isLandscape)
    val currentIsTouchLocked by rememberUpdatedState(isTouchLocked)
    val currentBrightnessGestureEnabled by rememberUpdatedState(landscapeBrightnessGestureEnabled)
    val currentVolumeGestureEnabled by rememberUpdatedState(landscapeVolumeGestureEnabled)
    val currentLandscapePullUpSeek by rememberUpdatedState(landscapePullUpSeekEnabled)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnUnmuteIfMuted by rememberUpdatedState(onUnmuteIfMuted)
    val currentIsScrubbing by rememberUpdatedState(scrubUiState.isActive)
    var edgeGestureHud by remember(item.id) { mutableStateOf<ReelsEdgeGestureHudState?>(null) }
    var edgeGestureHideToken by remember(item.id) { mutableLongStateOf(0L) }
    LaunchedEffect(edgeGestureHideToken, edgeGestureHud?.isDragging) {
        val hud = edgeGestureHud ?: return@LaunchedEffect
        if (hud.isDragging) return@LaunchedEffect
        delay(LANDSCAPE_EDGE_HUD_HOLD_MS)
        edgeGestureHud = null
    }
    LaunchedEffect(isLandscape) {
        if (!isLandscape) edgeGestureHud = null
    }
    // Real playback state of this page's player (audio-focus and lifecycle pauses stop the
    // player without changing the feed-level play intent); gates watched-time accrual.
    // Starts false: time must not accrue before the player confirms it is actually running
    // (a still reel reports its own eligibility).
    var actuallyPlaying by remember(item.id) { mutableStateOf(false) }
    // Sync live playback state into the host whenever this page settles as active (fixes the
    // preloaded-ready race where onPageChanged cleared isActuallyPlaying right after ready).
    LaunchedEffect(isActive) {
        if (isActive) {
            currentOnPlayingStateChanged(actuallyPlaying)
        }
    }
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
    var heldRate by remember(item.id) { mutableStateOf<Float?>(null) }
    val currentHeldRate by rememberUpdatedState(heldRate)
    // Timestamp of the last speed-hold release: replaces the sticky boolean flag that stayed
    // true forever when change.consume() cancelled detectTapGestures.
    var lastHoldEndedAtMs by remember(item.id) { mutableLongStateOf(0L) }
    var retrySignal by remember(item.id) { mutableIntStateOf(0) }
    var urlRefreshSignal by remember(item.id) { mutableIntStateOf(0) }
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
    fun requestSeek(fraction: Float, liveScrub: Boolean = false) {
        val clamped = fraction.coerceIn(0f, 1f)
        seekSequence++
        seekRequest = ReelsSeekRequest(id = seekSequence, fraction = clamped, liveScrub = liveScrub)
        progressState.floatValue = clamped
    }
    fun seekBy(deltaSec: Float, showNotice: Boolean = false) {
        val activeDurationSec = currentDurationSec
        if (activeDurationSec <= 0f) return
        val currentSec = progressState.floatValue * activeDurationSec
        val maxTargetSec = if (deltaSec > 0f && activeDurationSec > 0.5f) {
            (activeDurationSec - 0.5f).coerceAtLeast(currentSec)
        } else {
            activeDurationSec
        }
        val targetSec = (currentSec + deltaSec).coerceIn(0f, maxTargetSec)
        val target = (targetSec / activeDurationSec).coerceIn(0f, 1f)
        requestSeek(target, liveScrub = false)
        if (showNotice) {
            // The caption reports what the player really moved, so a tap near the ends
            // cannot promise +10 while the reel only had 3 seconds left.
            currentSeekNotice.show((target * activeDurationSec - currentSec).toInt())
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

    // Sparse sampler (4 Hz): monotonic deltas, no per-frame work and never a DB write.
    // Stops completely when off-page or backgrounded so idle tabs/screens burn zero main-thread cycles.
    LaunchedEffect(item.id, isActive, isForeground) {
        if (!isActive || !isForeground) return@LaunchedEffect
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
    val pageContext = LocalContext.current
    val hostActivity = pageContext as? Activity
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
            requestSeek(initialSeekFraction, liveScrub = false)
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
    // Also re-runs when an expired signed CDN URL triggers urlRefreshSignal.
    var videoUrl by remember(rawUrl, item.id) { mutableStateOf(rawUrl) }
    LaunchedEffect(rawUrl, item.id, retrySignal, urlRefreshSignal, mediaPlan) {
        val forceResolve = urlRefreshSignal > 0 && resolveUrl != null && !qualityModeEnabled
        when (if (forceResolve) ReelMediaPlan.RESOLVE else mediaPlan) {
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
        val ownerToken = qualityPlayback?.owner?.token ?: qualityItemState?.owner?.token
        if (ownerToken != null) "$ownerToken:${item.id}" else item.id
    } else {
        item.id
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
            .clipToBounds()
            .onSizeChanged {
                pageViewportWidthPx = it.width.toFloat()
                pageViewportHeightPx = it.height.toFloat()
            }
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
            .pointerInput(item.id) {
                // Persistent multi-touch Pinch-to-Zoom (1x..4x) + 2-finger pan: keeps the user's
                // chosen scale when fingers lift (snapping to exact 1x only below 1.05x).
                // Activates strictly when >=2 fingers are down so single-finger vertical pager
                // swipes, taps, and hold-to-rate gestures are completely unaffected.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zooming = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (!zooming && (zoomChange != 1f || pressedCount >= 2)) {
                                    zooming = true
                                    isPinchZooming = true
                                }
                                if (zooming) {
                                    val nextScale = ReelsZoom.normalizeScale(zoomScale.value * zoomChange)
                                    val (nextX, nextY) = ReelsZoom.clampOffset(
                                        offsetX = zoomOffsetX.value + panChange.x,
                                        offsetY = zoomOffsetY.value + panChange.y,
                                        scale = nextScale,
                                        viewWidth = size.width.toFloat(),
                                        viewHeight = size.height.toFloat(),
                                    )
                                    coroutineScope.launch {
                                        zoomScale.snapTo(nextScale)
                                        zoomOffsetX.snapTo(nextX)
                                        zoomOffsetY.snapTo(nextY)
                                    }
                                    event.changes.forEach { if (!it.isConsumed) it.consume() }
                                }
                            } else if (pressedCount == 0) {
                                break
                            } else if (zooming) {
                                // Only 1 finger remains after pinch: restore chrome immediately.
                                isPinchZooming = false
                            }
                        }
                    } finally {
                        if (zooming) {
                            isPinchZooming = false
                            lastHoldEndedAtMs = SystemClock.elapsedRealtime()
                            val settledScale = ReelsZoom.normalizeScale(zoomScale.value, snapToBase = true)
                            if (!ReelsZoom.isZoomed(settledScale)) {
                                // Only snap back when pinched close to 1x; otherwise persist zoom!
                                animateZoomTo(1f, resetPan = true)
                            }
                        }
                    }
                }
            }
            .pointerInput(item.id) {
                // Landscape-only edge vertical drags: left 0..16% adjusts screen brightness,
                // right 84..100% adjusts media volume. Center 16..84% stays untouched so
                // VerticalPager swipes work effortlessly.
                val topExclusionPx = 48.dp.toPx()
                val bottomExclusionPx = 72.dp.toPx()
                val minGestureSpanPx = 180.dp.toPx()
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!currentIsLandscape || currentIsTouchLocked) return@awaitEachGesture
                    val zone = resolveLandscapeEdgeZone(
                        xPx = down.position.x,
                        yPx = down.position.y,
                        widthPx = size.width.toFloat(),
                        heightPx = size.height.toFloat(),
                        topExclusionPx = topExclusionPx,
                        bottomExclusionPx = bottomExclusionPx,
                        brightnessEnabled = currentBrightnessGestureEnabled,
                        volumeEnabled = currentVolumeGestureEnabled,
                    ) ?: return@awaitEachGesture

                    val gestureSpanPx = maxOf(size.height.toFloat() * 0.65f, minGestureSpanPx)
                    val claimSlopPx = touchSlop * 0.85f
                    var totalDx = 0f
                    var totalDy = 0f
                    var dragging = false
                    var startLevel = 0.5f
                    var maxVolume = 15
                    var lastAppliedVolIndex = -1
                    var lastLevel = 0.5f

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!dragging && (change.isConsumed || currentIsScrubbing || currentHeldRate != null)) {
                                break
                            }
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y

                            if (!dragging) {
                                // Yield immediately if predominantly horizontal (e.g. hold/scrub).
                                if (abs(totalDx) > claimSlopPx && abs(totalDx) > abs(totalDy) * 1.15f) {
                                    break
                                }
                                if (abs(totalDy) >= claimSlopPx && abs(totalDy) > abs(totalDx) * 1.25f) {
                                    dragging = true
                                    when (zone) {
                                        ReelsLandscapeEdgeZone.Brightness -> {
                                            startLevel = readCurrentScreenBrightness(hostActivity)
                                        }
                                        ReelsLandscapeEdgeZone.Volume -> {
                                            val (volLevel, maxVol) = readCurrentMediaVolume(pageContext)
                                            startLevel = volLevel
                                            maxVolume = maxVol
                                            lastAppliedVolIndex = mapNormalizedVolumeToStreamIndex(volLevel, maxVol)
                                        }
                                    }
                                    lastLevel = startLevel
                                    // Subtract the initial slop so the level begins smoothly without jumping.
                                    totalDy = if (totalDy > 0f) {
                                        (totalDy - claimSlopPx).coerceAtLeast(0f)
                                    } else {
                                        (totalDy + claimSlopPx).coerceAtMost(0f)
                                    }
                                }
                            }

                            if (dragging) {
                                if (!change.isConsumed) change.consume()
                                val minLevel = if (zone == ReelsLandscapeEdgeZone.Brightness) {
                                    LANDSCAPE_MIN_BRIGHTNESS
                                } else {
                                    0f
                                }
                                val nextLevel = computeUpdatedEdgeGestureLevel(
                                    startLevel = startLevel,
                                    totalDyPx = totalDy,
                                    gestureSpanPx = gestureSpanPx,
                                    minLevel = minLevel,
                                    maxLevel = 1f,
                                )
                                when (zone) {
                                    ReelsLandscapeEdgeZone.Brightness -> {
                                        applyScreenBrightness(hostActivity, nextLevel)
                                    }
                                    ReelsLandscapeEdgeZone.Volume -> {
                                        if (nextLevel > lastLevel + 0.005f) {
                                            currentOnUnmuteIfMuted()
                                        }
                                        val applied = applyMediaVolume(
                                            context = pageContext,
                                            level = nextLevel,
                                            maxVolume = maxVolume,
                                            lastAppliedIndex = lastAppliedVolIndex,
                                        )
                                        if (applied != lastAppliedVolIndex) {
                                            lastAppliedVolIndex = applied
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                                lastLevel = nextLevel
                                edgeGestureHud = ReelsEdgeGestureHudState(
                                    zone = zone,
                                    level = nextLevel,
                                    isDragging = true,
                                )
                            }
                        }
                    } finally {
                        if (dragging) {
                            lastHoldEndedAtMs = SystemClock.elapsedRealtime()
                            edgeGestureHud = ReelsEdgeGestureHudState(
                                zone = zone,
                                level = lastLevel,
                                isDragging = false,
                            )
                            edgeGestureHideToken = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
            .pointerInput(item.id) {
                // Hold-to-rate in portrait (0.5x / 2x) OR YouTube-style hold-and-lift direct
                // scrubbing in landscape when landscapePullUpSeekEnabled is active.
                val fineEnterLiftPx = 32.dp.toPx()
                val fineExitLiftPx = 10.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (currentIsTouchLocked) return@awaitEachGesture
                    val rate = reelsHoldRate(down.position.x, size.width.toFloat())
                        ?: return@awaitEachGesture
                    val useLandscapeHoldSeek =
                        currentIsLandscape && currentLandscapePullUpSeek && currentDurationSec > 0f
                    val timeout = if (useLandscapeHoldSeek) {
                        min(viewConfiguration.longPressTimeoutMillis, 260L)
                    } else {
                        viewConfiguration.longPressTimeoutMillis
                    }
                    val slop = if (useLandscapeHoldSeek) {
                        viewConfiguration.touchSlop * 2f
                    } else {
                        viewConfiguration.touchSlop * 3f
                    }
                    val gaveUpEarly = withTimeoutOrNull(timeout) {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) return@withTimeoutOrNull true
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@withTimeoutOrNull true
                            if (!change.pressed || change.isConsumed) return@withTimeoutOrNull true
                            if ((change.position - down.position).getDistance() > slop) {
                                return@withTimeoutOrNull true
                            }
                        }
                    } == true
                    if (gaveUpEarly) return@awaitEachGesture

                    if (useLandscapeHoldSeek) {
                        val fineController = ReelsFineScrubController(
                            fineEnterLiftPx = fineEnterLiftPx,
                            fineExitLiftPx = fineExitLiftPx,
                        )
                        val startProgress = progressState.floatValue.coerceIn(0f, 1f)
                        val trackWidthPx = (size.width.toFloat() - 32.dp.toPx()).coerceAtLeast(1f)
                        fineController.start(
                            initialProgress = startProgress,
                            barWidthPx = trackWidthPx,
                            durationSec = currentDurationSec,
                            startProgress = startProgress,
                        )
                        currentOnScrubStart()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        scrubUiState = ReelsScrubUiState(
                            isActive = true,
                            progress = fineController.progress,
                            startProgress = startProgress,
                            isFineMode = false,
                            fromScreenGesture = true,
                            barWidthPx = trackWidthPx,
                        )
                        var lastLiveSeekAtMs = SystemClock.elapsedRealtime()
                        var lastFineTickSec = (fineController.progress * currentDurationSec).roundToInt()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    fineController.cancel()
                                    break
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val delta = change.positionChange()
                                if (!change.isConsumed) change.consume()
                                val modeToggled = fineController.move(
                                    dx = delta.x,
                                    dy = delta.y,
                                    allowFineMode = true,
                                )
                                if (modeToggled) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (fineController.isFineMode && currentDurationSec > 0f) {
                                    val tickSec = (fineController.progress * currentDurationSec).roundToInt()
                                    if (tickSec != lastFineTickSec) {
                                        lastFineTickSec = tickSec
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                                scrubUiState = ReelsScrubUiState(
                                    isActive = true,
                                    progress = fineController.progress,
                                    startProgress = startProgress,
                                    isFineMode = fineController.isFineMode,
                                    fromScreenGesture = true,
                                    barWidthPx = trackWidthPx,
                                )
                                val now = SystemClock.elapsedRealtime()
                                if (now - lastLiveSeekAtMs >= 75L) {
                                    lastLiveSeekAtMs = now
                                    requestSeek(fineController.progress, liveScrub = true)
                                }
                            }
                            fineController.end()?.let { target ->
                                if (abs(target - startProgress) > 0.001f) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    requestSeek(target, liveScrub = false)
                                }
                            }
                        } finally {
                            fineController.cancel()
                            scrubUiState = ReelsScrubUiState()
                            lastHoldEndedAtMs = SystemClock.elapsedRealtime()
                        }
                    } else {
                        try {
                            heldRate = rate
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.isConsumed) change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            heldRate = null
                            lastHoldEndedAtMs = SystemClock.elapsedRealtime()
                        }
                    }
                }
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = {
                        // Suppress only a lift event arriving immediately after a hold/pinch;
                        // never use a sticky boolean that stays true when consume() cancels tap.
                        val sinceHold = SystemClock.elapsedRealtime() - lastHoldEndedAtMs
                        if (heldRate == null && !isPinchZooming && sinceHold >= HOLD_TAP_SUPPRESS_MS) {
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
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            seekBy(seekDeltaSec, showNotice = true)
                        } else {
                            // Set-like, not toggle: the second double-tap on an already liked
                            // reel must stay a like.
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnLike()
                            heartBurstSequence++
                            val burst = HeartBurst(
                                id = heartBurstSequence,
                                position = offset,
                                rotationDeg = Random.nextFloat() * 36f - 18f,
                            )
                            heartBursts.add(burst)
                        }
                    },
                )
            },
    ) {
        val effectiveChromeVisible = chromeVisible && !isPinchZooming
        // 1. Video Player & Poster layer (supports pinch-to-zoom via graphicsLayer)
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
            seekRequest = seekRequest,
            seekToPositionMs = if (isActive && !initialSeekPositionApplied) initialSeekPositionMs else null,
            onSeekPositionConsumed = {
                initialSeekPositionApplied = true
                onInitialSeekPositionConsumed()
            },
            isScrubbing = scrubUiState.isActive,
            onScrubFrameCaptured = { capturedFraction, bitmap ->
                scrubThumbnailCache.put(capturedFraction, currentDurationSec, bitmap)
                scrubThumbVersion++
            },
            onRenderedFirstFrame = {
                if (currentIsActive) currentOnFirstFramePresented(currentItemId)
            },
            onProgressUpdate = {
                if (!scrubUiState.isActive) {
                    progressState.floatValue = it
                }
            },
            onBufferUpdate = { bufferFractionState.floatValue = it },
            onVideoCompleted = onVideoCompleted,
            // The player-reported duration is authoritative; the server value is only a
            // placeholder until STATE_READY (wrong server durations must not stick).
            knownDurationSec = item.durationSec ?: 0f,
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
                if (currentIsActive) currentOnPlayingStateChanged(playing)
            },
            onRefreshExpiredUrl = {
                when {
                    qualityModeEnabled -> {
                        onRetryQuality()
                        true
                    }
                    resolveUrl != null -> {
                        urlRefreshSignal++
                        true
                    }
                    else -> onRefreshExpiredUrl?.invoke() == true
                }
            },
            onCodecFallback = {
                when {
                    !qualityModeEnabled && pinnedHd && item.videoUrl.isNotBlank() && item.videoUrl != videoUrl -> {
                        pinnedHd = false
                        true
                    }
                    else -> onCodecFallback?.invoke() == true
                }
            },
            isInPictureInPicture = isInPictureInPicture,
            isMeteredNetwork = isMeteredNetwork,
            playbackSpeed = heldRate ?: effectivePlaybackSpeed,
            zoomScale = zoomScale.value,
            zoomOffsetX = zoomOffsetX.value,
            zoomOffsetY = zoomOffsetY.value,
            retrySignal = retrySignal,
            headers = headers,
            playbackHeadersProvider = playbackHeadersProvider,
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

        // 2. Cinema edge scrims for readable bare icons & text. The scrim is part of the
        // chrome: in clean fullscreen / auto-hidden state the video stays untouched, and
        // e-ink uses solid high-contrast backplates instead of translucent gradients.
        val isEInk = AuroraTheme.colors.isEInk
        AnimatedVisibility(
            visible = effectiveChromeVisible && !isInPictureInPicture && !isEInk,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.58f),
                                0.11f to Color.Black.copy(alpha = 0.24f),
                                0.22f to Color.Transparent,
                                0.58f to Color.Transparent,
                                0.78f to Color.Black.copy(alpha = 0.36f),
                                1.0f to Color.Black.copy(alpha = 0.84f),
                            ),
                        ),
                )
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(96.dp)
                            .background(
                                Brush.horizontalGradient(
                                    0.0f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.28f),
                                ),
                            ),
                    )
                }
            }
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

        // 4. Double-tap Heart burst animation at exact tap point with random tilt & spring
        if (!isInPictureInPicture && heartBursts.isNotEmpty()) {
            heartBursts.forEach { burst ->
                key(burst.id) {
                    HeartBurstOverlay(
                        burst = burst,
                        tint = AuroraTheme.colors.accent,
                        onFinished = { heartBursts.remove(burst) },
                    )
                }
            }
        }

        // 5. Speed pill + 5b. Pro-player Zoom HUD pill.
        ReelsTopSpeedAndZoomHud(
            holdingRate = heldRate,
            effectivePlaybackSpeed = effectivePlaybackSpeed,
            zoomScale = zoomScale.value,
            isPinchZooming = isPinchZooming,
            isCropMode = isCropMode,
            isActive = isActive,
            isLandscape = isLandscape,
            isInPictureInPicture = isInPictureInPicture,
            chromeVisible = chromeVisible,
            effectiveChromeVisible = effectiveChromeVisible,
            isSearchBarOpen = isSearchBarOpen,
            searchQuery = searchQuery,
            drawerOpen = drawerOpen,
            qualityDialogOpen = qualityDialogMode != null,
            isScrubbing = scrubUiState.isActive,
            onToggleCropMode = onToggleCropMode,
            onAnimateZoomTo = ::animateZoomTo,
        )

        // 6. Seek caption: light frost plate hugging the edge of the seek direction.
        ReelsSeekNoticeOverlay(
            seekNotice = seekNotice,
            heldRate = heldRate,
            isActive = isActive,
            isInPictureInPicture = isInPictureInPicture,
            isLandscape = isLandscape,
        )

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
                    ReelsLandscapeControlsOverlay(
                        item = item,
                        effectiveChromeVisible = effectiveChromeVisible,
                        scrubUiState = scrubUiState,
                        progressState = progressState,
                        bufferFractionState = bufferFractionState,
                        durationSec = durationSec,
                        sourceName = sourceName,
                        feedSectionLabel = feedSectionLabel,
                        isMuted = isMuted,
                        isLiked = isLiked,
                        isHdQuality = isHdQuality,
                        qualityModeEnabled = qualityModeEnabled,
                        qualityEntryLabel = qualityEntryLabel,
                        isCropMode = isCropMode,
                        effectivePlaybackSpeed = effectivePlaybackSpeed,
                        isAutoAdvance = isAutoAdvance,
                        showSearch = showSearch,
                        isSearchBarOpen = isSearchBarOpen,
                        searchQuery = searchQuery,
                        showFollowAction = showFollowAction,
                        isFollowingCreator = isFollowingCreator,
                        showHideAction = showHideAction,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPartial = isPartial,
                        downloadSizeText = downloadSizeText,
                        downloadProgress = downloadProgress,
                        sourceBadgeName = sourceBadgeName,
                        sourceBadgeIcon = sourceBadgeIcon,
                        playlistItems = playlistItems,
                        playlistIndex = playlistIndex,
                        preloadWifiOnly = preloadWifiOnly,
                        dataSaverEnabled = dataSaverEnabled,
                        sleepTimerValue = sleepTimerValue,
                        offlineUsedText = offlineUsedText,
                        offlineUsedFraction = offlineUsedFraction,
                        canClearOffline = canClearOffline,
                        isIncognito = isIncognito,
                        landscapeBrightnessGestureEnabled = landscapeBrightnessGestureEnabled,
                        landscapeVolumeGestureEnabled = landscapeVolumeGestureEnabled,
                        landscapePullUpSeekEnabled = landscapePullUpSeekEnabled,
                        drawerOpen = drawerOpen,
                        drawerTab = drawerTab,
                        onOpenDrawer = { tab ->
                            drawerTab = tab
                            drawerOpen = true
                        },
                        onCloseDrawer = { drawerOpen = false },
                        onOpenQualityDialog = { mode -> qualityDialogMode = mode },
                        onOpenHideDialog = {
                            drawerOpen = false
                            showHideDialog = true
                        },
                        onBack = onBack,
                        onToggleChrome = onToggleChrome,
                        onToggleCropMode = onToggleCropMode,
                        onToggleSearch = onToggleSearch,
                        onSearchSubmit = onSearchSubmit,
                        onClearSearch = onClearSearch,
                        onOpenSourcePicker = onOpenSourcePicker,
                        onExitLandscape = onExitLandscape,
                        onTagClick = onTagClick,
                        onAuthorClick = onAuthorClick,
                        authorDisplayLabel = authorDisplayLabel,
                        onCreatorClick = onCreatorClick,
                        onToggleFollowCreator = onToggleFollowCreator,
                        onSourceBadgeClick = onSourceBadgeClick,
                        onExcludeSourceFromMix = onExcludeSourceFromMix,
                        onToggleMute = onToggleMute,
                        onToggleTouchLock = onToggleTouchLock,
                        onUpdatePlaybackSpeed = updatePlaybackSpeed,
                        onToggleLike = onToggleLike,
                        onDownload = onDownload,
                        onShare = onShare,
                        onRequestSeek = ::requestSeek,
                        onScrubStart = onScrubStart,
                        onScrubStateChange = { state -> scrubUiState = state },
                        onToggleQuality = onToggleQuality,
                        onToggleAutoAdvance = onToggleAutoAdvance,
                        onToggleLandscapeBrightnessGesture = onToggleLandscapeBrightnessGesture,
                        onToggleLandscapeVolumeGesture = onToggleLandscapeVolumeGesture,
                        onToggleLandscapePullUpSeek = onToggleLandscapePullUpSeek,
                        onCycleSleepTimer = onCycleSleepTimer,
                        onToggleIncognito = onToggleIncognito,
                        onTogglePreloadWifiOnly = onTogglePreloadWifiOnly,
                        onToggleDataSaver = onToggleDataSaver,
                        onClearOffline = onClearOffline,
                        onClearVideoCache = onClearVideoCache,
                        onSelectPlaylistItem = onSelectPlaylistItem,
                    )
                } else {
                    ReelsPortraitControlsOverlay(
                        item = item,
                        effectiveChromeVisible = effectiveChromeVisible,
                        scrubUiState = scrubUiState,
                        progressState = progressState,
                        bufferFractionState = bufferFractionState,
                        durationSec = durationSec,
                        isLiked = isLiked,
                        isMuted = isMuted,
                        showFollowAction = showFollowAction,
                        isFollowingCreator = isFollowingCreator,
                        showHideAction = showHideAction,
                        showOfflineAction = showOfflineAction,
                        isOfflineStored = isOfflineStored,
                        landscape = landscape,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        isPartial = isPartial,
                        downloadSizeText = downloadSizeText,
                        downloadProgress = downloadProgress,
                        sourceBadgeName = sourceBadgeName,
                        sourceBadgeIcon = sourceBadgeIcon,
                        onToggleLike = onToggleLike,
                        onToggleMute = onToggleMute,
                        onShare = onShare,
                        onToggleFollowCreator = onToggleFollowCreator,
                        onOpenHideDialog = { showHideDialog = true },
                        onToggleOffline = onToggleOffline,
                        onToggleLandscape = onToggleLandscape,
                        onDownload = onDownload,
                        onTagClick = onTagClick,
                        onAuthorClick = onAuthorClick,
                        authorDisplayLabel = authorDisplayLabel,
                        onCreatorClick = onCreatorClick,
                        onSourceBadgeClick = onSourceBadgeClick,
                        onRequestSeek = ::requestSeek,
                        onScrubStart = onScrubStart,
                        onScrubStateChange = { state -> scrubUiState = state },
                    )
                }
            }
        }

        // Unclipped Full-Page Scrub Thumbnail Preview & YouTube Fine-Scrubbing Filmstrip
        if (isActive && !isInPictureInPicture && scrubUiState.isActive) {
            ReelsScrubOverlayHost(
                scrubUiState = scrubUiState,
                scrubThumbVersion = scrubThumbVersion,
                scrubThumbnailCache = scrubThumbnailCache,
                durationSec = durationSec,
                posterUrl = item.posterUrlVertical ?: item.posterUrl,
                isLandscape = isLandscape,
                chromeVisible = effectiveChromeVisible,
                fineScrubEnabled = landscapePullUpSeekEnabled,
            )
        }

        // Landscape edge brightness / volume HUD (Aurora Frost glass capsule).
        if (isActive && !isInPictureInPicture) {
            ReelsEdgeGestureHud(state = edgeGestureHud)
        }
    }

    // B3.2 hide choice: Aurora Frost Dialog with danger actions
    if (showHideDialog) {
        ReelsHideVideoDialog(
            onDismiss = { showHideDialog = false },
            onHideVideo = onHideVideo,
            onHideAuthor = onHideAuthor,
            sourceBadgeName = sourceBadgeName,
            onSourceBadgeClick = onSourceBadgeClick,
            onExcludeSourceFromMix = onExcludeSourceFromMix,
        )
    }
}

@Composable
private fun BoxScope.ReelsTopSpeedAndZoomHud(
    holdingRate: Float?,
    effectivePlaybackSpeed: Float,
    zoomScale: Float,
    isPinchZooming: Boolean,
    isCropMode: Boolean,
    isActive: Boolean,
    isLandscape: Boolean,
    isInPictureInPicture: Boolean,
    chromeVisible: Boolean,
    effectiveChromeVisible: Boolean,
    isSearchBarOpen: Boolean,
    searchQuery: String,
    drawerOpen: Boolean,
    qualityDialogOpen: Boolean,
    isScrubbing: Boolean,
    onToggleCropMode: () -> Unit,
    onAnimateZoomTo: (Float, Boolean) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val pillRate = holdingRate ?: effectivePlaybackSpeed
    val pillFast = pillRate > 1f
    val auroraColors = AuroraTheme.colors
    val showSpeedPill = pillRate != 1f && !isInPictureInPicture
    val isZoomed = ReelsZoom.isZoomed(zoomScale)
    val showZoomHud = !isInPictureInPicture &&
        isActive &&
        !isSearchBarOpen &&
        !drawerOpen &&
        !qualityDialogOpen &&
        !isScrubbing &&
        (isPinchZooming || (isZoomed && chromeVisible))
    val topHudPadding = if (isLandscape) {
        if (effectiveChromeVisible) 60.dp else 12.dp
    } else {
        when {
            !effectiveChromeVisible -> 14.dp
            searchQuery.isNotBlank() && !isSearchBarOpen -> 112.dp
            else -> 72.dp
        }
    }
    val topHudModifier = Modifier
        .align(Alignment.TopCenter)
        .windowInsetsPadding(
            if (isLandscape) {
                WindowInsets.safeDrawing
            } else {
                // Use safeDrawing (statusBars + displayCutout) so the Zoom/Speed HUD
                // stays below the camera notch/punch-hole even when immersive mode hides
                // the status bar (when statusBarsPadding() collapses to 0dp).
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            },
        )
        .padding(top = topHudPadding, start = 12.dp, end = 12.dp)

    val renderSpeedPill: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = showSpeedPill,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
        ) {
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
    }

    val renderZoomHud: @Composable () -> Unit = {
        AnimatedVisibility(
            visible = showZoomHud,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            Row(
                modifier = Modifier
                    .reelsFrostPill(
                        rim = auroraMenuRimLightBrush(auroraColors),
                        eInk = auroraColors.isEInk,
                        solid = auroraColors.cardBackground,
                        strong = true,
                    )
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = ReelsZoom.formatScaleLabel(zoomScale),
                    color = auroraColors.accent,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                if (!isPinchZooming && isZoomed) {
                    val fitLabel = stringResource(MR.strings.reels_aspect_fit)
                    val fillLabel = stringResource(MR.strings.reels_aspect_fill)
                    ReelsZoomPresetChip(
                        label = fitLabel,
                        selected = false,
                        accent = auroraColors.accent,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isCropMode) onToggleCropMode()
                            onAnimateZoomTo(1f, true)
                        },
                    )
                    ReelsZoomPresetChip(
                        label = fillLabel,
                        selected = false,
                        accent = auroraColors.accent,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (!isCropMode) onToggleCropMode()
                            onAnimateZoomTo(1f, true)
                        },
                    )
                    ReelsZoom.SCALE_PRESETS.forEach { preset ->
                        val activePreset = abs(zoomScale - preset) < 0.08f
                        ReelsZoomPresetChip(
                            label = ReelsZoom.formatScaleLabel(preset),
                            selected = activePreset,
                            accent = auroraColors.accent,
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAnimateZoomTo(preset, false)
                            },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable(role = Role.Button) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAnimateZoomTo(1f, true)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(MR.strings.action_reset),
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }

    if (isLandscape) {
        Row(
            modifier = topHudModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            renderSpeedPill()
            renderZoomHud()
        }
    } else {
        Column(
            modifier = topHudModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            renderSpeedPill()
            renderZoomHud()
        }
    }
}

@Composable
private fun BoxScope.ReelsSeekNoticeOverlay(
    seekNotice: ReelsSeekNotice,
    heldRate: Float?,
    isActive: Boolean,
    isInPictureInPicture: Boolean,
    isLandscape: Boolean,
) {
    val auroraColors = AuroraTheme.colors
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
                    abs(seconds),
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
}

@Composable
private fun BoxScope.ReelsLandscapeControlsOverlay(
    item: ShortVideoItem,
    effectiveChromeVisible: Boolean,
    scrubUiState: ReelsScrubUiState,
    progressState: State<Float>,
    bufferFractionState: State<Float>,
    durationSec: Float,
    sourceName: String,
    feedSectionLabel: String?,
    isMuted: Boolean,
    isLiked: Boolean,
    isHdQuality: Boolean,
    qualityModeEnabled: Boolean,
    qualityEntryLabel: String?,
    isCropMode: Boolean,
    effectivePlaybackSpeed: Float,
    isAutoAdvance: Boolean,
    showSearch: Boolean,
    isSearchBarOpen: Boolean,
    searchQuery: String,
    showFollowAction: Boolean,
    isFollowingCreator: Boolean,
    showHideAction: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPartial: Boolean,
    downloadSizeText: String?,
    downloadProgress: Float?,
    sourceBadgeName: String?,
    sourceBadgeIcon: ImageBitmap?,
    playlistItems: List<ShortVideoItem>,
    playlistIndex: Int,
    preloadWifiOnly: Boolean,
    dataSaverEnabled: Boolean,
    sleepTimerValue: String,
    offlineUsedText: String,
    offlineUsedFraction: Float,
    canClearOffline: Boolean,
    isIncognito: Boolean,
    landscapeBrightnessGestureEnabled: Boolean,
    landscapeVolumeGestureEnabled: Boolean,
    landscapePullUpSeekEnabled: Boolean,
    drawerOpen: Boolean,
    drawerTab: Int,
    onOpenDrawer: (Int) -> Unit,
    onCloseDrawer: () -> Unit,
    onOpenQualityDialog: (ReelsQualityDialogMode) -> Unit,
    onOpenHideDialog: () -> Unit,
    onBack: () -> Unit,
    onToggleChrome: () -> Unit,
    onToggleCropMode: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchSubmit: (String) -> Unit,
    onClearSearch: () -> Unit,
    onOpenSourcePicker: (() -> Unit)?,
    onExitLandscape: () -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorClick: (() -> Unit)?,
    authorDisplayLabel: String?,
    onCreatorClick: ((String) -> Unit)?,
    onToggleFollowCreator: () -> Unit,
    onSourceBadgeClick: (() -> Unit)?,
    onExcludeSourceFromMix: (() -> Unit)?,
    onToggleMute: () -> Unit,
    onToggleTouchLock: () -> Unit,
    onUpdatePlaybackSpeed: (Float) -> Unit,
    onToggleLike: () -> Unit,
    onDownload: (() -> Unit)?,
    onShare: (Float) -> Unit,
    onRequestSeek: (Float, Boolean) -> Unit,
    onScrubStart: () -> Unit,
    onScrubStateChange: (ReelsScrubUiState) -> Unit,
    onToggleQuality: () -> Unit,
    onToggleAutoAdvance: () -> Unit,
    onToggleLandscapeBrightnessGesture: () -> Unit,
    onToggleLandscapeVolumeGesture: () -> Unit,
    onToggleLandscapePullUpSeek: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onToggleIncognito: () -> Unit,
    onTogglePreloadWifiOnly: () -> Unit,
    onToggleDataSaver: () -> Unit,
    onClearOffline: () -> Unit,
    onClearVideoCache: () -> Unit,
    onSelectPlaylistItem: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    // Landscape TopBar
    AnimatedVisibility(
        visible = effectiveChromeVisible || isSearchBarOpen,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = Modifier.align(Alignment.TopCenter),
    ) {
        ReelsLandscapeTopBar(
            sourceName = sourceName,
            onBackClick = onBack,
            onOpenPlaylist = { onOpenDrawer(2) },
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
            onOpenSettings = { onOpenDrawer(0) },
            onExitLandscape = onExitLandscape,
            feedSectionLabel = feedSectionLabel,
        )
    }

    // Landscape Compact Bottom Meta (bottom-start, above the 2-row Unified Cinema Console)
    AnimatedVisibility(
        visible = effectiveChromeVisible && !scrubUiState.isActive,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        ReelsBottomMeta(
            item = item,
            onTagClick = onTagClick,
            onAuthorClick = onAuthorClick,
            authorDisplayLabel = authorDisplayLabel,
            onCreatorClick = onCreatorClick,
            showFollowAction = showFollowAction,
            isFollowingCreator = isFollowingCreator,
            onToggleFollowCreator = onToggleFollowCreator,
            onMetaStatsClick = { onOpenDrawer(0) },
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
        visible = effectiveChromeVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReelsLandscapeScrubberDock(
            progressState = progressState,
            bufferFractionState = bufferFractionState,
            durationSec = durationSec,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            onLockScreen = onToggleTouchLock,
            qualityLabel = qualityEntryLabel?.let { "$it ▾" },
            onQualityClick = {
                if (qualityModeEnabled) {
                    onOpenQualityDialog(ReelsQualityDialogMode.VIDEO)
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
                onUpdatePlaybackSpeed(nextReelsLandscapeSpeed(effectivePlaybackSpeed))
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
            onShare = { onShare(progressState.value * durationSec.coerceAtLeast(0f)) },
            onOpenMore = if (showHideAction) onOpenHideDialog else null,
            posterUrl = item.posterUrlVertical ?: item.posterUrl,
            onSeek = { frac -> onRequestSeek(frac, false) },
            onScrubPreview = { frac -> onRequestSeek(frac, true) },
            onScrubStart = onScrubStart,
            onScrubStateChange = onScrubStateChange,
            externalScrubFraction = if (scrubUiState.isActive && scrubUiState.fromScreenGesture) {
                scrubUiState.progress
            } else {
                null
            },
            fineScrubEnabled = landscapePullUpSeekEnabled,
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
            onOpenQualityVideo = { onOpenQualityDialog(ReelsQualityDialogMode.VIDEO) },
            onOpenQualityDefault = { onOpenQualityDialog(ReelsQualityDialogMode.GLOBAL) },
            isCropMode = isCropMode,
            onToggleCropMode = {
                onScrubStart()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onToggleCropMode()
            },
            playbackSpeed = effectivePlaybackSpeed,
            onSetPlaybackSpeed = onUpdatePlaybackSpeed,
            isAutoAdvance = isAutoAdvance,
            onToggleAutoAdvance = onToggleAutoAdvance,
            landscapeBrightnessGestureEnabled = landscapeBrightnessGestureEnabled,
            onToggleLandscapeBrightnessGesture = onToggleLandscapeBrightnessGesture,
            landscapeVolumeGestureEnabled = landscapeVolumeGestureEnabled,
            onToggleLandscapeVolumeGesture = onToggleLandscapeVolumeGesture,
            landscapePullUpSeekEnabled = landscapePullUpSeekEnabled,
            onToggleLandscapePullUpSeek = onToggleLandscapePullUpSeek,
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
            onOpenHide = if (showHideAction) onOpenHideDialog else null,
            currentSourceName = sourceBadgeName,
            onOpenSource = onSourceBadgeClick,
            onExcludeSourceFromMix = onExcludeSourceFromMix,
            onClearVideoCache = onClearVideoCache,
            onSelectClip = { idx ->
                onCloseDrawer()
                onSelectPlaylistItem(idx)
            },
            onDismiss = onCloseDrawer,
        )
    }
}

@Composable
private fun BoxScope.ReelsPortraitControlsOverlay(
    item: ShortVideoItem,
    effectiveChromeVisible: Boolean,
    scrubUiState: ReelsScrubUiState,
    progressState: State<Float>,
    bufferFractionState: State<Float>,
    durationSec: Float,
    isLiked: Boolean,
    isMuted: Boolean,
    showFollowAction: Boolean,
    isFollowingCreator: Boolean,
    showHideAction: Boolean,
    showOfflineAction: Boolean,
    isOfflineStored: Boolean,
    landscape: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isPartial: Boolean,
    downloadSizeText: String?,
    downloadProgress: Float?,
    sourceBadgeName: String?,
    sourceBadgeIcon: ImageBitmap?,
    onToggleLike: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: (Float) -> Unit,
    onToggleFollowCreator: () -> Unit,
    onOpenHideDialog: () -> Unit,
    onToggleOffline: () -> Unit,
    onToggleLandscape: () -> Unit,
    onDownload: (() -> Unit)?,
    onTagClick: (String) -> Unit,
    onAuthorClick: (() -> Unit)?,
    authorDisplayLabel: String?,
    onCreatorClick: ((String) -> Unit)?,
    onSourceBadgeClick: (() -> Unit)?,
    onRequestSeek: (Float, Boolean) -> Unit,
    onScrubStart: () -> Unit,
    onScrubStateChange: (ReelsScrubUiState) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val navBarsBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Guarantee a safe margin of at least 26.dp even when system gesture bar is hidden (navBars == 0dp)
    val safeBottomInset = maxOf(navBarsBottomPadding, 26.dp)
    val controlsBottomPadding = safeBottomInset + 48.dp
    AnimatedVisibility(
        visible = effectiveChromeVisible,
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
            onShare = { onShare(progressState.value * durationSec.coerceAtLeast(0f)) },
            likesCount = item.likesCount,
            onOpenHide = if (showHideAction) onOpenHideDialog else null,
            onToggleOffline = if (showOfflineAction) onToggleOffline else null,
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
            modifier = Modifier.padding(end = 8.dp, bottom = controlsBottomPadding),
        )
    }

    // Portrait Bottom Meta info
    AnimatedVisibility(
        visible = effectiveChromeVisible && !scrubUiState.isActive,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        ReelsBottomMeta(
            item = item,
            onTagClick = onTagClick,
            onAuthorClick = onAuthorClick,
            authorDisplayLabel = authorDisplayLabel,
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
                    end = 80.dp,
                    bottom = controlsBottomPadding,
                ),
        )
    }

    // Portrait Interactive Scrubber / Seek Bar (Aurora style)
    AnimatedVisibility(
        visible = effectiveChromeVisible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        ReelsProgressBar(
            progressState = progressState,
            durationSec = durationSec,
            bufferFractionState = bufferFractionState,
            posterUrl = item.posterUrlVertical ?: item.posterUrl,
            chromeVisible = effectiveChromeVisible,
            onSeek = { frac -> onRequestSeek(frac, false) },
            onScrubPreview = { frac -> onRequestSeek(frac, true) },
            onScrubStart = onScrubStart,
            onScrubStateChange = onScrubStateChange,
            externalScrubFraction = if (scrubUiState.isActive && scrubUiState.fromScreenGesture) {
                scrubUiState.progress
            } else {
                null
            },
            modifier = Modifier
                .padding(bottom = safeBottomInset + 4.dp),
        )
    }
}

@Composable
private fun BoxScope.ReelsScrubOverlayHost(
    scrubUiState: ReelsScrubUiState,
    scrubThumbVersion: Int,
    scrubThumbnailCache: ReelsScrubThumbnailCache<ImageBitmap>,
    durationSec: Float,
    posterUrl: String?,
    isLandscape: Boolean,
    chromeVisible: Boolean,
    fineScrubEnabled: Boolean,
) {
    val previewFrame = remember(scrubThumbVersion, scrubUiState.progress, durationSec) {
        scrubThumbnailCache.getBestFrame(scrubUiState.progress, durationSec)
    }
    val filmstripFrames = remember(scrubThumbVersion, scrubUiState.progress, scrubUiState.isFineMode, durationSec) {
        if (scrubUiState.isFineMode && isLandscape) {
            val centerSec = scrubUiState.progress.coerceIn(0f, 1f) * durationSec.coerceAtLeast(0f)
            val halfWindow = (
                min(
                    durationSec.coerceAtLeast(12f),
                    ReelsFineScrubController.DEFAULT_FINE_WINDOW_SEC,
                ) / 2f
                ).coerceAtLeast(3f)
            scrubThumbnailCache.framesInWindow(centerSec, halfWindow)
        } else {
            emptyList()
        }
    }
    val portraitNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val portraitAnchorPadding = maxOf(portraitNavBottom, 26.dp) + 18.dp
    ReelsScrubPreviewOverlay(
        scrubState = scrubUiState,
        durationSec = durationSec,
        previewFrame = previewFrame,
        filmstripFrames = filmstripFrames,
        posterUrl = posterUrl,
        isLandscape = isLandscape,
        chromeVisible = chromeVisible,
        fineScrubEnabled = fineScrubEnabled,
        bottomAnchorPadding = if (isLandscape) 66.dp else portraitAnchorPadding,
    )
}

@Composable
internal fun ReelsHideVideoDialog(
    onDismiss: () -> Unit,
    onHideVideo: () -> Unit,
    onHideAuthor: (() -> Unit)?,
    sourceBadgeName: String?,
    onSourceBadgeClick: (() -> Unit)?,
    onExcludeSourceFromMix: (() -> Unit)?,
) {
    AuroraFrostDialog(
        onDismiss = onDismiss,
        title = stringResource(MR.strings.reels_hide_dialog_title),
        footer = {
            AuroraFrostCancel(
                label = stringResource(MR.strings.action_cancel),
                onClick = onDismiss,
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
                        onDismiss()
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
                            onDismiss()
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
                            onDismiss()
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
                            onDismiss()
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

@Composable
private fun ReelsZoomPresetChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Color.Black else Color.White.copy(alpha = 0.9f),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum",
        ),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent else Color.White.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

internal const val REELS_HOLD_SLOW_RATE = 0.5f
internal const val REELS_HOLD_FAST_RATE = 2f
private const val HOLD_TAP_SUPPRESS_MS = 160L

private data class HeartBurst(
    val id: Long,
    val position: Offset,
    val rotationDeg: Float,
)

@Composable
private fun HeartBurstOverlay(
    burst: HeartBurst,
    tint: Color,
    onFinished: () -> Unit,
) {
    val scale = remember { Animatable(0.2f) }
    val alpha = remember { Animatable(1f) }
    val risePx = remember { Animatable(0f) }
    val iconSizePx = with(LocalDensity.current) { 96.dp.roundToPx() }

    LaunchedEffect(burst.id) {
        launch {
            scale.animateTo(
                targetValue = 1.25f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
            scale.animateTo(1.05f, tween(120, easing = FastOutSlowInEasing))
        }
        launch {
            delay(260)
            launch { risePx.animateTo(-72f, tween(340, easing = FastOutSlowInEasing)) }
            alpha.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
            onFinished()
        }
    }

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (burst.position.x - iconSizePx / 2f).roundToInt(),
                    y = (burst.position.y - iconSizePx / 2f + risePx.value).roundToInt(),
                )
            }
            .size(96.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = burst.rotationDeg
                this.alpha = alpha.value
            },
    )
}

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

package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.CustomFeedRef
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.animesource.model.SearchSuggestionKind
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreenModel.OfflineCopyResult
import eu.kanade.tachiyomi.ui.reels.components.CfBootstrapWebView
import eu.kanade.tachiyomi.ui.reels.components.ReelsBlockedTagsSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsContentPreferencesSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsCustomFeedsSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsEmptySearchState
import eu.kanade.tachiyomi.ui.reels.components.ReelsErrorState
import eu.kanade.tachiyomi.ui.reels.components.ReelsLoginDialog
import eu.kanade.tachiyomi.ui.reels.components.ReelsNextPageLoader
import eu.kanade.tachiyomi.ui.reels.components.ReelsSourcePickerSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsTopBar
import eu.kanade.tachiyomi.ui.reels.components.ReelsVideoPage
import eu.kanade.tachiyomi.ui.reels.components.ReelsWebLoginDialog
import eu.kanade.tachiyomi.ui.reels.player.clearReelsVideoCache
import eu.kanade.tachiyomi.ui.reels.player.prewarmReelHead
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import androidx.compose.foundation.lazy.grid.items as gridItems

data class ReelsFeedScreen(
    val sourceId: Long,
    // Offline playlist mode (opened from the Favorites screen). Only lightweight seek/sort
    // hints are stored here: Voyager Java-serializes every stacked Screen into the saved
    // state, and carrying the full favorites list blew the binder parcel
    // (TransactionTooLargeException). The model reloads the playlist from the favorites DB.
    val offlinePlaylist: Boolean = false,
    val playlistSort: FavoritesSort = FavoritesSort.DateDesc,
    val initialVideoId: String? = null,
    // Contract v18: one creator's page (creator != null) or the aggregated Following feed
    // (followingFeed = true). Mutually exclusive; never combined with offline playlists.
    val creator: String? = null,
    val followingFeed: Boolean = false,
    // Contract v19: one custom feed's page (customFeedId != null). Mutually exclusive with
    // creator/following/offline playlists.
    val customFeedId: String? = null,
    val customFeedName: String? = null,
    // Contract v20: one category's feed (nicheId != null). Mutually exclusive with the modes
    // above and offline playlists.
    val nicheId: String? = null,
    val nicheName: String? = null,
    // B3.1: resume the history position of this clip (best effort; the clip must be served by
    // the loaded feed pages).
    val resumeVideoId: String? = null,
) : Screen {
    // Voyager disposes screens (and their ScreenModels) by screen.key. The default key is only
    // the class name, so a popped offline playlist would share the live feed's key and never be
    // disposed -> models accumulate in ScreenModelStore. A deterministic content-based key makes
    // each pushed playlist unique so it is correctly disposed on pop. Do NOT use a random UUID
    // (the saveable state would orphan models across recreation). Creator and Following pages
    // ride the same rule: their keys must include creator/followingFeed.
    override val key: String
        get() = "ReelsFeedScreen:$sourceId:$offlinePlaylist:$playlistSort:$initialVideoId" +
            ":$creator:$followingFeed:$customFeedId:$nicheId:$resumeVideoId"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        // Reels are a portrait-first experience. Nested feeds overlap during Voyager transitions
        // (the incoming screen composes BEFORE the outgoing disposes), so the lock is a shared
        // holder count: the original orientation is restored only after the LAST feed leaves.
        DisposableEffect(Unit) {
            val activity = context as? Activity
            if (activity != null) {
                ReelsOrientationLock.acquire(activity)
            }
            onDispose {
                if (activity != null) {
                    ReelsOrientationLock.release(activity)
                }
            }
        }
        // The screen's content-based `key` (see above) already distinguishes the live feed from
        // an offline playlist, so a plain rememberScreenModel yields the right model per screen
        // and Voyager disposes each correctly on pop.
        val screenModel = rememberScreenModel {
            ReelsFeedScreenModel(
                initialSourceId = sourceId,
                offlinePlaylist = offlinePlaylist,
                playlistSort = playlistSort,
                initialVideoId = initialVideoId,
                creator = creator,
                followingFeed = followingFeed,
                customFeedId = customFeedId,
                customFeedName = customFeedName,
                nicheId = nicheId,
                nicheName = nicheName,
                resumeVideoId = resumeVideoId,
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }
        var pendingDeleteFeed by remember { mutableStateOf<CustomFeedRef?>(null) }
        // "Reset For You history" is destructive (watched reels become recommendable again),
        // so it is always confirmed. Explicit hidden/blacklist entries are NOT touched.
        var pendingResetHistory by remember { mutableStateOf(false) }
        val retryLabel = stringResource(MR.strings.action_retry)
        // Foreground gate for watch qualification: playback that keeps running while the app
        // is backgrounded (audio-only, PiP hand-off) must never earn "watched" credit.
        val lifecycleOwner = LocalLifecycleOwner.current
        var isForeground by remember(lifecycleOwner) {
            mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> isForeground = true
                    Lifecycle.Event.ON_PAUSE -> {
                        isForeground = false
                        screenModel.onForegroundLost()
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        // Audit H9: incognito must be VISIBLE — every like/follow/history write for this
        // source is silently suppressed while it is on. Reels owns its incognito: only the
        // reels-only toggle applies here — the global switch, the NSFW auto policy and the
        // per-extension set deliberately do not (product decision).
        val incognitoFlow = remember(state.isOffline) {
            if (state.isOffline) {
                flowOf(false)
            } else {
                Injekt.get<SourcePreferences>().reelsIncognitoMode().changes()
            }
        }
        val isIncognito by incognitoFlow.collectAsStateWithLifecycle(false)
        // B2: the More-menu row shows the live countdown (mm:ss), the armed end-of-video
        // mode, or Off.
        val sleepTimerValue = when {
            state.sleepAtVideoEnd -> stringResource(MR.strings.reels_sleep_timer_end_video)
            state.sleepTimerRemainingSec > 0 -> String.format(
                java.util.Locale.getDefault(),
                "%d:%02d",
                state.sleepTimerRemainingSec / 60,
                state.sleepTimerRemainingSec % 60,
            )
            else -> stringResource(MR.strings.reels_off_short)
        }
        // Contract v23: album-capable sources answer search with an album directory — the
        // screen renders album cards instead of the video pager for such queries.
        val albumMode = state.albumSearchCapable && state.searchQuery.isNotBlank()
        // Audit H8: the offline-copy quota usage, refreshed on entry and after every change.
        var offlineUsedBytes by remember { mutableLongStateOf(0L) }
        // Double-tap guard: one download/removal at a time (the store survives races, but a
        // second concurrent download of the same reel would burn traffic for nothing).
        var offlineBusy by remember { mutableStateOf(false) }
        var confirmClearOffline by remember { mutableStateOf(false) }
        // B3 touch lock (Just Player pattern): blocks swipes/taps/gestures; long-press on the
        // full-screen overlay unlocks. Session state — deliberately not persisted.
        var touchLocked by rememberSaveable { mutableStateOf(false) }
        // Device UX (sign-off): transient network failures must not stack snackbars over a
        // playing feed — the same message is coalesced within a cooldown window.
        var lastErrorSnackMsg by remember { mutableStateOf("") }
        var lastErrorSnackAt by remember { mutableLongStateOf(0L) }
        fun showErrorSnackbarOnce(message: String): Boolean {
            val now = SystemClock.elapsedRealtime()
            if (message == lastErrorSnackMsg && now - lastErrorSnackAt < ERROR_SNACK_COOLDOWN_MS) {
                return false
            }
            lastErrorSnackMsg = message
            lastErrorSnackAt = now
            return true
        }
        LaunchedEffect(Unit) { offlineUsedBytes = screenModel.offlineUsedBytes() }

        fun handleFollowToggle(creatorName: String?) {
            if (creatorName == null) return
            screenModel.toggleFollow(creatorName)
        }
        // B3.2: the hide snackbar offers an undo of the most recent hide (video or author).
        val hideSnackbarMessage = stringResource(MR.strings.reels_hidden_snackbar)
        val offlineSavedMessage = stringResource(MR.strings.reels_offline_saved)
        val offlineRemovedMessage = stringResource(MR.strings.reels_offline_removed)
        val offlineQuotaMessage = stringResource(MR.strings.reels_offline_failed_quota)
        val offlineNetworkMessage = stringResource(MR.strings.reels_offline_failed_network)
        val offlineUnsupportedMessage = stringResource(MR.strings.reels_offline_failed_unsupported)
        val undoLabel = stringResource(MR.strings.action_undo)
        fun notifyHidden() {
            // Capture THIS hide's token: with two rapid hides the older snackbar's undo must
            // not roll back the newer decision (single-slot lastHide).
            val token = screenModel.lastHideToken()
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = hideSnackbarMessage,
                    actionLabel = undoLabel,
                )
                if (result == SnackbarResult.ActionPerformed) screenModel.undoHide(token)
            }
        }
        // Preload gating must be reactive: a plain context call here would be recomputed
        // (stale) on every recomposition instead of tracking network changes. Transport
        // (Wi-Fi) and cost (metered) are tracked separately — a metered hotspot is not
        // "Wi-Fi" for the data saver, and an unmetered Ethernet is not metered.
        var networkStatus by remember { mutableStateOf(context.reelsNetworkStatus()) }
        DisposableEffect(Unit) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    networkStatus = networkCapabilities.toReelsNetworkStatus()
                }

                override fun onLost(network: Network) {
                    // The default network went away: a stale "on Wi-Fi" would let the
                    // wifi-gated preload keep running over whatever network comes next, and
                    // an unknown network must count as metered for the data saver.
                    networkStatus = ReelsNetworkStatus(onWifi = false, metered = true)
                }

                override fun onAvailable(network: Network) {
                    networkStatus = context.reelsNetworkStatus()
                }
            }
            cm?.registerDefaultNetworkCallback(callback)
            onDispose {
                cm?.unregisterNetworkCallback(callback)
            }
        }
        val preloadAllowed = reelsPreloadAllowed(
            preloadEnabled = state.preloadEnabled,
            preloadWifiOnly = state.preloadWifiOnly,
            onWifi = networkStatus.onWifi,
        )
        // Data saver: force SD on metered networks. The page pins this value at activation,
        // so playback never rebuilds mid-clip when connectivity changes.
        val effectiveHd = if (reelsForcesSd(state.dataSaverMetered, networkStatus.metered)) {
            false
        } else {
            state.isHdQuality
        }
        // U02: software fullscreen rotates the page subtree instead of requesting an
        // orientation. A window that is already landscape (split-screen/tablet/foldable)
        // or a system-owned PiP frame must not rotate at all.
        val windowConfiguration = LocalConfiguration.current
        val windowIsLandscape = reelsWindowIsLandscape(
            windowConfiguration.screenWidthDp,
            windowConfiguration.screenHeightDp,
        )
        val inPictureInPicture = ReelsPip.isInPictureInPictureMode
        // The active reel's orientation drives the PiP aspect ratio. Values are kept per
        // item: a preloaded neighbor may report before it settles into the active page.
        val videoOrientations = remember { mutableStateMapOf<String, Boolean>() }
        var activeVideoLandscape by remember { mutableStateOf<Boolean?>(null) }
        val currentActiveVideoLandscape by rememberUpdatedState(activeVideoLandscape)
        // Measured frame dimensions per item (audit C03): the PiP window shape comes from the
        // actual video rect/aspect, not from the portrait feed window.
        val videoSizes = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
        var activeVideoSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val currentActiveVideoSize by rememberUpdatedState(activeVideoSize)
        var chromeVisible by remember { mutableStateOf(true) }
        // Device UX: the ⋮ settings block holds playback and pins the chrome while open.
        var moreMenuOpen by remember { mutableStateOf(false) }
        // Landscape fullscreen (software-rotated overlay, see ReelsVideoPage): entered from
        // the expand button on landscape reels, left via the button or system back. The intent
        // is sticky: an auto-exit on a portrait reel keeps it armed so the next landscape reel
        // re-enters fullscreen on its own; a manual exit disarms it.
        var landscapeFullscreen by remember { mutableStateOf(false) }
        var landscapeAutoRestore by remember { mutableStateOf(false) }
        // U02: the portrait lock pins Display.rotation, so the physical hold side comes from
        // gravity. The sensor is registered only while the rotated frame is actually used.
        val landscapeRotation = rememberReelsLandscapeRotation(
            enabled = landscapeFullscreen && !windowIsLandscape && !inPictureInPicture,
        )
        val softwareRotationDegrees = reelsSoftwareRotationDegrees(
            landscapeRotation = landscapeRotation,
            windowIsLandscape = windowIsLandscape,
            inPictureInPicture = inPictureInPicture,
        )
        // The HOME-gesture handler runs outside composition: these refs keep the PiP params
        // and the chrome decision tied to the live frame, crop mode and rotation.
        val currentCropMode by rememberUpdatedState(state.isCropMode)
        val currentSoftwareRotation by rememberUpdatedState(softwareRotationDegrees)
        // U07: while software fullscreen is active, the window belongs to the video —
        // MainActivity's generic navigation-bar scrim must not paint over it.
        val fullscreenOwner = remember { Any() }
        DisposableEffect(fullscreenOwner) {
            onDispose { ReelsFullscreen.release(fullscreenOwner) }
        }
        BackHandler(enabled = landscapeFullscreen) {
            landscapeFullscreen = false
            landscapeAutoRestore = false
        }
        // Back closes the open search bar before leaving the feed (popup sheets and dialogs
        // own their dismiss handling already).
        BackHandler(enabled = !landscapeFullscreen && state.isSearchBarOpen) {
            screenModel.toggleSearchBar(false)
        }
        // Report fix: with an active search result (album cards or filtered feed), back
        // cancels the search and restores the previous feed instead of leaving Reels.
        BackHandler(
            enabled = !landscapeFullscreen && !state.isSearchBarOpen && state.searchQuery.isNotBlank(),
        ) {
            screenModel.clearSearch()
        }

        // Hide the system bars while the rotated video owns the screen; restore on exit
        // and on screen disposal so the rest of the app is unaffected.
        DisposableEffect(landscapeFullscreen, fullscreenOwner) {
            ReelsFullscreen.set(fullscreenOwner, landscapeFullscreen)
            val window = (context as? Activity)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            if (landscapeFullscreen && controller != null) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }

        // A reel that is actually playing keeps the screen awake (auto-advance must not fall
        // asleep mid-feed). The gate uses the player-reported state, not the play intent:
        // an audio-focus pause or a background transition must release the flag; a paused
        // reel lets the system timeout apply — same as the video player.
        DisposableEffect(state.isActuallyPlaying) {
            val window = (context as? Activity)?.window
            if (state.isActuallyPlaying) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                // Owner check (audit H1): on a feed→feed transition the incoming screen has
                // already re-added the flag; only clear it when leaving the reels surface
                // for good, otherwise the nested feed's screen goes dark mid-playback.
                if (navigator.lastItem !is ReelsFeedScreen) {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        // Declutter redesign: PiP enters via the HOME gesture, not a top-bar button. The feed
        // registers the entry handler while composed; MainActivity calls it from
        // onUserLeaveHint. Gates are evaluated at call time: feature toggle on, a reel is
        // actually playing (player-reported), and this is not the offline playlist variant.
        fun buildPipParams(activity: Activity): PictureInPictureParams = ReelsPip.buildParams(
            activity = activity,
            videoWidth = currentActiveVideoSize?.first ?: 0,
            videoHeight = currentActiveVideoSize?.second ?: 0,
            cropMode = currentCropMode,
            landscapeFallback = currentActiveVideoLandscape,
            rotated = currentSoftwareRotation != 0,
        )
        DisposableEffect(screenModel) {
            val handler: () -> Boolean = {
                val current = screenModel.state.value
                val activity = context as? Activity
                if (activity != null && current.isPipEnabled && current.isActuallyPlaying && !current.isOffline) {
                    val entered = runCatching {
                        activity.enterPictureInPictureMode(buildPipParams(activity))
                    }.getOrDefault(false)
                    // Hide the immersive chrome with the entry, before the system snapshots
                    // the surface: the PiP window must show the reel, not the feed's top bar.
                    if (entered) chromeVisible = false
                    entered
                } else {
                    false
                }
            }
            ReelsPip.handler = handler
            onDispose {
                // Owner check (audit H1): the incoming feed screen registers its own handler
                // BEFORE this disposal runs — nulling unconditionally would kill PiP for the
                // visible feed after any feed→feed navigation.
                if (ReelsPip.handler === handler) {
                    ReelsPip.handler = null
                }
            }
        }

        // Auto-advance keeps running in PiP: when the settled reel (or crop mode/rotation)
        // changes, update the system window shape instead of leaving the old cover ratio.
        LaunchedEffect(inPictureInPicture, activeVideoSize, state.isCropMode, softwareRotationDegrees) {
            if (!inPictureInPicture) return@LaunchedEffect
            val activity = context as? Activity ?: return@LaunchedEffect
            runCatching { activity.setPictureInPictureParams(buildPipParams(activity)) }
        }

        // B1: a rate-limit verdict surfaces the wait; the model schedules the capped auto-retry.
        LaunchedEffect(state.retryAfterSec) {
            if (state.retryAfterSec > 0) {
                snackbarHostState.showSnackbar(
                    context.stringResource(MR.strings.reels_rate_limited, state.retryAfterSec),
                )
            }
        }

        // Immersive: auto-hide the top bar after 3s of ACTUAL playback; any tap reveals it.
        // Device report: gating on the play INTENT (state.isPlaying, true by default) hid the
        // chrome while the feed was errored or stalled — with nothing playing the user could
        // not reach retry or the source switcher and had to re-enter the screen, racing the
        // next hide. Only hide while a reel really plays and the feed is healthy.
        LaunchedEffect(
            chromeVisible,
            state.isActuallyPlaying,
            state.isLoading,
            state.error,
            state.errorRes,
            state.items.size,
            moreMenuOpen,
        ) {
            // Device UX: an open settings block never auto-hides the chrome (the hide timer
            // must not race the user reading the menu); closing restarts the countdown.
            if (moreMenuOpen) return@LaunchedEffect
            if (
                reelsChromeShouldAutoHide(
                    visible = chromeVisible,
                    actuallyPlaying = state.isActuallyPlaying,
                    hasItems = state.items.isNotEmpty(),
                    hasError = state.error != null || state.errorRes != null,
                    isLoading = state.isLoading,
                )
            ) {
                delay(3000)
                chromeVisible = false
            }
        }

        // Device UX: opening the ⋮ settings block pauses playback and pins the chrome;
        // closing resumes playback (unless the user paused deliberately) and the auto-hide
        // countdown starts again on the next effect pass.
        LaunchedEffect(moreMenuOpen) {
            screenModel.setSettingsHold(moreMenuOpen)
            if (moreMenuOpen) chromeVisible = true
        }

        // A fresh full-feed error surfaces the chrome immediately: retry and the source
        // switcher must be reachable without guessing taps on a hidden bar.
        LaunchedEffect(state.error, state.errorRes) {
            if ((state.error != null || state.errorRes != null) && state.items.isEmpty()) {
                chromeVisible = true
            }
        }

        // The unmute hint disappears on its own after 6 s; a later re-entry shows it again
        // while the session stays undecided.
        LaunchedEffect(state.showUnmuteHint) {
            if (state.showUnmuteHint) {
                delay(6000)
                screenModel.dismissUnmuteHint()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            when {
                albumMode -> AlbumSearchResults(
                    state = state,
                    onOpenAlbum = { category ->
                        navigator.push(
                            ReelsAlbumScreen(
                                sourceId = state.currentSourceId,
                                albumId = category.id,
                                albumName = category.name,
                            ),
                        )
                    },
                    onToggleSave = { category -> screenModel.toggleAlbumSaved(category) },
                    onPreview = { category -> screenModel.loadAlbumPreview(category.id) },
                    onLoadMore = { screenModel.loadFeed() },
                    onResetSearch = screenModel::clearSearch,
                )
                state.isLoading && state.items.isEmpty() -> {
                    LoadingScreen(modifier = Modifier.fillMaxSize())
                }
                (state.error != null || state.errorRes != null) && state.items.isEmpty() && !state.isLoading -> {
                    ReelsErrorState(
                        // The FOLLOWING all-failed summary is a localized count, not the raw
                        // "'creator': msg; …" concatenation (audit H11); detail stays in logs.
                        message = state.errorCounts?.let { (failed, total) ->
                            context.stringResource(
                                MR.strings.reels_following_creators_unavailable,
                                failed,
                                total,
                            )
                        } ?: state.error.orEmpty(),
                        stringRes = state.errorRes,
                        onRetry = {
                            // Audit H5: with a rejected source (source=null) a plain loadFeed
                            // retry was a silent no-op. GLOBAL opens the source picker instead;
                            // fixed modes (creator/custom/niche) have nowhere to switch — back.
                            if (!screenModel.retryFromError()) navigator.pop()
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.items.isEmpty() && !state.isLoading && state.searchQuery.isNotBlank() -> {
                    ReelsEmptySearchState(
                        onResetSearch = screenModel::clearSearch,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.items.isEmpty() && !state.isLoading -> {
                    if (state.mode == ReelsFeedScreenModel.FeedMode.FOLLOWING) {
                        // Following with zero subscriptions: give the user the way in instead
                        // of a bare empty state.
                        EmptyScreen(
                            stringRes = MR.strings.reels_follows_empty,
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.reels_manage_follows,
                                    icon = Icons.Filled.People,
                                    onClick = {
                                        navigator.push(ReelsFollowsScreen(state.currentSourceId))
                                    },
                                ),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (
                        state.unseenOnly &&
                        state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL &&
                        state.searchQuery.isBlank() &&
                        !state.isOffline
                    ) {
                        // For You has nothing UNWATCHED left (or the bounded scan stopped).
                        // "Show watched" is an explicit mode switch, never a history wipe.
                        // Search and the offline playlist are explicit surfaces and keep the
                        // neutral "nothing here" copy: their emptiness has nothing to do with
                        // the watch history.
                        EmptyScreen(
                            stringRes = if (state.unseenScanStopped) {
                                MR.strings.reels_feed_empty_unseen_scanning
                            } else {
                                MR.strings.reels_feed_empty_unseen
                            },
                            actions = persistentListOf(
                                EmptyScreenAction(
                                    stringRes = MR.strings.reels_feed_show_watched,
                                    icon = Icons.Filled.PlayArrow,
                                    onClick = { screenModel.setUnseenOnly(false) },
                                ),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Audit H11: "No source found" was semantically wrong — the source IS
                        // there, it just served nothing (or everything is filtered/hidden).
                        EmptyScreen(
                            stringRes = MR.strings.reels_feed_empty,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = state.targetPageIndex.coerceIn(
                            0,
                            (state.items.size - 1).coerceAtLeast(0),
                        ),
                        pageCount = { state.items.size },
                    )

                    // PiP shape follows the settled reel, including one whose player already
                    // reported its frame while the page was only preloaded.
                    LaunchedEffect(pagerState.settledPage, state.items) {
                        val settledId = state.items.getOrNull(pagerState.settledPage)?.id
                        activeVideoLandscape = settledId?.let(videoOrientations::get)
                        activeVideoSize = settledId?.let(videoSizes::get)
                    }

                    // C-intermediate (audit): heads of the reels BEYOND the player preload window
                    // (+2..+4) are warmed into the video cache with plain bytes — no player, no
                    // decoder — so a deep swipe starts from disk instead of a cold CDN round-trip.
                    // The forward player preload intentionally stays at one neighbor (three live
                    // decoders competed for bandwidth). Same gate as the player preload and the
                    // same cache-key scheme as the page; a key mismatch (a quality toggle
                    // mid-flight) can only cost a cache miss, never corrupt.
                    LaunchedEffect(pagerState.settledPage, state.items, preloadAllowed, effectiveHd) {
                        if (!preloadAllowed) return@LaunchedEffect
                        val settled = pagerState.settledPage
                        val items = state.items
                        val offline = state.isOffline
                        val offlineSourceIds = state.offlineSourceIds
                        val sourceId = state.currentSourceId
                        val headers = state.sourceHeaders
                        withContext(Dispatchers.IO) {
                            // The effect's job dies on a settled-page/source/quality change; a
                            // prewarm run must stop pulling bytes instead of warming stale pages.
                            val prewarmJob = coroutineContext[Job]
                            for (index in settled + 2..settled + 4) {
                                if (prewarmJob?.isActive == false) break
                                val item = items.getOrNull(index) ?: break
                                val url = if (effectiveHd) item.videoUrlHd ?: item.videoUrl else item.videoUrl
                                val prefix = if (offline) {
                                    offlineSourceIds.getOrNull(index)?.toString() ?: "offline"
                                } else {
                                    sourceId.toString()
                                }
                                prewarmReelHead(
                                    context = context,
                                    url = url,
                                    cacheKey = "$prefix:${item.id}:${if (effectiveHd) "hd" else "sd"}",
                                    headers = headers,
                                    webUrl = item.webUrl,
                                    isCancelled = { prewarmJob?.isActive == false },
                                )
                            }
                        }
                    }

                    // Any full feed replacement (search / filters / source switch) bumps the
                    // generation; scroll to the requested page (0 on fresh loads, the remembered
                    // position when a cleared search restores the base feed). The applied
                    // generation is saved so re-entering composition (rotation, push/pop)
                    // does not reset the user's position by re-applying a stale target.
                    var appliedGeneration by rememberSaveable { mutableIntStateOf(-1) }
                    LaunchedEffect(state.feedGeneration) {
                        if (state.feedGeneration != appliedGeneration) {
                            appliedGeneration = state.feedGeneration
                            if (pagerState.pageCount > 0) {
                                pagerState.scrollToPage(
                                    state.targetPageIndex.coerceIn(0, pagerState.pageCount - 1),
                                )
                            }
                        }
                    }

                    // Passive gesture observer: `settledPage` alone cannot tell a deliberate
                    // swipe from an auto-advance/restore. This records WHEN a real drag moved
                    // the pager; it never consumes events, so the pager keeps full control.
                    val lastUserNavAtMs = remember(pagerState) { AtomicLong(0L) }

                    LaunchedEffect(pagerState) {
                        var previous = pagerState.settledPage
                        snapshotFlow { pagerState.settledPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                val from = previous
                                previous = page
                                val cause = ReelsForYou.resolveTransitionCause(
                                    fromPage = from,
                                    toPage = page,
                                    nowMs = SystemClock.elapsedRealtime(),
                                    lastUserNavAtMs = lastUserNavAtMs.get(),
                                )
                                screenModel.onPageChanged(page, cause)
                            }
                    }

                    // The settled page only changes after the fling ends. The runway needs the
                    // live page during the fling so the next page can already be in flight.
                    LaunchedEffect(pagerState) {
                        snapshotFlow { pagerState.currentPage }
                            .distinctUntilChanged()
                            .collect { page ->
                                screenModel.onPagerPositionChanged(page)
                            }
                    }

                    // Mid-feed append failures keep the feed usable; surface them transiently
                    // instead of replacing the whole screen with the error state.
                    val genericLoadError = stringResource(MR.strings.reels_feed_load_failed)
                    LaunchedEffect(state.pageError) {
                        state.pageError?.let { message ->
                            val text = message.ifBlank { genericLoadError }
                            if (showErrorSnackbarOnce(text)) {
                                snackbarHostState.showSnackbar(text)
                            }
                            screenModel.onPageErrorShown()
                        }
                    }

                    // Horizontal swipe switches feeds: left on the global feed opens this
                    // source's Following feed, right on Following pops back. The vertical
                    // pager owns the orthogonal axis; landscape fullscreen ignores swipes.
                    val feedSwipeEnabled = !landscapeFullscreen &&
                        (
                            followingFeed ||
                                (state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL && state.isCreatorCapable)
                            )
                    val swipeThresholdPx = with(LocalDensity.current) { 110.dp.toPx() }
                    var horizontalDragPx by remember { mutableFloatStateOf(0f) }

                    // Vertical Pager for reels video cards
                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        // Pages belong to the media identity, not the slot: after a feed reset
                        // an index can point at an entirely different reel, and per-page state
                        // (gesture captures, progress, speed) must not leak across. The offline
                        // playlist merges favorites from several sources, so its key includes
                        // the source — the same videoId may legitimately repeat there.
                        key = { index ->
                            val item = state.items.getOrNull(index)
                            val offlineSourceId = state.offlineSourceIds.getOrNull(index)
                            when {
                                item == null -> index
                                state.isOffline && offlineSourceId != null -> "$offlineSourceId:${item.id}"
                                else -> item.id
                            }
                        },
                        // The pager lives outside the software rotation, so manual swipes in
                        // landscape fullscreen would land sideways; auto-advance still moves
                        // the feed programmatically (portrait reels exit fullscreen, see
                        // ReelsVideoPage).
                        userScrollEnabled = !landscapeFullscreen && !touchLocked,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pagerState) {
                                // Observe-only: reports a completed vertical drag. The page is
                                // NOT required to have moved yet: a fling releases the finger
                                // while the pager is still on the origin page, and that release
                                // is exactly the "the user decided" signal the skip path needs.
                                // A drag that snaps back never changes settledPage, so it still
                                // records nothing (FY04: no skip).
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    var dragged = false
                                    drag(down.id) { change ->
                                        if (abs(change.positionChange().y) > 0f) dragged = true
                                    }
                                    if (dragged) lastUserNavAtMs.set(SystemClock.elapsedRealtime())
                                }
                            }
                            .pointerInput(feedSwipeEnabled, swipeThresholdPx) {
                                if (!feedSwipeEnabled) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDragPx = 0f },
                                    onDragCancel = { horizontalDragPx = 0f },
                                    onDragEnd = {
                                        when {
                                            horizontalDragPx <= -swipeThresholdPx && !followingFeed ->
                                                navigator.push(
                                                    ReelsFeedScreen(
                                                        sourceId = state.currentSourceId,
                                                        followingFeed = true,
                                                    ),
                                                )
                                            horizontalDragPx >= swipeThresholdPx && followingFeed ->
                                                navigator.pop()
                                        }
                                        horizontalDragPx = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    horizontalDragPx += dragAmount
                                }
                            },
                    ) { page ->
                        val item = state.items.getOrNull(page)
                        if (item != null) {
                            // Contract v25: the source's per-item media verdict; remembered
                            // per item so re-composition does not re-run plugin code.
                            val mediaDescriptor = remember(item) { screenModel.mediaDescriptor(item) }
                            ReelsVideoPage(
                                item = item,
                                // Activate only the settled page: during fast flings intermediate
                                // pages must not spin up a player and start downloading.
                                isActive = (page == pagerState.settledPage),
                                // Preload only the forward neighbor: buffering both neighbors
                                // kept up to three decoders alive and competed for bandwidth.
                                // A back-swipe replays quickly from the disk cache instead.
                                isPreload = preloadAllowed && page == pagerState.settledPage + 1,
                                isPlaying = state.isPlaying,
                                isMuted = state.isMuted,
                                isLiked = state.isLiked(item.id, page),
                                isHdQuality = effectiveHd,
                                isAutoAdvance = state.isAutoAdvance,
                                isCropMode = state.isCropMode,
                                chromeVisible = chromeVisible && page == pagerState.settledPage && !landscapeFullscreen,
                                isLandscapeFullscreen = landscapeFullscreen,
                                softwareRotationDegrees = softwareRotationDegrees,
                                isInPictureInPicture = inPictureInPicture,
                                onVideoLandscapeKnown = { landscape ->
                                    videoOrientations[item.id] = landscape
                                    if (page == pagerState.settledPage) {
                                        activeVideoLandscape = landscape
                                    }
                                },
                                onVideoSizeKnown = { width, height ->
                                    videoSizes[item.id] = width to height
                                    if (page == pagerState.settledPage) {
                                        activeVideoSize = width to height
                                    }
                                },
                                landscapeAutoRestore = landscapeAutoRestore,
                                onLandscapeFullscreenChange = { enter ->
                                    landscapeFullscreen = enter
                                    landscapeAutoRestore = enter
                                },
                                onLandscapeAutoExit = { landscapeFullscreen = false },
                                onTogglePlayPause = {
                                    chromeVisible = true
                                    screenModel.togglePlayPause()
                                },
                                onToggleLike = { screenModel.toggleLike(item, page) },
                                // Double-tap is idempotent: a second tap must not unlike.
                                onLike = { screenModel.setLike(item, true, page) },
                                onToggleMute = screenModel::toggleMute,
                                onShare = { secondsPlayed ->
                                    // Prefer the watch page URL; raw CDN links can expire.
                                    // B3.6: append a timeline query so the receiver jumps to
                                    // the shared moment (site-agnostic &t= fallback).
                                    val baseUrl = item.webUrl ?: item.videoUrl
                                    val shareUrl = if (secondsPlayed >= 1f) {
                                        // The query must land BEFORE any '#' fragment: "site/x#y"
                                        // becomes "site/x?t=30s#y", not the invalid "site/x#y?t=30s".
                                        val hash = baseUrl.indexOf('#')
                                        val core = if (hash >= 0) baseUrl.substring(0, hash) else baseUrl
                                        val fragment = if (hash >= 0) baseUrl.substring(hash) else ""
                                        val sep = if (core.contains('?')) "&" else "?"
                                        "$core${sep}t=${secondsPlayed.toInt()}s$fragment"
                                    } else {
                                        baseUrl
                                    }
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, null))
                                },
                                onTagClick = { tag ->
                                    screenModel.search(tag)
                                },
                                // B3.2 "not interested": local hide + undo snackbar (live feeds only).
                                showHideAction = !state.isOffline,
                                onHideVideo = {
                                    screenModel.hideVideo(item)
                                    notifyHidden()
                                },
                                onHideAuthor = item.author?.let { author ->
                                    {
                                        screenModel.hideAuthor(author)
                                        notifyHidden()
                                    }
                                },
                                // B3.3 offline copy: shown only in the offline playlist.
                                showOfflineAction = state.isOffline,
                                isOfflineStored = state.offlineSourceIds.getOrNull(page)
                                    ?.let { "$it:${item.id}" } in state.offlineStored,
                                onToggleOffline = {
                                    if (!offlineBusy) {
                                        offlineBusy = true
                                        coroutineScope.launch {
                                            try {
                                                val result = screenModel.toggleOfflineCopy(item, page)
                                                offlineUsedBytes = screenModel.offlineUsedBytes()
                                                val message = when (result) {
                                                    OfflineCopyResult.SAVED -> offlineSavedMessage
                                                    OfflineCopyResult.REMOVED -> offlineRemovedMessage
                                                    OfflineCopyResult.FAILED_QUOTA -> offlineQuotaMessage
                                                    OfflineCopyResult.FAILED_NETWORK -> offlineNetworkMessage
                                                    OfflineCopyResult.FAILED_UNSUPPORTED -> offlineUnsupportedMessage
                                                }
                                                snackbarHostState.showSnackbar(message)
                                            } finally {
                                                offlineBusy = false
                                            }
                                        }
                                    }
                                },
                                // Contract v18 creator surfaces: capability + author gated.
                                showFollowAction = state.isCreatorCapable && !state.isOffline && item.author != null,
                                isFollowingCreator = item.author?.let { it in state.followingCreators } == true,
                                onToggleFollowCreator = { handleFollowToggle(item.author) },
                                onAuthorClick = if (
                                    state.isCreatorCapable && !state.isOffline && item.author != null &&
                                    // Already on that creator's page: self-push would only stack
                                    // a duplicate screen.
                                    !(
                                        state.mode == ReelsFeedScreenModel.FeedMode.CREATOR &&
                                            item.author == state.creator
                                        )
                                ) {
                                    {
                                        navigator.push(
                                            ReelsFeedScreen(
                                                sourceId = state.currentSourceId,
                                                creator = item.author,
                                            ),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onCreatorClick = if (state.isCreatorCapable && !state.isOffline) {
                                    { creator ->
                                        if (!(
                                                state.mode == ReelsFeedScreenModel.FeedMode.CREATOR &&
                                                    creator == state.creator
                                                )
                                        ) {
                                            navigator.push(
                                                ReelsFeedScreen(
                                                    sourceId = state.currentSourceId,
                                                    creator = creator,
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                onVideoCompleted = {
                                    screenModel.onVideoCompleted(item.id)
                                    // B2 sleep timer: "after this video" pauses the feed
                                    // (sticky) instead of auto-advancing to the next reel.
                                    if (!screenModel.consumeSleepAtVideoEnd()) {
                                        coroutineScope.launch {
                                            // Only the settled page may auto-advance: an ENDED from
                                            // a neighbor/preload player (or the end-hold retry
                                            // racing it) must not skip a page.
                                            val settled = pagerState.settledPage
                                            if (page == settled && settled < state.items.size - 1) {
                                                pagerState.animateScrollToPage(settled + 1)
                                            }
                                        }
                                    }
                                },
                                onPlaybackError = { msg, retry ->
                                    // Device UX (sign-off): preload neighbors fail silently —
                                    // only the settled page's playback errors reach the user.
                                    if (page == pagerState.settledPage) {
                                        // Offline playlist: the stored CDN link may have expired — kick
                                        // the on-demand re-resolve now so the retry tap meets the
                                        // refreshed URL.
                                        screenModel.refreshOfflineUrl(page)
                                        if (showErrorSnackbarOnce(msg)) {
                                            coroutineScope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = msg,
                                                    actionLabel = retryLabel,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) retry()
                                            }
                                        }
                                    }
                                },
                                onScrubStart = { chromeVisible = true },
                                onVisitStarted = screenModel::onVisitStarted,
                                onFirstFramePresented = screenModel::onFirstFramePresented,
                                onPlaybackSample = screenModel::onPlaybackSample,
                                onVisitEnded = screenModel::onVisitEnded,
                                isForeground = isForeground,
                                onPlayingStateChanged = screenModel::setPlaybackRunning,
                                initialSeekFraction = if (item.id ==
                                    state.resumeVideoId
                                ) {
                                    state.resumeFraction
                                } else {
                                    null
                                },
                                onInitialSeekConsumed = screenModel::onInitialSeekConsumed,
                                initialSeekPositionMs = if (item.id == state.pendingSeekItemId) {
                                    state.pendingSeekPositionMs
                                } else {
                                    null
                                },
                                onInitialSeekPositionConsumed = {
                                    screenModel.onSeekPositionConsumed(item.id)
                                },
                                // Audit H3: the offline playlist mixes sources — the cache key prefix must be the
                                // SLOT's sourceId, not the single tapped-favorite source; videoIds
                                // are only unique per source, a shared prefix lets CacheDataSource
                                // serve one source's bytes for another's reel.
                                cachePrefix = if (state.isOffline) {
                                    state.offlineSourceIds.getOrNull(page)?.toString() ?: "offline"
                                } else {
                                    state.currentSourceId.toString()
                                },
                                resolveUrl = { pageItem ->
                                    screenModel.resolvePlaybackUrl(pageItem, effectiveHd)
                                },
                                mediaDescriptor = mediaDescriptor,
                                isLastPage = page == state.items.lastIndex,
                                headers = state.sourceHeaders,
                            )
                        }
                    }

                    // Next-page loading indicator (the PiP frame shows only the reel).
                    if (state.isLoading && state.items.isNotEmpty() && !inPictureInPicture) {
                        ReelsNextPageLoader(modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }

            // "Tap to unmute" (approved variant A): visible while the session sound is
            // undecided; tapping unmutes (and decides), otherwise it auto-dismisses.
            AnimatedVisibility(
                visible = state.showUnmuteHint && state.isMuted && !landscapeFullscreen &&
                    !inPictureInPicture,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 150.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, AuroraTheme.colors.accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .clickable { screenModel.toggleMute() }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(AuroraTheme.colors.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Text(
                        text = stringResource(MR.strings.reels_unmute_hint),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            // Top Bar Overlay; auto-hidden in immersive mode, revealed on tap or when a
            // search/filter/source sheet is open. The system-owned PiP frame never shows it.
            AnimatedVisibility(
                visible = !landscapeFullscreen && !inPictureInPicture &&
                    (
                        chromeVisible || state.isSearchBarOpen || state.isFilterDialogOpen ||
                            state.isSourcePickerOpen || state.isLoginDialogOpen || state.isCustomFeedsOpen ||
                            state.isWebLoginDialogOpen || moreMenuOpen
                        ),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                ReelsTopBar(
                    sourceName = when {
                        state.isOffline -> stringResource(MR.strings.reels_favorites_title)
                        state.mode == ReelsFeedScreenModel.FeedMode.FOLLOWING ->
                            stringResource(MR.strings.reels_following_feed)
                        state.mode == ReelsFeedScreenModel.FeedMode.CREATOR ->
                            "@${state.creator.orEmpty()}"
                        state.mode == ReelsFeedScreenModel.FeedMode.CUSTOM ->
                            state.customFeedName.orEmpty()
                        state.mode == ReelsFeedScreenModel.FeedMode.NICHE ->
                            state.nicheName.orEmpty()
                        else -> state.sourceName
                    },
                    sourceIcon = state.sourceIcons[state.currentSourceId],
                    searchQuery = state.searchQuery,
                    isSearchBarOpen = state.isSearchBarOpen,
                    isAutoAdvance = state.isAutoAdvance,
                    isCropMode = state.isCropMode,
                    isHdQuality = state.isHdQuality,
                    dataSaverEnabled = state.dataSaverMetered,
                    preloadEnabled = state.preloadEnabled,
                    preloadWifiOnly = state.preloadWifiOnly,
                    isOffline = state.isOffline,
                    // Search and source picking belong to the global feed; the filter sheet additionally
                    // opens in NICHE mode for order-capable sources (contract v21).
                    showSearch = state.supportsTags && state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL,
                    showFilter = state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL ||
                        (state.mode == ReelsFeedScreenModel.FeedMode.NICHE && state.isCategoryOrderCapable),
                    // Source-supplied chips for the search bar (contract v19 addendum);
                    // no chips when the source supplies none.
                    searchHints = state.searchHints,
                    // Categorized search tabs (contract v20) with preview rows.
                    searchSuggestions = state.searchSuggestions,
                    onSuggestionClick = { suggestion ->
                        when (suggestion.kind) {
                            SearchSuggestionKind.NICHE -> navigator.push(
                                ReelsFeedScreen(
                                    sourceId = state.currentSourceId,
                                    nicheId = suggestion.id,
                                    nicheName = suggestion.label,
                                ),
                            )
                            SearchSuggestionKind.CREATOR -> navigator.push(
                                ReelsFeedScreen(
                                    sourceId = state.currentSourceId,
                                    creator = suggestion.id,
                                ),
                            )
                            SearchSuggestionKind.TAG -> screenModel.search(suggestion.label)
                        }
                    },
                    onSuggestionsRequest = screenModel::requestSearchSuggestions,
                    // Favorites stays a direct circle; personal content groups into the account hub.
                    // Source switching lives on the title badge only — one affordance per action.
                    // Account hub (V1): login state, custom feeds, Following, login/logout, Niches.
                    showAccount = (
                        state.isLoginCapable ||
                            state.isBrowseCapable ||
                            state.isCreatorCapable ||
                            state.isCustomFeedCapable ||
                            state.isContentPreferencesCapable ||
                            state.isBlockedTagsCapable
                        ) && !state.isOffline && state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL,
                    isLoggedIn = state.loggedInAccount != null,
                    loggedInAccount = state.loggedInAccount,
                    showLoginAccountRow = state.isLoginCapable,
                    showCustomFeedsAccountRow = state.isCustomFeedCapable,
                    showFollowsAccountRow = state.isCreatorCapable,
                    showNichesAccountRow = state.isBrowseCapable &&
                        state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL,
                    showContentPrefsAccountRow = state.isContentPreferencesCapable,
                    showBlockedTagsAccountRow = state.isBlockedTagsCapable,
                    showSourcePicker = !state.isOffline &&
                        state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL &&
                        state.availableSources.size > 1,
                    showFollowToggle =
                    (state.mode == ReelsFeedScreenModel.FeedMode.CREATOR && state.isCreatorCapable) ||
                        (state.mode == ReelsFeedScreenModel.FeedMode.NICHE && state.isCategorySubscribable),
                    isFollowingCreator = if (state.mode == ReelsFeedScreenModel.FeedMode.NICHE) {
                        state.isCategoryFollowed
                    } else {
                        state.creator?.let { it in state.followingCreators } == true
                    },
                    onToggleFollow = {
                        if (state.mode == ReelsFeedScreenModel.FeedMode.NICHE) {
                            screenModel.toggleCategoryFollow()
                        } else {
                            handleFollowToggle(state.creator)
                        }
                    },
                    onBackClick = {
                        // Report fix: the top-bar back arrow mirrors the system back behavior.
                        if (state.searchQuery.isNotBlank()) {
                            screenModel.clearSearch()
                        } else {
                            navigator.pop()
                        }
                    },
                    onOpenSourcePicker = { screenModel.toggleSourcePicker(true) },
                    onLoginRequest = { screenModel.openLoginFlow() },
                    onLogout = screenModel::logout,
                    onOpenCustomFeeds = { screenModel.toggleCustomFeeds(true) },
                    onOpenNiches = { navigator.push(ReelsNichesScreen(sourceId = state.currentSourceId)) },
                    onOpenContentPrefs = { screenModel.toggleContentPreferences(true) },
                    onOpenBlockedTags = { screenModel.toggleBlockedTags(true) },
                    onToggleAutoAdvance = screenModel::toggleAutoAdvance,
                    onToggleCropMode = screenModel::toggleCropMode,
                    onToggleQuality = screenModel::toggleQuality,
                    onToggleDataSaver = screenModel::toggleDataSaver,
                    onTogglePreload = screenModel::togglePreload,
                    onTogglePreloadWifiOnly = screenModel::togglePreloadWifiOnly,
                    isPipEnabled = state.isPipEnabled,
                    onTogglePip = screenModel::togglePip,
                    offlineUsedBytes = offlineUsedBytes,
                    onClearOfflineStorage = { confirmClearOffline = true },
                    isIncognito = isIncognito && !state.isOffline,
                    sleepTimerValue = sleepTimerValue,
                    onSetSleepTimerMinutes = { minutes ->
                        screenModel.setSleepTimer(
                            when {
                                minutes < 0 -> ReelsFeedScreenModel.SleepTimerOption.OFF
                                minutes == 0 -> ReelsFeedScreenModel.SleepTimerOption.END_OF_VIDEO
                                minutes <= 15 -> ReelsFeedScreenModel.SleepTimerOption.M15
                                minutes <= 30 -> ReelsFeedScreenModel.SleepTimerOption.M30
                                else -> ReelsFeedScreenModel.SleepTimerOption.M60
                            },
                        )
                    },
                    isTouchLocked = touchLocked,
                    onToggleTouchLock = { touchLocked = !touchLocked },
                    isReelsIncognito = isIncognito,
                    onToggleReelsIncognito = screenModel::toggleReelsIncognito,
                    isUnseenOnly = state.unseenOnly,
                    onToggleUnseenOnly = { screenModel.setUnseenOnly(!state.unseenOnly) },
                    onResetForYouHistory = { pendingResetHistory = true },
                    onMoreMenuOpenChanged = { moreMenuOpen = it },
                    onClearVideoCache = {
                        coroutineScope.launch {
                            val freed = withContext(Dispatchers.IO) { clearReelsVideoCache(context) }
                            snackbarHostState.showSnackbar(
                                context.stringResource(
                                    MR.strings.reels_video_cache_cleared,
                                    Formatter.formatFileSize(context, freed),
                                ),
                            )
                        }
                    },
                    onToggleSearchBar = { screenModel.toggleSearchBar(!state.isSearchBarOpen) },
                    onOpenFilterDialog = { screenModel.toggleFilterDialog(true) },
                    onOpenFavorites = { navigator.push(ReelsFavoritesScreen()) },
                    onOpenHistory = { navigator.push(ReelsWatchHistoryScreen()) },
                    onOpenHidden = { navigator.push(ReelsHiddenScreen()) },
                    onOpenAlbums = { navigator.push(ReelsAlbumsScreen(state.currentSourceId)) },
                    nicheAlbumSaved = if (nicheId != null) nicheId in state.savedAlbumIds else null,
                    onToggleNicheAlbum = { screenModel.toggleCurrentNicheAlbum() },
                    onOpenFollows = { navigator.push(ReelsFollowsScreen(sourceId = state.currentSourceId)) },
                    onSearch = screenModel::search,
                    onClearSearch = screenModel::clearSearch,
                    modifier = Modifier,
                )
            }

            // Filter Bottom Sheet Dialog
            if (state.isFilterDialogOpen) {
                SourceFilterAnimeDialog(
                    onDismissRequest = { screenModel.toggleFilterDialog(false) },
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = screenModel::applyFilters,
                    onUpdate = screenModel::setFilters,
                )
            }

            // Source Switcher Bottom Sheet Dialog
            if (state.isSourcePickerOpen) {
                ReelsSourcePickerSheet(
                    onDismissRequest = { screenModel.toggleSourcePicker(false) },
                    sources = state.availableSources,
                    currentSourceId = state.currentSourceId,
                    onSelectSource = screenModel::switchSource,
                    icons = state.sourceIcons,
                )
            }

            // Account / login dialog (contract v19)
            if (state.isLoginDialogOpen) {
                ReelsLoginDialog(
                    loggedInAccount = state.loggedInAccount,
                    isLoggingIn = state.isLoggingIn,
                    loginError = state.loginError,
                    loginRejected = state.loginRejected,
                    onLogin = screenModel::login,
                    onLogout = screenModel::logout,
                    onDismiss = { screenModel.toggleLoginDialog(false) },
                )
            }

            // Hosted web login WebView (contract v20)
            if (state.isWebLoginDialogOpen) {
                screenModel.webLoginUrl()?.let { url ->
                    ReelsWebLoginDialog(
                        startUrl = url,
                        freshStartUrl = { screenModel.ownAuthorizeUrl() },
                        showHint = state.webLoginHint,
                        isOwnRedirect = screenModel::isOwnWebLoginRedirect,
                        stage2Attempt = state.webLoginStage2Attempt,
                        instrumentationJs = screenModel.sessionInstrumentationJs(),
                        extraCookieOrigins = screenModel.extraSessionCookieOrigins(),
                        onSession = { cookies, localStorage ->
                            screenModel.tryImportWebSession(cookies, localStorage)
                        },
                        onDoneSession = { cookies, localStorage ->
                            screenModel.tryImportWebSessionStage2(cookies, localStorage)
                        },
                        onOwnRedirect = { redirectUrl, cookies ->
                            screenModel.tryImportWebRedirect(redirectUrl, cookies)
                        },
                        onDismiss = { screenModel.toggleWebLoginDialog(false) },
                    )
                }
            }

            // Content preferences editor (contract v20)
            if (state.isContentPreferencesOpen) {
                ReelsContentPreferencesSheet(
                    preferences = state.contentPreferences,
                    isLoading = state.isContentPreferencesLoading,
                    error = state.contentPreferencesError,
                    saveFailed = state.contentPreferencesSaveFailed,
                    onSave = screenModel::saveContentPreferences,
                    onDismiss = { screenModel.toggleContentPreferences(false) },
                )
            }

            // Blocked tags editor (contract v20)
            if (state.isBlockedTagsOpen) {
                ReelsBlockedTagsSheet(
                    initialTags = state.blockedTags.orEmpty(),
                    isLoading = state.isBlockedTagsLoading,
                    error = state.blockedTagsError,
                    saveFailed = state.blockedTagsSaveFailed,
                    onSave = screenModel::saveBlockedTags,
                    onDismiss = { screenModel.toggleBlockedTags(false) },
                )
            }

            // Silent Cloudflare bootstrap (web-login-capable sources): an offscreen WebView
            // solves the managed challenge and lifts the cookies — no user interaction; the
            // import verification reloads the feed and unmounts the view on success.
            if (state.cfBootstrapAttempt > 0) {
                screenModel.webLoginUrl()?.let { url ->
                    CfBootstrapWebView(
                        startUrl = url,
                        attempt = state.cfBootstrapAttempt,
                        onSession = { cookies, localStorage ->
                            screenModel.tryImportWebSession(cookies, localStorage)
                        },
                        instrumentationJs = screenModel.sessionInstrumentationJs(),
                        extraCookieOrigins = screenModel.extraSessionCookieOrigins(),
                    )
                }
            }

            // Custom feeds picker (contract v19)
            if (state.isCustomFeedsOpen) {
                ReelsCustomFeedsSheet(
                    feeds = state.customFeeds,
                    isLoading = state.isCustomFeedsLoading,
                    error = state.customFeedsError,
                    onRetry = screenModel::loadCustomFeeds,
                    onDismissRequest = { screenModel.toggleCustomFeeds(false) },
                    onSelectFeed = { feed ->
                        screenModel.toggleCustomFeeds(false)
                        navigator.push(
                            ReelsFeedScreen(
                                sourceId = state.currentSourceId,
                                customFeedId = feed.id,
                                customFeedName = feed.name,
                            ),
                        )
                    },
                    onNewFeed = {
                        screenModel.toggleCustomFeeds(false)
                        navigator.push(ReelsCustomFeedEditorScreen(sourceId = state.currentSourceId))
                    },
                    onEditFeed = { feed ->
                        screenModel.toggleCustomFeeds(false)
                        navigator.push(
                            ReelsCustomFeedEditorScreen(
                                sourceId = state.currentSourceId,
                                feedId = feed.id,
                                initialName = feed.name,
                            ),
                        )
                    },
                    onDeleteFeed = { feed -> pendingDeleteFeed = feed },
                )
            }

            // Reset For You history confirmation
            if (pendingResetHistory) {
                AlertDialog(
                    onDismissRequest = { pendingResetHistory = false },
                    title = { Text(stringResource(MR.strings.reels_menu_reset_history)) },
                    text = { Text(stringResource(MR.strings.reels_reset_history_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                screenModel.resetForYouHistory()
                                pendingResetHistory = false
                            },
                        ) {
                            Text(stringResource(MR.strings.reels_menu_reset_history))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingResetHistory = false }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }

            // Delete custom-feed confirmation
            pendingDeleteFeed?.let { feed ->
                AlertDialog(
                    onDismissRequest = { pendingDeleteFeed = null },
                    title = { Text(stringResource(MR.strings.reels_custom_feed_delete)) },
                    text = { Text(stringResource(MR.strings.reels_custom_feed_delete_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                screenModel.deleteCustomFeed(feed.id)
                                pendingDeleteFeed = null
                            },
                        ) {
                            Text(stringResource(MR.strings.reels_custom_feed_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDeleteFeed = null }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }

            // Offline-copies cleanup confirmation (audit H8): destructive, so confirmed.
            if (confirmClearOffline) {
                AlertDialog(
                    onDismissRequest = { confirmClearOffline = false },
                    title = { Text(stringResource(MR.strings.reels_offline_storage)) },
                    text = { Text(stringResource(MR.strings.reels_offline_storage_confirm)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmClearOffline = false
                                coroutineScope.launch {
                                    val freed = screenModel.clearOfflineStorage()
                                    offlineUsedBytes = 0L
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(
                                            MR.strings.reels_offline_storage_cleared,
                                            Formatter.formatFileSize(context, freed),
                                        ),
                                    )
                                }
                            },
                        ) {
                            Text(stringResource(MR.strings.reels_offline_storage))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearOffline = false }) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    },
                )
            }

            // Playback errors surface here (e.g. expired CDN link) instead of a silent poster.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // B3 touch lock: a full-screen input shield ABOVE every control. Taps/gestures die
            // here (the pager swipe is additionally disabled); a long-press unlocks.
            if (touchLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { },
                                onDoubleTap = { },
                                onLongPress = { touchLocked = false },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(MR.strings.reels_touch_lock),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(34.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.reels_touch_lock_hint),
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // Long-press preview of an album's content (contract v23 search cards): mini-grid of
        // the first page thumbnails + save/open actions (prototype v2, sign-off).
        state.previewAlbumId?.let { previewId ->
            val previewCategory = state.albumSearchResults.firstOrNull { it.id == previewId }
            Dialog(onDismissRequest = screenModel::closeAlbumPreview) {
                // Sign-off v3/D: one custom surface — the haze header spans edge-to-edge (no
                // AlertDialog text-slot insets, hence no "window inside a window"), and the
                // grid plus actions continue the very same surface.
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(112.dp),
                        ) {
                            previewCategory?.imageUrl?.let { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = 1.25f, scaleY = 1.25f)
                                        .blur(30.dp)
                                        .alpha(0.55f),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.35f to Color.Transparent,
                                                0.82f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                                            ),
                                        ),
                                    ),
                            )
                            Column(modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp)) {
                                Text(
                                    text = previewCategory?.name ?: previewId,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                previewCategory?.let { category ->
                                    TextButton(onClick = { screenModel.toggleAlbumSaved(category) }) {
                                        Text(
                                            stringResource(
                                                if (category.id in state.savedAlbumIds) {
                                                    MR.strings.reels_album_remove_album
                                                } else {
                                                    MR.strings.reels_album_save
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        when {
                            state.previewLoading -> Box(
                                modifier = Modifier.fillMaxWidth().height(140.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                            state.previewItems.isEmpty() -> Box(
                                modifier = Modifier.fillMaxWidth().height(96.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(MR.strings.reels_feed_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                gridItems(state.previewItems, key = { it.id }) { item ->
                                    if (item.posterUrl.isBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(3f / 4f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                        .copy(alpha = 0.5f),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Collections,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else {
                                        AsyncImage(
                                            model = item.posterUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(3f / 4f)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = screenModel::closeAlbumPreview) {
                                Text(stringResource(MR.strings.action_cancel))
                            }
                            TextButton(
                                onClick = {
                                    screenModel.closeAlbumPreview()
                                    previewCategory?.let { category ->
                                        navigator.push(
                                            ReelsAlbumScreen(
                                                sourceId = state.currentSourceId,
                                                albumId = category.id,
                                                albumName = category.name,
                                            ),
                                        )
                                    }
                                },
                            ) {
                                Text(stringResource(MR.strings.reels_album_open))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.reelsNetworkStatus(): ReelsNetworkStatus {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return ReelsNetworkStatus(onWifi = false, metered = true)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        ?: return ReelsNetworkStatus(onWifi = false, metered = true)
    return caps.toReelsNetworkStatus()
}

private fun NetworkCapabilities.toReelsNetworkStatus(): ReelsNetworkStatus = reelsNetworkStatus(
    onWifi = hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
    unmetered = hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
)

/** Device UX (sign-off): same-message error snackbars are coalesced within this window. */
private const val ERROR_SNACK_COOLDOWN_MS = 10_000L

/**
 * Contract v23 album-search results: album cards instead of the video pager. Tap opens the
 * album grid, the trailing button saves/removes it in the collection, long-press shows the
 * content preview dialog (prototype v2, sign-off). Pagination appends on scroll bottom.
 */
@Composable
private fun AlbumSearchResults(
    state: ReelsFeedScreenModel.State,
    onOpenAlbum: (FeedCategory) -> Unit,
    onToggleSave: (FeedCategory) -> Unit,
    onPreview: (FeedCategory) -> Unit,
    onLoadMore: () -> Unit,
    onResetSearch: () -> Unit,
) {
    // Report fix: the list lives under the overlay top bar — pad for status bar + bar height
    // so the first album card is never hidden beneath the chrome; the open search block
    // (device fix) adds its own height on top.
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp +
        if (state.isSearchBarOpen) 116.dp else 0.dp
    if (state.albumSearchResults.isEmpty() && !state.isLoading) {
        ReelsEmptySearchState(onResetSearch = onResetSearch, modifier = Modifier.padding(top = topPadding))
        return
    }
    val listState = rememberLazyListState()
    val lastVisible by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
    }
    LaunchedEffect(lastVisible, state.albumSearchResults.size, state.albumSearchCanLoadMore) {
        if (state.albumSearchCanLoadMore && lastVisible >= state.albumSearchResults.size - 4) {
            onLoadMore()
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            bottom = 12.dp,
            top = topPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.albumSearchResults, key = { it.id }) { category ->
            val saved = category.id in state.savedAlbumIds
            // Aurora haze (device sign-off): the album cover blurred as backdrop under a
            // transparent surface instead of an opaque card.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { onOpenAlbum(category) },
                        onLongClick = { onPreview(category) },
                    ),
            ) {
                if (category.imageUrl != null) {
                    AsyncImage(
                        model = category.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(18.dp)
                            .alpha(0.45f),
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Report fix: graceful fallback when the album has no thumbnail.
                    if (category.imageUrl != null) {
                        AsyncImage(
                            model = category.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp, 72.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp, 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Collections,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        category.itemCount?.let { count ->
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onToggleSave(category) }) {
                        Icon(
                            imageVector = if (saved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircle,
                            contentDescription = null,
                            tint = if (saved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        }
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/**
 * Chrome auto-hide policy (device report): immersive hiding is only allowed while a reel is
 * ACTUALLY playing in a healthy feed. Errors, empty feeds and initial/stalled loads keep the
 * top bar reachable — hiding the bar there stranded the user without retry or source switch
 * (the play INTENT flag stayed true with nothing playing, so the old gate hid the chrome on
 * every non-playing state and every tap only revealed it for three seconds).
 */
internal fun reelsChromeShouldAutoHide(
    visible: Boolean,
    actuallyPlaying: Boolean,
    hasItems: Boolean,
    hasError: Boolean,
    isLoading: Boolean,
): Boolean = visible && actuallyPlaying && hasItems && !hasError && !isLoading

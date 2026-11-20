package eu.kanade.tachiyomi.ui.reels

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateSetOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
import eu.kanade.tachiyomi.animesource.model.ShortVideoItem
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.SourceFilterAnimeDialog
import eu.kanade.tachiyomi.ui.reels.ReelsFeedScreenModel.OfflineCopyResult
import eu.kanade.tachiyomi.ui.reels.components.CfBootstrapWebView
import eu.kanade.tachiyomi.ui.reels.components.ReelsBlockedTagsSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsContentPreferencesSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsCustomFeedsSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsEmptySearchState
import eu.kanade.tachiyomi.ui.reels.components.ReelsErrorState
import eu.kanade.tachiyomi.ui.reels.components.ReelsFeedSkeletonShimmer
import eu.kanade.tachiyomi.ui.reels.components.ReelsLoginDialog
import eu.kanade.tachiyomi.ui.reels.components.ReelsNextPageLoader
import eu.kanade.tachiyomi.ui.reels.components.ReelsQualityDialogMode
import eu.kanade.tachiyomi.ui.reels.components.ReelsSourcePickerSheet
import eu.kanade.tachiyomi.ui.reels.components.ReelsTopBar
import eu.kanade.tachiyomi.ui.reels.components.ReelsVideoPage
import eu.kanade.tachiyomi.ui.reels.components.ReelsVideoQualityDialog
import eu.kanade.tachiyomi.ui.reels.components.ReelsWebLoginDialog
import eu.kanade.tachiyomi.ui.reels.player.ReelsPlayerPool
import eu.kanade.tachiyomi.ui.reels.player.clearReelsVideoCache
import eu.kanade.tachiyomi.ui.reels.player.prewarmReelHead
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.reels.anime.model.ReelsQualityPreference
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.Serializable
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import androidx.compose.foundation.lazy.grid.items as gridItems

internal fun reelsDownloadKey(sourceId: Long, videoId: String): String = "$sourceId:$videoId"

internal enum class ReelsDownloadTap { Start, Cancel, Resume, Open }

/** The single download pod means four things depending on its state; kept pure so it is testable. */
internal fun resolveReelsDownloadTap(
    inProgress: Boolean,
    hasDestination: Boolean,
    isPartial: Boolean,
): ReelsDownloadTap = when {
    inProgress -> ReelsDownloadTap.Cancel
    !hasDestination -> ReelsDownloadTap.Start
    isPartial -> ReelsDownloadTap.Resume
    else -> ReelsDownloadTap.Open
}

/**
 * Computes guaranteed-unique VerticalPager keys for [state.items].
 *
 * Preserves stable media identity (`item.id` / `owner.token:item.id` / `mix:sourceId:item.id`)
 * for the first occurrence and suffixes `#dupN` on any unexpected duplicate so Compose's
 * `VerticalPager` can never crash with `IllegalArgumentException: Key ... was already used`.
 */
internal fun buildUniqueReelsPageKeys(state: ReelsFeedScreenModel.State): List<String> {
    val items = state.items
    if (items.isEmpty()) return emptyList()
    val counts = HashMap<String, Int>(items.size * 2)
    val roundRobinOwner = state.roundRobinOwner
    return List(items.size) { index ->
        val item = items[index]
        val offlineSourceId = state.offlineSourceIds.getOrNull(index)
        val baseKey = when {
            roundRobinOwner != null -> "${roundRobinOwner.token}:${item.id}"
            state.isMixMode -> "mix:${state.slotSourceId(index)}:${item.id}"
            state.isOffline && offlineSourceId != null -> "$offlineSourceId:${item.id}"
            else -> item.id
        }
        val seen = counts[baseKey] ?: 0
        counts[baseKey] = seen + 1
        if (seen == 0) baseKey else "$baseKey#dup$seen"
    }
}

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
        val activity = context as? Activity
        val coroutineScope = rememberCoroutineScope()
        // Reels are a portrait-first experience; landscape is an explicit shared choice for
        // the whole feed chain. Nested feeds overlap during Voyager transitions (the incoming
        // screen composes BEFORE the outgoing disposes), so the lock is a shared holder count.
        DisposableEffect(Unit) {
            ReelsPlayerPool.retainScreen()
            if (activity != null) {
                ReelsOrientationLock.acquire(activity)
            }
            onDispose {
                if (activity != null) {
                    ReelsOrientationLock.release(
                        activity,
                        keepIntent = navigator.items.any { it is ReelsFeedScreen },
                    )
                }
                ReelsPlayerPool.releaseScreen()
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
                        screenModel.onForegroundLost(
                            isConfigurationChange = activity?.isChangingConfigurations == true,
                        )
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
        var playbackSpeed by rememberSaveable { mutableFloatStateOf(1f) }
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

        fun handleFollowToggle(creatorName: String?, sourceId: Long = state.currentSourceId) {
            if (creatorName == null) return
            screenModel.toggleFollow(creatorName, sourceId)
        }
        // B3.2: the hide snackbar offers an undo of the most recent hide (video or author).
        val hideSnackbarMessage = stringResource(MR.strings.reels_hidden_snackbar)
        val offlineSavedMessage = stringResource(MR.strings.reels_offline_saved)
        val offlineRemovedMessage = stringResource(MR.strings.reels_offline_removed)
        val offlineQuotaMessage = stringResource(MR.strings.reels_offline_failed_quota)
        val offlineNetworkMessage = stringResource(MR.strings.reels_offline_failed_network)
        val offlineUnsupportedMessage = stringResource(MR.strings.reels_offline_failed_unsupported)
        val downloadSavedMessage = stringResource(MR.strings.reels_download_saved)
        val downloadFileSavedMessage = stringResource(MR.strings.reels_download_file_saved)
        val downloadFailedMessage = stringResource(MR.strings.reels_download_failed)
        val downloadUnsupportedMessage = stringResource(MR.strings.reels_download_unsupported)
        val downloadPausedMessage = stringResource(MR.strings.reels_download_paused)
        val downloads = remember(context) { ReelsDownloads(context.applicationContext) }
        val exportStates by ReelsDownloadManager.states.collectAsStateWithLifecycle()

        LaunchedEffect(Unit) {
            ReelsDownloadManager.events.collect { event ->
                val message = when (event) {
                    is ReelsDownloadEvent.Completed ->
                        if (event.toCustomDocument) downloadFileSavedMessage else downloadSavedMessage
                    is ReelsDownloadEvent.Paused -> downloadPausedMessage
                    is ReelsDownloadEvent.Failed -> downloadFailedMessage
                    is ReelsDownloadEvent.Unsupported -> downloadUnsupportedMessage
                }
                snackbarHostState.showSnackbar(message)
            }
        }

        var pendingDownload by rememberSaveable { mutableStateOf<ReelsExportRequest?>(null) }

        fun performDownload(
            request: ReelsExportRequest,
            destination: Uri? = null,
            resumeFrom: Long = 0L,
            resumeUri: Uri? = null,
        ) {
            ReelsDownloadManager.enqueue(
                context = context,
                request = request,
                destination = destination,
                resumeFrom = resumeFrom,
                resumeUri = resumeUri,
            )
        }

        // Android 9 and older need a user-granted document, not a new broad storage permission.
        val legacyDownloadPicker = rememberLauncherForActivityResult(
            remember {
                object : ActivityResultContracts.CreateDocument("*/*") {
                    override fun createIntent(context: Context, input: String): Intent =
                        super.createIntent(context, input).putExtra(
                            DocumentsContract.EXTRA_INITIAL_URI,
                            DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads"),
                        )
                }
            },
        ) { uri ->
            val request = pendingDownload
            pendingDownload = null
            if (request != null) {
                if (uri == null) {
                    ReelsDownloadManager.clearAsync(context, request.key)
                } else {
                    performDownload(request, uri)
                }
            }
        }

        fun handleDownload(item: ShortVideoItem, itemIndex: Int, hd: Boolean) {
            val sourceId = when {
                state.isOffline -> state.offlineSourceIds.getOrNull(itemIndex) ?: return
                state.isMixMode -> state.slotSourceId(itemIndex)
                else -> state.currentSourceId
            }
            val key = reelsDownloadKey(sourceId, item.id)
            val previous = ReelsDownloadManager.currentState(key)
            val resumable = previous?.takeIf { it.partial && it.uri != null }
            val resumeFrom = resumable?.bytes ?: 0L
            val resumeUri = resumable?.uri
            // The pod doubles as the stop button while a transfer runs: no second control to tap.
            if (resolveReelsDownloadTap(
                    previous?.inProgress == true,
                    previous?.uri != null,
                    resumable != null,
                ) == ReelsDownloadTap.Cancel
            ) {
                ReelsDownloadManager.cancel(context, key)
                return
            }
            if (previous?.inProgress == true || pendingDownload != null ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ReelsDownloadManager.hasActiveDownloads())
            ) {
                return
            }
            ReelsDownloadManager.markStarting(key, previous)
            val headers = HashMap(screenModel.downloadHeaders(sourceId))
            val baseSnapshot = if (resumeUri != null) {
                previous.qualitySnapshot
            } else {
                screenModel.captureQualityDownload(item, sourceId, headers)
            }
            val descriptor = screenModel.mediaDescriptor(item, sourceId)
            coroutineScope.launch {
                val qualitySnapshot = if (resumeFrom > 0L && baseSnapshot != null) {
                    val rehydrated = screenModel.rehydrateQualityDownload(baseSnapshot, resumeFrom)
                    if (rehydrated == null) {
                        ReelsDownloadManager.restoreState(key, previous ?: ReelsExportState())
                        snackbarHostState.showSnackbar(downloadFailedMessage)
                        return@launch
                    }
                    rehydrated
                } else {
                    baseSnapshot
                }
                if (qualitySnapshot == null && screenModel.isQualityModeItem(item.id, sourceId)) {
                    ReelsDownloadManager.restoreState(key, previous ?: ReelsExportState())
                    snackbarHostState.showSnackbar(downloadFailedMessage)
                    return@launch
                }
                if (previous != null && previous.uri != null && resumeUri == null) {
                    val uri = previous.uri
                    val exists = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                        } catch (_: Exception) {
                            false
                        }
                    }
                    if (exists) {
                        ReelsDownloadManager.restoreState(key, previous)
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(uri, context.contentResolver.getType(uri) ?: "video/*")
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            )
                        } catch (_: ActivityNotFoundException) {
                            snackbarHostState.showSnackbar(downloadFileSavedMessage)
                        } catch (_: SecurityException) {
                            snackbarHostState.showSnackbar(downloadFailedMessage)
                        }
                        return@launch
                    }
                }
                try {
                    if (!item.videoUrl.startsWith("file://") && descriptor?.offlineSupported == false) {
                        ReelsDownloadManager.clearAsync(context, key)
                        snackbarHostState.showSnackbar(downloadUnsupportedMessage)
                        return@launch
                    }
                    val url = qualitySnapshot?.item?.videoUrl?.takeIf { it.isNotBlank() }
                        ?: (if (hd) item.videoUrlHd?.takeIf { it.isNotBlank() } ?: item.videoUrl else item.videoUrl)
                            .takeIf { it.isNotBlank() }
                        ?: screenModel.resolvePlaybackUrl(item, hd, sourceId)
                    if (url.isNullOrBlank()) {
                        ReelsDownloadManager.clearAsync(context, key)
                        snackbarHostState.showSnackbar(downloadFailedMessage)
                        return@launch
                    }
                    val extension = Uri.parse(url).lastPathSegment?.substringAfterLast('.', "")?.lowercase()
                    if (extension == "mpd") {
                        ReelsDownloadManager.clearAsync(context, key)
                        snackbarHostState.showSnackbar(downloadUnsupportedMessage)
                        return@launch
                    }
                    val cacheKeys = buildList<String> {
                        qualitySnapshot?.let { snap ->
                            add(ReelsQualityController.cacheKey(snap.owner, snap.item.id, snap.cacheIdentity))
                        }
                        add("$sourceId:${item.id}:${if (hd) "hd" else "sd"}")
                        add(url)
                    }
                    val request = ReelsExportRequest(
                        item = qualitySnapshot?.item ?: item.copy(videoUrl = url, videoUrlHd = null),
                        sourceId = sourceId,
                        headers = headers,
                        qualitySnapshot = qualitySnapshot,
                        cacheKeys = cacheKeys,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        performDownload(request, resumeFrom = resumeFrom, resumeUri = resumeUri)
                    } else {
                        pendingDownload = request
                        legacyDownloadPicker.launch(downloads.fileName(request.item, sourceId))
                    }
                } catch (e: CancellationException) {
                    ReelsDownloadManager.clearAsync(context, key)
                    throw e
                } catch (_: Exception) {
                    pendingDownload = null
                    ReelsDownloadManager.clearAsync(context, key)
                    snackbarHostState.showSnackbar(downloadFailedMessage)
                }
            }
        }

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
        // U02: the page never rotates its own subtree; the window orientation is owned by
        // ReelsOrientationLock. Layout follows the actual window constraints.
        val windowConfiguration = LocalConfiguration.current
        val windowIsLandscape = reelsWindowIsLandscape(
            windowConfiguration.screenWidthDp,
            windowConfiguration.screenHeightDp,
        )
        val inPictureInPicture = ReelsPip.isInPictureInPictureMode
        val landscape = ReelsOrientationLock.landscape
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
        // Immersive chrome and the open settings block survive a configuration recreation:
        // the settings hold pins playback, and losing it would silently resume the reel.
        var chromeVisible by rememberSaveable { mutableStateOf(true) }
        // Device UX: the ⋮ settings block holds playback and pins the chrome while open.
        var moreMenuOpen by rememberSaveable { mutableStateOf(false) }
        // Track open page overlays by pageKey so a neighbor card leaving composition can never
        // overwrite the active card's open quality dialog or drawer.
        val openOverlayPages = remember { mutableStateSetOf<String>() }
        val qualityDialogOpen = openOverlayPages.isNotEmpty()
        var portraitQualityDialogMode by remember { mutableStateOf<ReelsQualityDialogMode?>(null) }
        var portraitQualityItemId by remember { mutableStateOf<String?>(null) }
        var portraitQualitySourceId by remember { mutableStateOf<Long?>(null) }
        val qualityDialogVisible = qualityDialogOpen || portraitQualityDialogMode != null
        // The HOME-gesture handler runs outside composition: this ref keeps the PiP params
        // tied to the live crop mode.
        val currentCropMode by rememberUpdatedState(state.isCropMode)
        // Back closes open search / active search filter first (including in landscape),
        // then exits landscape, then navigates back normally.
        BackHandler(enabled = landscape) {
            if (activity != null) {
                ReelsOrientationLock.setLandscape(activity, false)
            }
        }
        BackHandler(enabled = state.isSearchBarOpen) {
            screenModel.toggleSearchBar(false)
        }
        BackHandler(
            enabled = !state.isSearchBarOpen && state.searchQuery.isNotBlank(),
        ) {
            screenModel.clearSearch()
        }

        // Immersive status bar (device report): the status icons follow the feed chrome —
        // hidden with it (transient reveal by swipe still works) and restored with the top
        // bar or any blocking sheet. PiP owns its own window chrome.
        val systemChromeVisible = reelsSystemChromeVisible(
            chromeVisible = chromeVisible,
            moreMenuOpen = moreMenuOpen,
            qualityDialogOpen = qualityDialogVisible,
            isSearchBarOpen = state.isSearchBarOpen,
            isFilterDialogOpen = state.isFilterDialogOpen,
            isSourcePickerOpen = state.isSourcePickerOpen,
            isLoginDialogOpen = state.isLoginDialogOpen,
            isCustomFeedsOpen = state.isCustomFeedsOpen,
            isWebLoginDialogOpen = state.isWebLoginDialogOpen,
        ) && !inPictureInPicture
        DisposableEffect(systemChromeVisible) {
            val window = (context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (systemChromeVisible) {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
            onDispose { }
        }
        // Leaving the reels surface restores the status bar; a feed→feed hand-off must not
        // fight the incoming screen (same owner check as the keep-screen-on flag below).
        DisposableEffect(Unit) {
            onDispose {
                val window = (context as? Activity)?.window
                if (window != null && navigator.lastItem !is ReelsFeedScreen) {
                    WindowCompat.getInsetsController(window, window.decorView)
                        .show(WindowInsetsCompat.Type.statusBars())
                }
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
                    val activity = context as? Activity
                    if (activity?.isChangingConfigurations != true && window != null) {
                        val attrs = window.attributes
                        if (attrs.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                            attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                            window.attributes = attrs
                        }
                    }
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
            val volumeUpHandler: () -> Unit = {
                screenModel.unmuteIfMuted()
            }
            ReelsVolumeKeyHandler.onVolumeUp = volumeUpHandler
            onDispose {
                // Owner check (audit H1): the incoming feed screen registers its own handler
                // BEFORE this disposal runs — nulling unconditionally would kill PiP for the
                // visible feed after any feed→feed navigation.
                if (ReelsPip.handler === handler) {
                    ReelsPip.handler = null
                }
                if (ReelsVolumeKeyHandler.onVolumeUp === volumeUpHandler) {
                    ReelsVolumeKeyHandler.onVolumeUp = null
                }
            }
        }

        // Auto-advance keeps running in PiP: when the settled reel (or crop mode/rotation)
        // changes, update the system window shape instead of leaving the old cover ratio.
        LaunchedEffect(inPictureInPicture, activeVideoSize, state.isCropMode) {
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
            qualityDialogVisible,
            state.isSearchBarOpen,
        ) {
            // Device UX: an open settings block or search field never auto-hides the chrome;
            // closing restarts the countdown.
            if (moreMenuOpen || qualityDialogVisible || state.isSearchBarOpen) return@LaunchedEffect
            if (
                reelsChromeShouldAutoHide(
                    visible = chromeVisible,
                    actuallyPlaying = state.isActuallyPlaying,
                    hasItems = state.items.isNotEmpty(),
                    hasError = state.error != null || state.errorRes != null,
                    isLoading = state.isLoading,
                    blockingSurfaceOpen = moreMenuOpen || qualityDialogVisible || state.isSearchBarOpen,
                )
            ) {
                delay(3000)
                chromeVisible = false
            }
        }

        // Device UX: opening the ⋮ settings block pauses playback and pins the chrome;
        // closing resumes playback (unless the user paused deliberately) and the auto-hide
        // countdown starts again on the next effect pass.
        LaunchedEffect(moreMenuOpen, qualityDialogVisible) {
            val hold = moreMenuOpen || qualityDialogVisible
            screenModel.setSettingsHold(hold)
            if (hold) chromeVisible = true
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
                    ReelsFeedSkeletonShimmer(modifier = Modifier.fillMaxSize())
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
                    // reported its frame while the page was only preloaded. Also prunes stale
                    // dimensions outside a ±30 window in long sessions.
                    LaunchedEffect(pagerState.settledPage, state.items) {
                        val settledId = state.items.getOrNull(state.activeIndex)?.id
                        activeVideoLandscape = settledId?.let(videoOrientations::get)
                        activeVideoSize = settledId?.let(videoSizes::get)
                        if (videoOrientations.size > 64 || videoSizes.size > 64) {
                            val from = (state.activeIndex - 30).coerceAtLeast(0)
                            val to = (state.activeIndex + 30).coerceAtMost(state.items.lastIndex.coerceAtLeast(0))
                            val keepIds = (from..to).mapNotNullTo(HashSet()) { state.items.getOrNull(it)?.id }
                            videoOrientations.keys.retainAll(keepIds)
                            videoSizes.keys.retainAll(keepIds)
                        }
                    }

                    LaunchedEffect(state.dataSaverMetered, networkStatus.metered) {
                        screenModel.onQualityForceSdChanged(
                            reelsForcesSd(state.dataSaverMetered, networkStatus.metered),
                        )
                    }
                    // Quality bindings must exist before the player preload (+1) and the head-prewarm
                    // loop (+2..+4) can warm bytes. Page settles are handled by onPageChanged; this
                    // effect covers data readiness, feed replacement and a new quality owner. It is a
                    // background refresh, so an already-bound active reel is not retuned.
                    LaunchedEffect(
                        state.items.size,
                        state.feedGeneration,
                        state.qualityState?.owner?.token,
                        state.mixQualityStates.size,
                    ) {
                        screenModel.prepareQualityWindow(state.activeIndex, skipBoundStart = true)
                    }

                    // C-intermediate (audit): heads of the reels BEYOND the player preload window
                    // (+2..+4) are warmed into the video cache with plain bytes — no player, no
                    // decoder — so a deep swipe starts from disk instead of a cold CDN round-trip.
                    // The forward player preload intentionally stays at one neighbor (three live
                    // decoders competed for bandwidth). Same gate as the player preload and the
                    // same cache-key scheme as the page; a key mismatch (a quality toggle
                    // mid-flight) can only cost a cache miss, never corrupt.
                    LaunchedEffect(
                        pagerState.settledPage,
                        state.items,
                        preloadAllowed,
                        effectiveHd,
                        state.qualityState?.selectionRevision,
                        state.qualityState?.items?.values?.count { it.actual != null },
                        state.mixQualityStates,
                    ) {
                        if (!preloadAllowed) return@LaunchedEffect
                        val settled = pagerState.settledPage
                        val items = state.items
                        val offline = state.isOffline
                        val mixMode = state.isMixMode
                        val sourceId = state.currentSourceId
                        withContext(Dispatchers.IO) {
                            // The effect's job dies on a settled-page/source/quality change; a
                            // prewarm run must stop pulling bytes instead of warming stale pages.
                            val prewarmJob = coroutineContext[Job]
                            for (index in settled + 2..settled + 4) {
                                if (prewarmJob?.isActive == false) break
                                val item = items.getOrNull(index) ?: break
                                val slotSourceId = state.slotSourceId(index)
                                val quality = screenModel.qualityPlayback(item.id, slotSourceId)
                                if (screenModel.isQualityModeItem(item.id, slotSourceId) && quality == null) continue
                                val url = quality?.videoUrl
                                    ?: if (effectiveHd) item.videoUrlHd ?: item.videoUrl else item.videoUrl
                                val prefix = when {
                                    offline -> state.offlineSourceIds.getOrNull(index)?.toString() ?: "offline"
                                    mixMode -> slotSourceId.toString()
                                    else -> sourceId.toString()
                                }
                                prewarmReelHead(
                                    context = context,
                                    url = url,
                                    cacheKey = quality?.cacheKey
                                        ?: "$prefix:${item.id}:${if (effectiveHd) "hd" else "sd"}",
                                    headers = state.slotHeaders(index),
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
                    // pager owns the orthogonal axis.
                    val feedSwipeEnabled = !windowIsLandscape && !state.isMixMode && (
                        followingFeed ||
                            (state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL && state.isCreatorCapable)
                        )
                    val swipeThresholdPx = with(LocalDensity.current) { 110.dp.toPx() }
                    var horizontalDragPx by remember { mutableFloatStateOf(0f) }

                    // Voyager stack guarantee: when a new screen (Following, Creator, Album,
                    // Favorites) is pushed on top of this feed, immediately release this screen's
                    // active/preload players back to the pool without waiting for Activity ON_PAUSE.
                    val isTopScreen = navigator.lastItem.key == this@ReelsFeedScreen.key

                    // Duplicate-proof page keys: prevents IllegalArgumentException in VerticalPager
                    // if a plugin or multi-source mix serves a duplicate item.id.
                    val pageKeys = remember(
                        state.items,
                        state.roundRobinOwner,
                        state.isMixMode,
                        state.isOffline,
                        state.offlineSourceIds,
                    ) {
                        buildUniqueReelsPageKeys(state)
                    }

                    // Pull-to-refresh on page 0: pulling downward at the top of the feed refreshes
                    // without opening the top menu.
                    val pullRefreshThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
                    var pullRefreshOffsetPx by remember { mutableFloatStateOf(0f) }
                    val haptic = LocalHapticFeedback.current

                    // Vertical Pager for reels video cards
                    VerticalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        key = { index -> pageKeys.getOrNull(index) ?: index },
                        // The pager lives outside the software rotation, so manual swipes in
                        // landscape fullscreen would land sideways; auto-advance still moves
                        // the feed programmatically (portrait reels exit fullscreen, see
                        // ReelsVideoPage).
                        userScrollEnabled = !touchLocked && !qualityDialogVisible,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                pagerState,
                                state.isOffline,
                                touchLocked,
                                qualityDialogVisible,
                                windowIsLandscape,
                            ) {
                                // Observe-only vertical gesture + Pull-to-Refresh on page 0.
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    var dragged = false
                                    var totalDy = 0f
                                    var totalDx = 0f
                                    var pullArmed = false
                                    val canPullRefresh = !windowIsLandscape &&
                                        !state.isOffline &&
                                        !touchLocked &&
                                        !qualityDialogVisible &&
                                        pagerState.currentPage == 0 &&
                                        pagerState.currentPageOffsetFraction <= 0.01f
                                    drag(down.id) { change ->
                                        val delta = change.positionChange()
                                        if (abs(delta.y) > 0f) dragged = true
                                        totalDy += delta.y
                                        totalDx += delta.x
                                        if (canPullRefresh && totalDy > 0f && totalDy > abs(totalDx) * 1.25f) {
                                            val damped = (totalDy * 0.55f).coerceIn(0f, pullRefreshThresholdPx * 1.45f)
                                            pullRefreshOffsetPx = damped
                                            if (!pullArmed && damped >= pullRefreshThresholdPx) {
                                                pullArmed = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            } else if (pullArmed && damped < pullRefreshThresholdPx) {
                                                pullArmed = false
                                            }
                                        } else if (pullRefreshOffsetPx > 0f && totalDy <= 0f) {
                                            pullRefreshOffsetPx = 0f
                                            pullArmed = false
                                        }
                                    }
                                    if (dragged) lastUserNavAtMs.set(SystemClock.elapsedRealtime())
                                    if (pullArmed) {
                                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                        screenModel.refreshFeed()
                                    }
                                    pullRefreshOffsetPx = 0f
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
                        // The render owner is part of this page's identity, exactly like the
                        // pager key above. A player disposed by an owner change can still
                        // deliver a late callback; it must speak for the generation this card
                        // was composed under (where the model rejects it as stale), never for
                        // whichever owner the state holds when the callback finally arrives.
                        val pageOwner = state.roundRobinOwner
                        val pageCallbacks = remember(pageOwner, page) {
                            ReelsRoundRobinPageCallbacks(screenModel, pageOwner, slotIndex = page)
                        }
                        if (item != null) {
                            val slotSourceId = state.slotSourceId(page)
                            val slotQualityState = if (state.isMixMode) {
                                state.mixQualityStates[slotSourceId]
                            } else {
                                state.qualityState
                            }
                            // Contract v25: the source's per-item media verdict; remembered
                            // per (item, slotSourceId) so re-composition does not re-run plugin code.
                            val mediaDescriptor = remember(item, slotSourceId) {
                                screenModel.mediaDescriptor(item, slotSourceId)
                            }
                            val export = exportStates[reelsDownloadKey(slotSourceId, item.id)]
                            val pageKey = pageKeys.getOrNull(page) ?: "$page:${item.id}"
                            ReelsVideoPage(
                                item = item,
                                // Activate only the settled page of the TOP Voyager screen: during
                                // fast flings or when another screen is pushed above, players must
                                // not keep running.
                                isActive = isTopScreen && (page == pagerState.settledPage),
                                // Preload only the forward neighbor on the top screen.
                                isPreload = isTopScreen && preloadAllowed && page == pagerState.settledPage + 1,
                                isPlaying = state.isPlaying,
                                isMuted = state.isMuted,
                                isLiked = state.isLiked(item.id, page),
                                isHdQuality = effectiveHd,
                                qualityModeEnabled = screenModel.isQualityModeItem(item.id, slotSourceId),
                                qualityPlayback = screenModel.qualityPlayback(item.id, slotSourceId),
                                qualityItemState = slotQualityState?.items?.get(item.id),
                                qualityGlobal = slotQualityState?.global ?: ReelsQualityPreference.Source,
                                onSetQualityGlobal = { screenModel.setQualityGlobalForSource(it, slotSourceId) },
                                onSetQualityOverride = { id, pref ->
                                    screenModel.setQualityOverrideForSource(id, pref, slotSourceId)
                                },
                                onClearQualityOverride = { id ->
                                    screenModel.clearQualityOverrideForSource(id, slotSourceId)
                                },
                                onQualityDialogOpenChanged = { open ->
                                    if (open) openOverlayPages.add(pageKey) else openOverlayPages.remove(pageKey)
                                },
                                onRetryQuality = { screenModel.prepareQualityForIndex(page) },
                                onRefreshExpiredUrl = { screenModel.refreshExpiredUrlForIndex(page) },
                                onCodecFallback = { screenModel.codecFallbackForIndex(page) },
                                playbackSpeed = playbackSpeed,
                                onPlaybackSpeedChange = { playbackSpeed = it },
                                isAutoAdvance = state.isAutoAdvance,
                                isCropMode = state.isCropMode,
                                chromeVisible = chromeVisible && (
                                    page == pagerState.settledPage || page == pagerState.currentPage
                                ),
                                isLandscape = windowIsLandscape,
                                showSearch = state.supportsSearch &&
                                    state.supportsTags &&
                                    state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL,
                                isSearchBarOpen = state.isSearchBarOpen,
                                onToggleSearch = { screenModel.toggleSearchBar(!state.isSearchBarOpen) },
                                searchQuery = state.searchQuery,
                                onSearchSubmit = screenModel::search,
                                onClearSearch = screenModel::clearSearch,
                                onOpenSourcePicker = if (
                                    !state.isOffline &&
                                    state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL &&
                                    state.availableSources.size > 1
                                ) {
                                    { screenModel.toggleSourcePicker(true) }
                                } else {
                                    null
                                },
                                landscape = landscape,
                                onToggleLandscape = {
                                    if (activity != null) {
                                        ReelsOrientationLock.setLandscape(activity, !landscape)
                                    }
                                },
                                onExitLandscape = {
                                    if (activity != null) {
                                        ReelsOrientationLock.setLandscape(activity, false)
                                    }
                                },
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
                                    screenModel.hideVideo(item, page)
                                    notifyHidden()
                                },
                                onHideAuthor = item.author?.let { author ->
                                    {
                                        screenModel.hideAuthor(author, page)
                                        notifyHidden()
                                    }
                                },
                                // B3.3 offline copy: shown only in the offline playlist.
                                showOfflineAction = state.isOffline,
                                isOfflineStored = "$slotSourceId:${item.id}" in state.offlineStored,
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
                                isDownloaded = export?.uri != null,
                                isDownloading = export?.inProgress == true,
                                isPartial = export?.partial == true,
                                downloadSizeText = export?.bytes
                                    ?.takeIf { it > 0L }?.let { Formatter.formatFileSize(context, it) },
                                downloadProgress = export?.let { export ->
                                    export.total?.takeIf { it > 0L }?.let { total ->
                                        (export.bytes.toFloat() / total).coerceIn(0f, 1f)
                                    }
                                },
                                onDownload = { handleDownload(item, page, effectiveHd) },
                                // Contract v18 creator surfaces: capability + author gated.
                                showFollowAction =
                                state.isSlotCreatorCapable(page) && !state.isOffline && item.author != null,
                                isFollowingCreator = item.author?.let { state.isFollowingCreator(it, page) } == true,
                                onToggleFollowCreator = { handleFollowToggle(item.author, slotSourceId) },
                                onAuthorClick = if (
                                    state.isSlotCreatorCapable(page) && !state.isOffline && item.author != null &&
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
                                                sourceId = slotSourceId,
                                                creator = item.author,
                                            ),
                                        )
                                    }
                                } else {
                                    null
                                },
                                onCreatorClick = if (state.isSlotCreatorCapable(page) && !state.isOffline) {
                                    { creator ->
                                        if (!(
                                                state.mode == ReelsFeedScreenModel.FeedMode.CREATOR &&
                                                    creator == state.creator
                                                )
                                        ) {
                                            navigator.push(
                                                ReelsFeedScreen(
                                                    sourceId = slotSourceId,
                                                    creator = creator,
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                    null
                                },
                                onVideoCompleted = {
                                    pageCallbacks.onVideoCompleted(item.id)
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
                                onVisitStarted = pageCallbacks::onVisitStarted,
                                onFirstFramePresented = pageCallbacks::onFirstFramePresented,
                                onPlaybackSample = pageCallbacks::onPlaybackSample,
                                onVisitEnded = pageCallbacks::onVisitEnded,
                                onVisitSuspendedForConfiguration = screenModel::onVisitSuspendedForConfiguration,
                                isForeground = isForeground,
                                onPlayingStateChanged = { playing ->
                                    screenModel.setPlaybackRunning(playing, pageIndex = page)
                                },
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
                                // Audit H3: the offline and Mix playlists mix sources — the cache key prefix must be the
                                // SLOT's sourceId, not the sentinel or single tapped-favorite source; videoIds
                                // are only unique per source, a shared prefix lets CacheDataSource
                                // serve one source's bytes for another's reel.
                                cachePrefix = when {
                                    state.isOffline -> state.offlineSourceIds.getOrNull(page)?.toString() ?: "offline"
                                    state.isMixMode -> slotSourceId.toString()
                                    else -> state.currentSourceId.toString()
                                },
                                resolveUrl = { pageItem ->
                                    screenModel.resolvePlaybackUrl(pageItem, effectiveHd, slotSourceId)
                                },
                                mediaDescriptor = mediaDescriptor,
                                isLastPage = page == state.items.lastIndex,
                                isTouchLocked = touchLocked,
                                onToggleTouchLock = { touchLocked = !touchLocked },
                                landscapeBrightnessGestureEnabled = state.landscapeBrightnessGesture,
                                landscapeVolumeGestureEnabled = state.landscapeVolumeGesture,
                                landscapePullUpSeekEnabled = state.landscapePullUpSeek,
                                onToggleLandscapeBrightnessGesture = screenModel::toggleLandscapeBrightnessGesture,
                                onToggleLandscapeVolumeGesture = screenModel::toggleLandscapeVolumeGesture,
                                onToggleLandscapePullUpSeek = screenModel::toggleLandscapePullUpSeek,
                                onUnmuteIfMuted = screenModel::unmuteIfMuted,
                                sourceName = when {
                                    state.isOffline -> ""
                                    state.isMixMode -> stringResource(MR.strings.reels_mix_title)
                                    else -> state.sourceName
                                },
                                feedSectionLabel = when {
                                    state.isOffline -> stringResource(MR.strings.reels_favorites_title)
                                    state.isMixMode -> stringResource(MR.strings.reels_menu_section_for_you)
                                    state.mode == ReelsFeedScreenModel.FeedMode.FOLLOWING ->
                                        stringResource(MR.strings.reels_following_feed)
                                    state.mode == ReelsFeedScreenModel.FeedMode.CREATOR ->
                                        "@${state.creator.orEmpty()}"
                                    state.mode == ReelsFeedScreenModel.FeedMode.CUSTOM ->
                                        state.customFeedName?.takeIf { it.isNotBlank() }
                                    state.mode == ReelsFeedScreenModel.FeedMode.NICHE ->
                                        state.nicheName?.takeIf { it.isNotBlank() }
                                    else -> stringResource(MR.strings.reels_menu_section_for_you)
                                },
                                sourceBadgeName = if (state.isMixMode) state.slotSourceName(page) else null,
                                sourceBadgeIcon = if (state.isMixMode) state.sourceIcons[slotSourceId] else null,
                                onSourceBadgeClick = if (state.isMixMode) {
                                    { screenModel.switchSource(slotSourceId) }
                                } else {
                                    null
                                },
                                onExcludeSourceFromMix = if (state.isMixMode) {
                                    { screenModel.excludeSourceFromMix(slotSourceId) }
                                } else {
                                    null
                                },
                                playlistItems = state.items,
                                playlistIndex = page,
                                onSelectPlaylistItem = { target ->
                                    coroutineScope.launch {
                                        pagerState.scrollToPage(target)
                                    }
                                },
                                onToggleCropMode = screenModel::toggleCropMode,
                                onToggleQuality = screenModel::toggleQuality,
                                onToggleAutoAdvance = screenModel::toggleAutoAdvance,
                                preloadWifiOnly = state.preloadWifiOnly,
                                onTogglePreloadWifiOnly = screenModel::togglePreloadWifiOnly,
                                dataSaverEnabled = state.dataSaverMetered,
                                onToggleDataSaver = screenModel::toggleDataSaver,
                                sleepTimerValue = sleepTimerValue,
                                onCycleSleepTimer = {
                                    screenModel.setSleepTimer(
                                        when {
                                            state.sleepTimerRemainingSec == 0 && !state.sleepAtVideoEnd ->
                                                ReelsFeedScreenModel.SleepTimerOption.END_OF_VIDEO
                                            state.sleepAtVideoEnd ->
                                                ReelsFeedScreenModel.SleepTimerOption.M15
                                            state.sleepTimerRemainingSec in 1..(15 * 60) ->
                                                ReelsFeedScreenModel.SleepTimerOption.M30
                                            state.sleepTimerRemainingSec in (15 * 60 + 1)..(30 * 60) ->
                                                ReelsFeedScreenModel.SleepTimerOption.M60
                                            else ->
                                                ReelsFeedScreenModel.SleepTimerOption.OFF
                                        },
                                    )
                                },
                                offlineUsedText = Formatter.formatFileSize(context, offlineUsedBytes),
                                offlineUsedFraction = (
                                    offlineUsedBytes.toFloat() /
                                        ReelsOfflineStore.MAX_OFFLINE_BYTES.toFloat()
                                    ).coerceIn(0f, 1f),
                                canClearOffline = offlineUsedBytes > 0L,
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
                                isIncognito = isIncognito,
                                onToggleIncognito = screenModel::toggleReelsIncognito,
                                onClearOffline = { confirmClearOffline = true },
                                onBack = {
                                    when {
                                        state.isSearchBarOpen -> screenModel.toggleSearchBar(false)
                                        state.searchQuery.isNotBlank() -> screenModel.clearSearch()
                                        landscape && activity != null ->
                                            ReelsOrientationLock.setLandscape(activity, false)
                                        else -> navigator.pop()
                                    }
                                },
                                onToggleChrome = { chromeVisible = !chromeVisible },
                                headers = state.slotHeaders(page),
                            )
                        }
                    }

                    // Pull-to-refresh Aurora indicator pill on page 0
                    val pullProgress = (pullRefreshOffsetPx / pullRefreshThresholdPx).coerceIn(0f, 1.2f)
                    if (pullProgress > 0.05f && !inPictureInPicture) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = (56.dp + (pullProgress * 36f).dp))
                                .graphicsLayer {
                                    scaleX = pullProgress.coerceIn(0.6f, 1.08f)
                                    scaleY = pullProgress.coerceIn(0.6f, 1.08f)
                                    alpha = pullProgress.coerceIn(0f, 1f)
                                    rotationZ = pullProgress * 220f
                                }
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141822).copy(alpha = 0.88f))
                                .border(
                                    1.dp,
                                    if (pullProgress >=
                                        1f
                                    ) {
                                        AuroraTheme.colors.accent
                                    } else {
                                        Color.White.copy(alpha = 0.25f)
                                    },
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = if (pullProgress >= 1f) AuroraTheme.colors.accent else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Next-page loading indicator (the PiP frame shows only the reel). Only the
                    // tail of the loaded list can actually wait for it: the adaptive runway
                    // prefetches in the background and must not flicker over the reel on screen.
                    if (state.isLoading && state.items.isNotEmpty() && !inPictureInPicture &&
                        state.canLoadMore && pagerState.currentPage >= state.items.lastIndex
                    ) {
                        ReelsNextPageLoader(modifier = Modifier.align(Alignment.BottomCenter))
                    }
                }
            }

            // "Tap to unmute" (approved variant A): visible while the session sound is
            // undecided; tapping unmutes (and decides), otherwise it auto-dismisses.
            AnimatedVisibility(
                visible = state.showUnmuteHint && state.isMuted &&
                    !inPictureInPicture,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(if (windowIsLandscape) Alignment.TopCenter else Alignment.BottomCenter)
                    .then(
                        if (windowIsLandscape) {
                            Modifier.padding(top = if (chromeVisible) 66.dp else 18.dp)
                        } else {
                            Modifier.padding(bottom = 150.dp)
                        },
                    ),
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

            // Portrait Top Bar Overlay; suppressed in landscape (where ReelsLandscapeTopBar owns
            // navigation, source/feed pills, inline search, and settings) and in PiP.
            AnimatedVisibility(
                visible = !inPictureInPicture &&
                    !windowIsLandscape &&
                    (
                        chromeVisible || moreMenuOpen ||
                            state.isSearchBarOpen || state.isFilterDialogOpen ||
                            state.isSourcePickerOpen || state.isLoginDialogOpen || state.isCustomFeedsOpen ||
                            state.isWebLoginDialogOpen
                        ),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val activeSlotSourceId = state.slotSourceId(state.activeIndex)
                ReelsTopBar(
                    sourceName = when {
                        state.isOffline -> stringResource(MR.strings.reels_favorites_title)
                        state.isMixMode -> stringResource(MR.strings.reels_mix_title)
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
                    sourceIcon = if (state.isMixMode) null else state.sourceIcons[state.currentSourceId],
                    searchQuery = state.searchQuery,
                    isSearchBarOpen = state.isSearchBarOpen,
                    isAutoAdvance = state.isAutoAdvance,
                    isCropMode = state.isCropMode,
                    isHdQuality = state.isHdQuality,
                    qualityModeEnabled = state.items.getOrNull(state.activeIndex)
                        ?.let { screenModel.isQualityModeItem(it.id, activeSlotSourceId) } == true,
                    qualityLabel = state.items.getOrNull(state.activeIndex)
                        ?.let { screenModel.qualityPlayback(it.id, activeSlotSourceId) }
                        ?.decision?.variant?.let { variant ->
                            val shortEdge = listOfNotNull(variant.width, variant.height)
                                .minOrNull()?.takeIf { it > 0 }
                            if (variant.isSource) "Source" else shortEdge?.let { "${it}p" }
                        },
                    onOpenQualityVideo = {
                        val item = state.items.getOrNull(state.activeIndex)
                        if (item != null) {
                            portraitQualityItemId = item.id
                            portraitQualitySourceId = activeSlotSourceId
                            portraitQualityDialogMode = ReelsQualityDialogMode.VIDEO
                        }
                    },
                    onOpenQualityDefault = {
                        val item = state.items.getOrNull(state.activeIndex)
                        if (item != null) {
                            portraitQualityItemId = item.id
                            portraitQualitySourceId = activeSlotSourceId
                            portraitQualityDialogMode = ReelsQualityDialogMode.GLOBAL
                        }
                    },
                    dataSaverEnabled = state.dataSaverMetered,
                    preloadEnabled = state.preloadEnabled,
                    preloadWifiOnly = state.preloadWifiOnly,
                    isOffline = state.isOffline,
                    // Search and source picking belong to the global feed; the filter sheet additionally
                    // opens in NICHE mode for order-capable sources (contract v21).
                    showSearch = state.supportsSearch &&
                        state.supportsTags &&
                        state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL,
                    showFilter = state.supportsFilter && (
                        state.mode == ReelsFeedScreenModel.FeedMode.GLOBAL ||
                            (state.mode == ReelsFeedScreenModel.FeedMode.NICHE && state.isCategoryOrderCapable)
                        ),
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
                    showAccount = !state.isMixMode && (
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
                    landscapeBrightnessGestureEnabled = state.landscapeBrightnessGesture,
                    onToggleLandscapeBrightnessGesture = screenModel::toggleLandscapeBrightnessGesture,
                    landscapeVolumeGestureEnabled = state.landscapeVolumeGesture,
                    onToggleLandscapeVolumeGesture = screenModel::toggleLandscapeVolumeGesture,
                    landscapePullUpSeekEnabled = state.landscapePullUpSeek,
                    onToggleLandscapePullUpSeek = screenModel::toggleLandscapePullUpSeek,
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
                    isUnseenOnly = if (state.isMixMode) state.mixUnseenOnly else state.unseenOnly,
                    onToggleUnseenOnly = {
                        val currentUnseen = if (state.isMixMode) state.mixUnseenOnly else state.unseenOnly
                        screenModel.setUnseenOnly(!currentUnseen)
                    },
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
                    onOpenAlbums = { navigator.push(ReelsAlbumsScreen(null)) },
                    nicheAlbumSaved = if (nicheId != null) nicheId in state.savedAlbumIds else null,
                    onToggleNicheAlbum = { screenModel.toggleCurrentNicheAlbum() },
                    onOpenFollows = { navigator.push(ReelsFollowsScreen(sourceId = state.currentSourceId)) },
                    onSearch = screenModel::search,
                    onClearSearch = screenModel::clearSearch,
                    modifier = Modifier,
                )
            }

            portraitQualityDialogMode?.let { mode ->
                val itemId = portraitQualityItemId
                val targetSourceId = portraitQualitySourceId ?: state.slotSourceId(state.activeIndex)
                val targetQualityState = if (state.isMixMode) {
                    state.mixQualityStates[targetSourceId]
                } else {
                    state.qualityState
                }
                ReelsVideoQualityDialog(
                    mode = mode,
                    itemState = itemId?.let { targetQualityState?.items?.get(it) },
                    global = targetQualityState?.global ?: ReelsQualityPreference.Source,
                    onSelect = { preference ->
                        if (itemId != null) {
                            if (mode == ReelsQualityDialogMode.VIDEO) {
                                screenModel.setQualityOverrideForSource(itemId, preference, targetSourceId)
                            } else {
                                screenModel.setQualityGlobalForSource(preference, targetSourceId)
                            }
                        }
                        portraitQualityDialogMode = null
                    },
                    onUseDefault = {
                        itemId?.let { screenModel.clearQualityOverrideForSource(it, targetSourceId) }
                        portraitQualityDialogMode = null
                    },
                    onDismiss = { portraitQualityDialogMode = null },
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
                    allSourcesId = SourcePreferences.REELS_ALL_SOURCES_ID,
                    excludedMixSourceIds = state.excludedMixSourceIds,
                    mixBurstSize = state.mixBurstSize,
                    mixUnseenOnly = state.mixUnseenOnly,
                    mixSourceStatuses = state.mixSourceStatuses,
                    onToggleMixSourceExcluded = screenModel::toggleMixSourceExcluded,
                    onSetMixBurstSize = screenModel::setMixBurstSize,
                    onToggleMixUnseenOnly = { screenModel.setMixUnseenOnly(!state.mixUnseenOnly) },
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
            // here (the pager swipe is additionally disabled); a long-press or pill tap unlocks.
            if (touchLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { },
                                onDoubleTap = { },
                                onLongPress = { touchLocked = false },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF141822).copy(alpha = 0.88f))
                            .border(1.dp, AuroraTheme.colors.accent.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                            .clickable { touchLocked = false }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = stringResource(MR.strings.reels_touch_lock),
                            tint = AuroraTheme.colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(MR.strings.reels_touch_lock_hint),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
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
    blockingSurfaceOpen: Boolean = false,
): Boolean = !blockingSurfaceOpen && visible && actuallyPlaying && hasItems && !hasError && !isLoading

/**
 * Status bar policy (device report): the system status icons follow the feed chrome. The
 * chrome counts as visible while the top bar is up or any blocking surface (settings block,
 * search bar, source/account sheets and dialogs) is presented over the feed — those need
 * the bar for a sane visual frame.
 */
internal fun reelsSystemChromeVisible(
    chromeVisible: Boolean,
    moreMenuOpen: Boolean,
    qualityDialogOpen: Boolean = false,
    isSearchBarOpen: Boolean,
    isFilterDialogOpen: Boolean,
    isSourcePickerOpen: Boolean,
    isLoginDialogOpen: Boolean,
    isCustomFeedsOpen: Boolean,
    isWebLoginDialogOpen: Boolean,
): Boolean = chromeVisible || moreMenuOpen || qualityDialogOpen || isSearchBarOpen || isFilterDialogOpen ||
    isSourcePickerOpen || isLoginDialogOpen || isCustomFeedsOpen || isWebLoginDialogOpen

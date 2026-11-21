package eu.kanade.tachiyomi.ui.reels.player

import android.app.Activity
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.transformations
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.reels.ReelsHlsDownloader
import eu.kanade.tachiyomi.ui.reels.components.computeScrubThumbnailSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Vertical-feed video player backed by a pooled [ExoPlayer] and [ReelsDynamicLoadControl].
 *
 * - [isActive]: the settled page. The player renders video, unlocks the 50 s buffer ceiling, and
 *   follows [isPlaying] gated by foreground lifecycle / PiP.
 * - [isPreload]: an adjacent (next) page. A pooled player + pre-attached [TextureView] prepare
 *   with `playWhenReady = false` and a 15 s preload buffer cap so frame-0 is already decoded on
 *   the surface the moment the user starts swiping.
 *
 * Switching [videoUrl] / [mediaIdentity] (quality toggle or expired-URL refresh) swaps the
 * [MediaSource] in-place on the live player with `seekTo(currentPos)` — zero decoder teardown and
 * zero poster flash.
 */
@OptIn(UnstableApi::class)
@Composable
fun ReelsPlayerView(
    videoUrl: String,
    posterUrl: String,
    isActive: Boolean,
    isPreload: Boolean,
    isMuted: Boolean,
    isPlaying: Boolean,
    isAutoAdvance: Boolean,
    isCropMode: Boolean,
    // Contract v25 media descriptor: true/false is the source's authoritative media kind;
    // null keeps the legacy URL-extension inference below.
    isStill: Boolean? = null,
    // The page knows no stream can arrive (blank URL without a usable resolver): keep the
    // poster but drop the loading spinner that would otherwise spin forever.
    isMediaUnplayable: Boolean = false,
    seekToFraction: Float? = null,
    seekRequest: ReelsSeekRequest? = null,
    seekToPositionMs: Long? = null,
    onSeekPositionConsumed: () -> Unit = {},
    isScrubbing: Boolean = false,
    onScrubFrameCaptured: ((fraction: Float, bitmap: ImageBitmap) -> Unit)? = null,
    onRenderedFirstFrame: () -> Unit = {},
    onProgressUpdate: (Float) -> Unit,
    onBufferUpdate: (Float) -> Unit = {},
    onVideoCompleted: () -> Unit,
    // Increment to re-prepare the current media from 0 after a playback error (retry action).
    retrySignal: Int = 0,
    // The last feed page loops instead of ending (auto-advance has nowhere to go).
    isLastPage: Boolean = false,
    // Stable progressive-stream cache key (sourceId:videoId:quality) so re-watches hit the
    // disk cache even after signed CDN URLs rotate.
    cacheKey: String? = null,
    // Contract v29: identity of the clip (dwell/visit) and of the actual media bytes. A
    // quality switch must replace the player source even when the URL string is unchanged;
    // the clip identity keeps dwell/visit scoped to the item, not the rendition.
    clipIdentity: String? = null,
    mediaIdentity: String? = null,
    // TalkBack description of the playing video (title/author from the item).
    videoDescription: String? = null,
    // Feed metadata duration in seconds, used as fallback when ExoPlayer reports C.TIME_UNSET.
    knownDurationSec: Float = 0f,
    onDurationKnown: (Float) -> Unit = {},
    // Fired from onVideoSizeChanged once real dimensions are known: true for
    // landscape reels (the only ones offered the landscape-fullscreen affordance).
    onVideoLandscapeKnown: (Boolean) -> Unit = {},
    // Measured frame dimensions for the PiP window shape (audit C03); zero-sized/unknown
    // frames never fire.
    onVideoSizeKnown: (Int, Int) -> Unit = { _, _ -> },
    onPlaybackError: (String) -> Unit = {},
    onBufferingChanged: (Boolean) -> Unit = {},
    // Actual Player.isPlaying changes of this player (buffering, audio-focus loss, lifecycle
    // pause, end): the feed needs the REAL playback state, not only the play intent.
    onPlaybackRunningChanged: (Boolean) -> Unit = {},
    // Smart self-recovery hooks: silent URL re-signing on 401/403/410 and SD/H.264 fallback on
    // hardware decoder errors. Return true when recovery was initiated (suppresses error toast).
    onRefreshExpiredUrl: (() -> Boolean)? = null,
    onCodecFallback: (() -> Boolean)? = null,
    isInPictureInPicture: Boolean = false,
    playbackSpeed: Float = 1f,
    zoomScale: Float = 1f,
    zoomOffsetX: Float = 0f,
    zoomOffsetY: Float = 0f,
    headers: Map<String, String> = emptyMap(),
    // Item page URL (e.g. https://fikfap.com/post/123): origin used as the Referer fallback
    // when the feed source exposes no headers of its own.
    webUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    // Lazy signing (balbums v6+): a blank url means the stream is signed just-in-time by the
    // page; show the poster layer with a spinner until the resolved url arrives instead of
    // building a player around an empty MediaItem (which would surface a playback error).
    if (videoUrl.isBlank()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = if (isCropMode) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (!isMediaUnplayable) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                )
            }
        }
        return
    }
    val context = LocalContext.current
    // Live state snapshots: Player.Listener and surface callbacks are attached once per pooled
    // player instance and MUST read current composition values, never stale preload-time captures.
    val currentIsActive by rememberUpdatedState(isActive)
    val currentIsPreload by rememberUpdatedState(isPreload)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentIsMuted by rememberUpdatedState(isMuted)
    val currentIsAutoAdvance by rememberUpdatedState(isAutoAdvance)
    val currentIsLastPage by rememberUpdatedState(isLastPage)
    val currentCropMode by rememberUpdatedState(isCropMode)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentZoomOffsetX by rememberUpdatedState(zoomOffsetX)
    val currentZoomOffsetY by rememberUpdatedState(zoomOffsetY)
    val currentPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val currentInPip by rememberUpdatedState(isInPictureInPicture)
    val currentOnSeekPositionConsumed by rememberUpdatedState(onSeekPositionConsumed)
    val currentOnRenderedFirstFrame by rememberUpdatedState(onRenderedFirstFrame)
    val currentOnProgressUpdate by rememberUpdatedState(onProgressUpdate)
    val currentOnBufferUpdate by rememberUpdatedState(onBufferUpdate)
    val currentOnVideoCompleted by rememberUpdatedState(onVideoCompleted)
    val currentKnownDurationSec by rememberUpdatedState(knownDurationSec)
    val currentOnDurationKnown by rememberUpdatedState(onDurationKnown)
    val currentOnVideoLandscapeKnown by rememberUpdatedState(onVideoLandscapeKnown)
    val currentOnVideoSizeKnown by rememberUpdatedState(onVideoSizeKnown)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnBufferingChanged by rememberUpdatedState(onBufferingChanged)
    val currentOnPlaybackRunningChanged by rememberUpdatedState(onPlaybackRunningChanged)
    val currentOnRefreshExpiredUrl by rememberUpdatedState(onRefreshExpiredUrl)
    val currentOnCodecFallback by rememberUpdatedState(onCodecFallback)
    val currentIsScrubbing by rememberUpdatedState(isScrubbing)
    val currentOnScrubFrameCaptured by rememberUpdatedState(onScrubFrameCaptured)
    var lastScrubCaptureAtMs by remember { mutableLongStateOf(0L) }
    var lastRequestedScrubFraction by remember { mutableFloatStateOf(0f) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val currentLifecycleStarted by rememberUpdatedState(isLifecycleStarted)
    var pooledPlayer by remember { mutableStateOf<PooledReelsPlayer?>(null) }
    var attachedListener by remember { mutableStateOf<Player.Listener?>(null) }
    val player: ExoPlayer? = pooledPlayer?.player

    // P01: a real background stop clears the play intent even while BUFFERING, where
    // Player.isPlaying is false but playWhenReady is still true. PiP is the one stop state
    // that keeps playing; ON_START restores the current page's intent from live values.
    // P04: registered for stills too — their dwell must freeze in the background exactly
    // like a video's playback.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isLifecycleStarted = event.targetState.isAtLeast(Lifecycle.State.STARTED)
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val inPip = currentInPip || (context as? Activity)?.isInPictureInPictureMode == true
                    if (reelsShouldPauseOnStop(inPictureInPicture = inPip)) {
                        pooledPlayer?.player?.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    pooledPlayer?.player?.playWhenReady = reelsShouldRunPlayback(
                        isActive = currentIsActive,
                        isPlaying = currentIsPlaying,
                        lifecycleStarted = true,
                    )
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Photo reels (mixed albums): ExoPlayer cannot decode stills — render the image and
    // drive the minimum-dwell progress/advance ourselves (device fix for photo albums).
    // v25: a declared STILL kind covers opaque/extensionless photo URLs.
    if (isStill ?: isReelImageUrl(videoUrl)) {
        val photoClock = remember(videoUrl) { ReelsWatchClock() }
        var photoCompleted by remember(videoUrl) { mutableStateOf(false) }
        var photoReady by remember(videoUrl) { mutableStateOf(false) }
        LaunchedEffect(videoUrl) {
            if (!photoReady) {
                delay(STILL_READY_GRACE_MS)
                photoReady = true
            }
        }
        val photoEligible = reelsStillDwellEligible(
            isActive = isActive,
            isPlaying = isPlaying,
            lifecycleStarted = isLifecycleStarted || isInPictureInPicture,
            completed = photoCompleted,
            mediaReady = photoReady,
        )
        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                .background(Color.Black),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            val stillScale = if (isCropMode) ContentScale.Crop else ContentScale.Fit
            val stillZoomMod = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffsetX
                    translationY = zoomOffsetY
                    clip = true
                }
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = stillScale,
                    modifier = stillZoomMod,
                )
            }
            AsyncImage(
                model = videoUrl,
                contentDescription = videoDescription,
                contentScale = stillScale,
                modifier = stillZoomMod,
                onSuccess = { photoReady = true },
                onError = { photoReady = true },
            )
        }
        LaunchedEffect(photoEligible) {
            val now = SystemClock.elapsedRealtime()
            if (!photoEligible) {
                photoClock.stop(now)
                currentOnPlaybackRunningChanged(false)
                currentOnBufferingChanged(false)
                return@LaunchedEffect
            }
            currentOnBufferingChanged(false)
            currentOnPlaybackRunningChanged(true)
            currentOnRenderedFirstFrame()
            photoClock.start(now)
            while (true) {
                val dwellMs = photoClock.elapsedMs(SystemClock.elapsedRealtime())
                currentOnProgressUpdate((dwellMs.toFloat() / REELS_MIN_DWELL_MS).coerceIn(0f, 1f))
                if (dwellMs >= REELS_MIN_DWELL_MS) break
                withFrameNanos { }
            }
            photoClock.stop(SystemClock.elapsedRealtime())
            currentOnProgressUpdate(1f)
            photoCompleted = true
        }
        LaunchedEffect(photoCompleted, isActive, isLifecycleStarted, isAutoAdvance, isLastPage) {
            if (photoCompleted && isActive && isLifecycleStarted && isAutoAdvance && !isLastPage) {
                currentOnVideoCompleted()
            }
        }
        LaunchedEffect(isActive) {
            if (!isActive) {
                photoCompleted = false
                photoClock.reset()
            }
        }
        return
    }

    val networkClient = ReelsPlayerPool.sharedNetworkClient
    val blurTransformation = remember { ReelsBlurTransformation() }

    // Per-clip retry budgets; keyed on clipIdentity (or videoUrl when no clipIdentity) so
    // quality/URL switches keep recovery attempts bounded.
    val recoveryScopeKey = clipIdentity ?: videoUrl
    var autoRetry429Count by remember(videoUrl) { mutableIntStateOf(0) }
    var pending429Retry by remember(videoUrl) { mutableIntStateOf(0) }
    var autoRetryIoCount by remember(videoUrl) { mutableIntStateOf(0) }
    var pendingIoRetry by remember(videoUrl) { mutableIntStateOf(0) }
    var expiredUrlRefreshAttempted by remember(recoveryScopeKey) { mutableStateOf(false) }
    var codecFallbackAttempted by remember(recoveryScopeKey) { mutableStateOf(false) }
    var lastSafePositionMs by remember(recoveryScopeKey) { mutableLongStateOf(0L) }
    var isFirstFrameRendered by remember(recoveryScopeKey) { mutableStateOf(false) }
    var firstFrameReportedForActive by remember(recoveryScopeKey) { mutableStateOf(false) }
    val dwellClock = remember(recoveryScopeKey) { ReelsWatchClock() }
    var endHold by remember(recoveryScopeKey) { mutableStateOf<ReelsEndHold?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    var videoWidth by remember(recoveryScopeKey) { mutableIntStateOf(0) }
    var videoHeight by remember(recoveryScopeKey) { mutableIntStateOf(0) }
    var videoPixelRatio by remember(recoveryScopeKey) { mutableFloatStateOf(1f) }

    var restorePositionMs by remember(recoveryScopeKey) { mutableLongStateOf(0L) }
    var pendingSeekFraction by remember(recoveryScopeKey) { mutableFloatStateOf(-1f) }
    var pendingSeekPositionMs by remember(recoveryScopeKey) { mutableLongStateOf(-1L) }
    var createdForUrl by remember { mutableStateOf<String?>(null) }
    var createdForMediaIdentity by remember { mutableStateOf<String?>(null) }

    fun updateMatrix(
        tv: TextureView?,
        vw: Int,
        vh: Int,
        crop: Boolean,
        viewWOverride: Float? = null,
        viewHOverride: Float? = null,
        pixelRatio: Float = videoPixelRatio,
        userZoom: Float = currentZoomScale,
        panX: Float = currentZoomOffsetX,
        panY: Float = currentZoomOffsetY,
    ) {
        if (tv == null) return
        val fallbackSize = pooledPlayer?.player?.videoSize
        val resolvedVw = if (vw > 0) vw else (fallbackSize?.width ?: 0)
        val resolvedVh = if (vh > 0) vh else (fallbackSize?.height ?: 0)
        if (resolvedVw <= 0 || resolvedVh <= 0) return
        val resolvedPixelRatio = pixelRatio.takeIf { it.isFinite() && it > 0f }
            ?: fallbackSize?.pixelWidthHeightRatio?.takeIf { it.isFinite() && it > 0f }
            ?: 1f

        val viewW = (viewWOverride?.takeIf { it > 0f } ?: tv.width.toFloat())
        val viewH = (viewHOverride?.takeIf { it > 0f } ?: tv.height.toFloat())
        val (sx, sy) = calculateReelTextureScale(
            vw = resolvedVw,
            vh = resolvedVh,
            viewW = viewW,
            viewH = viewH,
            crop = crop,
            pixelRatio = resolvedPixelRatio,
        ) ?: return

        val safeZoom = ReelsZoom.normalizeScale(userZoom)
        val matrix = Matrix()
        matrix.setScale(sx * safeZoom, sy * safeZoom, viewW / 2f, viewH / 2f)
        if (ReelsZoom.isZoomed(safeZoom)) {
            val (clampedX, clampedY) = ReelsZoom.clampOffset(panX, panY, safeZoom, viewW, viewH)
            matrix.postTranslate(clampedX, clampedY)
        }
        tv.setTransform(matrix)
        tv.invalidate()
        (tv.parent as? android.view.View)?.invalidate()
        tv.postInvalidateOnAnimation()
    }

    fun buildMediaSource(url: String, key: String?) = run {
        val requestHeaders = reelsRequestHeaders(headers, webUrl)
        val upstream = DefaultDataSource.Factory(
            context.applicationContext,
            OkHttpDataSource.Factory(networkClient)
                .setDefaultRequestProperties(requestHeaders),
        )
        // Loopback streaming proxies (e.g. local TDLib HTTP server on 127.0.0.1) already manage
        // their own disk cache; wrapping them in SimpleCache duplicates disk I/O and adds lock
        // contention without saving any network traffic.
        val dataSourceFactory = if (isLoopbackReelUrl(url)) {
            upstream
        } else {
            CacheDataSource.Factory()
                .setCache(getReelsVideoCache(context.applicationContext))
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        val isProgressiveContainer = isProgressiveReelUrl(url)
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .apply {
                if (key != null && isProgressiveContainer && !isLoopbackReelUrl(url)) {
                    setCustomCacheKey(key)
                }
            }
            .build()
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
        DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory).createMediaSource(mediaItem)
    }

    // Player lifecycle: acquire from pool for active or preloaded pages, return to pool otherwise.
    LaunchedEffect(videoUrl, isActive, isPreload, mediaIdentity) {
        val needed = isActive || isPreload
        if (!needed) {
            dwellClock.stop(SystemClock.elapsedRealtime())
            pooledPlayer?.let { entry ->
                if (entry.player.currentPosition > 0L) restorePositionMs = entry.player.currentPosition
                ReelsPlayerPool.release(entry, attachedListener)
            }
            pooledPlayer = null
            attachedListener = null
            createdForUrl = null
            createdForMediaIdentity = null
            isFirstFrameRendered = false
            firstFrameReportedForActive = false
            endHold = null
            return@LaunchedEffect
        }

        val existingEntry = pooledPlayer
        if (existingEntry == null) {
            autoRetry429Count = 0
            pending429Retry = 0
            autoRetryIoCount = 0
            pendingIoRetry = 0

            val entry = ReelsPlayerPool.acquire(
                context = context,
                isActive = isActive,
                isMuted = currentIsMuted,
            )
            val exoPlayer = entry.player
            val owner = exoPlayer
            val listener = object : Player.Listener {
                private var lastReportedDurationMs = C.TIME_UNSET

                private fun syncDurationAndPendingSeeks() {
                    val playerDurationMs = owner.duration
                    if (playerDurationMs > 0L && playerDurationMs != lastReportedDurationMs) {
                        lastReportedDurationMs = playerDurationMs
                        currentOnDurationKnown(playerDurationMs.toFloat() / 1000f)
                    }
                    if (owner.playbackState != Player.STATE_READY) return
                    val effectiveDurationMs = resolveReelDurationMs(playerDurationMs, currentKnownDurationSec)
                    if (restorePositionMs > 0L) {
                        val targetPos = if (effectiveDurationMs > 0L) {
                            restorePositionMs.coerceIn(0L, effectiveDurationMs)
                        } else {
                            restorePositionMs
                        }
                        owner.seekTo(targetPos)
                        restorePositionMs = 0L
                    }
                    if (pendingSeekPositionMs >= 0L && effectiveDurationMs > 0L) {
                        owner.seekTo(pendingSeekPositionMs.coerceIn(0L, effectiveDurationMs))
                        pendingSeekPositionMs = -1L
                        currentOnSeekPositionConsumed()
                    } else if (pendingSeekFraction >= 0f && effectiveDurationMs > 0L) {
                        owner.seekTo(resolveReelSeekPositionMs(pendingSeekFraction, effectiveDurationMs))
                        pendingSeekFraction = -1f
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (pooledPlayer?.player !== owner) return
                    syncDurationAndPendingSeeks()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (pooledPlayer?.player !== owner) return
                    currentOnBufferingChanged(playbackState == Player.STATE_BUFFERING)
                    when (playbackState) {
                        Player.STATE_READY -> {
                            autoRetry429Count = 0
                            autoRetryIoCount = 0
                            if (owner.currentPosition > 0) {
                                lastSafePositionMs = owner.currentPosition
                            }
                            syncDurationAndPendingSeeks()
                        }
                        Player.STATE_ENDED -> {
                            val dwellMs = dwellClock.elapsedMs(SystemClock.elapsedRealtime())
                            val hold = reelsEndHold(
                                durationMs = resolveReelDurationMs(owner.duration, currentKnownDurationSec),
                                dwellMs = dwellMs,
                                minDwellMs = REELS_MIN_DWELL_MS,
                            )
                            if (hold == null) {
                                currentOnVideoCompleted()
                            } else {
                                if (hold.loop) owner.repeatMode = Player.REPEAT_MODE_ONE
                                endHold = hold
                            }
                        }
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (pooledPlayer?.player !== owner) return
                    videoWidth = videoSize.width
                    videoHeight = videoSize.height
                    videoPixelRatio = videoSize.pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
                    updateMatrix(
                        tv = textureViewRef,
                        vw = videoSize.width,
                        vh = videoSize.height,
                        crop = currentCropMode,
                        pixelRatio = videoPixelRatio,
                    )
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        currentOnVideoSizeKnown(videoSize.width, videoSize.height)
                        currentOnVideoLandscapeKnown(videoSize.width > videoSize.height)
                    }
                }

                override fun onRenderedFirstFrame() {
                    if (pooledPlayer?.player !== owner) return
                    isFirstFrameRendered = true
                    if (currentIsActive) {
                        firstFrameReportedForActive = true
                        if (owner.isPlaying) dwellClock.start(SystemClock.elapsedRealtime())
                        currentOnRenderedFirstFrame()
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    if (pooledPlayer?.player !== owner) return
                    if (playing && isFirstFrameRendered && currentIsActive) {
                        dwellClock.start(SystemClock.elapsedRealtime())
                        if (owner.currentPosition > 0) {
                            lastSafePositionMs = owner.currentPosition
                        }
                    } else {
                        dwellClock.stop(SystemClock.elapsedRealtime())
                    }
                    currentOnPlaybackRunningChanged(playing)
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (pooledPlayer?.player !== owner) return
                    logcat(LogPriority.ERROR) {
                        "Reels player error ${error.errorCodeName} on $videoUrl"
                    }
                    val category = classifyPlaybackError(error)
                    if (category == PlaybackErrorCategory.CODEC_ERROR && !codecFallbackAttempted) {
                        val fallback = currentOnCodecFallback
                        if (fallback != null) {
                            codecFallbackAttempted = true
                            val pos = owner.currentPosition.coerceAtLeast(0L)
                            if (pos > 0L) restorePositionMs = pos
                            if (fallback.invoke()) {
                                currentOnBufferingChanged(true)
                                return
                            }
                        }
                    }
                    if (category == PlaybackErrorCategory.EXPIRED_URL && !expiredUrlRefreshAttempted) {
                        val refresher = currentOnRefreshExpiredUrl
                        if (refresher != null) {
                            expiredUrlRefreshAttempted = true
                            val pos = owner.currentPosition.coerceAtLeast(0L)
                            if (pos > 0L) restorePositionMs = pos
                            if (refresher.invoke()) {
                                currentOnBufferingChanged(true)
                                return
                            }
                        }
                    }
                    if (category == PlaybackErrorCategory.RATE_LIMITED &&
                        autoRetry429Count < MAX_PLAYBACK_429_RETRIES
                    ) {
                        autoRetry429Count++
                        pending429Retry = autoRetry429Count
                        currentOnBufferingChanged(true)
                        return
                    }
                    if (category == PlaybackErrorCategory.TRANSIENT_IO ||
                        (category == PlaybackErrorCategory.EXPIRED_URL && isHttp403(error))
                    ) {
                        if (autoRetryIoCount < MAX_PLAYBACK_IO_RETRIES) {
                            autoRetryIoCount++
                            pendingIoRetry = autoRetryIoCount
                            currentOnBufferingChanged(true)
                            return
                        }
                        val currentPos = owner.currentPosition
                        val fallback = calculateIoFallbackPosition(currentPos, lastSafePositionMs)
                        if (fallback != null) {
                            logcat(LogPriority.WARN) {
                                "Reels player IO error exhausted at ${currentPos}ms on $videoUrl; falling back to ${fallback}ms"
                            }
                            autoRetryIoCount = 0
                            pendingIoRetry = 0
                            evictConnectionsAsync(networkClient)
                            owner.seekTo(fallback)
                            owner.prepare()
                            owner.playWhenReady = reelsShouldRunPlayback(
                                isActive = currentIsActive,
                                isPlaying = currentIsPlaying,
                                lifecycleStarted = currentLifecycleStarted || currentInPip,
                            )
                            currentOnBufferingChanged(true)
                            return
                        }
                    }
                    currentOnPlaybackError(error.localizedMessage ?: error.errorCodeName)
                }
            }
            exoPlayer.apply {
                repeatMode = if (currentIsAutoAdvance && !currentIsLastPage) {
                    Player.REPEAT_MODE_OFF
                } else {
                    Player.REPEAT_MODE_ONE
                }
                volume = if (currentIsMuted) 0f else 1f
                setAudioAttributes(ReelsPlayerPool.reelsAudioAttributes(), /* handleAudioFocus = */ !currentIsMuted)
                setPlaybackSpeed(currentPlaybackSpeed)
                setMediaSource(buildMediaSource(videoUrl, cacheKey))
                playWhenReady = false
                addListener(listener)
                currentSurface?.takeIf { it.isValid }?.let { setVideoSurface(it) }
                prepare()
            }
            attachedListener = listener
            pooledPlayer = entry
            createdForUrl = videoUrl
            createdForMediaIdentity = mediaIdentity
            currentOnPlaybackRunningChanged(false)
        } else if (
            createdForUrl != null &&
            (createdForUrl != videoUrl || createdForMediaIdentity != mediaIdentity)
        ) {
            // In-place quality switch / expired-URL refresh: keep the live ExoPlayer and its
            // rendered frame (no poster flash, no MediaCodec teardown) and seek seamlessly.
            autoRetry429Count = 0
            pending429Retry = 0
            autoRetryIoCount = 0
            pendingIoRetry = 0
            val p = existingEntry.player
            val currentPos = p.currentPosition.coerceAtLeast(0L)
            if (currentPos > 0L) restorePositionMs = currentPos
            p.setMediaSource(buildMediaSource(videoUrl, cacheKey))
            if (restorePositionMs > 0L) p.seekTo(restorePositionMs)
            p.prepare()
            createdForUrl = videoUrl
            createdForMediaIdentity = mediaIdentity
        }

        // Update dynamic buffer ceiling & playback state on active <-> preload transitions.
        pooledPlayer?.let { entry ->
            entry.loadControl.isActivePage = isActive
            val p = entry.player
            currentSurface?.takeIf { it.isValid }?.let { p.setVideoSurface(it) }
            val vs = p.videoSize
            if (vs.width > 0 && vs.height > 0) {
                videoWidth = vs.width
                videoHeight = vs.height
                videoPixelRatio = vs.pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
                updateMatrix(textureViewRef, vs.width, vs.height, currentCropMode, pixelRatio = videoPixelRatio)
            }
            if (isActive) {
                // If frame-0 was already pre-decoded during preload, report firstFrame now.
                if (isFirstFrameRendered && !firstFrameReportedForActive) {
                    firstFrameReportedForActive = true
                    currentOnRenderedFirstFrame()
                }
                val shouldRun = reelsShouldRunPlayback(
                    isActive = true,
                    isPlaying = currentIsPlaying,
                    lifecycleStarted = currentLifecycleStarted || currentInPip,
                )
                p.playWhenReady = shouldRun
                if (p.isPlaying && isFirstFrameRendered) {
                    dwellClock.start(SystemClock.elapsedRealtime())
                }
                currentOnPlaybackRunningChanged(p.isPlaying)
            } else {
                // Preload: keep surface attached so frame-0 pre-decodes, never play audio.
                p.playWhenReady = false
                firstFrameReportedForActive = false
                dwellClock.stop(SystemClock.elapsedRealtime())
            }
        }
    }

    // Minimum-dwell hold for finished short clips.
    endHold?.let { hold ->
        LaunchedEffect(hold, isAutoAdvance, isPlaying, isActive, isLifecycleStarted) {
            if (isAutoAdvance && isPlaying && isActive && isLifecycleStarted) {
                delay(hold.remainingMs)
                endHold = null
                currentOnVideoCompleted()
            }
        }
    }

    // React to Surface becoming available or updated (for BOTH active and preload pages).
    LaunchedEffect(currentSurface, player, isActive, isPreload) {
        val s = currentSurface
        if (s != null && s.isValid && (isActive || isPreload)) {
            player?.let { p ->
                p.setVideoSurface(s)
                val vs = p.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    videoWidth = vs.width
                    videoHeight = vs.height
                    videoPixelRatio = vs.pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
                }
                updateMatrix(textureViewRef, videoWidth, videoHeight, currentCropMode, pixelRatio = videoPixelRatio)
            }
        }
    }

    // React to crop mode, manual zoom/pan, and video size changes.
    LaunchedEffect(
        isCropMode,
        videoWidth,
        videoHeight,
        videoPixelRatio,
        textureViewRef,
        zoomScale,
        zoomOffsetX,
        zoomOffsetY,
    ) {
        updateMatrix(
            tv = textureViewRef,
            vw = videoWidth,
            vh = videoHeight,
            crop = isCropMode,
            pixelRatio = videoPixelRatio,
            userZoom = zoomScale,
            panX = zoomOffsetX,
            panY = zoomOffsetY,
        )
    }

    fun captureScrubFrame(tv: TextureView?, force: Boolean = false) {
        val callback = currentOnScrubFrameCaptured ?: return
        if (tv == null || !tv.isAvailable) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastScrubCaptureAtMs < SCRUB_FRAME_CAPTURE_INTERVAL_MS) return
        val (targetW, targetH) = computeScrubThumbnailSize(
            videoWidth = videoWidth.takeIf { it > 0 } ?: tv.width,
            videoHeight = videoHeight.takeIf { it > 0 } ?: tv.height,
        )
        val bitmap = runCatching { tv.getBitmap(targetW, targetH) }.getOrNull() ?: return
        lastScrubCaptureAtMs = now
        val p = pooledPlayer?.player
        val effectiveDurationMs = p?.let { resolveReelDurationMs(it.duration, currentKnownDurationSec) } ?: 0L
        val capturedFraction = if (p != null && effectiveDurationMs > 0L) {
            (p.currentPosition.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            lastRequestedScrubFraction
        }
        callback(capturedFraction, bitmap.asImageBitmap())
    }

    LaunchedEffect(isScrubbing, textureViewRef) {
        if (isScrubbing) {
            captureScrubFrame(textureViewRef, force = true)
        }
    }

    // React to monotonic seek requests (double-tap ±10s, live keyframe scrub, final commit).
    LaunchedEffect(seekRequest?.id, player) {
        val req = seekRequest ?: return@LaunchedEffect
        val frac = req.fraction.coerceIn(0f, 1f)
        if (req.liveScrub) {
            lastRequestedScrubFraction = frac
        }
        player?.let { p ->
            p.setSeekParameters(if (req.liveScrub) SeekParameters.CLOSEST_SYNC else SeekParameters.DEFAULT)
            val effectiveDurationMs = resolveReelDurationMs(p.duration, currentKnownDurationSec)
            if (effectiveDurationMs > 0L) {
                p.seekTo(resolveReelSeekPositionMs(frac, effectiveDurationMs))
            } else if (!req.liveScrub) {
                pendingSeekFraction = frac
            }
        }
    }

    // Backward-compatible seekToFraction support for callers not using seekRequest.
    LaunchedEffect(seekToFraction, player) {
        seekToFraction?.let { frac ->
            player?.let { p ->
                p.setSeekParameters(SeekParameters.DEFAULT)
                val effectiveDurationMs = resolveReelDurationMs(p.duration, currentKnownDurationSec)
                if (effectiveDurationMs > 0L) {
                    p.seekTo(resolveReelSeekPositionMs(frac, effectiveDurationMs))
                } else {
                    pendingSeekFraction = frac.coerceIn(0f, 1f)
                }
            }
        }
    }

    LaunchedEffect(seekToPositionMs, player) {
        seekToPositionMs?.let { ms ->
            player?.let { p ->
                val effectiveDurationMs = resolveReelDurationMs(p.duration, currentKnownDurationSec)
                if (p.playbackState == Player.STATE_READY && effectiveDurationMs > 0L) {
                    p.seekTo(ms.coerceIn(0L, effectiveDurationMs))
                    currentOnSeekPositionConsumed()
                } else {
                    pendingSeekPositionMs = ms
                }
            }
        }
    }

    // React to auto-advance / last-page changes dynamically — bound to player instance!
    LaunchedEffect(isAutoAdvance, isLastPage, player) {
        player?.repeatMode = if (isAutoAdvance && !isLastPage) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    // Manual retry after a surfaced playback error: explicit restart from 0.
    LaunchedEffect(retrySignal) {
        if (retrySignal > 0 && currentIsActive) {
            autoRetry429Count = 0
            pending429Retry = 0
            autoRetryIoCount = 0
            pendingIoRetry = 0
            expiredUrlRefreshAttempted = false
            codecFallbackAttempted = false
            player?.apply {
                seekTo(0)
                prepare()
                playWhenReady = reelsShouldRunPlayback(
                    isActive = currentIsActive,
                    isPlaying = currentIsPlaying,
                    lifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                        currentInPip,
                )
            }
        }
    }

    // 429 backoff: re-prepare WITHOUT resetting the position and WITHOUT forcing playback.
    LaunchedEffect(pending429Retry, player, isActive, isPlaying, isLifecycleStarted) {
        val attempt = pending429Retry
        val retryPlayer = player
        if (attempt <= 0 || retryPlayer == null) return@LaunchedEffect
        if (!reelsShouldRunPlayback(isActive, isPlaying, isLifecycleStarted || isInPictureInPicture)) {
            currentOnBufferingChanged(false)
            return@LaunchedEffect
        }
        delay(PLAYBACK_429_BACKOFF_MS * attempt)
        val stillStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || currentInPip
        if (player !== retryPlayer || !reelsShouldRunPlayback(currentIsActive, currentIsPlaying, stillStarted)) {
            return@LaunchedEffect
        }
        retryPlayer.prepare()
        retryPlayer.playWhenReady = true
        pending429Retry = 0
    }

    // Transient IO / seek error recovery: re-prepare WITHOUT resetting the position.
    LaunchedEffect(pendingIoRetry, player, isActive, isPlaying, isLifecycleStarted) {
        val attempt = pendingIoRetry
        val retryPlayer = player
        if (attempt <= 0 || retryPlayer == null) return@LaunchedEffect
        if (!reelsShouldRunPlayback(isActive, isPlaying, isLifecycleStarted || isInPictureInPicture)) {
            currentOnBufferingChanged(false)
            return@LaunchedEffect
        }
        evictConnectionsAsync(networkClient)
        delay(PLAYBACK_IO_BACKOFF_MS * attempt)
        val stillStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || currentInPip
        if (player !== retryPlayer || !reelsShouldRunPlayback(currentIsActive, currentIsPlaying, stillStarted)) {
            return@LaunchedEffect
        }
        retryPlayer.prepare()
        retryPlayer.playWhenReady = true
        pendingIoRetry = 0
    }

    // React to play / pause / lifecycle changes — ALWAYS gated by lifecycleStarted / PiP!
    LaunchedEffect(isPlaying, isActive, isLifecycleStarted, isInPictureInPicture, player) {
        player?.let { p ->
            if (isActive) {
                p.playWhenReady = reelsShouldRunPlayback(
                    isActive = true,
                    isPlaying = isPlaying,
                    lifecycleStarted = isLifecycleStarted || isInPictureInPicture,
                )
            } else {
                p.playWhenReady = false
            }
        }
    }

    // React to mute / unmute changes — bound to player instance!
    LaunchedEffect(isMuted, player) {
        player?.apply {
            volume = if (isMuted) 0f else 1f
            setAudioAttributes(ReelsPlayerPool.reelsAudioAttributes(), /* handleAudioFocus = */ !isMuted)
        }
    }

    // React to speed changes (long-press 2x, drawer speed) — bound to player instance!
    LaunchedEffect(playbackSpeed, player) {
        player?.setPlaybackSpeed(playbackSpeed)
    }

    // Smooth 60fps playback progress tracking while actively playing; 4Hz fallback when idle/paused.
    LaunchedEffect(isActive, player) {
        while (isActive) {
            val p = player
            val isActivelyRunning = p?.isPlaying == true
            if (p != null) {
                val effectiveDurationMs = resolveReelDurationMs(p.duration, currentKnownDurationSec)
                if (effectiveDurationMs > 0L) {
                    if (isActivelyRunning && p.currentPosition > 0) {
                        lastSafePositionMs = p.currentPosition
                    }
                    currentOnBufferUpdate(
                        (p.bufferedPosition.toFloat() / effectiveDurationMs.toFloat()).coerceIn(0f, 1f),
                    )
                }
                val progress = resolveReelPlaybackProgress(
                    currentPositionMs = p.currentPosition,
                    effectiveDurationMs = effectiveDurationMs,
                    isFirstFrameRendered = isFirstFrameRendered,
                    isEndedOrHolding = endHold != null || p.playbackState == Player.STATE_ENDED,
                    dwellMs = dwellClock.elapsedMs(SystemClock.elapsedRealtime()),
                )
                if (progress != null) {
                    currentOnProgressUpdate(progress)
                }
            }
            if (isActivelyRunning) {
                withFrameNanos { }
            } else {
                delay(250)
            }
        }
    }

    // Unmount-only release back to pool.
    DisposableEffect(Unit) {
        onDispose {
            dwellClock.stop(SystemClock.elapsedRealtime())
            pooledPlayer?.let { entry ->
                if (entry.player.currentPosition > 0L) restorePositionMs = entry.player.currentPosition
                ReelsPlayerPool.release(entry, attachedListener)
            }
            pooledPlayer = null
            attachedListener = null
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds().background(Color.Black)) {
        // 1. Ambient Blurred Background (Coil downsampled StackBlur cached once off-main-thread).
        // Zero per-frame GPU RenderEffect overhead on scroll, consistent look on API 26–35+.
        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(ReelsBlurTransformation.DEFAULT_MAX_DIMENSION, ReelsBlurTransformation.DEFAULT_MAX_DIMENSION)
                    .transformations(blurTransformation)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.45f),
            )
        }

        // 2. Video Surface Layer — mounted for BOTH active AND preload cards so frame-0 is already
        // decoded onto the TextureView surface before the user starts swiping.
        if (isActive || isPreload) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        textureViewRef = this
                        addOnLayoutChangeListener {
                                view,
                                left,
                                top,
                                right,
                                bottom,
                                oldLeft,
                                oldTop,
                                oldRight,
                                oldBottom,
                            ->
                            val newW = (right - left).toFloat()
                            val newH = (bottom - top).toFloat()
                            val oldW = (oldRight - oldLeft).toFloat()
                            val oldH = (oldBottom - oldTop).toFloat()
                            if (newW > 0f && newH > 0f && (newW != oldW || newH != oldH)) {
                                updateMatrix(
                                    tv = view as? TextureView,
                                    vw = videoWidth,
                                    vh = videoHeight,
                                    crop = currentCropMode,
                                    viewWOverride = newW,
                                    viewHOverride = newH,
                                    pixelRatio = videoPixelRatio,
                                )
                            }
                        }
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val s = Surface(st)
                                currentSurface = s
                                if (currentIsActive || currentIsPreload) {
                                    pooledPlayer?.player?.setVideoSurface(s)
                                }
                                updateMatrix(
                                    tv = this@apply,
                                    vw = videoWidth,
                                    vh = videoHeight,
                                    crop = currentCropMode,
                                    viewWOverride = width.toFloat(),
                                    viewHOverride = height.toFloat(),
                                    pixelRatio = videoPixelRatio,
                                )
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                updateMatrix(
                                    tv = this@apply,
                                    vw = videoWidth,
                                    vh = videoHeight,
                                    crop = currentCropMode,
                                    viewWOverride = width.toFloat(),
                                    viewHOverride = height.toFloat(),
                                    pixelRatio = videoPixelRatio,
                                )
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                pooledPlayer?.player?.setVideoSurface(null)
                                currentSurface?.release()
                                currentSurface = null
                                textureViewRef = null
                                isFirstFrameRendered = false
                                firstFrameReportedForActive = false
                                dwellClock.stop(SystemClock.elapsedRealtime())
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                isFirstFrameRendered = true
                                if (currentIsScrubbing) {
                                    captureScrubFrame(this@apply)
                                }
                            }
                        }
                    }
                },
                update = { tv ->
                    textureViewRef = tv
                    updateMatrix(
                        tv = tv,
                        vw = videoWidth,
                        vh = videoHeight,
                        crop = isCropMode,
                        pixelRatio = videoPixelRatio,
                        userZoom = zoomScale,
                        panX = zoomOffsetX,
                        panY = zoomOffsetY,
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (videoDescription != null) {
                            Modifier.semantics { contentDescription = videoDescription }
                        } else {
                            Modifier
                        },
                    ),
            )
        }

        // 3. Poster / Thumbnail placeholder overlay (hides once frame-0 renders, even during preload)
        AnimatedVisibility(
            visible = !isFirstFrameRendered && posterUrl.isNotBlank(),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize(),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    .size(POSTER_DECODE_SIZE, POSTER_DECODE_SIZE)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = if (isCropMode) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val REELS_CACHE_BYTES = 1024L * 1024 * 1024

// Container suffixes the stable progressive cache key is safe for: adaptive manifests and
// unknown containers keep the URL-derived key (media3 validates setCustomCacheKey usage).
private val PROGRESSIVE_CONTAINER_EXTENSIONS = setOf("mp4", "m4v", "webm", "mov", "mkv", "ts", "m2ts", "3gp")

private const val REELS_CACHE_DIR = "reels_video"

// One decode size for the placeholder overlay.
private const val POSTER_DECODE_SIZE = 480

/** 429 auto-recovery: this many silent backoff re-prepares before the error surfaces. */
private const val MAX_PLAYBACK_429_RETRIES = 3
private const val PLAYBACK_429_BACKOFF_MS = 3_000L

/** Transient IO / seek auto-recovery. */
private const val MAX_PLAYBACK_IO_RETRIES = 3
private const val PLAYBACK_IO_BACKOFF_MS = 1_000L

/** ConnectionPool.evictAll() closes sockets (network IO): never call it on the main thread. */
private fun evictConnectionsAsync(client: OkHttpClient) {
    Thread { client.connectionPool.evictAll() }.start()
}

enum class PlaybackErrorCategory {
    RATE_LIMITED,
    EXPIRED_URL,
    CODEC_ERROR,
    TRANSIENT_IO,
    FATAL,
}

private fun isHttp403(error: Throwable?): Boolean {
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 403) return true
        cause = cause.cause
    }
    return false
}

internal fun classifyPlaybackError(error: Throwable?): PlaybackErrorCategory {
    if (error is PlaybackException) {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> return PlaybackErrorCategory.CODEC_ERROR
        }
    }
    var cause: Throwable? = error
    while (cause != null) {
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return when (cause.responseCode) {
                429 -> PlaybackErrorCategory.RATE_LIMITED
                401, 403, 410 -> PlaybackErrorCategory.EXPIRED_URL
                408, 416, 500, 502, 503, 504 -> PlaybackErrorCategory.TRANSIENT_IO
                else -> PlaybackErrorCategory.FATAL
            }
        } else if (cause is java.io.IOException || cause is HttpDataSource.HttpDataSourceException) {
            return PlaybackErrorCategory.TRANSIENT_IO
        }
        cause = cause.cause
    }
    return PlaybackErrorCategory.FATAL
}

internal fun calculateIoFallbackPosition(
    currentPositionMs: Long,
    lastSafePositionMs: Long,
): Long? {
    if (currentPositionMs <= 0L) return null
    return if (lastSafePositionMs in 1L until (currentPositionMs - 5_000L)) {
        lastSafePositionMs
    } else {
        0L
    }
}

/** Minimum time a reel stays on screen before auto-advance moves on (stills/short clips). */
const val REELS_MIN_DWELL_MS = 5_000L

/**
 * After this long a still's poster counts as the visible media even if the full-size photo
 * request has not completed: a hung CDN call must not stall the feed's auto-advance.
 */
internal const val STILL_READY_GRACE_MS = 6_000L

data class ReelsEndHold(val remainingMs: Long, val loop: Boolean)

internal fun reelsEndHold(durationMs: Long, dwellMs: Long, minDwellMs: Long = REELS_MIN_DWELL_MS): ReelsEndHold? {
    val remainingMs = minDwellMs - dwellMs
    if (remainingMs <= 0) return null
    return ReelsEndHold(remainingMs = remainingMs, loop = durationMs > 0)
}

/**
 * Resolves the effective playback duration in milliseconds.
 *
 * ExoPlayer's [playerDurationMs] is authoritative whenever positive (not [C.TIME_UNSET] or 0).
 * When ExoPlayer cannot determine duration upfront (e.g. fragmented/streamed long videos without
 * header duration), falls back to the feed item's [fallbackDurationSec] so progress and seeking
 * remain functional instead of dropping into the 5-second still-dwell timer.
 */
internal fun resolveReelDurationMs(
    playerDurationMs: Long,
    fallbackDurationSec: Float,
): Long {
    if (playerDurationMs > 0L) return playerDurationMs
    if (fallbackDurationSec.isFinite() && fallbackDurationSec > 0f) {
        return (fallbackDurationSec * 1000f).toLong().coerceAtLeast(1L)
    }
    return 0L
}

/**
 * Converts a normalized seek [fraction] (`0f..1f`) into a safe target position in milliseconds.
 *
 * Clamps the upper bound [eofMarginMs] before the exact end of the container (`effectiveDurationMs`)
 * whenever the clip is longer than [eofMarginMs], so rapid `+10s` double-tap seeks or 100% scrubs
 * never seek to exact byte EOF (`offset == fileSize`), avoiding empty HTTP ranges and C++ stream
 * downloader assertions at the end boundary.
 */
internal fun resolveReelSeekPositionMs(
    fraction: Float,
    effectiveDurationMs: Long,
    eofMarginMs: Long = 250L,
): Long {
    if (effectiveDurationMs <= 0L) return 0L
    val clampedFraction = fraction.coerceIn(0f, 1f)
    val maxPositionMs = if (effectiveDurationMs > eofMarginMs) {
        effectiveDurationMs - eofMarginMs
    } else {
        effectiveDurationMs
    }
    return (clampedFraction * effectiveDurationMs).toLong().coerceIn(0L, maxPositionMs)
}

/**
 * Computes normalized `[0f, 1f]` progress for the seekbar.
 *
 * Only falls back to `dwellMs / minDwellMs` when duration is unknown AND the clip has ended or
 * is holding minimum dwell; an actively playing video with unknown duration stays at `0f`
 * instead of racing the bar to 100% in 5 seconds.
 */
internal fun resolveReelPlaybackProgress(
    currentPositionMs: Long,
    effectiveDurationMs: Long,
    isFirstFrameRendered: Boolean,
    isEndedOrHolding: Boolean,
    dwellMs: Long,
    minDwellMs: Long = REELS_MIN_DWELL_MS,
): Float? {
    if (effectiveDurationMs > 0L) {
        return (currentPositionMs.coerceAtLeast(0L).toFloat() / effectiveDurationMs.toFloat())
            .coerceIn(0f, 1f)
    }
    if (isFirstFrameRendered && isEndedOrHolding && minDwellMs > 0L) {
        return (dwellMs.coerceAtLeast(0L).toFloat() / minDwellMs.toFloat()).coerceIn(0f, 1f)
    }
    return if (isFirstFrameRendered) 0f else null
}

/** P01: a real background stop clears the play intent; PiP is the only exempt stop state. */
internal fun reelsShouldPauseOnStop(inPictureInPicture: Boolean): Boolean = !inPictureInPicture

/** P02: the single playback-eligibility gate every playback/retry path derives its intent from. */
internal fun reelsShouldRunPlayback(isActive: Boolean, isPlaying: Boolean, lifecycleStarted: Boolean): Boolean =
    isActive && isPlaying && lifecycleStarted

internal fun reelsStillDwellEligible(
    isActive: Boolean,
    isPlaying: Boolean,
    lifecycleStarted: Boolean,
    completed: Boolean,
    mediaReady: Boolean,
): Boolean = isActive && isPlaying && lifecycleStarted && !completed && mediaReady

private val reelsVideoCacheOwner = ReelsCacheOwner<SimpleCache>()

@OptIn(UnstableApi::class)
internal fun getReelsVideoCache(context: android.content.Context): SimpleCache {
    return reelsVideoCacheOwner.getOrCreate {
        SimpleCache(
            File(context.cacheDir, REELS_CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(REELS_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }
}

/**
 * Opens a zero-network [InputStream] straight from the process-wide [SimpleCache] when 100% of a
 * progressive reel is already cached on disk. Returns null on cache miss or partial cache.
 */
internal data class CachedReelStream(
    val inputStream: InputStream,
    val contentLength: Long,
)

@OptIn(UnstableApi::class)
internal fun openCachedReelStreamIfComplete(
    context: android.content.Context,
    cacheKeys: List<String>,
): CachedReelStream? {
    val cache = reelsVideoCacheOwner.current() ?: return null
    for (key in cacheKeys) {
        if (key.isBlank()) continue
        val contentLength = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        if (contentLength <= 0L) continue
        if (!cache.isCached(key, 0L, contentLength)) continue
        val dataSource = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(null)
            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
            .createDataSource()
        val spec = DataSpec.Builder()
            .setUri(Uri.EMPTY)
            .setKey(key)
            .setPosition(0L)
            .setLength(contentLength)
            .build()
        return try {
            dataSource.open(spec)
            CachedReelStream(
                inputStream = object : InputStream() {
                    private val oneByte = ByteArray(1)
                    override fun read(): Int {
                        val n = read(oneByte, 0, 1)
                        return if (n == -1) -1 else (oneByte[0].toInt() and 0xFF)
                    }

                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = dataSource.read(b, off, len)
                        return if (n == C.RESULT_END_OF_INPUT) -1 else n
                    }

                    override fun close() {
                        runCatching { dataSource.close() }
                    }
                },
                contentLength = contentLength,
            )
        } catch (_: Exception) {
            runCatching { dataSource.close() }
            null
        }
    }
    return null
}

@OptIn(UnstableApi::class)
internal fun clearReelsVideoCache(context: android.content.Context): Long {
    val dir = File(context.cacheDir, REELS_CACHE_DIR)
    val freedWithoutCache = reelsVideoCacheOwner.clearIfAbsent {
        val freed = dir.walk().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        freed
    }
    if (freedWithoutCache != null) return freedWithoutCache

    val cache = reelsVideoCacheOwner.current() ?: return 0L
    var freed = 0L
    for (key in cache.keys.toList()) {
        for (span in cache.getCachedSpans(key).toList()) {
            freed += span.length
            cache.removeSpan(span)
        }
    }
    return freed
}

internal fun reelsRequestHeaders(headers: Map<String, String>, webUrl: String?): Map<String, String> {
    if (headers.keys.any { it.equals("Referer", ignoreCase = true) }) return headers
    val referer = runCatching {
        val page = Uri.parse(webUrl)
        val host = page.host?.takeIf { it.isNotBlank() }
        if (host != null && (page.scheme == "http" || page.scheme == "https")) {
            val port = if (page.port != -1) ":${page.port}" else ""
            "${page.scheme}://$host$port/"
        } else {
            null
        }
    }.getOrNull() ?: return headers
    return headers + ("Referer" to referer)
}

/** The progressive-container whitelist check for a media URL (stable customCacheKey is safe). */
internal fun isProgressiveReelUrl(url: String): Boolean =
    Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase() in PROGRESSIVE_CONTAINER_EXTENSIONS

/** Detects HLS playlists so head-prewarm can warm the manifest + first segment. */
internal fun isHlsReelUrl(url: String): Boolean =
    Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase() == "m3u8"

/** Photo-reel detection: still-image containers ride the feed as dwell-held stills. */
internal fun isReelImageUrl(url: String): Boolean =
    Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase() in STILL_IMAGE_EXTENSIONS

private val STILL_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

/** Detects local loopback proxy URLs (127.0.0.1 / localhost / ::1) that manage their own cache. */
internal fun isLoopbackReelUrl(url: String): Boolean {
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return false
    return host == "localhost" ||
        host == "127.0.0.1" ||
        host.startsWith("127.") ||
        host == "::1" ||
        host == "[::1]"
}

/** Head-prewarm chunk: how many leading bytes of a reel are warmed ahead of the player window. */
internal const val HEAD_PREWARM_BYTES = 512L * 1024L

/**
 * Warms the first [HEAD_PREWARM_BYTES] of a progressive reel (under [cacheKey]) OR the manifest
 * and first segment of an HLS (.m3u8) reel (under URL-derived keys matching ExoPlayer) into the
 * shared [SimpleCache]. Best-effort; call from a background dispatcher.
 */
@OptIn(UnstableApi::class)
internal fun prewarmReelHead(
    context: android.content.Context,
    url: String,
    cacheKey: String,
    headers: Map<String, String>,
    webUrl: String?,
    isCancelled: () -> Boolean = { false },
) {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
    // Never prewarm local streaming proxies (e.g. TDLib on 127.0.0.1): background head pulls
    // compete directly with the active video's download slots and hold proxy cache leases.
    if (isLoopbackReelUrl(url)) return
    val appContext = context.applicationContext
    val resolvedHeaders = reelsRequestHeaders(headers, webUrl)

    if (isHlsReelUrl(url)) {
        prewarmHlsHead(appContext, url, resolvedHeaders, isCancelled)
        return
    }
    if (!isProgressiveReelUrl(url)) return
    prewarmUrlBytes(
        appContext = appContext,
        url = url,
        cacheKey = cacheKey,
        headers = resolvedHeaders,
        maxBytes = HEAD_PREWARM_BYTES,
        isCancelled = isCancelled,
    )
}

@OptIn(UnstableApi::class)
private fun prewarmHlsHead(
    appContext: android.content.Context,
    manifestUrl: String,
    headers: Map<String, String>,
    isCancelled: () -> Boolean,
) {
    if (isCancelled()) return
    // 1. Warm the master/media manifest URL into SimpleCache (URL-derived key matches ExoPlayer HLS).
    prewarmUrlBytes(
        appContext = appContext,
        url = manifestUrl,
        cacheKey = null,
        headers = headers,
        maxBytes = 128L * 1024L,
        isCancelled = isCancelled,
    )
    if (isCancelled()) return
    // 2. Resolve media playlist + first segment and prewarm them into SimpleCache.
    val playlist = runCatching {
        runBlocking { ReelsHlsDownloader.resolveMediaPlaylist(prewarmNetworkClient, manifestUrl, headers) }
    }.getOrNull() ?: return
    if (isCancelled()) return
    if (playlist.mediaPlaylistUrl != manifestUrl) {
        prewarmUrlBytes(
            appContext = appContext,
            url = playlist.mediaPlaylistUrl,
            cacheKey = null,
            headers = headers,
            maxBytes = 128L * 1024L,
            isCancelled = isCancelled,
        )
    }
    if (isCancelled()) return
    playlist.initSegmentUrl?.let { initUrl ->
        prewarmUrlBytes(
            appContext = appContext,
            url = initUrl,
            cacheKey = null,
            headers = headers,
            maxBytes = 128L * 1024L,
            isCancelled = isCancelled,
        )
    }
    if (isCancelled()) return
    playlist.segmentUrls.firstOrNull()?.let { firstSegUrl ->
        prewarmUrlBytes(
            appContext = appContext,
            url = firstSegUrl,
            cacheKey = null,
            headers = headers,
            maxBytes = HEAD_PREWARM_BYTES,
            isCancelled = isCancelled,
        )
    }
}

@OptIn(UnstableApi::class)
private fun prewarmUrlBytes(
    appContext: android.content.Context,
    url: String,
    cacheKey: String?,
    headers: Map<String, String>,
    maxBytes: Long,
    isCancelled: () -> Boolean,
) {
    val cache = getReelsVideoCache(appContext)
    val effectiveKey = cacheKey ?: url
    if (cache.isCached(effectiveKey, 0, maxBytes)) return
    val upstream = DefaultDataSource.Factory(
        appContext,
        OkHttpDataSource.Factory(prewarmNetworkClient)
            .setDefaultRequestProperties(headers),
    )
    val dataSource = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
    val spec = DataSpec.Builder()
        .setUri(url)
        .apply { if (cacheKey != null) setKey(cacheKey) }
        .setLength(maxBytes)
        .build()
    try {
        dataSource.open(spec)
        val buffer = ByteArray(64 * 1024)
        while (!isCancelled()) {
            if (dataSource.read(buffer, 0, buffer.size) == C.RESULT_END_OF_INPUT) break
        }
    } catch (_: Exception) {
        // Prewarm is best-effort; a failed chunk is simply a cache miss later.
    } finally {
        runCatching { dataSource.close() }
    }
}

private val prewarmNetworkClient: OkHttpClient by lazy {
    Injekt.get<NetworkHelper>().client.newBuilder()
        .cache(null)
        .callTimeout(HEAD_PREWARM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()
}

private const val HEAD_PREWARM_TIMEOUT_MS = 20_000L

internal fun calculateReelTextureScale(
    vw: Int,
    vh: Int,
    viewW: Float,
    viewH: Float,
    crop: Boolean,
    pixelRatio: Float = 1f,
): Pair<Float, Float>? {
    if (vw <= 0 || vh <= 0 || viewW <= 0f || viewH <= 0f) return null
    val safePixelRatio = pixelRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val displayVw = vw.toFloat() * safePixelRatio
    val displayVh = vh.toFloat()
    val videoRatio = displayVw / displayVh
    val viewRatio = viewW / viewH

    return if (crop) {
        if (videoRatio > viewRatio) {
            ((viewH * displayVw / displayVh) / viewW) to 1f
        } else {
            1f to ((viewW * displayVh / displayVw) / viewH)
        }
    } else {
        if (videoRatio > viewRatio) {
            1f to ((viewW * displayVh / displayVw) / viewH)
        } else {
            ((viewH * displayVw / displayVh) / viewW) to 1f
        }
    }
}

private const val SCRUB_FRAME_CAPTURE_INTERVAL_MS = 65L

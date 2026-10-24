package eu.kanade.tachiyomi.ui.reels.player

import android.app.Activity
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.delay
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * Vertical-feed video player backed by ExoPlayer.
 *
 * - [isActive]: the settled page. The player renders video and follows [isPlaying].
 * - [isPreload]: an adjacent (next) page. The player is prepared with `playWhenReady = false`
 *   and no surface so the first seconds are buffered before the user swipes to it.
 *
 * Switching [videoUrl] (e.g. HD/SD quality toggle) rebuilds the player and restores the
 * playback position captured right before the rebuild.
 */
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
    seekToFraction: Float?,
    onProgressUpdate: (Float) -> Unit,
    onVideoCompleted: () -> Unit,
    // Increment to re-prepare the current media from 0 after a playback error (retry action).
    retrySignal: Int = 0,
    // The last feed page loops instead of ending (auto-advance has nowhere to go).
    isLastPage: Boolean = false,
    // Stable progressive-stream cache key (sourceId:videoId:quality) so re-watches hit the
    // disk cache even after signed CDN URLs rotate.
    cacheKey: String? = null,
    // TalkBack description of the playing video (title/author from the item).
    videoDescription: String? = null,
    onDurationKnown: (Float) -> Unit = {},
    // Fired from onVideoSizeChanged once real dimensions are known: true for
    // landscape reels (the only ones offered the landscape-fullscreen affordance).
    onVideoLandscapeKnown: (Boolean) -> Unit = {},
    onPlaybackError: (String) -> Unit = {},
    onBufferingChanged: (Boolean) -> Unit = {},
    // Actual Player.isPlaying changes of this player (buffering, audio-focus loss, lifecycle
    // pause, end): the feed needs the REAL playback state, not only the play intent.
    onPlaybackRunningChanged: (Boolean) -> Unit = {},
    playbackSpeed: Float = 1f,
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
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White,
            )
        }
        return
    }
    val context = LocalContext.current
    // P02: delayed retries read the LIVE page intent, not the values captured when the
    // player listener was created.
    val currentIsActive by rememberUpdatedState(isActive)
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }

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
                    val inPip = (context as? Activity)?.isInPictureInPictureMode == true
                    if (reelsShouldPauseOnStop(inPictureInPicture = inPip)) {
                        player?.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    player?.playWhenReady = reelsShouldRunPlayback(
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
    if (isReelImageUrl(videoUrl)) {
        val photoClock = remember(videoUrl) { ReelsWatchClock() }
        var photoCompleted by remember(videoUrl) { mutableStateOf(false) }
        // A still has no player: its playback state is the page intent gated by the same
        // foreground contract as a video (pause and background stop the dwell).
        val photoEligible = isActive && isPlaying && isLifecycleStarted && !photoCompleted
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            AsyncImage(
                model = videoUrl,
                contentDescription = videoDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Dwell accumulates only eligible time: 2 s of foreground playback followed by a
        // 10 s pause/background is still 2 s of the minimum 5 s, never 12 s.
        LaunchedEffect(photoEligible) {
            val now = SystemClock.elapsedRealtime()
            if (!photoEligible) {
                photoClock.stop(now)
                onPlaybackRunningChanged(false)
                onBufferingChanged(false)
                return@LaunchedEffect
            }
            onBufferingChanged(false)
            onPlaybackRunningChanged(true)
            photoClock.start(now)
            while (true) {
                val dwellMs = photoClock.elapsedMs(SystemClock.elapsedRealtime())
                onProgressUpdate((dwellMs.toFloat() / REELS_MIN_DWELL_MS).coerceIn(0f, 1f))
                if (dwellMs >= REELS_MIN_DWELL_MS) break
                delay(250)
            }
            photoClock.stop(SystemClock.elapsedRealtime())
            onProgressUpdate(1f)
            photoCompleted = true
        }
        // A completed still advances only when there is somewhere to go; with auto-advance
        // off or on the last page it stays at full progress, mirroring a looping video.
        LaunchedEffect(photoCompleted, isActive, isLifecycleStarted, isAutoAdvance, isLastPage) {
            if (photoCompleted && isActive && isLifecycleStarted && isAutoAdvance && !isLastPage) {
                onVideoCompleted()
            }
        }
        // A fresh appearance starts a fresh dwell: returning to a still shows it for the
        // minimum time again instead of advancing instantly on the accumulated total.
        LaunchedEffect(isActive) {
            if (!isActive) {
                photoCompleted = false
                photoClock.reset()
            }
        }
        return
    }
    val networkClient = remember { Injekt.get<NetworkHelper>().client }
    // Listener and surface callbacks are created once with the player; they must read the
    // CURRENT composition values, not the ones captured on first composition.
    val currentCropMode by rememberUpdatedState(isCropMode)
    // CDN rate-limit (429) auto-recovery: silent backoff re-prepares before the error
    // snackbar is allowed to surface (device logcat evidence). The counter and the pending
    // attempt belong to one media URL; replacing the player drops both.
    var autoRetry429Count by remember(videoUrl) { mutableIntStateOf(0) }
    var pending429Retry by remember(videoUrl) { mutableIntStateOf(0) }
    var isFirstFrameRendered by remember(videoUrl) { mutableStateOf(false) }
    // P05: playback visibility time for the minimum-dwell rule — wall clock, pauses and
    // buffering no longer count (see reelsEndHold).
    val dwellClock = remember(videoUrl) { ReelsWatchClock() }
    // Non-null while holding a finished still/short reel on screen before advancing.
    var endHold by remember(videoUrl) { mutableStateOf<ReelsEndHold?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Playback position to restore after a quality (URL) switch within the same page.
    var restorePositionMs by remember { mutableLongStateOf(0L) }
    // A seek request that arrived while the player was still cold (duration unknown) is
    // parked here and applied by the listener at STATE_READY — otherwise the B3.1 resume
    // seek is silently dropped on a directly activated (non-preloaded) clip.
    var pendingSeekFraction by remember { mutableFloatStateOf(-1f) }
    // The media URL the current player instance was built for, so a URL change (quality
    // toggle) is handled explicitly inside the lifecycle effect instead of via the
    // DisposableEffect disposal order.
    var createdForUrl by remember { mutableStateOf<String?>(null) }

    fun updateMatrix(tv: TextureView?, vw: Int, vh: Int, crop: Boolean) {
        if (tv == null || vw <= 0 || vh <= 0) return
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        val matrix = Matrix()
        val sx: Float
        val sy: Float

        val videoRatio = vw.toFloat() / vh.toFloat()
        val viewRatio = viewW / viewH

        if (crop) {
            // Fill screen by cropping
            if (videoRatio > viewRatio) {
                sx = (viewH * vw / vh) / viewW
                sy = 1f
            } else {
                sx = 1f
                sy = (viewW * vh / vw) / viewH
            }
        } else {
            // Fit screen preserving full aspect ratio
            if (videoRatio > viewRatio) {
                sx = 1f
                sy = (viewW * vh / vw) / viewH
            } else {
                sx = (viewH * vw / vh) / viewW
                sy = 1f
            }
        }

        matrix.setScale(sx, sy, viewW / 2f, viewH / 2f)
        tv.setTransform(matrix)
    }

    // Player lifecycle: create for active or preloaded pages, release otherwise.
    LaunchedEffect(videoUrl, isActive, isPreload) {
        val needed = isActive || isPreload
        if (!needed) {
            dwellClock.stop(SystemClock.elapsedRealtime())
            player?.let { p ->
                if (p.duration > 0) restorePositionMs = p.currentPosition
                p.release()
            }
            player = null
            createdForUrl = null
            isFirstFrameRendered = false
            endHold = null
            return@LaunchedEffect
        }

        if (createdForUrl != null && createdForUrl != videoUrl) {
            // Quality switch within the page: capture the position BEFORE releasing so it
            // seeks back once the new source reaches STATE_READY.
            player?.let { p ->
                if (p.duration > 0) restorePositionMs = p.currentPosition
                p.release()
            }
            player = null
            isFirstFrameRendered = false
        }

        if (player == null) {
            // A re-created player starts a new retry budget; a pending 429 attempt for the
            // released instance must never fire against this one.
            autoRetry429Count = 0
            pending429Retry = 0
            // Short clips: cap buffering so a preload player doesn't hoard tens of MB /
            // compete with the active video for bandwidth (media3 default is ~50s).
            // minBufferMs must be >= both playback thresholds (media3 assertion).
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 15_000, 2_500, 5_000)
                .build()
            val exoPlayer = ExoPlayer.Builder(context)
                // Request audio focus only while the reel is AUDIBLE (audit H4): a muted feed
                // must not duck/pause the user's music. Unmuting re-requests focus below;
                // audible reels still arbitrate with calls and concurrent page players.
                .setAudioAttributes(reelsAudioAttributes(), /* handleAudioFocus = */ !isMuted)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    repeatMode = if (isAutoAdvance && !isLastPage) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    volume = if (isMuted) 0f else 1f
                    // App network stack (cookies/DoH/proxy) + disk cache for repeat watches.
                    // Feed plugins implement AnimeFeedSource (not AnimeHttpSource), so `headers`
                    // is empty for them and Bunny-CDN 403s the media requests: fall back to a
                    // generic same-origin Referer derived from the item's page URL.
                    val requestHeaders = reelsRequestHeaders(headers, webUrl)
                    // DefaultDataSource routes file:///content:// (B3.3 offline copies) to local readers and
                    // delegates http(s) to the OkHttp factory — OkHttp alone rejects file:// URLs.
                    val upstream = DefaultDataSource.Factory(
                        context,
                        OkHttpDataSource.Factory(networkClient)
                            .setDefaultRequestProperties(requestHeaders),
                    )
                    val dataSourceFactory = CacheDataSource.Factory()
                        .setCache(getReelsVideoCache(context.applicationContext))
                        .setUpstreamDataSourceFactory(upstream)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    // DefaultMediaSourceFactory infers the type (progressive today, HLS/DASH
                    // ready); customCacheKey keys the progressive cache by the stable reel id
                    // instead of the signed CDN URL. setCustomCacheKey is only valid for
                    // progressive content — media3 rejects it for adaptive sources — so the
                    // key applies only to a whitelist of container suffixes; everything else
                    // (m3u8/mpd manifests, unknown containers) keeps the URL-derived key.
                    val isProgressiveContainer = isProgressiveReelUrl(videoUrl)
                    val mediaItem = MediaItem.Builder()
                        .setUri(videoUrl)
                        .apply { if (cacheKey != null && isProgressiveContainer) setCustomCacheKey(cacheKey) }
                        .build()
                    setMediaSource(DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem))
                    playWhenReady = false
                    // P03: callbacks from a replaced/released instance must never report the
                    // previous player's state over the live one.
                    val owner = this
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    autoRetry429Count = 0
                                    val durSec = duration.toFloat() / 1000f
                                    if (durSec > 0) onDurationKnown(durSec)
                                    if (restorePositionMs > 0) {
                                        seekTo(restorePositionMs)
                                        restorePositionMs = 0L
                                    }
                                    // Apply a seek parked while the player was cold (resume-seek).
                                    if (pendingSeekFraction >= 0f && duration > 0) {
                                        seekTo((pendingSeekFraction * duration).toLong())
                                        pendingSeekFraction = -1f
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // repeatMode==ONE (auto-advance off, or the last page) never
                                    // reaches ENDED and a preload player has playWhenReady=false,
                                    // so reaching ENDED here means the active page finished ->
                                    // advance. Do NOT capture isActive/isAutoAdvance (they go
                                    // stale on preload->active).
                                    // Stills and very short clips reach ENDED within moments and
                                    // would flip to the next page before they can be seen: hold
                                    // them for the minimum dwell, looping real videos meanwhile.
                                    // P05: only visible playback accrues — the clock is stopped
                                    // by pause/buffering/background and ignores loading time.
                                    val dwellMs = dwellClock.elapsedMs(SystemClock.elapsedRealtime())
                                    val hold = reelsEndHold(
                                        durationMs = duration.coerceAtLeast(0L),
                                        dwellMs = dwellMs,
                                        minDwellMs = REELS_MIN_DWELL_MS,
                                    )
                                    if (hold == null) {
                                        onVideoCompleted()
                                    } else {
                                        if (hold.loop) repeatMode = Player.REPEAT_MODE_ONE
                                        endHold = hold
                                    }
                                }
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            videoWidth = videoSize.width
                            videoHeight = videoSize.height
                            updateMatrix(textureViewRef, videoSize.width, videoSize.height, currentCropMode)
                            if (videoSize.width > 0 && videoSize.height > 0) {
                                onVideoLandscapeKnown(videoSize.width > videoSize.height)
                            }
                        }

                        override fun onRenderedFirstFrame() {
                            isFirstFrameRendered = true
                            // The dwell clock measures visible playback: a player can already
                            // be playing while the first frame is still being decoded.
                            if (owner.isPlaying) dwellClock.start(SystemClock.elapsedRealtime())
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            // P03: isPlaying is the REAL state (READY + playWhenReady +
                            // not suppressed). playWhenReady alone reported buffering,
                            // audio-focus suppression and ended clips as "running".
                            if (player !== owner) return
                            if (isPlaying && isFirstFrameRendered) {
                                dwellClock.start(SystemClock.elapsedRealtime())
                            } else {
                                dwellClock.stop(SystemClock.elapsedRealtime())
                            }
                            onPlaybackRunningChanged(isPlaying)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            logcat(LogPriority.ERROR) {
                                "Reels player error ${error.errorCodeName} on $videoUrl"
                            }
                            // CDN rate limit (device logcat: HTTP 429 on the media GET with a
                            // valid signed URL): back off and re-prepare silently a few times,
                            // keeping the buffering spinner up, before surfacing the error.
                            var cause: Throwable? = error
                            var rateLimited = false
                            while (cause != null) {
                                if (cause is HttpDataSource.InvalidResponseCodeException &&
                                    cause.responseCode == 429
                                ) {
                                    rateLimited = true
                                    break
                                }
                                cause = cause.cause
                            }
                            if (rateLimited && autoRetry429Count < MAX_PLAYBACK_429_RETRIES) {
                                autoRetry429Count++
                                pending429Retry = autoRetry429Count
                                onBufferingChanged(true)
                                return
                            }
                            onPlaybackError(error.localizedMessage ?: error.errorCodeName)
                        }
                    })
                    prepare()
                }
            player = exoPlayer
            createdForUrl = videoUrl
            // P03: a fresh instance has not confirmed playback yet. Without this the feed
            // would keep the previous reel's running state while the new one buffers.
            onPlaybackRunningChanged(false)
        }

        player?.let { p ->
            if (isActive) {
                currentSurface?.takeIf { it.isValid }?.let { p.setVideoSurface(it) }
                p.playWhenReady = isPlaying
            } else {
                // Preload: buffer without rendering or playing audio.
                p.setVideoSurface(null)
                p.playWhenReady = false
            }
        }
    }

    // Minimum-dwell hold: keep a finished short clip looping / a still image on screen,
    // then advance. Re-evaluated against auto-advance, pause and the foreground state so
    // the feed never moves on by itself in the background or while the user switched
    // auto-advance off or paused to look at the frame.
    endHold?.let { hold ->
        LaunchedEffect(hold, isAutoAdvance, isPlaying, isActive, isLifecycleStarted) {
            if (isAutoAdvance && isPlaying && isActive && isLifecycleStarted) {
                delay(hold.remainingMs)
                endHold = null
                onVideoCompleted()
            }
        }
    }

    // React to Surface becoming available or updated.
    LaunchedEffect(currentSurface) {
        val s = currentSurface
        if (s != null && s.isValid && isActive) {
            player?.setVideoSurface(s)
        }
    }

    // React to crop mode changes.
    LaunchedEffect(isCropMode, videoWidth, videoHeight, textureViewRef) {
        updateMatrix(textureViewRef, videoWidth, videoHeight, isCropMode)
    }

    // React to seek request.
    LaunchedEffect(seekToFraction) {
        seekToFraction?.let { frac ->
            player?.let { p ->
                if (p.duration > 0) {
                    p.seekTo((frac.coerceIn(0f, 1f) * p.duration).toLong())
                } else {
                    pendingSeekFraction = frac.coerceIn(0f, 1f)
                }
            }
        }
    }

    // React to auto-advance / last-page changes dynamically.
    LaunchedEffect(isAutoAdvance, isLastPage) {
        player?.repeatMode = if (isAutoAdvance && !isLastPage) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    // Manual retry after a surfaced playback error: explicit restart from 0. P02: the play
    // intent still comes from the live eligibility gate — a stale snackbar action must not
    // start a paused, backgrounded, or already-replaced page.
    LaunchedEffect(retrySignal) {
        if (retrySignal > 0 && currentIsActive) {
            autoRetry429Count = 0
            pending429Retry = 0
            player?.apply {
                seekTo(0)
                prepare()
                playWhenReady = reelsShouldRunPlayback(
                    isActive = currentIsActive,
                    isPlaying = currentIsPlaying,
                    lifecycleStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
                )
            }
        }
    }

    // 429 backoff: re-prepare WITHOUT resetting the position and WITHOUT forcing playback.
    // A pause, background stop, page change, or player replacement cancels the wait; the
    // attempt stays parked and is retried once playback is eligible again.
    LaunchedEffect(pending429Retry, player, isActive, isPlaying, isLifecycleStarted) {
        val attempt = pending429Retry
        val retryPlayer = player
        if (attempt <= 0 || retryPlayer == null) return@LaunchedEffect
        if (!reelsShouldRunPlayback(isActive, isPlaying, isLifecycleStarted)) {
            // No spinner over a reel that is not allowed to retry right now.
            onBufferingChanged(false)
            return@LaunchedEffect
        }
        delay(PLAYBACK_429_BACKOFF_MS * attempt)
        val stillStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (player !== retryPlayer || !reelsShouldRunPlayback(currentIsActive, currentIsPlaying, stillStarted)) {
            return@LaunchedEffect
        }
        retryPlayer.prepare()
        retryPlayer.playWhenReady = true
        pending429Retry = 0
    }

    // React to play / pause changes.
    LaunchedEffect(isPlaying, isActive) {
        player?.let { p ->
            if (isActive) p.playWhenReady = isPlaying
        }
    }

    // React to mute / unmute changes. Audit H4: focus follows audibility — muting ABANDONS
    // audio focus (the user's music resumes), unmuting re-requests it.
    LaunchedEffect(isMuted) {
        player?.apply {
            volume = if (isMuted) 0f else 1f
            setAudioAttributes(reelsAudioAttributes(), /* handleAudioFocus = */ !isMuted)
        }
    }

    // React to speed changes (long-press 2x).
    LaunchedEffect(playbackSpeed) {
        player?.setPlaybackSpeed(playbackSpeed)
    }

    // Track playback progress. Runs while the page is active (also while paused) so seek
    // math and the scrubber stay correct when playback is stopped.
    LaunchedEffect(isActive) {
        while (isActive) {
            player?.let { p ->
                val duration = p.duration
                if (duration > 0) {
                    onProgressUpdate((p.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f))
                } else if (isFirstFrameRendered) {
                    // No media duration: run the bar over the same playback dwell window.
                    val dwellMs = dwellClock.elapsedMs(SystemClock.elapsedRealtime())
                    onProgressUpdate((dwellMs.toFloat() / REELS_MIN_DWELL_MS).coerceIn(0f, 1f))
                }
            }
            delay(250)
        }
    }

    // Unmount-only release: URL changes are handled explicitly inside the lifecycle effect
    // (createdForUrl) so the position capture cannot race the disposal order.
    DisposableEffect(Unit) {
        onDispose {
            dwellClock.stop(SystemClock.elapsedRealtime())
            player?.let { p ->
                if (p.duration > 0) restorePositionMs = p.currentPosition
                p.release()
            }
            player = null
            // The Surface belongs to the TextureView and is released in onSurfaceTextureDestroyed;
            // do not release it here so a URL change (quality toggle) keeps the render target.
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. Ambient Blurred Background (Aurora glow behind letterboxed videos).
        // Blur a small downsampled bitmap once (cached by Coil) instead of a full-screen
        // RenderEffect blur that re-renders on every pager scroll frame.
        if (posterUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(posterUrl)
                    // Same size as the placeholder overlay so Coil dedupes to a single decode.
                    .size(POSTER_DECODE_SIZE, POSTER_DECODE_SIZE)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp)
                    .alpha(0.4f),
            )
        }

        // 2. Video Surface Layer with Matrix Aspect Ratio control
        if (isActive) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        textureViewRef = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val s = Surface(st)
                                currentSurface = s
                                player?.setVideoSurface(s)
                                updateMatrix(this@apply, videoWidth, videoHeight, currentCropMode)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                                updateMatrix(this@apply, videoWidth, videoHeight, currentCropMode)
                            }

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                // Detach the player BEFORE releasing the Surface so the render
                                // thread never draws into a released Surface (crash window).
                                player?.setVideoSurface(null)
                                currentSurface?.release()
                                currentSurface = null
                                textureViewRef = null
                                isFirstFrameRendered = false
                                dwellClock.stop(SystemClock.elapsedRealtime())
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                isFirstFrameRendered = true
                            }
                        }
                    }
                },
                update = { tv ->
                    textureViewRef = tv
                    updateMatrix(tv, videoWidth, videoHeight, currentCropMode)
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

        // 3. Poster / Thumbnail placeholder overlay (hides only AFTER the first video frame renders)
        AnimatedVisibility(
            visible = !isFirstFrameRendered && posterUrl.isNotBlank(),
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(250)),
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

// One decode size shared by the blurred background and the placeholder overlay.
private const val POSTER_DECODE_SIZE = 480

/** 429 auto-recovery: this many silent backoff re-prepares before the error surfaces. */
private const val MAX_PLAYBACK_429_RETRIES = 3
private const val PLAYBACK_429_BACKOFF_MS = 3_000L

/** Minimum time a reel stays on screen before auto-advance moves on (stills/short clips). */
const val REELS_MIN_DWELL_MS = 5_000L

/**
 * What the player should do when the active reel reaches its natural end while
 * auto-advance is on: [remainingMs] of hold before advancing, and whether a real
 * video should loop meanwhile (stills have nothing to loop).
 */
data class ReelsEndHold(val remainingMs: Long, val loop: Boolean)

/**
 * Decides the end-of-reel action. Stills (unknown/zero duration) and clips shorter
 * than [minDwellMs] would otherwise flip to the next page before they can be seen.
 *
 * @param durationMs media duration at the end (<= 0 / unset for stills)
 * @param dwellMs accumulated visible PLAYBACK time (a [ReelsWatchClock] stopped by pauses,
 *   buffering and background), so only time the user could actually see counts
 * @return null to advance right away once the dwell is satisfied
 */
internal fun reelsEndHold(durationMs: Long, dwellMs: Long, minDwellMs: Long = REELS_MIN_DWELL_MS): ReelsEndHold? {
    val remainingMs = minDwellMs - dwellMs
    if (remainingMs <= 0) return null
    return ReelsEndHold(remainingMs = remainingMs, loop = durationMs > 0)
}

/** P01: a real background stop clears the play intent; PiP is the only exempt stop state. */
internal fun reelsShouldPauseOnStop(inPictureInPicture: Boolean): Boolean = !inPictureInPicture

/** P02: the single playback-eligibility gate every retry path must derive its intent from. */
internal fun reelsShouldRunPlayback(isActive: Boolean, isPlaying: Boolean, lifecycleStarted: Boolean): Boolean =
    isActive && isPlaying && lifecycleStarted

private val reelsVideoCacheOwner = ReelsCacheOwner<SimpleCache>()

// Process-wide LRU disk cache so re-watching a reel doesn't hit the network again.
// Must be called with an application context: StandaloneDatabaseProvider is a
// SQLiteOpenHelper that would otherwise retain the first Activity forever.
private fun getReelsVideoCache(context: android.content.Context): SimpleCache {
    return reelsVideoCacheOwner.getOrCreate {
        SimpleCache(
            File(context.cacheDir, REELS_CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(REELS_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }
}

/**
 * Empties the reels video disk cache; returns the freed bytes. While the process-wide
 * SimpleCache is live, spans are removed through it — deleting files under an active
 * index would corrupt it. A reel playing over a removed span falls back to the network
 * (FLAG_IGNORE_CACHE_ON_ERROR). With no live instance the folder (index included) is
 * deleted outright; that decision and the first cache creation are serialized by
 * [ReelsCacheOwner], so the directory cannot disappear under an initializing cache.
 * File IO: call from a background dispatcher.
 */
internal fun clearReelsVideoCache(context: android.content.Context): Long {
    val dir = File(context.cacheDir, REELS_CACHE_DIR)
    val freedWithoutCache = reelsVideoCacheOwner.clearIfAbsent {
        val freed = dir.walk().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        freed
    }
    if (freedWithoutCache != null) return freedWithoutCache

    // The owner never releases a created cache, so this is non-null once clearIfAbsent
    // declined to run.
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

/** Shared media audio attributes; focus handling follows audibility (audit H4). */
private fun reelsAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
    .build()

/**
 * Reel media request headers: the feed's own headers, or — when none carries a Referer (feed
 * plugins implement AnimeFeedSource, not AnimeHttpSource, so Bunny-CDN-style checks reject
 * their media requests) — a generic same-origin Referer derived from the item's page URL.
 */
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

/** Photo-reel detection: still-image containers ride the feed as dwell-held stills. */
internal fun isReelImageUrl(url: String): Boolean =
    Uri.parse(url).lastPathSegment
        ?.substringAfterLast('.', "")
        ?.lowercase() in STILL_IMAGE_EXTENSIONS

private val STILL_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")

/** Head-prewarm chunk: how many leading bytes of a reel are warmed ahead of the player window. */
internal const val HEAD_PREWARM_BYTES = 512L * 1024L

/**
 * C-intermediate (audit): warms the first [HEAD_PREWARM_BYTES] of a reel into the SAME
 * process-wide video cache under the SAME [cacheKey] the player will later use, so deep
 * swipes (beyond the single-player preload window) start from disk instead of a cold CDN
 * round-trip. No player, no decoder — bytes only, which is why the forward player preload
 * stays at one neighbor (three live decoders competed for bandwidth).
 *
 * Skips non-http URLs (offline copies are already local) and non-progressive containers
 * (their cache key is URL-derived, a stable-key prewarm would never be read back), and
 * anything already cached. Failures are swallowed: prewarm is best-effort.
 * Blocking network + file IO: call from a background dispatcher.
 */
internal fun prewarmReelHead(
    context: android.content.Context,
    url: String,
    cacheKey: String,
    headers: Map<String, String>,
    webUrl: String?,
) {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return
    if (!isProgressiveReelUrl(url)) return
    val appContext = context.applicationContext
    val cache = getReelsVideoCache(appContext)
    if (cache.isCached(cacheKey, 0, HEAD_PREWARM_BYTES)) return
    val networkClient: OkHttpClient = Injekt.get<NetworkHelper>().client
    val upstream = DefaultDataSource.Factory(
        appContext,
        OkHttpDataSource.Factory(networkClient)
            .setDefaultRequestProperties(reelsRequestHeaders(headers, webUrl)),
    )
    val dataSource = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstream)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .createDataSource()
    val spec = DataSpec.Builder()
        .setUri(url)
        .setKey(cacheKey)
        .setLength(HEAD_PREWARM_BYTES)
        .build()
    runCatching {
        dataSource.open(spec)
        try {
            // CacheDataSource writes spans into the cache as bytes are pulled through read();
            // the length-bounded spec stops the drain at HEAD_PREWARM_BYTES even when the CDN
            // ignores the Range hint and offers the whole body.
            val buffer = ByteArray(64 * 1024)
            while (dataSource.read(buffer, 0, buffer.size) != C.RESULT_END_OF_INPUT) {
                // drain
            }
        } finally {
            dataSource.close()
        }
    }
}

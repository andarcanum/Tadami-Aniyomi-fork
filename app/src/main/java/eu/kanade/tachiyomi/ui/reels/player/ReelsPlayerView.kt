package eu.kanade.tachiyomi.ui.reels.player

import android.graphics.Matrix
import android.graphics.SurfaceTexture
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
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
    onPlaybackError: (String) -> Unit = {},
    onBufferingChanged: (Boolean) -> Unit = {},
    playbackSpeed: Float = 1f,
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val networkClient = remember { Injekt.get<NetworkHelper>().client }
    // Listener and surface callbacks are created once with the player; they must read the
    // CURRENT composition values, not the ones captured on first composition.
    val currentCropMode by rememberUpdatedState(isCropMode)
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isFirstFrameRendered by remember(videoUrl) { mutableStateOf(false) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // Playback position to restore after a quality (URL) switch within the same page.
    var restorePositionMs by remember { mutableLongStateOf(0L) }
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
            player?.let { p ->
                if (p.duration > 0) restorePositionMs = p.currentPosition
                p.release()
            }
            player = null
            createdForUrl = null
            isFirstFrameRendered = false
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
            // Short clips: cap buffering so a preload player doesn't hoard tens of MB /
            // compete with the active video for bandwidth (media3 default is ~50s).
            // minBufferMs must be >= both playback thresholds (media3 assertion).
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(5_000, 15_000, 2_500, 5_000)
                .build()
            val exoPlayer = ExoPlayer.Builder(context)
                // Request audio focus: reels must duck/pause for calls and not play over the
                // user's music, and concurrent page players must arbitrate with each other.
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true,
                )
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .build()
                .apply {
                    repeatMode = if (isAutoAdvance && !isLastPage) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    volume = if (isMuted) 0f else 1f
                    // App network stack (cookies/DoH/proxy) + disk cache for repeat watches.
                    val upstream = OkHttpDataSource.Factory(networkClient)
                        .setDefaultRequestProperties(headers)
                    val dataSourceFactory = CacheDataSource.Factory()
                        .setCache(getReelsVideoCache(context.applicationContext))
                        .setUpstreamDataSourceFactory(upstream)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    // DefaultMediaSourceFactory infers the type (progressive today, HLS/DASH
                    // ready); customCacheKey keys the progressive cache by the stable reel id
                    // instead of the signed CDN URL. Not valid for adaptive streams.
                    val mediaItem = MediaItem.Builder()
                        .setUri(videoUrl)
                        .apply { if (cacheKey != null) setCustomCacheKey(cacheKey) }
                        .build()
                    setMediaSource(DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem))
                    playWhenReady = false
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                            when (playbackState) {
                                Player.STATE_READY -> {
                                    val durSec = duration.toFloat() / 1000f
                                    if (durSec > 0) onDurationKnown(durSec)
                                    if (restorePositionMs > 0) {
                                        seekTo(restorePositionMs)
                                        restorePositionMs = 0L
                                    }
                                }
                                Player.STATE_ENDED -> {
                                    // repeatMode==ONE (auto-advance off, or the last page) never
                                    // reaches ENDED and a preload player has playWhenReady=false,
                                    // so reaching ENDED here means the active page finished ->
                                    // advance. Do NOT capture isActive/isAutoAdvance (they go
                                    // stale on preload->active).
                                    onVideoCompleted()
                                }
                            }
                        }

                        override fun onVideoSizeChanged(videoSize: VideoSize) {
                            videoWidth = videoSize.width
                            videoHeight = videoSize.height
                            updateMatrix(textureViewRef, videoSize.width, videoSize.height, currentCropMode)
                        }

                        override fun onRenderedFirstFrame() {
                            isFirstFrameRendered = true
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            logcat(LogPriority.ERROR) {
                                "Reels player error ${error.errorCodeName} on $videoUrl"
                            }
                            onPlaybackError(error.localizedMessage ?: error.errorCodeName)
                        }
                    })
                    prepare()
                }
            player = exoPlayer
            createdForUrl = videoUrl
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
                }
            }
        }
    }

    // React to auto-advance / last-page changes dynamically.
    LaunchedEffect(isAutoAdvance, isLastPage) {
        player?.repeatMode = if (isAutoAdvance && !isLastPage) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    // Retry after a playback error: re-prepare the same media from the beginning.
    LaunchedEffect(retrySignal) {
        if (retrySignal > 0) {
            player?.apply {
                seekTo(0)
                prepare()
                playWhenReady = true
            }
        }
    }

    // React to play / pause changes.
    LaunchedEffect(isPlaying, isActive) {
        player?.let { p ->
            if (isActive) p.playWhenReady = isPlaying
        }
    }

    // React to mute / unmute changes.
    LaunchedEffect(isMuted) {
        player?.volume = if (isMuted) 0f else 1f
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
                }
            }
            delay(250)
        }
    }

    // Pause when the app goes to background so audio/video/network don't keep running.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive, isPlaying) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player?.let { if (it.isPlaying) it.pause() }
                Lifecycle.Event.ON_START -> player?.let { it.playWhenReady = isActive && isPlaying }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Unmount-only release: URL changes are handled explicitly inside the lifecycle effect
    // (createdForUrl) so the position capture cannot race the disposal order.
    DisposableEffect(Unit) {
        onDispose {
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

private const val REELS_CACHE_BYTES = 200L * 1024 * 1024

// One decode size shared by the blurred background and the placeholder overlay.
private const val POSTER_DECODE_SIZE = 480

private var reelsVideoCache: SimpleCache? = null

// Process-wide LRU disk cache so re-watching a reel doesn't hit the network again.
// Must be called with an application context: StandaloneDatabaseProvider is a
// SQLiteOpenHelper that would otherwise retain the first Activity forever.
private fun getReelsVideoCache(context: android.content.Context): SimpleCache {
    return reelsVideoCache ?: SimpleCache(
        File(context.cacheDir, "reels_video"),
        LeastRecentlyUsedCacheEvictor(REELS_CACHE_BYTES),
        StandaloneDatabaseProvider(context),
    ).also { reelsVideoCache = it }
}

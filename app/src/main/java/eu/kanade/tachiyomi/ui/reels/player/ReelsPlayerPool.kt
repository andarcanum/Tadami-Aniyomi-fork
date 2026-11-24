package eu.kanade.tachiyomi.ui.reels.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/**
 * Pooled [ExoPlayer] + [ReelsDynamicLoadControl] pair.
 *
 * Reusing 2–3 instances across vertical swipes avoids destroying/allocating hardware MediaCodec
 * decoders, AudioTracks, and OkHttpClients on every fling (cutting swipe start latency and
 * preventing decoder-limit errors on mid-range devices).
 */
internal data class PooledReelsPlayer(
    val player: ExoPlayer,
    val loadControl: ReelsDynamicLoadControl,
)

@OptIn(UnstableApi::class)
internal object ReelsPlayerPool {

    private const val MAX_IDLE_PLAYERS = 3

    private val idle = ArrayDeque<PooledReelsPlayer>()
    private var activeScreens = 0

    /**
     * Shared cache-less OkHttpClient for all reel players (never rebuilt per card).
     * Uses a dedicated Dispatcher with a higher per-host cap so player streams to a local proxy
     * (127.0.0.1) or single CDN host never queue behind Coil thumbnail requests.
     */
    val sharedNetworkClient: OkHttpClient by lazy {
        val playerDispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        Injekt.get<NetworkHelper>().client.newBuilder()
            .cache(null)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool())
            .dispatcher(playerDispatcher)
            .build()
    }

    /** Retains the pool while at least one [eu.kanade.tachiyomi.ui.reels.ReelsFeedScreen] is live. */
    fun retainScreen() {
        activeScreens++
    }

    /** Releases idle pooled players once the last reels screen leaves composition. */
    fun releaseScreen() {
        activeScreens = (activeScreens - 1).coerceAtLeast(0)
        if (activeScreens == 0) {
            drainIdle()
        }
    }

    fun acquire(
        context: Context,
        isActive: Boolean,
        isMuted: Boolean,
    ): PooledReelsPlayer {
        val reused = idle.pollFirst()
        if (reused != null) {
            reused.loadControl.isActivePage = isActive
            reused.loadControl.isBackgrounded = false
            reused.loadControl.isMeteredNetwork = false
            reused.player.apply {
                playWhenReady = false
                setVideoSurface(null)
                stop()
                clearMediaItems()
                setPlaybackSpeed(1f)
                setSeekParameters(SeekParameters.DEFAULT)
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (isMuted) 0f else 1f
                setAudioAttributes(reelsAudioAttributes(), /* handleAudioFocus = */ !isMuted)
            }
            return reused
        }
        val appContext = context.applicationContext
        val loadControl = ReelsDynamicLoadControl(initialActive = isActive)
        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(reelsAudioAttributes(), /* handleAudioFocus = */ !isMuted)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = false
                volume = if (isMuted) 0f else 1f
            }
        return PooledReelsPlayer(player = player, loadControl = loadControl)
    }

    fun release(entry: PooledReelsPlayer, listener: Player.Listener?) {
        entry.loadControl.isActivePage = false
        entry.loadControl.isBackgrounded = false
        entry.loadControl.isMeteredNetwork = false
        val p = entry.player
        if (listener != null) {
            runCatching { p.removeListener(listener) }
        }
        runCatching {
            p.playWhenReady = false
            p.setVideoSurface(null)
            p.stop()
            p.clearMediaItems()
            p.setSeekParameters(SeekParameters.DEFAULT)
        }
        if (activeScreens > 0 && idle.size < MAX_IDLE_PLAYERS) {
            idle.addLast(entry)
        } else {
            runCatching { p.release() }
        }
    }

    fun drainIdle() {
        while (idle.isNotEmpty()) {
            val entry = idle.pollFirst() ?: break
            runCatching { entry.player.release() }
        }
    }

    internal fun reelsAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()
}

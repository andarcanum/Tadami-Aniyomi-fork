package eu.kanade.tachiyomi.ui.reels.player

import androidx.annotation.OptIn
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator

/**
 * Dynamic LoadControl for short-form reels playback:
 *
 * - Fast startup: [BUFFER_FOR_PLAYBACK_MS] (600 ms) and [BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS]
 *   (1 200 ms) instead of ExoPlayer's 2.5 s / 5 s defaults so swipes start almost immediately.
 * - Dynamic buffer ceiling: while a player is preloading an adjacent card ([isActivePage] == false),
 *   loading pauses once [PRELOAD_MAX_BUFFER_US] (8 s, or 4 s on metered networks) is buffered so
 *   preload does not waste bandwidth/battery. When the card becomes settled ([isActivePage] == true),
 *   the ceiling opens up to [ACTIVE_MAX_BUFFER_MS] (50 s, or 20 s on metered networks) without
 *   recreating the ExoPlayer instance.
 * - Background gate: when [isBackgrounded] is true, loading halts immediately.
 */
@OptIn(UnstableApi::class)
internal class ReelsDynamicLoadControl(
    initialActive: Boolean = false,
) : LoadControl {

    @Volatile
    var isActivePage: Boolean = initialActive

    @Volatile
    var isBackgrounded: Boolean = false

    @Volatile
    var isMeteredNetwork: Boolean = false

    private val delegate: DefaultLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            ACTIVE_MIN_BUFFER_MS,
            ACTIVE_MAX_BUFFER_MS,
            BUFFER_FOR_PLAYBACK_MS,
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setBackBuffer(BACK_BUFFER_DURATION_MS, /* retainBackBufferFromKeyframe = */ true)
        .build()

    override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

    override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

    override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
        delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (isBackgrounded) return false
        val preloadLimitUs = if (isMeteredNetwork) METERED_PRELOAD_MAX_BUFFER_US else PRELOAD_MAX_BUFFER_US
        if (!isActivePage && parameters.bufferedDurationUs >= preloadLimitUs) {
            return false
        }
        if (isActivePage && isMeteredNetwork && parameters.bufferedDurationUs >= METERED_ACTIVE_MAX_BUFFER_US) {
            return false
        }
        return delegate.shouldContinueLoading(parameters)
    }

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
        delegate.shouldStartPlayback(parameters)

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean {
        if (isBackgrounded) return false
        val preloadLimitUs = if (isMeteredNetwork) METERED_PRELOAD_MAX_BUFFER_US else PRELOAD_MAX_BUFFER_US
        if (bufferedDurationUs >= preloadLimitUs) return false
        return delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)
    }

    companion object {
        internal const val ACTIVE_MIN_BUFFER_MS = 10_000
        internal const val ACTIVE_MAX_BUFFER_MS = 50_000
        internal const val METERED_ACTIVE_MAX_BUFFER_MS = 20_000
        internal const val METERED_ACTIVE_MAX_BUFFER_US = METERED_ACTIVE_MAX_BUFFER_MS * 1_000L
        internal const val PRELOAD_MAX_BUFFER_MS = 8_000
        internal const val PRELOAD_MAX_BUFFER_US = PRELOAD_MAX_BUFFER_MS * 1_000L
        internal const val METERED_PRELOAD_MAX_BUFFER_MS = 4_000
        internal const val METERED_PRELOAD_MAX_BUFFER_US = METERED_PRELOAD_MAX_BUFFER_MS * 1_000L
        internal const val BUFFER_FOR_PLAYBACK_MS = 600
        internal const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_200
        internal const val BACK_BUFFER_DURATION_MS = 10_000
    }
}

package eu.kanade.tachiyomi.ui.reels.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

@OptIn(UnstableApi::class)
class ReelsDynamicLoadControlTest {

    private val playerId = PlayerId.UNSET
    private val timeline = SinglePeriodTimeline(
        /* durationUs = */ 120_000_000L,
        /* isSeekable = */ true,
        /* isDynamic = */ false,
        /* useLiveConfiguration = */ false,
        /* manifest = */ null,
        /* mediaItem = */ MediaItem.EMPTY,
    )
    private val mediaPeriodId = MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0))

    private fun params(bufferedDurationMs: Long): LoadControl.Parameters =
        LoadControl.Parameters(
            playerId,
            timeline,
            mediaPeriodId,
            /* playbackPositionUs = */ 0L,
            /* bufferedDurationUs = */ bufferedDurationMs * 1_000L,
            /* playbackSpeed = */ 1f,
            /* playWhenReady = */ true,
            /* rebuffering = */ false,
            /* targetLiveOffsetUs = */ C.TIME_UNSET,
            /* lastRebufferRealtimeMs = */ C.TIME_UNSET,
        )

    private fun createControl(initialActive: Boolean): ReelsDynamicLoadControl =
        ReelsDynamicLoadControl(initialActive = initialActive).apply {
            onPrepared(playerId)
        }

    @Test
    fun `backgrounded stops loading and preloading immediately`() {
        val control = createControl(initialActive = true)
        control.shouldContinueLoading(params(bufferedDurationMs = 1_000L)) shouldBe true

        control.isBackgrounded = true

        control.shouldContinueLoading(params(bufferedDurationMs = 1_000L)) shouldBe false
        control.shouldContinuePreloading(
            playerId,
            timeline,
            mediaPeriodId,
            bufferedDurationUs = 1_000_000L,
        ) shouldBe false
    }

    @Test
    fun `preload caps buffer at 8s unmetered and 4s metered`() {
        val control = createControl(initialActive = false)

        control.shouldContinueLoading(params(bufferedDurationMs = 5_000L)) shouldBe true
        control.shouldContinueLoading(params(bufferedDurationMs = 8_000L)) shouldBe false
        control.shouldContinuePreloading(
            playerId,
            timeline,
            mediaPeriodId,
            bufferedDurationUs = 8_000_000L,
        ) shouldBe false

        control.isMeteredNetwork = true

        control.shouldContinueLoading(params(bufferedDurationMs = 3_000L)) shouldBe true
        control.shouldContinueLoading(params(bufferedDurationMs = 4_000L)) shouldBe false
        control.shouldContinuePreloading(
            playerId,
            timeline,
            mediaPeriodId,
            bufferedDurationUs = 4_000_000L,
        ) shouldBe false
    }

    @Test
    fun `active page caps buffer at 20s on metered network while allowing deeper buffer unmetered`() {
        val control = createControl(initialActive = true)

        // Start loading below ACTIVE_MIN_BUFFER_MS (10s) so DefaultLoadControl enters loading state.
        control.shouldContinueLoading(params(bufferedDurationMs = 5_000L)) shouldBe true
        control.shouldContinueLoading(params(bufferedDurationMs = 20_000L)) shouldBe true

        control.isMeteredNetwork = true
        control.shouldContinueLoading(params(bufferedDurationMs = 19_000L)) shouldBe true
        control.shouldContinueLoading(params(bufferedDurationMs = 20_000L)) shouldBe false
    }
}

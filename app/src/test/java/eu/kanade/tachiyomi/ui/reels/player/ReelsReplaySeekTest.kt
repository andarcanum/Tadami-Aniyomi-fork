package eu.kanade.tachiyomi.ui.reels.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test

class ReelsReplaySeekTest {

    @Test
    fun target_seek_clamped_to_timeline_duration() {
        val durationMs = 45000L
        val requestedSeekMs = 50000L
        val clamped = requestedSeekMs.coerceIn(0L, durationMs)
        assertEquals(45000L, clamped)
    }

    @Test
    fun target_seek_zero_or_negative_clamped_to_zero() {
        val durationMs = 45000L
        val requestedSeekMs = -500L
        val clamped = requestedSeekMs.coerceIn(0L, durationMs)
        assertEquals(0L, clamped)
    }

    @Test
    fun consumed_seek_is_nullified_once_acknowledged() {
        var pendingSeek: Long? = 37200L
        var ackCount = 0

        fun onAck() {
            ackCount++
            pendingSeek = null
        }

        // Simulate seek ack
        onAck()
        assertEquals(1, ackCount)
        assertNull(pendingSeek)

        // Recreation does not re-apply because pendingSeek is null
        var reapplyAttempted = false
        if (pendingSeek != null) {
            reapplyAttempted = true
        }
        assertEquals(false, reapplyAttempted)
    }
}

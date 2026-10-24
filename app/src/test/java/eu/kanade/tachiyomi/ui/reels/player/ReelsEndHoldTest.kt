package eu.kanade.tachiyomi.ui.reels.player

import androidx.media3.common.C
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsEndHoldTest {

    @Test
    fun `a reel that satisfied the dwell advances right away`() {
        reelsEndHold(durationMs = 8_000, dwellMs = 8_000, minDwellMs = 5_000).shouldBeNull()
        reelsEndHold(durationMs = 800, dwellMs = 30_000, minDwellMs = 5_000).shouldBeNull()
    }

    @Test
    fun `a still image holds for the full dwell without looping`() {
        val hold = reelsEndHold(durationMs = 0, dwellMs = 0, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 5_000
        hold.loop shouldBe false

        // C.TIME_UNSET and any non-positive duration are treated as stills too.
        val unset = reelsEndHold(durationMs = C.TIME_UNSET, dwellMs = 1_000, minDwellMs = 5_000)!!
        unset.remainingMs shouldBe 4_000
        unset.loop shouldBe false
    }

    @Test
    fun `a short video holds for the remaining dwell and loops meanwhile`() {
        val hold = reelsEndHold(durationMs = 2_000, dwellMs = 2_000, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 3_000
        hold.loop shouldBe true
    }

    @Test
    fun `playback time already accrued counts toward the dwell`() {
        // 4.5 s of real playback before the clip ended leaves half a second to hold.
        val hold = reelsEndHold(durationMs = 2_000, dwellMs = 4_500, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 500
        hold.loop shouldBe true
    }

    @Test
    fun `exact dwell boundary advances without a hold`() {
        reelsEndHold(durationMs = 2_000, dwellMs = 5_000, minDwellMs = 5_000).shouldBeNull()
    }
}

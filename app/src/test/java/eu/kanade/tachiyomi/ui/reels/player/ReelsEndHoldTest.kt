package eu.kanade.tachiyomi.ui.reels.player

import androidx.media3.common.C
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsEndHoldTest {

    @Test
    fun `a reel that satisfied the dwell advances right away`() {
        reelsEndHold(durationMs = 8_000, shownMs = 8_000, minDwellMs = 5_000).shouldBeNull()
        reelsEndHold(durationMs = 800, shownMs = 30_000, minDwellMs = 5_000).shouldBeNull()
    }

    @Test
    fun `a still image holds for the full dwell without looping`() {
        val hold = reelsEndHold(durationMs = 0, shownMs = 0, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 5_000
        hold.loop shouldBe false

        // C.TIME_UNSET and any non-positive duration are treated as stills too.
        val unset = reelsEndHold(durationMs = C.TIME_UNSET, shownMs = 1_000, minDwellMs = 5_000)!!
        unset.remainingMs shouldBe 4_000
        unset.loop shouldBe false
    }

    @Test
    fun `a short video holds for the remaining dwell and loops meanwhile`() {
        val hold = reelsEndHold(durationMs = 2_000, shownMs = 2_000, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 3_000
        hold.loop shouldBe true
    }

    @Test
    fun `shown time already counts toward the dwell`() {
        // The user paused on the last frame for a while before the reel ended.
        val hold = reelsEndHold(durationMs = 2_000, shownMs = 4_500, minDwellMs = 5_000)!!
        hold.remainingMs shouldBe 500
        hold.loop shouldBe true
    }

    @Test
    fun `exact dwell boundary advances without a hold`() {
        reelsEndHold(durationMs = 2_000, shownMs = 5_000, minDwellMs = 5_000).shouldBeNull()
    }
}

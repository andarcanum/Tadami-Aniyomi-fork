package eu.kanade.tachiyomi.ui.reels.player

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsPlaybackLifecycleTest {

    @Test
    fun `a real background stop pauses even when the player is buffering`() {
        // P01: Player.isPlaying is false while BUFFERING; the stop handler must not use it
        // as the trigger for clearing playWhenReady.
        reelsShouldPauseOnStop(inPictureInPicture = false) shouldBe true
    }

    @Test
    fun `picture-in-picture is the only stop state that keeps playing`() {
        reelsShouldPauseOnStop(inPictureInPicture = true) shouldBe false
    }

    @Test
    fun `retry playback requires an active page the play intent and a started lifecycle`() {
        reelsShouldRunPlayback(isActive = true, isPlaying = true, lifecycleStarted = true) shouldBe true

        reelsShouldRunPlayback(isActive = false, isPlaying = true, lifecycleStarted = true) shouldBe false
        reelsShouldRunPlayback(isActive = true, isPlaying = false, lifecycleStarted = true) shouldBe false
        reelsShouldRunPlayback(isActive = true, isPlaying = true, lifecycleStarted = false) shouldBe false
    }
}

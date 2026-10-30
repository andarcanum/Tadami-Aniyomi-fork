package eu.kanade.tachiyomi.ui.reels

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReelsDownloadKeyTest {

    @Test
    fun `online download identity is source scoped even though like identity is not`() {
        val state = ReelsFeedScreenModel.State(currentSourceId = 11L)
        state.likeKey("clip", 0) shouldBe "clip"
        reelsDownloadKey(state.currentSourceId, "clip") shouldBe "11:clip"
    }

    @Test
    fun `same video id in two sources never shares a download status`() {
        reelsDownloadKey(11L, "clip") shouldBe "11:clip"
        reelsDownloadKey(22L, "clip") shouldBe "22:clip"
        (reelsDownloadKey(11L, "clip") == reelsDownloadKey(22L, "clip")) shouldBe false
    }

    @Test
    fun `captured source identity remains unchanged after a source switch`() {
        val original = ReelsFeedScreenModel.State(currentSourceId = 11L)
        val capturedKey = reelsDownloadKey(original.currentSourceId, "clip")
        val switched = original.copy(currentSourceId = 22L)
        capturedKey shouldBe "11:clip"
        reelsDownloadKey(switched.currentSourceId, "clip") shouldBe "22:clip"
    }

    @Test
    fun `negative source ids and punctuation in video ids remain unambiguous`() {
        reelsDownloadKey(-11L, "clip:one/two") shouldBe "-11:clip:one/two"
    }

    @Test
    fun `one pod means four things depending on its state`() {
        resolveReelsDownloadTap(false, false, false) shouldBe ReelsDownloadTap.Start
        resolveReelsDownloadTap(true, false, false) shouldBe ReelsDownloadTap.Cancel
        resolveReelsDownloadTap(true, true, false) shouldBe ReelsDownloadTap.Cancel
        resolveReelsDownloadTap(false, true, true) shouldBe ReelsDownloadTap.Resume
        resolveReelsDownloadTap(false, true, false) shouldBe ReelsDownloadTap.Open
    }
}

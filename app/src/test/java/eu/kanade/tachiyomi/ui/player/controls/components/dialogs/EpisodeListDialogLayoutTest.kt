package eu.kanade.tachiyomi.ui.player.controls.components.dialogs

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpisodeListDialogLayoutTest {

    @Test
    fun `episode list dialog layout spec adapts to screen width`() {
        resolveEpisodeListDialogLayoutSpec(480) shouldBe EpisodeListDialogLayoutSpec(
            widthFraction = 0.9f,
            heightFraction = 0.85f,
            maxWidthDp = 560,
        )
        resolveEpisodeListDialogLayoutSpec(700) shouldBe EpisodeListDialogLayoutSpec(
            widthFraction = 0.85f,
            heightFraction = 0.8f,
            maxWidthDp = 760,
        )
        resolveEpisodeListDialogLayoutSpec(1080) shouldBe EpisodeListDialogLayoutSpec(
            widthFraction = 0.75f,
            heightFraction = 0.8f,
            maxWidthDp = 960,
        )
    }
}
